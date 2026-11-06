package com.scolapay.core.schooling;

import java.time.Instant;
import java.util.UUID;

/** Publié dans la transaction de l'inscription. Point d'entrée prévu pour la facturation. */
public record StudentEnrolled(
        UUID establishmentId,
        UUID studentId,
        UUID enrollmentId,
        UUID academicYearId,
        String level,
        Instant occurredAt) {}
