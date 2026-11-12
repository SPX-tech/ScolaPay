# 0029 : premier règlement espèces et intégrité entre modules

- **Statut** : Tranche technique intégrée par #32 au commit `e49d802`, lot 2 encore ouvert. A05 a depuis été validé explicitement, voir [0031](0031-identification-et-autorisation-de-restitution.md). A06 à A08 restent ouverts pour leurs autres modalités.
- **Date** : 2026-11-11
- **Références** : [0026](0026-contrats-encaissements-especes.md), [0027](0027-restitution-excedents-jamais-affectes.md), [0028](0028-projections-reglements-et-ajustements.md).

## Choix techniques et décisions métier encore ouvertes

Le parcours initial #32 réutilisait une réception sans collecter l'identité du remettant ; cette limite ne constituait aucune validation d'A05. A05 est désormais explicitement accepté : les nouvelles réceptions exigent une identité suivant 0031 et les anciennes suivent une déclaration signée examinée indépendamment. Le port d'affectation ne crée toujours ni personne financière, ni droit familial, ni bénéficiaire implicite.

Les codes `OPEN`, `PARTIALLY_SETTLED`, `SETTLED` et `SETTLED_WITH_ADJUSTMENTS` décrivent l'état technique calculé et testé, avec montants facturés, ajustés et réglés distincts et conservation des statuts historiques sans paiement. Leur présence dans le code ne vaut pas arbitrage métier A08. Leur usage opérationnel, les justificatifs, le contenu fiscal, les remboursements et les autres états restent à décider.

## Périmètre conservateur

Une commande affecte un montant explicite à une obligation d'une année ouverte, à partir de sa réception existante. Dans cette tranche, seul le déclarant peut affecter sa réception, et celle-ci reste liée à une seule obligation. Ces limites techniques évitent un transfert de caisse ou un panier non défini ; elles ne constituent pas une politique définitive de délégation ou de multi-obligations.

Une correction ou annulation active bloque l'affectation. Après règlement, corrections, annulations et contrepassations génériques sont refusées ; les remises compatibles avec le reste suivent A02 et les validations sensibles. Les règles encore inconnues ne sont pas remplacées par une ventilation inventée. Les fonds reçus restent enregistrés si une affectation est refusée.

## Frontière et transaction

`payments` appelle le port public `BillingSettlements`. `billing` conserve le journal, les projections et les échéances ; il ne dépend pas de `payments.internal`. Aucun dépôt Java n'accède aux tables de l'autre module. Les références croisées et gardes SQL documentées ci-dessous sont des contraintes d'intégration dans la base commune.

La réception a déjà été confirmée et auditée. La transaction d'affectation verrouille cette source, puis l'obligation, relit les montants et la version du journal et crée l'affectation, son `SETTLEMENT`, sa preuve et l'audit. Les gardes verrouillent aussi l'année en lecture partagée pendant cette confirmation. Tous les nouveaux faits sont validés ou annulés ensemble. La réception source n'est jamais recréée ni supprimée par cette transaction.

L'identité de commande est unique par établissement et opération. Le rejeu est lié à l'auteur, la source, la cible, le montant et la version présentée. Il retrouve la preuve historique avant de contrôler un nouvel état du journal. Un plan modifié ne peut pas être repris silencieusement ; après issue HTTP inconnue, une lecture par clé permet la reprise même si les nouvelles affectations sont désactivées.

## Intégrité PostgreSQL

V9 ajoute une table immuable `cash_allocations` et une référence dans `obligation_entries`, sans réécrire V1 à V8. Les contraintes vérifient :

- une réception confirmée, avec preuve et audit V8, dans le même établissement et la même devise que l'obligation ;
- montant disponible sur la source, reste à payer suffisant et version du journal concordante sous verrou ;
- un seul règlement correspondant à chaque affectation et aucune affectation confirmée sans règlement ;
- égalité des références, montants, auteur et horodatage, et preuve liée à son audit au commit ;
- non-négativité et bornes du journal, impossibilité de remplacer un règlement par une contrepassation générique ;
- maintien de la seconde personne pour les ajustements qui éteignent le dû. L'exception ne concerne que le `SETTLEMENT` relié à sa source vérifiée.

