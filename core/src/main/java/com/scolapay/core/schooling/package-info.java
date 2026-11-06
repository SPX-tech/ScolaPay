/**
 * Années académiques, dossiers scolaires, inscriptions et leur historique, relations entre
 * comptes et dossiers. Publie vers la future facturation {@link
 * com.scolapay.core.schooling.StudentDirectory} et les événements d'inscription.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Scolarité",
        allowedDependencies = {"shared", "audit", "identity", "establishment"})
package com.scolapay.core.schooling;
