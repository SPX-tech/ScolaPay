package com.scolapay.core.schooling;

import java.util.UUID;

/**
 * Vérifie, dans la transaction de l'inscription, que le tarif confirmé par le personnel est bien celui
 * en vigueur. La scolarité ne connaît pas la facturation : c'est celle-ci qui implémente ce contrôle.
 */
public interface EnrollmentFeeConfirmation {

    void verify(UUID establishmentId, UUID academicYearId, UUID confirmedCatalogVersionId);
}
