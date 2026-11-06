package com.scolapay.core.billing.internal;

import com.scolapay.core.schooling.EnrollmentFeeConfirmation;
import com.scolapay.core.shared.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class PublishedCatalogConfirmation implements EnrollmentFeeConfirmation {

    private final FeeCatalogRepository catalogs;

    PublishedCatalogConfirmation(FeeCatalogRepository catalogs) {
        this.catalogs = catalogs;
    }

    @Override
    public void verify(UUID establishmentId, UUID academicYearId, UUID confirmedCatalogVersionId) {
        boolean current = catalogs.publishedVersion(establishmentId, academicYearId)
                .filter(version -> version.id().equals(confirmedCatalogVersionId))
                .isPresent();
        if (!current) {
            throw ApiException.conflict(
                    "FEES_PREVIEW_OUTDATED", "Le tarif a changé depuis l'aperçu : consultez à nouveau les frais avant de confirmer.");
        }
    }
}
