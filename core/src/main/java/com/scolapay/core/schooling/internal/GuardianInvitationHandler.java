package com.scolapay.core.schooling.internal;

import com.scolapay.core.establishment.Establishments;
import com.scolapay.core.identity.InvitationHandler;
import com.scolapay.core.shared.ApiException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class GuardianInvitationHandler implements InvitationHandler {

    static final String PURPOSE = "schooling.guardian";
    static final String RELATIONSHIP_ATTRIBUTE = "relationship";
    static final String FINANCIAL_ATTRIBUTE = "financiallyResponsible";

    private final SchoolingRepository repository;
    private final Establishments establishments;
    private final Clock clock;

    GuardianInvitationHandler(SchoolingRepository repository, Establishments establishments, Clock clock) {
        this.repository = repository;
        this.establishments = establishments;
        this.clock = clock;
    }

    @Override
    public String purpose() {
        return PURPOSE;
    }

    @Override
    public void onAccepted(Accepted invitation) {
        boolean available = establishments.find(invitation.establishmentId())
                        .filter(Establishments.Establishment::active)
                        .isPresent()
                && repository.findStudent(invitation.establishmentId(), invitation.subjectId()).isPresent();
        if (!available) {
            throw ApiException.conflict("RECORD_UNAVAILABLE", "Ce dossier n'est plus disponible.");
        }
        repository.insertGrant(
                new SchoolingRepository.Grant(
                        UUID.randomUUID(),
                        invitation.establishmentId(),
                        invitation.subjectId(),
                        invitation.accountId(),
                        "GUARDIAN",
                        invitation.attributes().get(RELATIONSHIP_ATTRIBUTE),
                        Boolean.parseBoolean(invitation.attributes().get(FINANCIAL_ATTRIBUTE)),
                        clock.instant(),
                        null),
                invitation.issuedBy());
    }
}
