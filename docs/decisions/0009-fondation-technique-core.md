# 0009 : fondation technique du backend Java

- **Statut** : Acceptée
- **Date** : 2026-11-06
- **Décideur** : propriétaire du projet, sur proposition de la Mission 002
- **Remplace** : aucune

## Contexte

[0006](0006-backend-java-spring-boot.md) fixe Java, Spring Boot et un monolithe modulaire. Il restait à choisir les versions et les outils. Le projet doit rester simple à reprendre, et la moindre erreur d'isolation ou d'argent y coûte cher.

## Décision

| Sujet | Choix | Motif |
| --- | --- | --- |
| Java | 25 | Version LTS courante |
| Framework | Spring Boot 4.1.2 | Branche stable la plus récente, relevée sur Maven Central |
| Build | Maven, wrapper figé par somme de contrôle | Déclaratif et sans artefact binaire dans le dépôt |
| Modularité | Spring Modulith 2.1.2, en vérification seulement | `ApplicationModules.verify()` fait échouer la CI à toute dépendance non déclarée |
| Accès aux données | `JdbcClient` et SQL explicite, sans ORM | Voir ci-dessous |
| Migrations | Flyway, migrations SQL versionnées | Le SQL est aussi la documentation du schéma |
| Base | PostgreSQL 18.6 | Nouvelle base sans volume à reprendre : la contrainte de [D-005](../architecture-scolapay-2.md#décision-005--migrations-séparées-du-démarrage) ne s'applique pas à elle |
| Sécurité | Spring Security en serveur de ressources JWT | Validation standard, jeton sans état ; voir [0010](0010-identite-et-acces.md) |
| Tests | JUnit et Testcontainers, vrai HTTP et vrai PostgreSQL | L'isolation et la concurrence ne se prouvent pas avec des doubles |

### Pourquoi SQL explicite plutôt que JPA ou Spring Data JDBC

- **L'isolation reste visible.** Chaque requête porte `establishment_id` sous les yeux du relecteur, et chaque méthode de dépôt l'exige.
- **Pas de comportement implicite.** Le chargement différé et l'écriture automatique à la validation sont des risques dans un système financier, et les graphes d'entités traversent facilement les frontières de modules.
- **Le verrouillage est direct.** `FOR UPDATE`, les index partiels et `ON CONFLICT` s'écrivent tels quels, ce dont le moteur de facturation aura besoin.
- **Le coût est connu** : davantage de code de mappage. Il pourra être revu si ce code devient un frein réel. jOOQ serait alors à évaluer, au prix d'une étape de génération.

### Frontières de modules

Dépendances autorisées, déclarées dans chaque `package-info.java` et vérifiées par `ModularityTest` :

| Module | Rôle | Peut dépendre de |
| --- | --- | --- |
| `shared` | Erreurs HTTP, pagination, colonnes | Rien |
| `audit` | Journal immuable | `shared` |
| `identity` | Comptes, connexion, jetons, invitations | `shared`, `audit` |
| `establishment` | Établissements, personnel, rôles, permissions | `shared`, `audit`, `identity` |
| `schooling` | Années, dossiers, inscriptions, liens familiaux | `shared`, `audit`, `identity`, `establishment` |
| `billing` | **Prévu, n'existe pas** | `schooling` et les précédents |

Le paquet racine d'un module est son API publique, `internal` lui est réservé. Une clé étrangère peut viser `accounts` ou `establishments` depuis un autre module : l'intégrité passe avant la pureté, tant que tout vit dans une même base. Un module ne lit ni n'écrit les tables d'un autre.

## Conséquences

- Le backend se construit, se teste et s'exécute sans NestJS (`core/`).
- Les événements `StudentEnrolled` et `EnrollmentEnded` sont publiés de façon synchrone, dans la transaction. **Il n'y a pas encore d'outbox durable** : elle est exigée par la Mission 003 avant les premiers événements asynchrones critiques, et le registre de publication de Spring Modulith est le candidat à évaluer.
- Les migrations partagent pour l'instant le pool de l'application et son `statement_timeout` de 15 secondes. Une migration longue exigera une configuration dédiée.

## Questions ouvertes

- Contrôle d'accès PostgreSQL par ligne (RLS) en défense supplémentaire, à évaluer avec le pool et les tâches asynchrones, comme le prévoyait [D-002](../architecture-scolapay-2.md#décision-002--isolation-explicite-par-établissement).
- Rôles PostgreSQL distincts pour les migrations et pour l'application, à définir avant toute mise en service.
