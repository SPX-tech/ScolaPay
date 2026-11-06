/**
 * Établissements, personnel, rôles et permissions. Porte la frontière d'isolation :
 * {@link com.scolapay.core.establishment.EstablishmentAccess} est la seule autorité qui
 * dit ce qu'un compte peut faire dans un établissement.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Établissements",
        allowedDependencies = {"shared", "audit", "identity"})
package com.scolapay.core.establishment;
