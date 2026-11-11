/** Réceptions et affectations d'espèces confirmées, preuves internes et règlement via l'API de billing. */
@org.springframework.modulith.ApplicationModule(
        displayName = "Encaissements",
        allowedDependencies = {"shared", "audit", "establishment", "billing"})
package com.scolapay.core.payments;
