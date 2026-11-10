package com.scolapay.core.identity.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.identity.CurrentActor;
import com.scolapay.core.identity.InvitationHandler;
import com.scolapay.core.identity.Invitations;
import com.scolapay.core.identity.InvitationDelivery;
import com.scolapay.core.identity.InvitationDispatchState;
import com.scolapay.core.shared.PortalProperties;
import com.scolapay.core.shared.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class InvitationService implements Invitations, InvitationDispatchState {

    public record AcceptanceResult(UUID accountId, String purpose, UUID establishmentId) {}

    private static final Duration VALIDITY = Duration.ofDays(7);
    private static final int TOKEN_BYTES = 32;

    private final InvitationRepository invitations;
    private final AccountRepository accounts;
    private final AuthenticationService authentication;
    private final PasswordEncoder passwordEncoder;
    private final CurrentActor actor;
    private final AuditTrail audit;
    private final JsonMapper json;
    private final Clock clock;
    private final ObjectProvider<InvitationDelivery> delivery;
    private final PortalProperties portal;
    private final Map<String, InvitationHandler> handlers;
    private final SecureRandom random = new SecureRandom();

    InvitationService(
            InvitationRepository invitations,
            AccountRepository accounts,
            AuthenticationService authentication,
            PasswordEncoder passwordEncoder,
            CurrentActor actor,
            AuditTrail audit,
            JsonMapper json,
            Clock clock,
            List<InvitationHandler> handlers,
            ObjectProvider<InvitationDelivery> delivery,
            PortalProperties portal) {
        this.invitations = invitations;
        this.accounts = accounts;
        this.authentication = authentication;
        this.passwordEncoder = passwordEncoder;
        this.actor = actor;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
        this.delivery = delivery;
        this.portal = portal;
        this.handlers = handlers.stream().collect(Collectors.toMap(InvitationHandler::purpose, Function.identity()));
    }

    @Override
    @Transactional
    public Issued issue(NewInvitation invitation) {
        if (!handlers.containsKey(invitation.purpose())) {
            throw new IllegalArgumentException("Aucun handler pour l'invitation " + invitation.purpose());
        }
        InvitationDelivery channel = delivery.getIfAvailable();
        if (channel == null || !channel.supports(invitation.contact().kind())) {
            throw ApiException.conflict("INVITATION_CHANNEL_UNAVAILABLE", "Aucun canal de remise n'est disponible pour ce contact.");
        }
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(VALIDITY);
        invitations.insert(
                id,
                hash(token),
                invitation.contact(),
                invitation.purpose(),
                invitation.establishmentId(),
                invitation.subjectId(),
                json.writeValueAsString(invitation.attributes()),
                invitation.issuedBy(),
                expiresAt,
                now);
        audit.record(
                invitation.establishmentId(),
                invitation.issuedBy(),
                "identity.invitation.issued",
                "invitation",
                id,
                Map.of("purpose", invitation.purpose(), "contactKind", invitation.contact().kind().name()));
        channel.deliver(id, invitation.contact(), token, expiresAt);
        return new Issued(id, expiresAt);
    }

    @Override
    public boolean isDeliverable(UUID invitationId) {
        return invitations.deliverablePurpose(invitationId, clock.instant())
                .filter(purpose -> !purpose.startsWith("schooling.") || portal.enabled()).isPresent();
    }

    @Override
    public InvitationDelivery.Report deliveryStatus(UUID establishmentId, UUID invitationId) {
        if (!invitations.belongsTo(establishmentId, invitationId)) {
            throw ApiException.notFound("INVITATION_NOT_FOUND", "Invitation introuvable.");
        }
        InvitationDelivery channel = delivery.getIfAvailable();
        return channel == null ? new InvitationDelivery.Report("UNAVAILABLE", 0, null, "CHANNEL_UNAVAILABLE")
                : channel.status(invitationId);
    }

    @Override
    @Transactional
    public void revoke(UUID establishmentId, UUID invitationId, UUID actorAccountId) {
        if (invitations.revoke(establishmentId, invitationId, clock.instant()) == 0) {
            throw ApiException.notFound("INVITATION_NOT_FOUND", "Invitation introuvable ou déjà utilisée.");
        }
        audit.record(establishmentId, actorAccountId, "identity.invitation.revoked", "invitation", invitationId, Map.of());
    }

    /**
     * Si un compte existe déjà pour ce contact, seul ce compte connecté peut accepter : connaître
     * le jeton ne suffit pas à se rattacher au compte de quelqu'un d'autre. Un compte connecté
     * différent est refusé plutôt que relié en silence à un contact qui n'est pas le sien.
     */
    @Transactional
    public AcceptanceResult accept(String token, String fullName, String password) {
        InvitationRepository.Row invitation = invitations.lockByTokenHash(hash(token))
                .orElseThrow(() -> ApiException.notFound("INVITATION_INVALID", "Cette invitation n'est pas valide."));
        Instant now = clock.instant();
        if (invitation.acceptedAt() != null || invitation.revokedAt() != null || !invitation.expiresAt().isAfter(now)) {
            throw ApiException.conflict("INVITATION_UNUSABLE", "Cette invitation a expiré ou a déjà été utilisée.");
        }

        Optional<AccountRepository.Account> existing = accounts.findByIdentifier(invitation.contact());
        Optional<UUID> caller = actor.accountId();
        UUID accountId;
        if (existing.isPresent()) {
            accountId = existing.get().id();
            if (caller.isEmpty()) {
                throw ApiException.unauthorized(
                        "LOGIN_REQUIRED", "Un compte existe pour ce contact : connectez-vous pour accepter l'invitation.");
            }
            if (!caller.get().equals(accountId)) {
                throw ApiException.forbidden("INVITATION_NOT_FOR_ACCOUNT", "Cette invitation concerne un autre compte.");
            }
        } else {
            if (caller.isPresent()) {
                throw ApiException.forbidden("INVITATION_NOT_FOR_ACCOUNT", "Cette invitation concerne un autre compte.");
            }
            accountId = createAccount(invitation, fullName, password, now);
        }

        invitations.markAccepted(invitation.id(), accountId, now);
        InvitationHandler handler = handlers.get(invitation.purpose());
        if (handler == null) {
            throw new IllegalStateException("Aucun handler pour l'invitation " + invitation.purpose());
        }
        handler.onAccepted(new InvitationHandler.Accepted(
                invitation.id(),
                accountId,
                invitation.establishmentId(),
                invitation.subjectId(),
                json.readValue(invitation.attributes(), new TypeReference<Map<String, String>>() {}),
                invitation.issuedBy()));
        audit.record(
                invitation.establishmentId(),
                accountId,
                "identity.invitation.accepted",
                "invitation",
                invitation.id(),
                Map.of("purpose", invitation.purpose()));
        return new AcceptanceResult(accountId, invitation.purpose(), invitation.establishmentId());
    }

    private UUID createAccount(InvitationRepository.Row invitation, String fullName, String password, Instant now) {
        if (fullName == null || fullName.isBlank()) {
            throw ApiException.invalid("FULL_NAME_REQUIRED", "Le nom complet est requis pour créer le compte.");
        }
        authentication.validateNewPassword(password);
        UUID accountId = UUID.randomUUID();
        accounts.create(accountId, fullName.trim(), passwordEncoder.encode(password), false, now);
        accounts.addIdentifier(accountId, invitation.contact(), now);
        return accountId;
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
