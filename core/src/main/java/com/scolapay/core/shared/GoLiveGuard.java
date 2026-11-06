package com.scolapay.core.shared;

import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Empêche le démarrage d'une application dont le portail est ouvert sans que ses conditions soient
 * remplies. S'exécute avant le démarrage du serveur HTTP : aucune requête ne peut passer entre-temps.
 */
@Component
@ConditionalOnProperty(name = "scolapay.portal.enabled", havingValue = "true")
class GoLiveGuard implements SmartInitializingSingleton {

    private final List<GoLiveCondition> conditions;

    GoLiveGuard(List<GoLiveCondition> conditions) {
        this.conditions = conditions;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> unmet = conditions.stream()
                .flatMap(condition -> condition.unmet().stream().map(reason -> condition.name() + " : " + reason))
                .toList();
        if (!unmet.isEmpty()) {
            throw new IllegalStateException("Ouverture du portail refusée. " + String.join(" ; ", unmet));
        }
    }
}
