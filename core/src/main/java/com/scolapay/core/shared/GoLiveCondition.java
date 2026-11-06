package com.scolapay.core.shared;

import java.util.Optional;

/**
 * Condition à remplir avant d'ouvrir le portail des familles. Elle s'évalue sur l'état réel et
 * n'offre aucune option de contournement.
 */
public interface GoLiveCondition {

    String name();

    /** Vide si la condition est remplie, sinon ce qui manque. */
    Optional<String> unmet();
}
