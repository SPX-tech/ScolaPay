package com.scolapay.core.establishment.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Establishments.Establishment;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.establishment.StaffRole;
import com.scolapay.core.identity.CurrentActor;
import com.scolapay.core.identity.Identifier;
import com.scolapay.core.identity.Invitations;
import com.scolapay.core.shared.ApiException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstablishmentService {

    public record NewEstablishment(
            String code,
            String name,
            String kind,
            String timezone,
            String currency,
            Integer ageOfMajority,
            String firstAdminContact) {}

    public record Created(Establishment establishment, Invitations.Issued firstAdminInvitation) {}

    private static final int DEFAULT_AGE_OF_MAJORITY = 18;
    private static final Set<String> STATUSES = Set.of("ACTIVE", "SUSPENDED");

    private final EstablishmentRepository establishments;
    private final EstablishmentAccess access;
    private final Invitations invitations;
    private final CurrentActor actor;
    private final AuditTrail audit;
    private final Clock clock;

    public EstablishmentService(
            EstablishmentRepository establishments,
            EstablishmentAccess access,
            Invitations invitations,
            CurrentActor actor,
            AuditTrail audit,
            Clock clock) {
        this.establishments = establishments;
        this.access = access;
        this.invitations = invitations;
        this.actor = actor;
        this.audit = audit;
        this.clock = clock;
    }

    /** L'opérateur crée l'établissement et invite son administrateur, sans jamais en devenir membre. */
    @Transactional
    public Created create(NewEstablishment request) {
        UUID operatorId = actor.requireOperator();
        Identifier firstAdmin = Identifier.parse(request.firstAdminContact());
        requireValidTimezone(request.timezone());
        requireValidCurrency(request.currency());
        int ageOfMajority = request.ageOfMajority() == null ? DEFAULT_AGE_OF_MAJORITY : request.ageOfMajority();

        Establishment establishment = new Establishment(
                UUID.randomUUID(),
                request.code(),
                request.name().trim(),
                request.kind(),
                request.timezone(),
                request.currency(),
                ageOfMajority,
                "ACTIVE");
        establishments.insert(establishment, clock.instant());
        audit.record(
                establishment.id(),
                operatorId,
                "establishment.created",
                "establishment",
                establishment.id(),
                Map.of("code", establishment.code()));
        return new Created(establishment, inviteAdmin(establishment.id(), firstAdmin, operatorId));
    }

    /** Le jeton n'étant visible qu'une fois, l'opérateur doit pouvoir en réémettre un. */
    @Transactional
    public Invitations.Issued reissueAdminInvitation(UUID establishmentId, String contact) {
        UUID operatorId = actor.requireOperator();
        Identifier identifier = Identifier.parse(contact);
        establishments.find(establishmentId)
                .orElseThrow(() -> ApiException.notFound("ESTABLISHMENT_NOT_FOUND", "Établissement introuvable."));
        return inviteAdmin(establishmentId, identifier, operatorId);
    }

    @Transactional
    public void setStatus(UUID establishmentId, String status) {
        UUID operatorId = actor.requireOperator();
        if (!STATUSES.contains(status)) {
            throw ApiException.invalid("INVALID_STATUS", "Statut inconnu.");
        }
        if (establishments.setStatus(establishmentId, status, clock.instant()) == 0) {
            throw ApiException.notFound("ESTABLISHMENT_NOT_FOUND", "Établissement introuvable.");
        }
        audit.record(
                establishmentId, operatorId, "establishment.status.changed", "establishment", establishmentId,
                Map.of("status", status));
    }

    @Transactional(readOnly = true)
    public Establishment read(UUID establishmentId) {
        access.require(establishmentId, Permission.ESTABLISHMENT_READ);
        return establishments.find(establishmentId)
                .orElseThrow(() -> ApiException.notFound("ESTABLISHMENT_NOT_FOUND", "Établissement introuvable."));
    }

    @Transactional
    public Establishment updateSettings(UUID establishmentId, String name, Integer ageOfMajority) {
        UUID actorId = access.require(establishmentId, Permission.ESTABLISHMENT_MANAGE);
        Establishment current = establishments.lock(establishmentId)
                .orElseThrow(() -> ApiException.notFound("ESTABLISHMENT_NOT_FOUND", "Établissement introuvable."));
        String newName = name == null ? current.name() : name.trim();
        int newAge = ageOfMajority == null ? current.ageOfMajority() : ageOfMajority;
        establishments.updateSettings(establishmentId, newName, newAge, clock.instant());
        audit.record(
                establishmentId, actorId, "establishment.settings.updated", "establishment", establishmentId,
                Map.of("ageOfMajority", newAge));
        return establishments.find(establishmentId).orElseThrow();
    }

    private Invitations.Issued inviteAdmin(UUID establishmentId, Identifier contact, UUID issuedBy) {
        return invitations.issue(new Invitations.NewInvitation(
                contact,
                StaffInvitationHandler.PURPOSE,
                establishmentId,
                null,
                Map.of(StaffInvitationHandler.ROLE_ATTRIBUTE, StaffRole.ESTABLISHMENT_ADMIN.name()),
                issuedBy));
    }

    private static void requireValidTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException invalid) {
            throw ApiException.invalid("INVALID_TIMEZONE", "Fuseau horaire inconnu.");
        }
    }

    private static void requireValidCurrency(String currency) {
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException invalid) {
            throw ApiException.invalid("INVALID_CURRENCY", "Devise ISO 4217 inconnue.");
        }
    }
}
