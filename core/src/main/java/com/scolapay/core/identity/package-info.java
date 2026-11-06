/**
 * Identité globale d'une personne : compte, identifiants, authentification, invitations.
 * Ce module ne sait rien des établissements ni des dossiers : être authentifié ne donne
 * accès à aucune donnée métier. Ce que deviendra une invitation acceptée est décidé par
 * les modules qui l'émettent, via {@link com.scolapay.core.identity.InvitationHandler}.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Identité",
        allowedDependencies = {"shared", "audit"})
package com.scolapay.core.identity;
