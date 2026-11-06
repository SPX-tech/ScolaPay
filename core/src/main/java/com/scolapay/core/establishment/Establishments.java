package com.scolapay.core.establishment;

import java.util.Optional;
import java.util.UUID;

/** Lecture des règles d'un établissement par les autres modules. N'applique aucune autorisation. */
public interface Establishments {

    record Establishment(
            UUID id,
            String code,
            String name,
            String kind,
            String timezone,
            String currency,
            int ageOfMajority,
            String status) {

        public boolean active() {
            return "ACTIVE".equals(status);
        }
    }

    Optional<Establishment> find(UUID establishmentId);
}
