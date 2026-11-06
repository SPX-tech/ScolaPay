# 0011 : transition depuis NestJS

- **Statut** : Acceptée
- **Date** : 2026-11-06
- **Décideur** : propriétaire du projet, sur proposition de la Mission 002
- **Remplace** : aucune
- **Répond à** : questions ouvertes de [0006](0006-backend-java-spring-boot.md)

## Contexte

Aucune instance n'est en service depuis le 2026-11-06. L'ancienne instance ne contenait qu'un établissement de démonstration, dont les données sont archivées hors du dépôt. Cela permet de construire directement le nouveau backend, sans justifier de perdre les références existantes.

## Décision

| Sujet | Stratégie |
| --- | --- |
| Schéma PostgreSQL | Base et migrations **distinctes** (`core/`, Flyway). Le schéma Prisma n'est ni modifié ni partagé |
| Sources de vérité | Jamais deux écrivains sur une même donnée. Les deux backends ne partagent aucune table |
| Données historiques | Aucune reprise automatique : il n'existe que des données de démonstration. L'archive est conservée. Un outil d'import ne se justifierait que si des données réelles apparaissaient |
| Contrat HTTP | `/api/v2`, nouveau et sans compatibilité. `/api/v1` reste la référence documentaire du comportement NestJS |
| Clients | Le dashboard web et l'application Android restent sur `/api/v1` dans le dépôt. Le portail familles naîtra sur `/api/v2`, les écrans existants seront portés au fil des domaines |
| Code NestJS | Conservé dans le dépôt comme référence fonctionnelle et technique, avec ses tests. Plus aucun nouveau domaine n'y entre |

### Correspondance du modèle

| NestJS | ScolaPay 2.0 | Remarque |
| --- | --- | --- |
| `Tenant` | `establishments` | Ajoute le type et l'âge de la majorité |
| `User` | `accounts`, `account_identifiers`, `memberships`, `membership_roles` | Voir [0010](0010-identite-et-acces.md) |
| `TENANT_ADMIN`, `CASHIER`, `ACCOUNTANT`, `AUDITOR` | `ESTABLISHMENT_ADMIN`, `CASHIER`, `ACCOUNTANT`, `AUDITOR` | Ajoute `REGISTRAR` |
| `Student` | `student_records` | **Sans dette ni crédit** : les soldes appartiendront à la facturation |
| `SchoolClass` | `enrollments.level` (texte) | Un catalogue de classes ou de niveaux reste à définir |
| `AuditLog` | `audit_events` | Immuable par trigger, écrit dans la transaction |
| `Transaction`, `VirtualAccount`, `LedgerEntry`, `WithdrawalRequest`, `OutboxEvent` | **Pas encore repris** | Facturation et paiements |

### Ce qui est repris de NestJS, et ce qui ne l'est pas

Repris : isolation par établissement, journal d'audit transactionnel, montants jamais en flottant, écritures idempotentes, fermeture par défaut des prestataires.

Non repris : `tenantId` dans le jeton et en-tête `x-tenant-id`, `User` rattaché à un seul établissement, solde modifiable directement par `PATCH` sur l'élève, clés étrangères simples.

## Conséquences

- Les [invariants financiers](../PROJECT_CONTEXT.md#invariants-financiers) restent applicables et s'imposeront aux modules Java concernés. Les scénarios de `backend/test/financial-integrity.e2e-spec.ts` décrivent le comportement à reproduire.
- NestJS ne redevient une dépendance que si le propriétaire décide de le remettre en service, ce qui serait un nouveau déploiement.

## Questions ouvertes

- Condition et moment où le code NestJS pourra quitter le dépôt. D'ici là, il est conservé.
- Ordre de portage des fonctions existantes : caisse, reçus, trésorerie, notifications.
