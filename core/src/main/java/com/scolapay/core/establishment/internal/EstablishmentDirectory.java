package com.scolapay.core.establishment.internal;

import com.scolapay.core.establishment.Establishments;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EstablishmentDirectory implements Establishments {

    private final EstablishmentRepository establishments;

    public EstablishmentDirectory(EstablishmentRepository establishments) {
        this.establishments = establishments;
    }

    @Override
    public Optional<Establishment> find(UUID establishmentId) {
        return establishments.find(establishmentId);
    }
}
