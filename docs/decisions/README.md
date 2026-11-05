# Décisions d'architecture

Une décision mérite une entrée ici lorsqu'elle est coûteuse à inverser ou qu'elle oriente le travail de plusieurs missions : langage ou framework, modèle de données partagé, frontière entre services, prestataire financier, modèle de sécurité. Le reste vit dans le code et les PR.

## Index

| N° | Décision | Statut | Implémentation |
| --- | --- | --- | --- |
| [D-001](../architecture-scolapay-2.md#décision-001--conserver-le-monolithe-modulaire) | Monolithe modulaire | Acceptée, choix de NestJS pour les futurs domaines remplacé par 0006 | En place |
| [D-002](../architecture-scolapay-2.md#décision-002--isolation-explicite-par-établissement) | Isolation explicite par établissement | Acceptée | Partielle : contrôle applicatif, contraintes composites à venir |
| [D-003](../architecture-scolapay-2.md#décision-003--conserver-les-unités-financières-historiques) | Unités financières historiques | Acceptée | En place |
| [D-004](../architecture-scolapay-2.md#décision-004--prestataires-financiers-fermés-par-défaut) | Prestataires fermés par défaut | Acceptée | En place |
| [D-005](../architecture-scolapay-2.md#décision-005--migrations-séparées-du-démarrage) | Migrations séparées du démarrage | Acceptée | En place |
| [0006](0006-backend-java-spring-boot.md) | Backend cible en Java/Spring Boot | Orientation validée | Non démarrée |
| [0007](0007-portail-familles-avant-apk.md) | Portail web des familles avant une APK | Orientation validée | Non démarrée |
| [0008](0008-go-services-paiement.md) | Go pour des services de paiement spécialisés | Orientation validée, usage éventuel | Non démarrée |

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
