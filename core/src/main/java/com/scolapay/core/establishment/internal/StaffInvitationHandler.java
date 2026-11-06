package com.scolapay.core.establishment.internal;

import com.scolapay.core.establishment.StaffRole;
import com.scolapay.core.identity.InvitationHandler;
import com.scolapay.core.shared.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class StaffInvitationHandler implements InvitationHandler {

    static final String PURPOSE = "establishment.staff";
    static final String ROLE_ATTRIBUTE = "role";

    private final EstablishmentRepository establishments;
    private final MembershipRepository memberships;
    private final Clock clock;

    StaffInvitationHandler(EstablishmentRepository establishments, MembershipRepository memberships, Clock clock) {
        this.establishments = establishments;
        this.memberships = memberships;
        this.clock = clock;
    }

    @Override
    public String purpose() {
        return PURPOSE;
    }

    @Override
    public void onAccepted(Accepted invitation) {
        // Le verrou sérialise avec les changements de personnel, comme dans MembershipService.
        boolean active = establishments.lock(invitation.establishmentId())
                .map(establishment -> establishment.active())
                .orElse(false);
        if (!active) {
            throw ApiException.conflict("ESTABLISHMENT_UNAVAILABLE", "Cet établissement n'accepte pas de nouveaux membres.");
        }
        Instant now = clock.instant();
        UUID membershipId = memberships.upsertActive(invitation.establishmentId(), invitation.accountId(), now);
        memberships.addRole(
                invitation.establishmentId(),
                membershipId,
                StaffRole.valueOf(invitation.attributes().get(ROLE_ATTRIBUTE)),
                now);
    }
}
