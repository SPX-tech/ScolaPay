# 0030 : réservations des excédents jamais affectés

- **Statut** : proposition technique du lot 2, en revue. La restitution limitée est une exigence acquise de 0027 ; A05 à A08 restent ouverts.
- **Date** : 2026-11-12
- **Références** : [0027](0027-restitution-excedents-jamais-affectes.md), [0029](0029-premier-reglement-especes.md), [contrat HTTP](../payments-cash-excess.md).

## Portée livrée

La tranche réserve une fraction jamais affectée d'une réception existante et conserve une demande motivée puis une décision indépendante. Elle n'effectue aucune remise physique et ne crée aucun mouvement de sortie. L'approbation est un examen du budget, avec la portée immuable `RESERVATION_ONLY_V1`, jamais une autorisation de remettre les espèces à une personne.

L'identité du bénéficiaire, son mandat, les permissions de remise et les justificatifs définitifs ne sont pas définis. Aucun endpoint, état final ou paramètre de configuration ne permet donc de confirmer une restitution dans cette livraison. Ces absences ne résolvent pas A05 à A08. La cible V1 de 0027 et le lot 2 restent incomplets.

## Provenance sans solde de crédit

Dans le périmètre actuel, les réceptions et affectations sont immuables, les rectifications et compensations de règlements sont interdites et aucune restitution n'existe. Le calcul est donc :

- `F` : montant reçu moins monnaie rendue, fait immuable V8 ;
- `H` : somme de **toutes** les affectations historiques de la réception, sans lire un solde net ou un statut de billing ;
- `Q` : somme des demandes sans décision de rejet ou retrait ; une approbation conserve la réservation ;
- `E = F - H - Q` : disponible pour une nouvelle réservation ou affectation ; `T = 0`.

Cette représentation suffit à ce périmètre fermé. Elle ne prétend pas implémenter tout le modèle N/L/T proposé par 0027. L'introduction future d'une compensation, d'un remplacement ou d'une rectification devra conserver H comme consommation historique irréversible et distinguer disponibilité courante et provenance éligible. Le fait de libérer une réservation **avant** approbation ne supprime aucune affectation historique et ne crée pas de provenance.

Une nouvelle référence sur la même réception ne réamorce pas F. La création mensongère d'une autre réception pour les mêmes billets reste une fraude déclarative que le logiciel seul ne peut reconnaître ; A03, A04 et les preuves physiques sont nécessaires. Aucun transfert de provenance ou crédit par compte ou élève n'est ajouté.

## Faits et décisions immuables

V10 ajoute `cash_excess_requests` et `cash_excess_decisions`, sans modifier V1 à V9. La demande contient source, devise, montant, motif, auteur, date, référence et instantané F/H/Q avant réservation. Son contenu et sa portée ne sont jamais modifiables. Une décision unique, elle aussi immuable, vaut approbation, rejet motivé ou retrait motivé. Les preuves restent des enregistrements internes suivant 0019, sans valeur fiscale affirmée.

| État calculé | Q | Suite dans cette tranche |
| --- | --- | --- |
| `REQUESTED` | Conservé | Approbation ou rejet par un autre compte, retrait par l'auteur |
| `APPROVED_RESERVED` | Conservé | Relecture et rejeu de la décision seulement |
| `REJECTED`, `WITHDRAWN` | Libéré | Relecture et rejeu ; nouvelle demande avec une nouvelle intention si nécessaire |

Il n'existe ni expiration, ni retrait ou rejet après approbation, ni remise implicite par une reprise. Ce choix conservateur peut immobiliser un budget approuvé : il doit être assumé avant toute activation de test. La gestion des incidents et une éventuelle libération après approbation nécessitent leur propre politique. Les approbations `RESERVATION_ONLY_V1` ne pourront pas devenir silencieusement des autorisations physiques lors d'une évolution ; il faudra une nouvelle décision, les preuves et la revalidation alors requises.

## Concurrence, intégrité et audit

Demandes, décisions et affectations utilisent le même verrou de réception V9. Les montants sont relus dans une requête distincte après acquisition du verrou. Les écritures exigent `READ COMMITTED`, comme les règlements ; les lectures composées emploient `REPEATABLE READ`. Les fonctions de garde SQL sont `VOLATILE` par défaut pour relire l'état après attente, suivant les limites décrites en 0029.

Les gardes V10 contrôlent F/H/Q et les instantanés, y compris les écritures SQL directes ; une garde additive interdit à une affectation de consommer Q sans remplacer les contrôles V9. Références, montants, devise, établissement, affiliation des auteurs, unicité de la décision et distinction des comptes sont contraints. NULL ne rend aucun contrôle facultatif. Les triggers d'immutabilité refusent modification, suppression et troncature.

Chaque demande ou décision exige au commit son audit lié au même établissement, auteur, référence et source. Une erreur annule aussi la réservation ou sa libération. Aucun audit général ne recopie les motifs privés. Les permissions HTTP restent contrôlées par `EstablishmentAccess` ; les contraintes SQL ne remplacent pas l'authentification et les habilitations applicatives d'un compte détenant l'accès direct à la base.

Chaque famille de commande possède une clé UUID permanente par établissement. Le rejeu est lié à l'auteur et au contenu normalisé, et ne reprend pas une intention différente. Les lectures par clé survivent à la désactivation des écritures. Une issue réseau incertaine impose de retrouver cette identité, sans nouvelle référence ni geste physique. Il n'existe pas d'effet monétaire secondaire asynchrone.

## Permissions limitées à la réservation

Le déclarant d'une réception peut demander une réservation avec le rôle caissier, comptable ou administrateur. Comptable, administrateur et directeur peuvent examiner le budget d'un autre compte. L'auteur seul retire sa demande encore sans décision ; l'auditeur reste en lecture seule. Les motifs sont visibles de leur auteur et des lecteurs habilités aux motifs (comptable, administrateur, directeur, auditeur). Un autre caissier ne les reçoit pas.

Ces capacités sont celles de l'examen technique du budget et de la conservation de caisse dans cette tranche. Elles ne désignent ni bénéficiaire ni auteur du geste physique, ne créent aucun mandat et ne valent pas politique définitive d'A05 à A08. Aucun droit familial n'est ajouté.

## Arbitrages nécessaires avant remise effective

1. **Bénéficiaire et mandat** : définir comment établir et conserver le lien entre la réception, le remettant et la personne recevant l'excédent. Recommandation à arbitrer : une identité vérifiée et une preuve explicite de mandat si elle diffère ; ni parenté ni connexion seules ne suffisent.
2. **Habilitation de remise** : décider qui peut confirmer le geste physique et les incompatibilités entre demande, examen et remise. A01 exige deux comptes pour la demande et l'approbation ; aucune troisième personne n'est imposée ici.
3. **Preuve et incident** : arrêter les justificatifs, leur confidentialité, leur conservation et le traitement d'une remise incertaine ou déconnectée. Recommandation à arbitrer : réserver les fonds tant que l'incident n'est pas résolu par un contrôle indépendant, sans libération par délai technique.
4. **Transition de politique** : revalider les anciennes réservations techniques avec le bénéficiaire, les preuves et les permissions retenus. Aucune approbation V1 ne doit déclencher une remise à elle seule.

Le secours manuel A03 et le contrôle physique quotidien A04 restent à réaliser avant pilote. La fonctionnalité est désactivée par défaut ; ni déploiement, migration persistante, fournisseur ni ouverture de Families n'est autorisé par cette ADR.
