# Mission 003 : ScolaPay Billing Engine

**Statut : cadrage validé par le propriétaire, construction autorisée lot par lot.** L'état de chaque lot est tenu à jour ci-dessous. Aucun déploiement ni paiement réel.

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
| Outbox durable et contrôle de mise en service | Pénalités de retard |

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
- Le montant restant dû n'a aucune route d'écriture directe.

**Rôles et confidentialité**
- Un contributeur ne reçoit que les champs de la vue minimale : le test compare la réponse à la liste exacte des champs autorisés.
- Un contributeur ne voit ni les lignes, ni les remises, ni les autres personnes liées.
- Un responsable non redevable voit montants et échéances, jamais les motifs de bourses et remises.
- Un rôle financier n'ouvre jamais l'accès académique, et un intitulé de frais ne révèle pas le niveau d'un majeur sans consentement.
- Aucun redevable n'est déduit de l'âge de l'étudiant ou d'un lien de parenté. Personnes physiques et morales sont représentables, et un redevable peut rester non confirmé.

**Ajustements**
- Sans motif ou sans la permission requise, un ajustement est refusé.
- Une remise totale ou une correction exceptionnelle n'a aucun effet avant la confirmation d'une seconde personne, et son auteur ne peut pas la confirmer lui-même.
- Un retrait ne modifie aucune obligation : il la place en réexamen.

**Isolation**
- La matrice existante couvre d'office les routes `billing`.
- Une clé composite empêche en base toute obligation sur le dossier d'un autre établissement.

**Fiabilité**
- Les quatre tests de sortie de [0016](../decisions/0016-outbox-durable.md) sont verts.
- Rejouer une inscription, relancer un lot, ou publier une nouvelle version du catalogue ne facture jamais deux fois le même frais.

**Mise en service**
- Le portail est fermé par défaut pour les familles : surface `/me` familiale en 404 et invitations familiales refusées.
- Avec le portail activé et une condition non remplie, l'application refuse de démarrer.

## Lots

Chaque lot est une PR verte, indépendante, et met à jour `PROJECT_CONTEXT.md`.

| Lot | Contenu | Dépend de |
| --- | --- | --- |
| 1 | Outbox durable et ses tests de sortie, contrôle de mise en service | |
| 2 | Catalogue académique structuré dans `schooling` | |
| 3 | Module `billing` : catalogue de frais et ses versions, publication | Lot 2 |
| 4 | Obligations, échéances et journal, émission sans double facturation | Lots 1 et 3 |
| 5 | Remises et ajustements, permissions, confirmation par une seconde personne, réexamen après retrait | Lot 4 |
| 6 | Personnes physiques et morales, rôles, vues par rôle, retrait de `financial_contributor` | Lot 4 |
| 7 | Campagne de mutations et clôture | Tous |

## Décisions du propriétaire

Confirmées le 2026-11-06, et intégrées à [0015](../decisions/0015-modele-de-facturation.md) :

1. **Redevable** identifié selon l'engagement effectivement établi. Jamais déduit de l'âge ni d'un lien de parenté. Personnes physiques et morales, redevable possiblement non confirmé.
2. **Remises et ajustements** par le comptable ou l'administrateur, avec motif obligatoire, permissions distinctes et audit immuable. Protection renforcée pour les remises totales et corrections exceptionnelles.
3. **Responsable financier non redevable** : montants et échéances, sans les motifs confidentiels. Le contributeur garde une vue strictement limitée au paiement.
4. **Pénalités de retard** exclues.
5. **Catalogue académique structuré** avant la tarification, adapté aux écoles et extensible aux filières, programmes et semestres, sans sur-ingénierie.
6. **Retrait** : aucune suppression automatique, réexamen, et ajustement autorisé, justifié et tracé.

Tout changement structurant ou question juridique non tranchée est remonté au propriétaire.

## Risques

- **Règles juridiques non confirmées** : qualité de redevable, confidentialité, consentement. Elles bloquent l'ouverture du portail, pas la construction.
- **Montants absents du catalogue actuel** : rien n'est à reprendre de NestJS, dont la convention est différente. Voir [0012](../decisions/0012-convention-monetaire.md).
- **Outbox** : le choix technique peut changer selon les tests du lot 1.
- **Numérotation légale des factures** : non tranchée, voir [0015](../decisions/0015-modele-de-facturation.md#questions-ouvertes).
