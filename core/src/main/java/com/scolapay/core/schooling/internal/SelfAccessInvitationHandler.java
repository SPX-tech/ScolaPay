package com.scolapay.core.schooling.internal;

import com.scolapay.core.establishment.Establishments;
import com.scolapay.core.identity.InvitationHandler;
import com.scolapay.core.shared.ApiException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class SelfAccessInvitationHandler implements InvitationHandler {

    static final String PURPOSE = "schooling.self-access";

    private final SchoolingRepository repository;
    private final Establishments establishments;
    private final Clock clock;

    SelfAccessInvitationHandler(SchoolingRepository repository, Establishments establishments, Clock clock) {
        this.repository = repository;
        this.establishments = establishments;
        this.clock = clock;
    }

    @Override
    public String purpose() {
        return PURPOSE;
    }

    /** La majorité est revérifiée : la règle de l'établissement a pu changer depuis l'émission. */
    @Override
    public void onAccepted(Accepted invitation) {
        Establishments.Establishment establishment = establishments.find(invitation.establishmentId())
                .filter(Establishments.Establishment::active)
                .orElseThrow(() -> ApiException.conflict("RECORD_UNAVAILABLE", "Ce dossier n'est plus disponible."));
        SchoolingRepository.Student student = repository
                .findStudent(invitation.establishmentId(), invitation.subjectId())
                .orElseThrow(() -> ApiException.conflict("RECORD_UNAVAILABLE", "Ce dossier n'est plus disponible."));
        if (!Majority.isReached(student.birthDate(), establishment, clock)) {
            throw ApiException.conflict(
                    "STUDENT_NOT_ADULT", "L'étudiant doit avoir atteint l'âge de la majorité fixé par l'établissement.");
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
                        clock.instant(),
                        null),
                invitation.issuedBy());
    }
}
