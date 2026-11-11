# 0026 : contrats du domaine d'encaissement espèces

- **Statut** : Orientation métier acceptée. Contrats d'architecture proposés pour la revue du lot 1, avant autorisation du développement.
- **Date** : 2026-11-11
- **Décideur** : propriétaire du projet pour l'orientation. Les arbitrages A01 à A08 du cadrage restent ouverts.
- **Implémentation** : aucune. Le module `payments`, ses ports Java et ses routes n'existent pas.
- **Complète** : [0010](0010-identite-et-acces.md), [0012](0012-convention-monetaire.md), [0015](0015-modele-de-facturation.md), [0016](0016-outbox-durable.md), [0019](0019-nature-des-documents-et-numerotation.md), [0021](0021-obligations-et-journal.md) et [0022](0022-ajustements-et-reexamen.md).
- **Remplace** : aucune décision passée. Prépare l'extension additive de la facturation pour les règlements confirmés.

## Contexte vérifié

La Mission 003 est clôturée au commit `9f54029`. `billing` conserve ses services dans `internal` et n'expose pas encore de port public de règlement. V6 refuse `SETTLEMENT`, verrouille l'obligation et impose une seconde personne pour une écriture non initiale qui éteint le dû. Ces garanties sont correctes pour le périmètre existant.

`CurrentActor` détermine le compte connecté. `EstablishmentAccess` exige une adhésion et un établissement actifs, puis la permission. Le rôle d'opérateur ne donne aucun accès aux finances d'un établissement. `AuditTrail.record` impose une transaction existante. Les nouvelles opérations doivent suivre ces frontières.

## Orientation validée

Un module Java `payments` enregistre les espèces effectivement reçues, leurs affectations et leurs preuves. Il est distinct de `billing`. Les paiements partiels sont conservés et les paniers multi-obligations puis multi-élèves sont introduits progressivement. La restitution des excédents jamais affectés suit [0027](0027-restitution-excedents-jamais-affectes.md).

La confirmation d'espèces repose sur l'attestation d'un acteur habilité. Le logiciel garantit la cohérence et l'historique de cette déclaration, sans constater les billets. La procédure de contrôle physique reste à arbitrer en A04.

## Contrats proposés entre modules

| Propriétaire | Service rendu | Entrées et résultat attendus |
| --- | --- | --- |
| `identity` | Identifier l'acteur | Compte issu du contexte de sécurité, jamais choisi dans le corps de commande. Le JWT ne fournit ni permission financière ni établissement de confiance |
| `establishment` | Autoriser l'action | Établissement explicite et capacité requise. Compte, adhésion et établissement actifs. Non-membre en 404, membre sans capacité en 403, suivant les conventions existantes |
| `billing` | Lire les cibles financières sous verrou | Identifiants d'obligations, devise, montant initial, ajustements, règlements, reste, échéances et version du journal. Toute référence est contextualisée par établissement |
| `billing` | Inscrire un règlement justifié | Référence immuable d'affectation confirmée, montant et devise. Une écriture `SETTLEMENT` correspondant exactement à cette affectation, ou refus de toute la transaction |
| `billing` | Compenser un règlement par un chemin dédié | Référence d'origine et preuve du mouvement correctif autorisé. Conservation des contraintes de source, de montant et d'unicité. Aucune route générique d'ajustement ne peut l'utiliser |
| `payments` | Constater la réception | Référence de réception, montants, remettant déclaré, attestation, auteur serveur, dates et clé d'idempotence. Une réception durable et son justificatif |
| `payments` | Affecter ou régulariser | Réception source, plan explicite et état financier attendu. Affectations avec règlements correspondants, ou refus sans effet partiel implicite |
| `payments` | Demander, approuver puis confirmer une restitution | Contrat de 0027, dont la provenance jamais affectée et la remise physique |
| `audit` | Tracer l'opération et ses décisions | Établissement, acteur serveur, action, cible et références techniques. Écriture dans la même transaction que le changement |

