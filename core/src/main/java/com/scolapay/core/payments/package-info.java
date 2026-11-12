/** Préparations numériques et sources de paiement distinctes ; aucun statut externe ne crée seul un règlement. */
@org.springframework.modulith.ApplicationModule(
        displayName = "Paiements",
        allowedDependencies = {"shared", "audit", "establishment", "billing"})
package com.scolapay.core.payments;