L'ordre SQL pris en charge est affectation puis écriture correspondante, dans la même transaction. La première référence est différée jusqu'au commit ; la seconde et les gardes interdisent d'insérer un règlement sans source. Les snapshots conservés dans la preuve sont historiques, jamais un second solde courant. Le reste courant demeure la somme du journal.

## Isolation des écritures et des lectures

Les écritures d'affectation et du journal exigent explicitement `READ COMMITTED`. Les gardes refusent les autres isolations, y compris en SQL direct. Les services financiers existants écrivent déjà avec cette isolation par défaut ; leurs lectures composées restent en `REPEATABLE READ`.

Un verrou sur un parent immuable ne rend pas actuel un ancien instantané de somme. PostgreSQL décrit cette limite des [verrous explicites](https://www.postgresql.org/docs/18/applevel-consistency.html). En `READ COMMITTED`, les fonctions de garde, `VOLATILE` par défaut, prennent un nouvel instantané pour leurs requêtes, suivant les [règles de volatilité](https://www.postgresql.org/docs/18/xfunc-volatility.html). Le verrou parent sérialise alors les contrôles et insertions. Aucun verrou consultatif ou cache de solde local n'est ajouté.

Le refus d'isolations d'écriture non prises en charge est une restriction technique de cette livraison. Les autoriser ultérieurement nécessiterait une stratégie et des tests spécifiques ; retirer simplement la garde ne serait pas suffisant. Cette restriction ne limite pas les instantanés cohérents des lectures.

## Montants, échéances et confidentialité

Les projections distinguent initial I, ajustements A, net facturé B = I + A, règlements affectés P et reste R = B − P. Les paiements ne consomment pas le cumul des ajustements sensibles. Une remise dépasse le reste : refus, sans crédit créé ni montant tronqué.

Pour les écritures actives prises en charge, A02 réduit les échéances les plus éloignées et couvre les plus anciennes. La contrainte R ≥ 0 garantit que la réduction ne touche pas les montants déjà couverts. Les échéances initiales ne bougent jamais. La couverture courante est dérivée du journal et de cet échéancier immuable ; aucune ligne de paiement ne choisit arbitrairement une échéance différente.

Les vues familiales conservent leurs types et permissions. Elles ne recopient ni preuves, ni acteurs, ni versements individuels de caisse. Le total d'ajustements du responsable exclut les règlements. Families reste fermé en production.

## Limites et vérification

Les deux capacités d'écriture restent désactivées par défaut. Il n'existe ni crédit libre, remboursement, restitution effective, rectification de caisse, panier multi-obligations ni fournisseur. La restitution V1 reste exigée pour terminer le lot 2 ; les réservations ajoutées par [0030](0030-reservations-excedents-especes.md) complètent la protection du budget sans autoriser de remise physique. V10 complète V9 sans la réécrire et les affectations excluent désormais les montants réservés.

Les tests sont exécutés sur PostgreSQL jetable : API, montants, droits, concurrence entre appels et instances, arrêt après commit avant réponse, SQL direct, audit défaillant, péremption et régressions M003. L'absence de `SETTLED` dans une énumération historique est remplacée par une preuve fonctionnelle : une remise complète reste `WAIVED`, avec zéro règlement, et une écriture de paiement sans source reste impossible.

Les campagnes de mutations M003 restent des preuves de leur périmètre historique. Certaines ciblent la définition V6 de la garde, remplacée ici par V9 : leur réutilisation pour M004 demandera de cibler la définition effective et de requalifier les scénarios. Aucun résultat historique de mutations n'est présenté comme une validation de cette nouvelle chaîne.

A03 et A04 restent à réaliser avant pilote. A05 et les deux précisions métier de 0031 sont validés ; A06 à A08 restent ouverts pour leurs autres modalités. Aucun déploiement ni migration persistante n'est autorisé par cette ADR.
