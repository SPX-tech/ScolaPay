# Règlement espèces d'une obligation

Cette livraison du lot 2 réutilise les [réceptions intégrées par #31](payments-cash-receptions.md). Une réception déclarée n'est pas une obligation réglée. Seule une affectation confirmée, liée à son `SETTLEMENT`, sa preuve et son audit, réduit le reste à payer.

La tranche reste désactivée pour tout usage réel. La restitution des excédents jamais affectés n'est pas opérationnelle et le lot 2 n'est pas terminé. Les décisions et restrictions figurent dans [0029](decisions/0029-premier-reglement-especes.md).

## Parcours et contrat

1. Déclarer la réception réelle par l'API existante. Sa preuve physique déclarative reste immuable.
2. Lire `GET /api/v2/establishments/{establishmentId}/billing/obligations/{obligationId}/financial-position` pour présenter les montants, la couverture et `journalSize`.
3. Affecter un montant explicite par `POST /api/v2/establishments/{establishmentId}/payments/cash-receptions/{receptionId}/allocations`.

```json
{
  "idempotencyKey": "4dbba2bf-5ae8-41f4-91df-37c838103487",
  "obligationId": "6916ede0-2312-447f-9cf5-c6bd8d07645d",
  "amountMinor": "40000",
  "expectedJournalSize": 1
}
```

La création répond 201 ; un rejeu identique répond 200 avec la même preuve historique. Toute réutilisation différente de clé donne 409. La commande ne contient ni auteur, devise choisie, payeur, bénéficiaire, date forcée ou montant de remise. Aucun rôle financier ni accès familial n'est créé.

La réception source doit appartenir au compte déclarant habilité, dans le même établissement et la même devise. Une seule obligation par réception est autorisée dans cette tranche. Plusieurs affectations successives à cette obligation et plusieurs réceptions pour la régler sont possibles. Aucun montant n'est affecté automatiquement à une autre charge.

`CASH_ALLOCATE` est accordée aux caissiers, comptables et administrateurs. Directeur, auditeur et secrétariat ne peuvent pas affecter. Les lectures financières et de preuve gardent respectivement `BILLING_READ` et `CASH_RECEPTIONS_READ`, toujours avec adhésion active. Un opérateur global ou un lien familial ne contourne pas cette barrière.

## Projections

La position distingue `issuedAmountMinor`, `adjustmentsMinor`, `netBilledMinor`, `settledMinor` et `dueMinor`. Les montants JSON sont des chaînes d'unités minimales ISO. `allocationAllowed` indique seulement l'éligibilité financière de la cible, sans autoriser le compte ni réserver des fonds ; la confirmation recontrôle permissions, capacité, auteur, source, version et montants sous verrou.

Les échéances exposent séparément montant original, net après remise, règlement affecté et reste. Exemple : échéances 40 000 et 60 000 XAF, remise 20 000 et règlement 50 000 : nets 40 000 / 40 000, règlements 40 000 / 10 000, restes 0 / 30 000. L'échéancier initial 40 000 / 60 000 demeure inchangé.

Les corrections ou annulations actives ne reçoivent pas de ventilation inventée : `installmentsAvailable=false`, liste de couverture vide et confirmation refusée. Les montants courants restent lisibles. Les années non ouvertes sont également refusées.

Les statuts techniques validés sont `OPEN`, `PARTIALLY_SETTLED`, `SETTLED`, `SETTLED_WITH_ADJUSTMENTS`. Sans paiement, les états M003 `WAIVED`, `CORRECTED`, `CANCELLED` et `ADJUSTED_TO_ZERO` restent distincts. Une remise complète n'est jamais déclarée encaissée.

## Preuve et reprise

`GET .../payments/allocations/{id}` ou `GET .../payments/allocations/by-key/{key}` relit la preuve, même après désactivation des nouvelles affectations. La référence `CA-{uuid}` est interne, sans prétention fiscale.

La preuve contient réception, obligation et écriture liées, montant et devise, auteur serveur, date, version et snapshots initial/net facturé/règlements/reste avant et après. Ces snapshots ne changent pas lorsqu'une autre opération est effectuée ensuite. Les soldes courants sont recalculés depuis le journal.

Les lectures de réception montrent désormais le cumul confirmé et les espèces encore non affectées. Leurs identifiants, référence, auteur, horodatage, reçu et monnaie rendue d'origine restent immuables. Les champs de couverture sont courants : un rejeu de déclaration ne doit pas être interprété comme une nouvelle réception ou une restauration des fonds déjà affectés.

Une réponse perdue après commit impose une relecture par clé ou le rejeu de la même intention. Ni arrêt brutal ni seconde instance ne doivent recréer la réception, l'affectation ou son règlement. Une nouvelle clé ne prouve pas un nouveau geste physique : A03/A04 restent indispensables avant usage réel.

## Refus et limites

Une version périmée donne `409 ALLOCATION_OUTDATED`. Un montant supérieur au dû ou aux espèces disponibles donne 422, sans troncature. Une attente de verrou dépassée donne un conflit transitoire. Aucun refus ne supprime les espèces déjà reçues.

Après paiement, les remises compatibles gardent le cumul sensible et la seconde personne à l'extinction du dû. Toute nouvelle écriture périme une ancienne demande. Corrections, annulations et contrepassations génériques après paiement restent indisponibles ; un règlement ne se contrepasse jamais par les routes d'ajustement.

Les droits familiaux ne changent pas. Ni preuve de caisse, identifiant de reçu, acteur ni versement individuel n'est diffusé dans ces vues. Les agrégats ne classent plus un paiement comme ajustement. Families reste fermé.

`SCOLAPAY_CASH_ALLOCATIONS_ENABLED=false` et `SCOLAPAY_CASH_RECEPTIONS_ENABLED=false` restent les valeurs par défaut. Les activations utilisées en CI sont limitées aux bases jetables. V9 suit V8, puis valide le redémarrage et les checksums sans réparation ni `outOfOrder`. Les écritures SQL exigent `READ COMMITTED` ; les lectures composites utilisent des instantanés cohérents. Le détail de cette restriction est explicite dans 0029.

Identité de remettant hors de cette tranche, mandats, restitution, rectifications, dates historiques, contenu fiscal et paniers restent à arbitrer ou construire. La procédure de secours A03 et le contrôle quotidien de caisse A04 restent requis avant pilote. Aucun fournisseur, frontend ou déploiement n'est ajouté.
