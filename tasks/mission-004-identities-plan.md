# Mission 004 lot 2 : identification et autorisation nominative

Base `7b3896583b0483c36d82df9726e677c3a206187d`, branche `mission004/lot2-restitution-identities`.

## Décisions reçues et limite de la tranche

A05 est accepté : remettant identifié sans compte obligatoire, bénéficiaire par défaut égal au remettant, vérification au moment de la remise, mandat ou droit explicite pour un tiers, pas de copies intégrales systématiques de pièces. Demandeur et approbateur distincts ; aucune troisième personne systématique n'est imposée au caissier habilité.

Le propriétaire a aussi validé une procédure minimale pour les anciennes réceptions : déclaration signée du remettant, rapprochement avec le reçu original et examen d'un second compte habilité. Le justificatif papier de remise signé par bénéficiaire et caissier, conservé par l'établissement avec référence unique et attestation enregistrées, est validé. Le numérique signé et les incidents incertains restent à cadrer séparément. A06 à A08 demeurent ouverts pour leurs autres questions.

Première PR : réception avec identité obligatoire et confidentielle, vérification des anciennes sources, bénéficiaire égal au remettant vérifié et nouvelle autorisation indépendante. Aucune confirmation physique n'est activable dans cette PR : le mouvement de sortie, son justificatif final et le traitement des incidents feront l'objet d'une tranche complète. Les tiers mandatés restent fermés tant que le contrôle de leur droit n'est pas défini.

## Tâches et critères

- [x] Observer en CI l'échec fonctionnel des contrats d'identité à la réception avant implémentation : [43228908511](https://github.com/SPX-tech/ScolaPay/actions/runs/43228908511), deux assertions échouées, zéro erreur et zéro ignoré. [43230117104](https://github.com/SPX-tech/ScolaPay/actions/runs/43230117104) : premier vert, deux tests.
- [x] Ajouter V11 sans modifier V1 à V10 : identité obligatoire pour les nouvelles réceptions, anciennes sources conservées sans transformation silencieuse ; preuves, unicité et audit au commit.
- [x] Conserver les données privées chiffrées, permissions par établissement, idempotence liée au contenu et refus des configurations absentes ou invalides.
- [x] Construire la vérification indépendante d'une ancienne source et l'autorisation nominative sans changer ses faits, son budget ou les décisions RESERVATION_ONLY_V1. [43231694943](https://github.com/SPX-tech/ScolaPay/actions/runs/43231694943) : trois assertions 201/404, zéro erreur et zéro ignoré avant implémentation ; [43233437388](https://github.com/SPX-tech/ScolaPay/actions/runs/43233437388) : cinq tests verts.
- [x] Vérifier API, SQL direct/NULL, réassociations interdites, concurrence, rollback, reprises et confidentialité : [43236097694](https://github.com/SPX-tech/ScolaPay/actions/runs/43236097694), **251 tests dont 40 nouveaux**, zéro échec, erreur ou test ignoré. Les assertions historiques restent conservées avec une identité valide en fixture.
- [x] Actualiser ADR, contrats, contexte et cadrage : A05 accepté avec ses modalités validées, autres modalités d'A06 à A08 ouvertes, lot 2 ouvert et A03/A04 avant pilote.
- **Livraison** : PR en brouillon, résultats de la tête finale dans sa description ; CI et Containers sur PostgreSQL jetable, aucun test lourd local ou VPS.

La première campagne des nouveaux parcours ([43231189851](https://github.com/SPX-tech/ScolaPay/actions/runs/43231189851)) comportait deux assertions échouées et une erreur du test qui lisait un UUID dans une réponse 404. Le test a été corrigé pour vérifier d'abord le statut, puis la campagne RED propre 43231694943 a confirmé les trois fonctionnalités absentes. Cet incident n'est pas présenté comme une preuve fonctionnelle valide.

La première CI complète [43239909347](https://github.com/SPX-tech/ScolaPay/actions/runs/43239909347) a exécuté 607 tests : un échec et huit erreurs, sans ignoré, dus à `too many clients already`. Avant la première saturation, les journaux comptent 36 pools démarrés, 28 fermés et huit encore conservés par les contextes Spring, pouvant consommer 80 connexions auxquelles s'ajoutent les JVM de reprise. Les fixtures partagent désormais un maximum de quatre connexions par pool et aucun minimum inactif, dans le contexte de test comme dans ses JVM. La limite PostgreSQL, les timeouts, la configuration de production et toutes les assertions financières sont inchangés. Les validations de la nouvelle tête restent requises ; aucun résultat de cette CI échouée n'est présenté comme vert.

Les invariants de provenance F/H/Q et toutes les migrations intégrées restent inchangés. L'autorisation nominative ne produit aucune restitution, sortie ou SETTLEMENT. Aucun déploiement, migration persistante, fournisseur, frontend, ouverture Families, fusion ou changement des protections de main.
