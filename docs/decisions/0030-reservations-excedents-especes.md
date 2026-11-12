# 0030 : réservations des excédents jamais affectés

- **Statut** : tranche préparatoire validée et intégrée par #33 au commit `7b38965`. A05 a depuis été accepté et complété par [0031](0031-identification-et-autorisation-de-restitution.md) ; A06 à A08 restent ouverts pour leurs autres modalités.
- **Date** : 2026-11-12
- **Références** : [0027](0027-restitution-excedents-jamais-affectes.md), [0029](0029-premier-reglement-especes.md), [contrat HTTP](../payments-cash-excess.md).

## Portée livrée

La tranche réserve une fraction jamais affectée d'une réception existante et conserve une demande motivée puis une décision indépendante. Elle n'effectue aucune remise physique et ne crée aucun mouvement de sortie. L'approbation est un examen du budget, avec la portée immuable `RESERVATION_ONLY_V1`, jamais une autorisation de remettre les espèces à une personne.

La tranche #33 ne définissait pas l'identité du bénéficiaire ou la remise. A05 et les précisions sur la déclaration historique et le justificatif papier sont depuis validés en 0031 ; la préparation nominative les applique sans modifier les faits de cette ADR. Aucun endpoint ou paramètre ne confirme une restitution physique. La cible V1 de 0027 et le lot 2 restent incomplets, et les autres modalités d'A06 à A08 restent ouvertes.

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

Ces capacités restent celles de l'examen technique du budget dans #33. Elles ne désignent ni bénéficiaire ni auteur du geste physique et ne créent aucun mandat. La nouvelle préparation nominative de 0031 applique A05 sans transformer ces décisions historiques. Aucun droit familial n'est ajouté.

## Arbitrages nécessaires avant remise effective

1. **Bénéficiaire et mandat** : A05 est acquis ; 0031 construit le cas du remettant source. Le contrôle des droits ou mandats d'un tiers reste à préciser avant d'ouvrir ce cas, sans déduction de parenté ou de connexion.
2. **Habilitation de remise** : le caissier habilité peut confirmer après approbation par un compte distinct du demandeur, sans troisième personne systématique. Le parcours physique reste à construire et vérifier.
3. **Preuve et incident** : le justificatif papier signé bénéficiaire/caissier et conservé par l'établissement est validé. Le numérique signé et les incidents incertains restent à cadrer. Recommandation à arbitrer : résolution indépendante et documentée, aucun délai ne libérant le budget.
4. **Transition de politique** : 0031 construit une autorisation nominative distincte après vérification de la source. Aucune approbation V1 seule ne vaut autorisation physique ; le contrôle final au moment de la remise reste obligatoire et non construit.

Le secours manuel A03 et le contrôle physique quotidien A04 restent à réaliser avant pilote. La fonctionnalité est désactivée par défaut ; ni déploiement, migration persistante, fournisseur ni ouverture de Families n'est autorisé par cette ADR.
