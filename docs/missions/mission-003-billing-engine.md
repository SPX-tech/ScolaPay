# Mission 003 : ScolaPay Billing Engine

**Périmètre livré techniquement terminé, clôture proposée dans #29.** Les lots 1 à 6 sont intégrés. Le lot 7 a validé 195 mutations sur 195 avec trois contrôles sains et une CI complète de 461 tests Java sur le code consolidé après #26, #27 et #28. Les preuves et leurs SHA figurent dans le [journal de validation](../mission-003-validation.md).

## Objectif

Permettre aux établissements de définir des frais adaptés à leurs structures académiques, de générer des obligations financières, de gérer échéances, remises et ajustements, et de calculer les montants réellement dus.

Modèle détaillé : [0015](../decisions/0015-modele-de-facturation.md). Événements : [0016](../decisions/0016-outbox-durable.md). Ouverture du portail : [0017](../decisions/0017-controles-de-mise-en-service.md).

## Périmètre

| Inclus | Exclu |
| --- | --- |
| Catalogue de frais par année et unité académique, périodes de facturation | Encaissement réel, prestataires, webhooks |
| Émission d'obligations à l'inscription et en lot | Reçus, factures fiscales, remboursements, rapprochement |
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
- Un retrait, une réinscription, un nouveau tarif ou un changement de niveau ne modifient jamais une obligation : ils se signalent au réexamen.

**Indépendance vis-à-vis de l'encaissement**
- **Aucune route ne peut produire le statut `SETTLED` ni une écriture `SETTLEMENT`.** Le test parcourt toutes les routes réelles, comme la matrice d'isolation.
- Une remise totale produit `WAIVED`, une combinaison de remises et de corrections `ADJUSTED_TO_ZERO`, jamais `SETTLED`.
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
- Rejouer une inscription, relancer un lot, publier une nouvelle version du catalogue ou corriger un code de frais ne facture jamais deux fois la même charge.
- Publier un tarif ne crée aucune dette. Un élève déjà inscrit n'est facturé que par une émission explicite.

**Mise en service**
- Le portail est fermé par défaut pour les familles : surface `/me` familiale en 404 et invitations familiales refusées.
- Avec le portail activé et une condition non remplie, l'application refuse de démarrer.

## Lots

Résultats et limites dans le [journal de validation](../mission-003-validation.md).

Chaque lot est une PR verte, indépendante, et met à jour `PROJECT_CONTEXT.md`.

| Lot | Contenu | Dépend de | État |
| --- | --- | --- | --- |
| 1 | Outbox durable et ses tests de sortie, contrôle de mise en service | | **Terminé** |
| 2 | Catalogue académique structuré dans `schooling`, voir [0018](../decisions/0018-catalogue-academique.md) | | **Terminé** |
| 3 | Module `billing` : catalogue de frais, identité stable des charges, applicabilité, périodes de facturation, publication et versions, voir [0020](../decisions/0020-catalogue-de-frais-et-periodes.md) | Lot 2 | **Terminé** |
| 4 | Obligations, échéances et journal, émission sans double facturation, élèves déjà inscrits, distinction des [documents](../decisions/0019-nature-des-documents-et-numerotation.md), voir [0021](../decisions/0021-obligations-et-journal.md) | Lots 1 et 3 | **Terminé** |
| 5 | Remises, corrections, annulations, confirmation par une seconde personne, réexamen, voir [0022](../decisions/0022-ajustements-et-reexamen.md) | Lot 4 | **Terminé** |
| 6 | Personnes physiques et morales, rôles, vues par rôle, retrait de `financial_contributor`, voir [0023](../decisions/0023-personnes-liees-et-vues.md) | Lot 4 | **Terminé, intégré par #26** |
| 7 | Consolidation, campagne finale et bilan de clôture | Tous | **Validation technique terminée, clôture en revue dans #29** |

## Décisions du propriétaire

Confirmées le 2026-11-06, et intégrées à [0015](../decisions/0015-modele-de-facturation.md) :

1. **Redevable** identifié selon l'engagement effectivement établi. Jamais déduit de l'âge ni d'un lien de parenté. Personnes physiques et morales, redevable possiblement non confirmé.
2. **Remises et ajustements** par le comptable ou l'administrateur, avec motif obligatoire, permissions distinctes et audit immuable. Protection renforcée pour les remises totales et corrections exceptionnelles.
3. **Responsable financier non redevable** : montants et échéances, sans les motifs confidentiels. Le contributeur garde une vue strictement limitée au paiement.
4. **Pénalités de retard** exclues.
5. **Catalogue académique structuré** avant la tarification, adapté aux écoles et extensible aux filières, programmes et semestres, sans sur-ingénierie.
6. **Retrait** : aucune suppression automatique, réexamen, et ajustement autorisé, justifié et tracé.

Tout changement structurant ou question juridique non tranchée est remonté au propriétaire.

Les questions de [0023](../decisions/0023-personnes-liees-et-vues.md#décisions-ouvertes-pour-le-propriétaire) restent ouvertes après la clôture technique du périmètre livré. La réassociation d'un compte, plusieurs redevables et une éventuelle vue de l'étudiant sans rôle nécessiteraient des décisions et des évolutions distinctes. Les limites d'exploitation sont conservées dans le journal.

## Risques

- **Règles juridiques non confirmées** : qualité de redevable, confidentialité, consentement. Elles bloquent l'ouverture du portail, pas la construction.
- **Montants absents du catalogue actuel** : rien n'est à reprendre de NestJS, dont la convention est différente. Voir [0012](../decisions/0012-convention-monetaire.md).
- **Outbox** : un listener peut s'exécuter deux fois et sans ordre garanti. Le lot 4 doit respecter les [limites établies](../decisions/0016-outbox-durable.md#limites-à-respecter).
- **Conservation de l'archive des événements** : 180 jours à titre provisoire, sans purge automatique, voir [0016](../decisions/0016-outbox-durable.md#conservation).
- **Traitement fiscal et FEN au Tchad** : à valider avec un conseil fiscal et la DGI. Aucun document n'est présenté comme conforme d'ici là, voir [0019](../decisions/0019-nature-des-documents-et-numerotation.md#questions-à-poser-à-un-conseil-fiscal-et-à-la-dgi).