Les capacités et leur attribution aux rôles sont proposées, pas ajoutées au code par cette ADR. Le caissier possède aujourd'hui `BILLING_READ`, sans capacité d'encaissement. A01 doit préciser réception, affectation, lecture, demande, approbation, remise physique et rectification.

Ce sont des API de modules internes. La future surface HTTP d'encaissement reste sous `/api/v2/establishments/{establishmentId}`, protégée par la barrière d'adhésion existante. Aucun point d'entrée HTTP libre n'autorise l'insertion d'un règlement dans le journal.

## Frontière et transaction

La dépendance Java cible est `payments` vers l'API publique de `billing`, avec les API publiques d'identité, d'établissement, d'audit et de `shared`. `billing` ne dépend pas de l'implémentation `payments.internal`. Aucun dépôt ne lit ou n'écrit les tables de l'autre module.

Les contraintes SQL d'intégration assurent les références et la conservation au commit dans la base commune. Leur définition devra documenter l'appartenance des tables, les références croisées et l'ordre de verrouillage. Cette cohérence de schéma ne donne aucun droit d'accès aux dépôts applicatifs.

Le service d'encaissement ouvre une transaction locale courte. Le port d'écriture de `billing` et l'audit doivent y participer. Une écriture monétaire ne passe ni par une transaction indépendante ni par un listener exécuté après commit.

Ordre proposé pour une confirmation :

1. Identifier et autoriser l'acteur dans l'établissement.
2. Réserver ou retrouver la clé d'idempotence et verrouiller la réception concernée.
3. Verrouiller toutes les obligations concernées dans un ordre stable.
4. Relire les restes et vérifier le plan, la devise, les montants et les versions présentées.
5. Enregistrer réception ou mouvement, affectations, règlements et données justificatives.
6. Inscrire audit et publications durables nécessaires, puis valider ensemble.

Une erreur de contrainte, d'audit ou de publication annule l'ensemble des écritures de cette commande. Elle ne prétend pas annuler une réception physique déjà intervenue. La déclaration à régulariser ou la procédure de secours la prendra en charge.

## Réceptions et affectations

Une réception appartient à un établissement et une devise. Elle peut porter plusieurs affectations. L'élève se déduit de chaque obligation, sans identifiant d'élève unique imposé au niveau de la réception.

Le montant reçu, la monnaie réellement rendue au moment de la réception et le montant conservé sont distincts. Chaque montant respecte 0012, entier dans l'unité minimale ISO et chaîne décimale dans le JSON. Sommes et bornes sont contrôlées sans débordement. Les bornes de panier restent à fixer en A08.

Une réception ordinaire et ses affectations valides sont atomiques. Si le plan n'est plus applicable, aucune ligne n'est affectée silencieusement à sa place. Si les espèces sont déjà reçues, une déclaration à régulariser peut confirmer la réception sans affectation. Une affectation ultérieure a sa propre référence idempotente et consomme la réception existante.

Après résultat réseau ambigu, le client doit retrouver ou rejouer la même commande avant de déclarer une nouvelle réception. Un passage vers le parcours d'incident ne crée pas une seconde réception des mêmes fonds.

Le payeur peut être sans compte, sous réserve d'A05. Son identité déclarée ne désigne aucun redevable, ne crée aucun rôle financier et n'ouvre aucun dossier académique. Une personne financière existante conserve son identité et les règles de correction de 0023.

## Conservation au commit

Le futur schéma doit prouver les invariants suivants, y compris par SQL direct :

- chaque `SETTLEMENT` possède une affectation et une réception confirmées compatibles avec son établissement, sa devise et son montant ;
- chaque affectation monétaire confirmée possède exactement son écriture correspondante ;
- une affectation ne dépasse pas le reste disponible sous verrou ;
- les écritures et leurs sources ne peuvent pas être réutilisées sous une autre réception ou un autre établissement ;
- les références d'opération, d'affectation et de preuve sont uniques et jamais réutilisées après rectification ;
- les montants affectés, non affectés et restitués se réconcilient avec le montant conservé, selon 0027 ;
- une compensation a une source et une autorisation propres, conserve l'original et ne reconstitue pas l'éligibilité jamais affectée.

