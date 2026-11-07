package com.scolapay.core.schooling.internal;

import com.scolapay.core.establishment.Establishments;
import com.scolapay.core.identity.InvitationHandler;
import com.scolapay.core.shared.ApiException;
import com.scolapay.core.shared.PortalProperties;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class SelfAccessInvitationHandler implements InvitationHandler {

    static final String PURPOSE = "schooling.self-access";

    private final SchoolingRepository repository;
    private final Establishments establishments;
    private final PortalProperties portal;
    private final Clock clock;

    SelfAccessInvitationHandler(
            SchoolingRepository repository, Establishments establishments, PortalProperties portal, Clock clock) {
        this.repository = repository;
        this.establishments = establishments;
        this.portal = portal;
        this.clock = clock;
    }

    @Override
    public String purpose() {
        return PURPOSE;
    }

    /** La majorité est revérifiée : le dossier a pu être corrigé depuis l'émission. */
    @Override
    public void onAccepted(Accepted invitation) {
        portal.requireOpen();
        Establishments.Establishment establishment = establishments.find(invitation.establishmentId())
                .filter(Establishments.Establishment::active)
                .orElseThrow(() -> ApiException.conflict("RECORD_UNAVAILABLE", "Ce dossier n'est plus disponible."));
        SchoolingRepository.Student student = repository
                .findStudent(invitation.establishmentId(), invitation.subjectId())
                .orElseThrow(() -> ApiException.conflict("RECORD_UNAVAILABLE", "Ce dossier n'est plus disponible."));
        if (!Majority.isReached(student.birthDate(), establishment, clock)) {
            throw ApiException.conflict(
                    "STUDENT_NOT_ADULT", "L'étudiant doit avoir atteint l'âge légal de la majorité.");
        }
        repository.insertGrant(
                new SchoolingRepository.Grant(
                        UUID.randomUUID(),
                        invitation.establishmentId(),
                        invitation.subjectId(),
                        invitation.accountId(),
                        "SELF",
                        null,
                        false,
                        null,
                        clock.instant(),
                        null),
                invitation.issuedBy());
    }
}
