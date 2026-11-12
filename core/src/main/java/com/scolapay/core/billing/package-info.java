/**
 * Facturation scolaire : périodes de facturation, catalogue des frais et ses versions. Constate ce
 * qui est dû et les règlements justifiés via son API publique ; payments reste propriétaire de la
 * provenance des paiements. Une préparation numérique ne modifie pas le journal. Aucun tarif ne crée de dette par lui-même.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Facturation",
        allowedDependencies = {"shared", "audit", "identity", "establishment", "schooling"})
package com.scolapay.core.billing;
