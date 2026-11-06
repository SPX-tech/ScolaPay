package com.scolapay.core.schooling.internal;

import com.scolapay.core.establishment.Establishments;
import com.scolapay.core.identity.InvitationHandler;
import com.scolapay.core.shared.ApiException;
import com.scolapay.core.shared.PortalProperties;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ThirdPartyInvitationHandler implements InvitationHandler {

    static final String PURPOSE = "schooling.third-party";
    static final String RELATIONSHIP_ATTRIBUTE = "relationship";
    static final String ACADEMIC_ATTRIBUTE = "academicAccess";
    static final String FINANCIAL_ATTRIBUTE = "financialContributor";

    private final SchoolingRepository repository;
    private final Establishments establishments;
    private final PortalProperties portal;
    private final Clock clock;

    ThirdPartyInvitationHandler(
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

    /**
     * Si l'élève devient majeur avant l'acceptation, le lien est créé mais l'accès académique
     * reste fermé : {@link AcademicAccess} l'applique à la lecture, sans cas particulier ici.
     */
    @Override
    public void onAccepted(Accepted invitation) {
        portal.requireOpen();
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
                        "THIRD_PARTY",
                        invitation.attributes().get(RELATIONSHIP_ATTRIBUTE),
                        Boolean.parseBoolean(invitation.attributes().get(ACADEMIC_ATTRIBUTE)),
                        Boolean.parseBoolean(invitation.attributes().get(FINANCIAL_ATTRIBUTE)),
                        null,
                        clock.instant(),
                        null),
                invitation.issuedBy());
    }
}
