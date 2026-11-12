# Réservations d'excédents d'espèces jamais affectés

Cette tranche du lot 2 prépare la [restitution limitée](decisions/0027-restitution-excedents-jamais-affectes.md). Elle conserve les demandes, leur budget et leur décision, **sans remise physique opérationnelle**. La [0030](decisions/0030-reservations-excedents-especes.md) décrit la restriction `RESERVATION_ONLY_V1`. A05 à A08 restent ouverts ; les conventions du code ne sont pas des décisions métier du propriétaire.

## Capacités et activation

| Capacité | État |
| --- | --- |
| Demande motivée, budget réservé, décision d'un second compte | Construits, activables uniquement sur base de test jetable |
| Rejet ou retrait motivé avant décision, relecture et rejeu | Construits ; aucune libération après approbation |
| Confirmation physique, preuve finale de restitution, sortie d'espèces | Non construits et non activables, même avec le flag de réservation |
| Remboursement d'un règlement, compensation, rectification, crédit transférable | Interdits et indisponibles |

`SCOLAPAY_CASH_EXCESS_RESERVATIONS_ENABLED=false` par défaut. Désactivés, les POST répondent 404 avant validation de leur corps ; les GET autorisés restent utilisables. Les deux flags des réceptions et affectations restent indépendants et faux par défaut. Aucun flag n'autorise un usage réel de cette tranche ni une remise physique.

## Contrat HTTP

Préfixe `P = /api/v2/establishments/{establishmentId}/payments`.

| Méthode | Route sous P | Corps / résultat |
| --- | --- | --- |
| POST | `/cash-receptions/{id}/excess-requests` | `idempotencyKey`, `amountMinor` chaîne entière positive, `reason` non vide de 500 caractères au plus |
| POST | `/excess-requests/{id}/approval` | `idempotencyKey` ; examen budgétaire indépendant |
| POST | `/excess-requests/{id}/rejection` | `idempotencyKey`, `note` motivée de 500 caractères au plus |
| POST | `/excess-requests/{id}/withdrawal` | Même forme ; auteur seulement, avant décision |
| GET | `/excess-requests/{id}` | Faits historiques de la demande et état courant cohérent |
| GET | `/excess-requests/by-key/{key}` | Retrouver une demande après réponse incertaine |
| GET | `/excess-decisions/by-key/{key}` | Retrouver la preuve immuable d'une décision |

201 signifie un nouveau fait durable, 200 le rejeu de la même intention. Les champs inconnus, dont bénéficiaire ou confirmation physique, sont refusés. Il n'existe aucune route de confirmation de remise. Les références `XQ-{uuid}` et `XD-{uuid}` sont internes ; elles ne sont ni un reçu fiscal ni une preuve de sortie de caisse.

Les réponses distinguent `REQUESTED`, `APPROVED_RESERVED`, `REJECTED`, `WITHDRAWN`. Elles indiquent toujours `restitutedMinor="0"` et `physicalHandoverAvailable=false`. Une décision conserve sa source, son montant, sa devise, sa portée, son auteur et sa date. Le motif libre et la note sont masqués aux caissiers autres que leur auteur ; comptable, administrateur, directeur et auditeur disposent de la lecture des motifs. Ces données ne sont pas recopiées dans l'audit général.

Les droits techniques suivent 0030 : seule une réception du déclarant peut être réservée ; comptable, administrateur ou directeur différents du demandeur peuvent examiner son budget. L'auditeur ne décide pas. Non-membre : 404 ; membre sans capacité : 403. Aucun accès familial, mandat ou bénéficiaire n'en est déduit.

## Montants et provenance

`F` est le montant conservé de la réception V8. `H` additionne toutes ses affectations historiques V9, sans compensation ni lecture du solde de billing. `Q` additionne les réservations demandées ou approuvées. `E = F - H - Q` borne à la fois nouvelle réservation et nouvelle affectation. Aucun mouvement de sortie n'est créé.

La lecture de réception ajoute `historicalAllocatedMinor=H`, `reservedMinor=Q`, `availableForAllocationMinor=E`. `unallocatedMinor=F-H` reste compatible avec #32 : il **inclut** Q et n'est pas un budget libre à affecter. L'approbation ne modifie ni le montant conservé, ni le montant encaissé, ni le journal de billing. Une demande conserve son instantané `retainedMinor`, `historicalAllocatedBeforeMinor`, `reservedBeforeMinor`, `availableBeforeMinor` ; ces champs ne sont pas des soldes courants.

Exemple S05/S06 : F=120 000, H=100 000, demande Q=20 000. Le disponible devient zéro ; après approbation, Q reste 20 000, le non-affecté reste 20 000, le règlement reste 100 000 et aucune sortie n'est constatée. S08 : sur 20 000 disponibles, deux demandes de 15 000 ne peuvent pas toutes deux réussir. Une affectation concurrente partage la même exclusion.

S09/S10 : une fraction affectée ne peut pas redevenir éligible par contrepassation ou changement de référence. Les compensations et rectifications restent refusées. Dans ce périmètre immuable, H ne peut pas diminuer. Une future extension devra conserver cette consommation historique même si le solde courant change.

## Reprise, intégrité et limites

La demande et son audit, ou la décision et son audit, sont atomiques. Toutes les consommations et libérations prennent le même verrou de réception ; V10 complète les gardes V9, sans les remplacer. Les contrôles couvrent également SQL direct, NULL, références et instantanés forgés, isolation, immutabilité et audit obligatoire au commit. Écritures `READ COMMITTED`, lectures composées `REPEATABLE READ`.

Une clé rejouée avec un autre montant, source, auteur, motif ou décision donne `409 CASH_EXCESS_KEY_CONFLICT`. Budget insuffisant : `422 EXCESS_BUDGET_EXCEEDED`. Décision déjà prise : `409 EXCESS_REQUEST_ALREADY_DECIDED`. Contention transitoire : `409 CASH_EXCESS_BUSY`, reprendre la même intention. Une erreur ne supprime jamais la réception source ni une réservation validée auparavant.

Après réponse incertaine, relire par clé ou rejouer l'intention. Les réservations approuvées ne s'expirent pas et ne peuvent pas être libérées dans cette tranche : leur traitement opérationnel attend la politique d'incident. Ne jamais créer une nouvelle réception pour contourner cette immobilisation. Une autre clé ne prouve pas qu'il s'agit d'autres billets ; le contrôle physique A04 et le secours A03 restent indispensables.

Le lot 2 reste ouvert. Le parcours complet de remise, sa preuve finale et son idempotence physique ne sont **pas** validés par les tests de refus des confirmations répétées. Les décisions requises sur le bénéficiaire, le mandat, les habilitations, les preuves et la revalidation des réservations techniques figurent dans 0030. Aucun usage réel, fournisseur, déploiement, migration persistante ou ouverture de Families n'est autorisé.

## Vérification

Les suites `CashExcessReservationTest`, `CashExcessSqlTest`, `CashExcessDisabledTest` et `CashExcessRecoveryTest` passent par GitHub Actions sur PostgreSQL jetable. Elles couvrent réservations d'une réception partiellement affectée, quotas historiques, double consommation, décisions concurrentes, confidentialité, rollback et reprise après arrêt brutal entre commit et réponse. Les deux JVM partagent la même base sans verrou applicatif local. La validation Flyway conserve ordre, scripts, checksums et historique après redémarrage, sans `repair` ni `outOfOrder`.

Les campagnes et résultats exacts sont consignés dans la PR de cette tranche. Les tests du règlement, des ajustements, des vues familiales et des migrations existants sont conservés. Aucun test local lourd n'est requis.
