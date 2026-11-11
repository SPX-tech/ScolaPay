# Déclarations d'espèces, première PR intermédiaire du lot 2

La PR #31, intégrée au commit `6a2fdaa`, a construit les réceptions durables, initialement toutes non affectées. La [chaîne de règlement d'une obligation](payments-cash-settlements.md) réutilise ces sources sans les recréer. La réception physique déclarée reste distincte de ses affectations et du reste facturé. Le lot 2 n'est pas terminé et aucun pilote réel n'est prêt.

## Contrat HTTP

Préfixe : `/api/v2/establishments/{establishmentId}/payments/cash-receptions`.

| Méthode | Route | Effet |
| --- | --- | --- |
| POST | Préfixe | Déclarer une réception effective ; 201 à la création, 200 pour le même rejeu |
| GET | `/{id}` | Relire la preuve interne, y compris après désactivation des nouvelles déclarations |
| GET | `/by-key/{key}` | Retrouver le résultat d'une intention après réponse perdue, sans nouvelle réception physique |

Corps de déclaration :

```json
{
  "idempotencyKey": "4a853170-cdd9-4f43-a401-7a736a73aecb",
  "amountReceivedMinor": "12000",
  "changeGivenMinor": "2000",
  "cashReceivedConfirmed": true
}
```

La devise est celle de l'établissement. L'auteur, l'horodatage, la date locale et le fuseau sont déterminés par le serveur. Aucun compte, élève, bénéficiaire, montant affecté, date antidatée ou statut ne peut être fourni. Les champs inconnus sont refusés. Le montant reçu est compris entre 1 et 999 999 999 999 999 unités minimales ; la monnaie rendue est positive ou nulle et strictement inférieure au montant reçu.

La réponse conserve `id`, `reference`, `currency`, `amountReceivedMinor`, `changeGivenMinor`, `amountRetainedMinor`, `recordedAt`, `recordedByAccountId`, `businessDate` et `timezone`. Les champs courants `unallocatedMinor` et `settledMinor` suivent les affectations confirmées : initialement montant conservé et zéro, puis montant conservé moins affectations et cumul affecté. `allocationState` est dérivé : `UNALLOCATED`, `PARTIALLY_ALLOCATED` ou `ALLOCATED`. `proofNature` reste `INTERNAL_CASH_DECLARATION`. Les montants JSON sont des chaînes, suivant 0012.

Ces codes décrivent la couverture technique de la réception, distincte des statuts d'obligation validés pour la tranche. La référence `CR-{uuid}` n'est ni un numéro fiscal ni une preuve externe de présence des billets. Les faits physiques déclarés sont immuables ; leur couverture courante peut évoluer. Un rejeu conserve la même réception et ces faits, sans restaurer les fonds déjà affectés. Aucun PDF fiscal, transfert de caisse, crédit libre, restitution ou rectification n'est disponible.

## Permissions et confidentialité

`CASH_RECEPTIONS_RECORD` est attribuée au caissier, au comptable et à l'administrateur. Le directeur ne reçoit pas cette permission et l'auditeur reste en lecture seule. `CASH_RECEPTIONS_READ` est attribuée aux cinq rôles financiers pour relire ces preuves techniques dans leur établissement. Le secrétariat et un opérateur global sans adhésion n'y accèdent pas.

Chaque requête vérifie l'adhésion active et l'établissement actif avec `EstablishmentAccess`. Un non-membre reçoit 404, un membre sans capacité 403. Les relectures par identifiant ou clé sont contextualisées par établissement. Un rejeu est lié au compte déclarant ; une clé reprise par un autre auteur ou avec d'autres montants donne `409 CASH_RECEPTION_KEY_CONFLICT`, sans renvoyer la preuve originale.

La réponse ne contient ni clé d'idempotence, contact, nom de remettant, motif libre ou jeton. L'audit général contient uniquement la référence technique et les identifiants de rattachement. Le périmètre de lecture d'éventuelles données de payeur ou de justificatifs enrichis devra être revu avec A05 et A08. Aucun droit familial n'est créé.

## Transaction et limites réelles

Une insertion atomique `ON CONFLICT` revendique la clé `(establishment_id, idempotency_key)`. La réception et son audit sont validés dans la même transaction. Une concurrence sur la clé attend de façon bornée, puis relit le résultat ou donne un conflit transitoire. Il n'existe ni cache local d'idempotence ni expiration des clés financières.

