# Décisions d'architecture

Une décision mérite une entrée ici lorsqu'elle est coûteuse à inverser ou qu'elle oriente le travail de plusieurs missions : langage ou framework, modèle de données partagé, frontière entre services, prestataire financier, modèle de sécurité. Le reste vit dans le code et les PR.

## Index

| N° | Décision | Statut | Implémentation |
| --- | --- | --- | --- |
| [D-001](../architecture-scolapay-2.md#décision-001--conserver-le-monolithe-modulaire) | Monolithe modulaire | Acceptée, monolithe modulaire conservé, choix de NestJS pour les futurs domaines remplacé par 0006 | En place |
| [D-002](../architecture-scolapay-2.md#décision-002--isolation-explicite-par-établissement) | Isolation explicite par établissement | Acceptée | Partielle : contrôle applicatif, contraintes composites à venir |
| [D-003](../architecture-scolapay-2.md#décision-003--conserver-les-unités-financières-historiques) | Unités financières historiques | Acceptée pour le code NestJS, remplacée par 0012 pour le nouveau backend | En place dans `backend/` |
| [D-004](../architecture-scolapay-2.md#décision-004--prestataires-financiers-fermés-par-défaut) | Prestataires fermés par défaut | Acceptée | En place |
| [D-005](../architecture-scolapay-2.md#décision-005--migrations-séparées-du-démarrage) | Migrations séparées du démarrage | Acceptée | En place |
| [0006](0006-backend-java-spring-boot.md) | Nouveau backend ScolaPay 2.0 en Java/Spring Boot, code NestJS conservé comme référence | Acceptée | Fondation en place dans `core/`, facturation à venir |
| [0007](0007-portail-familles-avant-apk.md) | Portail web des familles avant une application Android | Acceptée | Non démarrée |
| [0008](0008-go-services-paiement.md) | Go pour de futurs services financiers spécialisés | Orientation validée, introduction non prévue immédiatement | Non démarrée |
| [0009](0009-fondation-technique-core.md) | Fondation technique du backend Java | Acceptée | En place dans `core/` |
| [0010](0010-identite-et-acces.md) | Modèle d'identité et d'accès | Acceptée | En place dans `core/`, sans remise des invitations |
| [0011](0011-transition-depuis-nestjs.md) | Transition depuis NestJS | Acceptée | Schéma et contrat séparés en place, portage des clients à venir |
| [0012](0012-convention-monetaire.md) | Montants entiers dans l'unité minimale ISO de chaque devise | Acceptée | S'imposera à la Mission 003 : aucun montant dans `core/` à ce jour |
| [0013](0013-majorite-et-autorisations-familiales.md) | Majorité légale et autorisations familiales | Acceptée, modalités juridiques à confirmer | En place dans `core/` |
| [0014](0014-android-hors-feuille-de-route.md) | Android hors de la feuille de route active | Acceptée | Contrôles CI séparés, code conservé |
| [0015](0015-modele-de-facturation.md) | Modèle du moteur de facturation | Acceptée, règles juridiques à valider | À venir, lots de la Mission 003 |
| [0016](0016-outbox-durable.md) | Outbox durable | Acceptée, registre Spring Modulith prouvé par les tests | En place, sans listener de production |
| [0017](0017-controles-de-mise-en-service.md) | Contrôles de mise en service | Acceptée | En place, portail fermé |
| [0018](0018-catalogue-academique.md) | Catalogue académique | Acceptée | En place |
| [0019](0019-nature-des-documents-et-numerotation.md) | Nature des documents et numérotation, étude de la FEN | Principes acceptés, traitement fiscal à valider | Référence interne seulement |
| [0020](0020-catalogue-de-frais-et-periodes.md) | Catalogue de frais, périodes de facturation et identité des charges | Acceptée | En place |
| [0021](0021-obligations-et-journal.md) | Obligations, échéanciers et journal | Acceptée | En place, sans aucun encaissement |
| [0022](0022-ajustements-et-reexamen.md) | Remises, corrections, annulations et réexamen | Acceptée | En place, sans aucun encaissement |
| [0025](0025-protection-authentification-publique.md) | Protection des API publiques d'authentification | Acceptée, développement validé | Intégration différée après #26 puis #27 ; contention et collisions à mesurer avant montée en charge |

## Statuts

- **Proposée** : en discussion, ne pas s'en servir comme hypothèse.
- **Orientation validée** : direction retenue par le propriétaire, exécution à cadrer.
- **Acceptée** : décision ferme et applicable.
- **Remplacée** : conserver le fichier et indiquer ce qui la remplace.

Le statut décrit la décision. La colonne « Implémentation » décrit le code. Une décision acceptée n'implique jamais une fonctionnalité livrée.

## Ajouter une décision

Copier le modèle ci-dessous dans `NNNN-titre-court.md`, ajouter la ligne à l'index et mettre à jour [`PROJECT_CONTEXT.md`](../PROJECT_CONTEXT.md), dans la PR qui porte le changement. Ne pas réécrire une décision passée : en ajouter une nouvelle qui la remplace.

```markdown
# NNNN : titre

- **Statut** : Proposée
- **Date** : AAAA-MM-JJ
- **Décideur** :
- **Remplace** : aucune

## Contexte
Le problème et les contraintes, vérifiables dans le dépôt quand c'est possible.

## Décision
Ce qui est retenu, en une ou deux phrases.

## Conséquences
Ce que cela rend plus facile, plus difficile, ou interdit.

## Questions ouvertes
Ce qui n'est pas tranché. Ne pas combler ces points par supposition.
```
