/**
 * Facturation scolaire : périodes de facturation, catalogue des frais et ses versions. Constate ce
 * qui est dû, jamais ce qui est encaissé. Aucun tarif ne crée de dette par lui-même.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Facturation",
        allowedDependencies = {"shared", "audit", "establishment", "schooling"})
package com.scolapay.core.billing;
