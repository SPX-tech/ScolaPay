package com.scolapay.core.establishment;

import java.util.Optional;
import java.util.UUID;

/**
 * Lecture des règles d'un établissement par les autres modules. N'applique aucune autorisation.
 * L'âge légal de la majorité vient du pays de l'établissement : il n'est pas configurable par lui.
 */
public interface Establishments {

    record Establishment(
            UUID id,
            String code,
            String name,
            String kind,
            String timezone,
            String currency,
            String countryCode,
            int legalAgeOfMajority,
            String status) {

        public boolean active() {
            return "ACTIVE".equals(status);
        }
    }

    Optional<Establishment> find(UUID establishmentId);
}
