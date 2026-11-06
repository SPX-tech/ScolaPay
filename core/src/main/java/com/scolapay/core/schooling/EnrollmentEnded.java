package com.scolapay.core.schooling;

import java.time.Instant;
import java.util.UUID;

/** Publié dans la transaction de fin d'inscription : abandon ou scolarité terminée. */
public record EnrollmentEnded(
        UUID establishmentId,
        UUID studentId,
        UUID enrollmentId,
        String finalStatus,
        Instant occurredAt) {}
