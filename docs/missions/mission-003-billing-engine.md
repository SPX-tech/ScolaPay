# Mission 003 : ScolaPay Billing Engine

**Statut : cadrage. Aucun code du moteur n'existe.** La construction démarre une fois les [décisions attendues](#décisions-attendues-du-propriétaire) tranchées.

## Objectif

Permettre aux établissements de définir des frais adaptés à leurs structures académiques, de générer des obligations financières, de gérer échéances, remises et ajustements, et de calculer les montants réellement dus.

Modèle détaillé : [0015](../decisions/0015-modele-de-facturation.md). Événements : [0016](../decisions/0016-outbox-durable.md). Ouverture du portail : [0017](../decisions/0017-controles-de-mise-en-service.md).

## Périmètre

| Inclus | Exclu |
| --- | --- |
| Catalogue de frais par année et niveau | Encaissement réel, prestataires, webhooks |
| Émission d'obligations à l'inscription et en lot | Reçus, remboursements, rapprochement |
| Échéanciers | Comptabilité en partie double, exports |
| Remises et ajustements motivés, tracés | Interfaces web et portail |
| Rôles redevable, responsable, contributeur | Notifications réelles |
| Calcul du dû et vues par rôle | Android, NestJS, tout déploiement |
| Outbox durable et contrôle de mise en service | Pénalités de retard, sauf décision contraire |

## Critères d'acceptation

Prouvés par des tests sur PostgreSQL réel, sans doubles, et par des mutations sur chaque garantie critique.

**Montants**
- Toute colonne de montant est un `BIGINT` nommé `*_minor`. Pour le XAF, 25 000 francs valent `25000`.
- La somme des échéances égale le montant émis, y compris avec un reste de division.
- Aucune opération ne mélange deux devises.

**Justesse du dû**
- Le dû égale toujours la somme du journal. Modifier une écriture est refusé par la base.
- Un tarif modifié ne change aucune obligation déjà émise.
- Dix ajustements simultanés sur une obligation donnent le bon total.
- Une remise ne peut pas rendre le dû négatif.

**Indépendance vis-à-vis de l'encaissement**
- **Aucune route ne peut produire le statut `SETTLED` ni une écriture `SETTLEMENT`.** Le test parcourt toutes les routes réelles, comme la matrice d'isolation.
- Une remise totale produit `WAIVED`, jamais `SETTLED`.

**Rôles et confidentialité**
- Un contributeur ne reçoit que les champs de la vue minimale : le test compare la réponse à la liste exacte des champs autorisés.
- Un contributeur ne voit ni les lignes, ni les remises, ni les autres personnes liées.
- Un rôle financier n'ouvre jamais l'accès académique, et un intitulé de frais ne révèle pas le niveau d'un majeur sans consentement.

**Isolation**
- La matrice existante couvre d'office les routes `billing`.
- Une clé composite empêche en base toute obligation sur le dossier d'un autre établissement.

**Fiabilité**
- Les quatre tests de sortie de [0016](../decisions/0016-outbox-durable.md) sont verts.
- Une inscription rejouée n'émet pas deux obligations.

**Mise en service**
- Avec le portail activé et la règle du Tchad non confirmée, l'application refuse de démarrer.

## Lots

Chaque lot est une PR verte, indépendante, et met à jour `PROJECT_CONTEXT.md`.

| Lot | Contenu | Dépend de |
| --- | --- | --- |
| 1 | Outbox durable et ses tests de sortie, contrôle de mise en service | |
| 2 | Catalogue de niveaux dans `schooling` | Décision 5 |
| 3 | Module `billing` : catalogue de frais, publication | Lot 2 |
| 4 | Obligations, échéances et journal, émission idempotente | Lots 1 et 3 |
| 5 | Remises et ajustements, permissions associées | Lot 4, décisions 2 et 4 |
| 6 | Rôles, vues par rôle, retrait de `financial_contributor` | Lot 4, décisions 1 et 3 |
| 7 | Campagne de mutations et clôture | Tous |

## Décisions attendues du propriétaire

Chaque point a une recommandation. Une réponse « d'accord » suffit.

| N° | Question | Recommandation | Pourquoi c'est à trancher |
| --- | --- | --- | --- |
| 1 | Qui peut être redevable ? | Une personne majeure, ou une personne morale. Un étudiant majeur est redevable par défaut de ses frais, sauf désignation d'un tiers. Pour un mineur, un représentant doit être désigné. À confirmer avec un juriste | Détermine qui reçoit une obligation |
| 2 | Qui accorde les remises et ajustements ? | Permission `BILLING_ADJUST` pour le comptable et l'administrateur, motif obligatoire, sans seuil de double validation dans cette mission | Règle de séparation des tâches |
| 3 | Un responsable financier voit-il les remises et bourses ? | Oui s'il est aussi redevable, sinon le montant total sans le motif | Donnée sensible |
| 4 | Pénalités de retard ? | Exclues de cette mission | Encadrement légal à vérifier |
| 5 | Remplacer `enrollments.level`, texte libre, par un catalogue de niveaux par établissement ? | Oui, avant de tarifer : on ne peut pas facturer de façon fiable sur un texte libre | Touche le module `schooling` |
| 6 | Que devient une obligation après un retrait de l'élève ? | Elle reste due et passe en revue. La décision prend la forme d'un ajustement motivé, sans annulation automatique ni prorata | Règle financière propre à chaque établissement |

## Risques

- **Règles juridiques non confirmées** : qualité de redevable, confidentialité, consentement. Elles bloquent l'ouverture du portail, pas la construction.
- **Montants absents du catalogue actuel** : rien n'est à reprendre de NestJS, dont la convention est différente. Voir [0012](../decisions/0012-convention-monetaire.md).
- **Outbox** : le choix technique peut changer selon les tests du lot 1.
