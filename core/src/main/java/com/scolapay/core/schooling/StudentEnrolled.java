package com.scolapay.core.schooling;

import java.time.Instant;
import java.util.UUID;

/**
 * Publié dans la transaction de l'inscription. {@code confirmedFeeCatalogVersionId} n'est renseigné que
 * si le personnel a confirmé les frais qui en découlent : sans lui, aucune obligation n'est générée.
 */
public record StudentEnrolled(
        UUID establishmentId,
        UUID studentId,
        UUID enrollmentId,
        UUID academicYearId,
        UUID academicUnitId,
        String level,
        UUID confirmedFeeCatalogVersionId,
        UUID actorAccountId,
        Instant occurredAt) {}
