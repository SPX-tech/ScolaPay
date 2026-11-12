# Mission 004 lot 2 : réservations des excédents jamais affectés

Base `e49d802fb124e9c0aac27107e5dab2b7cb59668b`, branche `mission004/lot2-excess-reservations`.

## Périmètre conservateur

La restitution limitée est une exigence acquise, pas un remboursement d'un règlement. Cette tranche construit le budget historique, les demandes motivées, leur réservation et la décision par un second compte. Elle n'active aucune remise physique : identité/mandat du bénéficiaire, habilitations de remise et justificatif final restent ouverts en A05 à A08.

Le budget disponible part des espèces conservées et déduit toutes les affectations historiques immuables, ainsi que les demandes actives. Il ne part jamais du net réglé de billing. Affectation et réservation partagent le verrou de réception et les gardes READ COMMITTED. L'approbation conserve la réservation, avec sortie physique nulle.

Choix techniques proposés pour revue : demandes par le déclarant habilité, approbation/rejet par comptable/administrateur/directeur distinct ; auditeur en lecture seule. Retrait par l'auteur et rejet ne libèrent que les demandes non approuvées. Une demande approuvée reste réservée, sans expiration ou annulation automatique ni autorisation de remise. Les faits portent un périmètre immuable RESERVATION_ONLY_V1 ; une future politique de remise ne pourra réutiliser silencieusement ces approbations sans bénéficiaire.

## Vérification et livraison

- [x] Examiner ADR 0027 et les invariants V8/V9, conserver A05 à A08 ouverts.
- [ ] Écrire les tests HTTP et observer l'échec fonctionnel en CI avant implémentation.
- [ ] Construire demandes/décisions immuables, preuves et audit atomiques, clés idempotentes et refus des états inconnus.
- [ ] Ajouter V10 sans réécrire V1 à V9, intégrer les réservations au budget d'affectation.
- [ ] Vérifier S05-S10, approbations multiples, retrait/rejet, confidentialité, SQL direct/NULL, commit défaillant, concurrence et reprise entre JVM.
- Les résultats de la tête finale et ses limites seront consignés dans la PR en brouillon. Le lot 2 reste ouvert ; A03/A04 demeurent nécessaires avant pilote.

Aucune opération financière réelle, migration persistante, fournisseur, frontend, ouverture Families, fusion ou modification des protections de main.