Une réception à régulariser sans affectation n'a aucun `SETTLEMENT`. L'existence d'un reçu de fonds ne permet pas de déclarer une obligation réglée.

## Idempotence, concurrence et lectures

La clé de commande est unique dans son contexte d'établissement et d'opération. Le serveur compare un contenu canonique. Un rejeu identique renvoie le résultat existant. Un contenu différent pour une clé déjà engagée produit un conflit. L'autorisation de consulter ce résultat est toujours vérifiée.

L'ordre de verrouillage doit être commun aux réceptions, affectations, rectifications et restitutions. Les ajustements de facturation gardent leur verrou d'obligation. Les délais sont bornés et un refus transitoire n'autorise aucun traitement partiel ou contournement des contrôles.

Les futurs contrats d'erreur suivent RFC 9457. Montants invalides en 422, plan ou version périmés et clé déjà utilisée avec un autre contenu en 409, ressource d'un autre établissement en 404 et permission absente en 403 après la barrière d'adhésion. Une erreur technique ne révèle ni paramètres SQL, ni contact, ni trace interne. Après un délai dépassé, la lecture par la référence idempotente détermine l'issue de l'opération.

Les lectures composées doivent utiliser un instantané cohérent ou une requête SQL unique. Les détails de reçu figent les informations nécessaires au moment de l'opération. Un solde actuel ne remplace pas rétroactivement le solde indiqué dans une preuve historique.

## Audit, événements et confidentialité

Le registre de caisse conserve les faits monétaires et les références justificatives. L'audit relie les auteurs, motifs autorisés, décisions et mouvements dans la transaction. Ses métadonnées générales privilégient les références techniques, sans recopier les motifs confidentiels ou contacts.

Les événements destinés à des effets secondaires transportent les références nécessaires. Ils suivent les garanties réelles de l'outbox, dont les reprises concurrentes et l'absence d'ordre. Les consommateurs sont idempotents. La livraison d'un message ou la production d'un PDF ne décide jamais de la confirmation monétaire.

Les permissions s'appliquent aux données rendues. Un reçu multi-élèves peut nécessiter une projection limitée. Les futurs clients ne déduisent aucun droit d'un identifiant de reçu. Families reste fermé.

## Références de preuve

La référence d'opération identifie un fait de réception, d'affectation, de décision ou de restitution. La référence du reçu identifie la preuve conservée. Les modifications ultérieures créent leurs propres références et liens, avec mention d'invalidation ou rectification quand nécessaire.

Ces références sont internes et sans garantie de continuité fiscale. Le contenu fiscal, le numéro éventuellement attribué par un système agréé et les obligations de conservation restent soumis à 0019. La V1 propose des données justificatives structurées. Leur rendu et contenu final relèvent d'A08 et des validations juridiques applicables.

## Vérification et questions ouvertes

S01 à S23 constituent le référentiel de la Mission 004, réparti dans la [matrice de portée par lot](../missions/mission-004-cash-collections.md#portée-des-scénarios-par-lot). Le lot 2 prouve le socle sur une obligation et le refus des parcours non autorisés. Les variantes multi-obligations relèvent du lot 3, les variantes multi-élèves du lot 4, les rectifications ultérieures autorisées du lot 5 et la consolidation du lot 6. Un scénario n'impose pas d'activer une capacité avant son lot ou avant arbitrage.

Les garanties fondamentales restent exigées dès le premier `SETTLEMENT`, notamment la provenance jamais affectée et le refus de toute compensation ou rectification qui les contournerait. Chaque nouvelle capacité reçoit ses preuves complètes avant activation. Les relations financières, budgets, acteurs distincts et sources sont testés par la vraie API, la concurrence et le SQL direct sur PostgreSQL jetable.

A01 à A08 précisent les permissions, échéances, secours, contrôle physique, identité du remettant, dates, rectifications et justificatifs. Cette ADR ne crée aucun contrat HTTP ou Java exécutable et ne vaut pas autorisation de développement.
