package com.scolapay.core.billing.internal;

import com.scolapay.core.schooling.StudentEnrolled;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Durable grâce à l'outbox : voir les limites de la décision 0016, que l'émission respecte. */
@Component
public class ObligationIssuanceListener {

    private final ObligationService obligations;

    public ObligationIssuanceListener(ObligationService obligations) {
        this.obligations = obligations;
    }

    @ApplicationModuleListener
    public void on(StudentEnrolled event) {
        obligations.issueForEnrollment(event);
    }
}
