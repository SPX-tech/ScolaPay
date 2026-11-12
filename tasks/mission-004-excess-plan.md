# Mission 004 lot 2 : réservations des excédents jamais affectés

Base `e49d802fb124e9c0aac27107e5dab2b7cb59668b`, branche `mission004/lot2-excess-reservations`.

## Périmètre conservateur

La restitution limitée est une exigence acquise, pas un remboursement d'un règlement. Cette tranche construit le budget historique, les demandes motivées, leur réservation et la décision par un second compte. Elle n'active aucune remise physique : identité/mandat du bénéficiaire, habilitations de remise et justificatif final restent ouverts en A05 à A08.

Le budget disponible part des espèces conservées et déduit toutes les affectations historiques immuables, ainsi que les demandes actives. Il ne part jamais du net réglé de billing. Affectation et réservation partagent le verrou de réception et les gardes READ COMMITTED. L'approbation conserve la réservation, avec sortie physique nulle.

Choix techniques proposés pour revue : demandes par le déclarant habilité, approbation/rejet par comptable/administrateur/directeur distinct ; auditeur en lecture seule. Retrait par l'auteur et rejet ne libèrent que les demandes non approuvées. Une demande approuvée reste réservée, sans expiration ou annulation automatique ni autorisation de remise. Les faits portent un périmètre immuable RESERVATION_ONLY_V1 ; une future politique de remise ne pourra réutiliser silencieusement ces approbations sans bénéficiaire.

## Vérification et livraison

- [x] Examiner ADR 0027 et les invariants V8/V9, conserver A05 à A08 ouverts.
- [x] Écrire les tests HTTP et observer l'échec fonctionnel en CI avant implémentation : [43180735222](https://github.com/SPX-tech/ScolaPay/actions/runs/43180735222), compilation réussie, deux assertions attendues 201 / obtenues 404, aucune erreur ni test ignoré.
- [x] Construire demandes/décisions immuables, preuves et audit atomiques, clés idempotentes et refus des états inconnus : [premier vert](https://github.com/SPX-tech/ScolaPay/actions/runs/43182191344), deux tests.
- [x] Ajouter V10 sans réécrire V1 à V9, intégrer les réservations au budget d'affectation.
- [x] Vérifier les fractions réservables S05/S06/S08 et les refus S09/S10, approbations multiples, retrait/rejet, confidentialité, SQL direct/NULL, commit défaillant, concurrence et reprise entre JVM : [43186931814](https://github.com/SPX-tech/ScolaPay/actions/runs/43186931814), 207 tests dont 33 propres aux excédents, zéro échec, erreur ou test ignoré.
- **Validation de livraison** : contrôler la tête finale avec les compléments SQL concurrents, la suite Java complète et Containers ; résultats et SHA exacts dans la PR.
- Les résultats de la tête finale et ses limites seront consignés dans la PR en brouillon. Le lot 2 reste ouvert ; A03/A04 demeurent nécessaires avant pilote.

S07 reste un test de refus du chemin de remise, pas une confirmation effective idempotente. Les scénarios complets de sortie et le modèle de provenance avec compensations ne sont pas déclarés livrés.

La campagne [43188387204](https://github.com/SPX-tech/ScolaPay/actions/runs/43188387204) a exécuté 211 tests : un échec d'assertion, zéro erreur et zéro ignoré. Le nouveau test familial attendait la fermeture du portail dans le contexte partagé `IntegrationTest`, qui l'ouvre explicitement pour exercer les autorisations. Les quatre refus de caisse étaient corrects. L'assertion est corrigée pour vérifier l'accès académique 200 et le refus des routes de caisse 404 ; les sondes JVM conservent le contrôle de fermeture par défaut. Aucun changement de configuration ou de garde de production n'est nécessaire.

Aucune opération financière réelle, migration persistante, fournisseur, frontend, ouverture Families, fusion ou modification des protections de main.
