package com.scolapay.core.establishment.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.establishment.StaffRole;
import com.scolapay.core.identity.AccountDirectory;
import com.scolapay.core.identity.CurrentActor;
import com.scolapay.core.identity.Identifier;
import com.scolapay.core.identity.Invitations;
import com.scolapay.core.shared.ApiException;
import com.scolapay.core.shared.PageQuery;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {

    public record Member(
            UUID membershipId, UUID accountId, String fullName, List<String> contacts, String status, Set<StaffRole> roles) {}

    private static final Set<String> STATUSES = Set.of("ACTIVE", "SUSPENDED", "REVOKED");

    private final MembershipRepository memberships;
    private final EstablishmentRepository establishments;
    private final EstablishmentAccess access;
    private final Invitations invitations;
    private final AccountDirectory directory;
    private final CurrentActor actor;
    private final AuditTrail audit;
    private final Clock clock;

    public MembershipService(
            MembershipRepository memberships,
            EstablishmentRepository establishments,
            EstablishmentAccess access,
            Invitations invitations,
            AccountDirectory directory,
            CurrentActor actor,
            AuditTrail audit,
            Clock clock) {
        this.memberships = memberships;
        this.establishments = establishments;
        this.access = access;
        this.invitations = invitations;
        this.directory = directory;
        this.actor = actor;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Invitations.Issued inviteStaff(UUID establishmentId, String contact, StaffRole role) {
        UUID actorId = access.require(establishmentId, Permission.MEMBERS_MANAGE);
        return invitations.issue(new Invitations.NewInvitation(
                Identifier.parse(contact),
                StaffInvitationHandler.PURPOSE,
                establishmentId,
                null,
                Map.of(StaffInvitationHandler.ROLE_ATTRIBUTE, role.name()),
                actorId));
    }

    @Transactional
    public void revokeInvitation(UUID establishmentId, UUID invitationId) {
        UUID actorId = access.require(establishmentId, Permission.MEMBERS_MANAGE);
        invitations.revoke(establishmentId, invitationId, actorId);
    }

    @Transactional(readOnly = true)
    public List<Member> list(UUID establishmentId, PageQuery page) {
        access.require(establishmentId, Permission.MEMBERS_MANAGE);
        List<MembershipRepository.Membership> rows = memberships.list(establishmentId, page);
        Map<UUID, AccountDirectory.AccountSummary> accounts = directory.findAll(
                rows.stream().map(MembershipRepository.Membership::accountId).toList());
        return rows.stream().map(row -> toMember(row, accounts.get(row.accountId()))).toList();
    }

    /** Un établissement garde toujours au moins un administrateur actif : le verrou couvre la vérification. */
    @Transactional
    public Member update(UUID establishmentId, UUID membershipId, String status, Set<StaffRole> roles) {
        UUID actorId = access.require(establishmentId, Permission.MEMBERS_MANAGE);
        if (status != null && !STATUSES.contains(status)) {
            throw ApiException.invalid("INVALID_STATUS", "Statut inconnu.");
        }
        establishments.lock(establishmentId);
        MembershipRepository.Membership current = memberships.find(establishmentId, membershipId)
                .orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "Membre introuvable."));

        String newStatus = status == null ? current.status() : status;
        Set<StaffRole> newRoles = roles == null ? current.roles() : roles;
        boolean remainsAdmin = "ACTIVE".equals(newStatus) && newRoles.contains(StaffRole.ESTABLISHMENT_ADMIN);
        if (!remainsAdmin && memberships.countOtherActiveAdmins(establishmentId, membershipId) == 0) {
            throw ApiException.conflict("LAST_ADMIN", "Un établissement doit conserver au moins un administrateur actif.");
        }

        Instant now = clock.instant();
        memberships.setStatus(establishmentId, membershipId, newStatus, now);
        memberships.replaceRoles(establishmentId, membershipId, newRoles, now);
        audit.record(
                establishmentId,
                actorId,
                "establishment.member.updated",
                "membership",
                membershipId,
                Map.of("status", newStatus, "roles", newRoles.stream().map(Enum::name).sorted().toList()));

        MembershipRepository.Membership updated = memberships.find(establishmentId, membershipId).orElseThrow();
        return toMember(updated, directory.findAll(List.of(updated.accountId())).get(updated.accountId()));
    }

    @Transactional(readOnly = true)
    public List<MembershipRepository.Affiliation> myAffiliations() {
        return memberships.affiliationsOf(actor.requireAccountId());
    }

    private static Member toMember(MembershipRepository.Membership row, AccountDirectory.AccountSummary account) {
        return new Member(row.id(), row.accountId(), account.fullName(), account.contacts(), row.status(), row.roles());
    }
}