Le dépassement du délai de verrouillage PostgreSQL `55P03` donne `409 CASH_RECEPTION_BUSY`, y compris quand JDBC le laisse non classé. Le client conserve la même clé pour reprendre. Les autres erreurs SQL ne sont pas assimilées à cette saturation. Le test de verrou maintenu vérifie le refus borné, l'absence d'effet partiel puis un rejeu réussi.

Après résultat HTTP inconnu, relire par clé ou rejouer la même intention avant tout nouveau geste physique. Une clé différente ne permet pas au logiciel de constater qu'il s'agit des mêmes billets : le contrôle physique et la procédure de secours restent nécessaires. Cette étape ne revendique aucune remise physique exactement une fois.

Le POST de déclaration ne communique avec aucun fournisseur et n'écrit pas dans le journal de facturation. L'affectation passe par son propre contrat atomique. Aucun événement secondaire n'est nécessaire à cette étape ; un futur consommateur devra suivre l'outbox durable sans décider de la confirmation monétaire après commit.

## Migration V8

V8 suit V7, V7_1 et V7_2 sans les modifier. `cash_receptions` conserve le constat, la confirmation explicite, les montants, l'auteur et la référence de preuve dans une même ligne immuable. Les contraintes PostgreSQL vérifient notamment :

- montants bornés, monnaie strictement inférieure au reçu, confirmation non NULL et vraie ;
- référence présente et liée à l'identifiant, devise de l'établissement et auteur affilié au même établissement ;
- unicité de la clé dans l'établissement et conservation de la date locale selon le fuseau enregistré ;
- audit lié au bon établissement, auteur, cible et référence exigé au commit ;
- refus de modification, suppression et troncature de la réception et de son audit.

L'index auteur/date prépare le futur contrôle quotidien. L'index partiel d'audit sert uniquement au contrôle SQL d'intégration ; le dépôt Java de `payments` ne lit pas la table d'audit. L'autorisation métier reste dans `EstablishmentAccess`, les clés étrangères ne prouvent pas le rôle ni le geste physique.

Aucun solde de crédit n'est créé. Le montant non affecté reste attaché à cette seule réception. V9 ajoute les affectations et leurs écritures correspondantes. Leurs faits immuables préservent l'historique de consommation ; réservations et contrôles complets de restitution restent à construire avant leur activation.

## Configuration et mise en service

`SCOLAPAY_CASH_RECEPTIONS_ENABLED` vaut `false` par défaut. Sans activation explicite, le POST autorisé renvoie `404 CASH_RECEPTIONS_DISABLED`. Les lectures autorisées restent disponibles pour retrouver des faits déjà enregistrés. L'activation est utilisée uniquement dans les environnements de test de cette livraison et ne signifie pas aptitude au pilote.

Avant tout pilote, réaliser notamment le contrôle quotidien par caissier, le rapprochement physique, les écarts et leur validation indépendante (A04), ainsi que la procédure manuelle de secours avec référence unique, justificatifs, rapprochement et contrôle indépendant (A03). Aucun chemin d'import de secours, datation manuelle ou système hors ligne n'est construit ici.

## Suite du lot 2 et arbitrages

Les orientations A01 à A04 sont validées. La nouvelle chaîne applique A02 aux paiements et remises pris en charge, sans modifier l'échéancier initial. Les autres corrections ne reçoivent pas de ventilation inventée et empêchent le règlement.

A05 à A08 restent partiellement ouverts : identité du remettant et bénéficiaire, mandat, dates métier et arriérés, rectifications, statuts et justificatifs finaux. La restitution des seuls excédents jamais affectés reste acquise, avec justification, second compte approbateur, remise physique confirmée et trace. Son parcours reste fermé tant que permissions détaillées, bénéficiaire et preuves ne sont pas suffisamment définis.

La [livraison de règlement](payments-cash-settlements.md) ajoute le port public de `billing` et la transaction affectation-preuve-journal-audit reliée à la réception confirmée. Elle doit être revue avec toutes les preuves de 0028. Les activations restent fermées pour tout usage réel ; restitution et contrôle quotidien ne sont pas livrés par cette chaîne.

## Validation

Les tests réels couvrent les API, rôles, isolation, montants, relectures, concurrence, audit atomique, contraintes SQL et maintien de l'interdiction de règlement. Une sonde de test dans une JVM séparée vérifie l'arrêt après commit avant réponse, les reprises et la concurrence entre deux instances. Les migrations et sommes de contrôle doivent rester identiques au redémarrage.

Les preuves exactes de la tête revue figurent dans la PR et les artefacts GitHub Actions. Aucune compilation ni suite lourde n'est exécutée sur le poste ou le VPS. Aucun déploiement, fournisseur réel ou portail Families n'est activé.
