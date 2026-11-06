package com.scolapay.core.establishment.internal;

import com.scolapay.core.shared.GoLiveCondition;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Porte sur toutes les règles enregistrées, pas seulement sur celles des établissements actuels :
 * sinon, un établissement créé après le démarrage échapperait au contrôle.
 */
@Component
class LegalRulesCondition implements GoLiveCondition {

    private final EstablishmentRepository repository;

    LegalRulesCondition(EstablishmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public String name() {
        return "regles-juridiques-confirmees";
    }

    @Override
    public Optional<String> unmet() {
        List<String> countries = repository.countriesWithUnconfirmedLegalRule();
        return countries.isEmpty()
                ? Optional.empty()
                : Optional.of("règle non confirmée pour " + String.join(", ", countries));
    }
}
