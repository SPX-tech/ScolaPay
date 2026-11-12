# Mission 004 lot 2 : identification et autorisation nominative

Base `7b3896583b0483c36d82df9726e677c3a206187d`, branche `mission004/lot2-restitution-identities`.

## Décisions reçues et limite de la tranche

A05 est accepté : remettant identifié sans compte obligatoire, bénéficiaire par défaut égal au remettant, vérification au moment de la remise, mandat ou droit explicite pour un tiers, pas de copies intégrales systématiques de pièces. Demandeur et approbateur distincts ; aucune troisième personne systématique n'est imposée au caissier habilité.

Le propriétaire a aussi validé une procédure minimale pour les anciennes réceptions : déclaration signée du remettant, rapprochement avec le reçu original et examen d'un second compte habilité. Le justificatif papier de remise signé par bénéficiaire et caissier, conservé par l'établissement avec référence unique et attestation enregistrées, est validé. Le numérique signé et les incidents incertains restent à cadrer séparément. A06 à A08 demeurent ouverts pour leurs autres questions.

Première PR : réception avec identité obligatoire et confidentielle, vérification des anciennes sources, bénéficiaire égal au remettant vérifié et nouvelle autorisation indépendante. Aucune confirmation physique n'est activable dans cette PR : le mouvement de sortie, son justificatif final et le traitement des incidents feront l'objet d'une tranche complète. Les tiers mandatés restent fermés tant que le contrôle de leur droit n'est pas défini.

## Tâches et critères

- [ ] Observer en CI l'échec fonctionnel des contrats d'identité à la réception avant implémentation.
- [ ] Ajouter V11 sans modifier V1 à V10 : identité obligatoire pour les nouvelles réceptions, anciennes sources conservées sans transformation silencieuse ; preuves, unicité et audit au commit.
- [ ] Conserver les données privées chiffrées, les permissions par établissement, la liaison de l'idempotence au contenu et le refus d'une configuration absente.
- [ ] Construire la vérification indépendante d'une ancienne source et l'autorisation nominative d'une réservation approuvée sans changer ses faits, son budget ou les décisions RESERVATION_ONLY_V1.
- [ ] Vérifier API réelles, SQL direct/NULL, tentatives de réassociation, concurrence, rollback, reprises et confidentialité. Les tests historiques sont adaptés uniquement à l'identité désormais obligatoire ; leurs autres assertions sont conservées.
- [ ] Actualiser ADR, contrats, contexte et cadrage : A05 accepté avec ses modalités validées, A06 à A08 ouverts, lot 2 ouvert et A03/A04 avant pilote.
- **Livraison** : PR en brouillon, résultats de la tête finale dans sa description ; CI et Containers sur PostgreSQL jetable, aucun test lourd local ou VPS.

Les invariants de provenance F/H/Q et toutes les migrations intégrées restent inchangés. L'autorisation nominative ne produit aucune restitution, sortie ou SETTLEMENT. Aucun déploiement, migration persistante, fournisseur, frontend, ouverture Families, fusion ou changement des protections de main.
