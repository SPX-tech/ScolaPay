package com.scolapay.core.establishment;

import com.scolapay.core.establishment.internal.MembershipRepository;
import com.scolapay.core.identity.CurrentActor;
import com.scolapay.core.shared.ApiException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Seule autorité sur ce qu'un compte peut faire dans un établissement. Seule une adhésion
 * active à un établissement actif ouvre l'accès : ni le statut d'opérateur ni un lien
 * familial n'y suffisent. Un non-membre reçoit un 404, identique à un établissement
 * inexistant, pour ne pas révéler quels établissements existent.
 */
@Service
public class EstablishmentAccess {

    private final MembershipRepository memberships;
    private final CurrentActor actor;

    public EstablishmentAccess(MembershipRepository memberships, CurrentActor actor) {
        this.memberships = memberships;
        this.actor = actor;
    }

    public UUID requireMember(UUID establishmentId) {
        UUID accountId = actor.requireAccountId();
        rolesOf(establishmentId, accountId);
        return accountId;
    }

    /** Renvoie le compte autorisé, pour tracer l'auteur de l'opération. */
    public UUID require(UUID establishmentId, Permission permission) {
        UUID accountId = actor.requireAccountId();
        boolean granted = rolesOf(establishmentId, accountId).stream().anyMatch(role -> role.grants(permission));
        if (!granted) {
            throw ApiException.forbidden("PERMISSION_DENIED", "Votre rôle ne permet pas cette opération.");
        }
        return accountId;
    }

    private Set<StaffRole> rolesOf(UUID establishmentId, UUID accountId) {
        Optional<Set<StaffRole>> roles = memberships.activeRoles(establishmentId, accountId);
        return roles.orElseThrow(() -> ApiException.notFound("ESTABLISHMENT_NOT_FOUND", "Établissement introuvable."));
    }
}
