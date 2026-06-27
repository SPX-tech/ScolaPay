# 📋 ÉTAT DU PROJET — SCOLAPAY TCHAD (SaaS B2B2C)

> **Mission :** Système SaaS de gestion financière 100 % cash pour écoles et universités au Tchad
> **Architecture :** Multi-Tenant (isolation par `tenantId`) | Numérisation du Cash
> **Stack :** NestJS + PostgreSQL + Prisma (Backend) · Next.js 15 (Dashboard web) · Kotlin / Jetpack Compose (Android)
>
> _Dernière mise à jour : 2026-06-26 — ce document décrit l'état **réel** du code, pas une roadmap prévisionnelle._

---

## 🎯 Périmètre de la version actuelle

La version en cours est volontairement **100 % Cash**. L'intégration de paiements externes
(**Kadryza Pay**, connexions bancaires) est **explicitement hors périmètre** pour l'instant :
l'objectif est de rendre la version cash robuste et prête à l'emploi avant d'élargir.

---

## ✅ CE QUI EST EN PLACE

### 1. Base de données (Prisma / PostgreSQL)

Tous les modèles métier portent `tenantId` (isolation multi-tenant), avec index composites et
clés uniques par tenant. Les montants financiers sont en **cents (BigInt)**, jamais en float.

| Modèle | Détails |
|---|---|
| `Tenant` | École : `code` unique, `currency` (XAF), `timezone` (Africa/Ndjamena), `isActive` |
| `User` | Rôles `TENANT_ADMIN` / `CASHIER` / `ACCOUNTANT` / `AUDITOR` ; statuts ACTIVE/INVITED/SUSPENDED |
| `Student` | `debtCents` / `creditCents` (BigInt), rattachement classe, statut, `metadata` JSON |
| `SchoolClass` | Classes par `academicYear`, statut ACTIVE/ARCHIVED |
| `Transaction` | Types `CASH_PAYMENT` / `DEBT_ADJUSTMENT` / `REFUND` ; POSTED/VOIDED ; `receiptHash` SHA-256, `qrCodeData`, `idempotencyKey` unique, void avec raison |
| `OutboxEvent` | Outbox pattern (PENDING → PROCESSING → SENT/FAILED) pour notifications async |
| `AuditLog` | Traçabilité : actor, action, `before`/`after` JSON |

Migration la plus récente : `20260627000000_add_financial_foundations`
(modèles `VirtualAccount` et `WithdrawalRequest`, voir section 7).
Seed (`prisma/seed.ts`) : tenant `DEMO001`, 3 utilisateurs (`admin@demo.td`, `caissier@demo.td`,
`comptable@demo.td`), 6 classes, 12 étudiants, 1 compte virtuel (solde 0).

### 2. API Backend (NestJS) — préfixe `/api/v1`

Sécurité globale : **AuthGuard (JWT)** + **TenantGuard** + **RolesGuard**, `ValidationPipe` global,
`BigIntSerializerInterceptor`, `enableShutdownHooks`.

| Module | Endpoints / capacités |
|---|---|
| `auth` | `POST /auth/login` (JWT) |
| `tenants` | `GET /tenants/me` |
| `users` | list, getById, create, update, `suspend`, `activate` |
| `students` | CRUD complet, query paginée/filtrée, `POST /students/import` (import en masse) |
| `classes` | CRUD complet |
| `ledger` | `POST /ledger/record-cash-payment` (ACID, idempotent, calcul dette/crédit) ; `GET /ledger/receipts/:id` (reçu + QR code) |
| `transactions` | list/filtres, par étudiant, getById, `POST /:id/void` |
| `dashboard` | `GET /dashboard/summary` (KPIs) ; `reports/collections` ; `reports/students-debt` |
| `outbox` | `GET /pending` ; `POST /process` (déclenchement manuel) **+ polling automatique** |
| `notifications` | Envoi SMS via Africa's Talking, avec **fallback mock** automatique |
| `audit` | Service de journalisation transverse |
| `finance` | `GET /schools/balance` ; `POST /schools/withdraw` (rails locaux, voir section 7) |
| `health` | `GET /health` |

### 3. Worker Outbox — **automatisé** ✅

- `OutboxWorkerService.processPendingEvents()` : traite les events `PENDING`/`FAILED`,
  retry avec back-off exponentiel, `MAX_RETRIES = 3`, garde `isProcessing` anti-chevauchement.
- `OutboxSchedulerService` : déclenche le worker **toutes les minutes**
  (`@Cron(CronExpression.EVERY_MINUTE)`), activé via `ScheduleModule.forRoot()`.
- Désactivable par environnement : `OUTBOX_POLLING_ENABLED=false` (tests, CI, instance non-worker).
- L'endpoint `POST /outbox/process` reste disponible pour un déclenchement manuel / diagnostic.

### 4. Notifications SMS (Africa's Talking) — **validé** ✅

- **Mode mock par défaut** : sans `AT_API_KEY`, les SMS sont simulés dans les logs (statut `queued`).
- **Bascule réelle automatique** : dès que `AT_API_KEY` (+ `AT_USERNAME`, `AT_SENDER_ID` optionnel)
  sont définis, le SDK est instancié et envoie de vrais SMS au tuteur (statut `sent` / `failed`).
- Comportement verrouillé par tests unitaires (`notifications.service.spec.ts`).

### 5. Front-ends

- **Web** (`web/`, Next.js 15 App Router, React 19, TanStack Query, Tailwind + shadcn/Radix) :
  login + pages dashboard (accueil, students + détail, classes, transactions, ledger, users,
  reports, settings).
- **Android** (`android/`, Kotlin / Jetpack Compose, Hilt, Retrofit/OkHttp, EncryptedSharedPreferences) :
  architecture MVVM (data/api, repositories, di, ui + ViewModels) couvrant login, dashboard,
  students, transactions, ledger, reports, users, settings.

### 6. Infra & tests

- `docker-compose.yml` : Postgres 16 + backend + web + Nginx (reverse proxy port 8080).
  Les variables SMS (`AT_*`) et `OUTBOX_POLLING_ENABLED` sont transmises au service `backend`.
- Tests : specs unitaires (students, transactions, receipt, notifications, virtual-account) + E2E (`test/`).
  Suite verte : **62 tests** côté backend.

### 7. Rails financiers — **posés, non activés** 🧱

Fondations préparées pour les paiements numériques / retraits (Kadryza Pay, mobile money,
banque), **découplées du ledger cash** et **sans aucune connexion externe** pour l'instant.

- **Modèles Prisma** : `VirtualAccount` (1-1 `Tenant` : `balanceCents`, `pendingWithdrawalCents`,
  `currency`) et `WithdrawalRequest` (`method` MOBILE_MONEY/BANK_TRANSFER, `status`
  PENDING/PROCESSING/COMPLETED/FAILED, `reference` **unique global** pour la réconciliation).
  `TransactionType` enrichi de `MOBILE_PAYMENT` et `WITHDRAWAL`.
- **`VirtualAccountService`** : invariant `disponible = balanceCents - pendingWithdrawalCents ≥ 0`,
  garanti par des incréments atomiques (`credit` / `reserve` / `complete` / `release`).
  `creditFromPayment` est réservé aux `MOBILE_PAYMENT` (le cash physique n'alimente pas le float).
- **`WithdrawalsService`** : blocage des fonds + création de la demande PENDING dans une **seule
  transaction ACID**.
- **`SchoolsController`** : `GET /schools/balance` et `POST /schools/withdraw` (fonctionnels en
  local, aucun transfert réel). Couvert par 8 tests unitaires sur l'invariant de solde.

> ⚠️ Non encore branché : `creditFromPayment` sera appelé depuis le flux `MOBILE_PAYMENT`
> quand celui-ci sera implémenté ; aucun appel prestataire (Kadryza/banque) n'existe à ce stade.

---

## 🔭 RESTE À FAIRE (pour finaliser la version cash)

| Sujet | Statut | Note |
|---|---|---|
| Automatisation worker outbox | ✅ Fait | Cron 1 min + flag d'activation |
| Validation module SMS (mock ↔ réel) | ✅ Fait | Couvert par tests |
| Rails financiers (schéma + services + endpoints) | ✅ Fait | Locaux, non activés (voir section 7) |
| Activation paiements numériques (Kadryza, mobile money) | ⬜ À faire | Brancher `creditFromPayment` + intégration prestataire |
| Verrou SQL anti-concurrence (outbox + retraits) | ⬜ À faire | Voir dette technique ci-dessous (avant multi-instance) |
| CI/CD (GitHub Actions) | ⬜ À faire | Lint + build + tests sur PR |
| Couverture de tests | 🟡 Partielle | Étendre aux services non couverts (ledger, classes, users) |
| Durcissement prod | ⬜ À faire | HTTPS, secrets, backup Postgres, monitoring/logs |

---

## ⚠️ LIMITES CONNUES / DETTE TECHNIQUE

> **Thème commun : concurrence en multi-instances.** Les deux points ci-dessous reposent sur un
> motif « lire puis écrire » correct en **mono-instance** (déploiement docker-compose actuel) mais
> sujet aux *race conditions* si plusieurs réplicas backend tournent en parallèle. La parade est la
> même : un **verrou de ligne PostgreSQL** (`SELECT … FOR UPDATE`) dans la transaction. À traiter
> **avant tout scaling horizontal**.

### 1. Worker outbox
Le garde `isProcessing` est **en mémoire** : il empêche le chevauchement au sein d'**une seule
instance**. En cas de scaling horizontal, deux instances pourraient traiter le même événement, car
la séquence `findMany` → `update` n'est **pas atomique** entre process.

> **Parade** : sélectionner les events avec `SELECT … FOR UPDATE SKIP LOCKED` (ou un advisory lock
> Postgres) afin que chaque réplica prenne un lot distinct sans collision.

### 2. Demandes de retrait (double-retrait concurrent)
`VirtualAccountService.reserveForWithdrawal` **lit** le compte virtuel, calcule le disponible, puis
**incrémente** `pendingWithdrawalCents` — le tout dans une transaction ACID. En mono-instance c'est
correct. En multi-instances, deux retraits simultanés peuvent **lire le même disponible** avant que
l'un n'ait bloqué les fonds, et tous deux passer la vérification → **double-retrait** dépassant le
solde (l'invariant `disponible ≥ 0` serait violé).

> **Parade** : verrouiller la ligne du compte avant lecture, p. ex.
> `SELECT * FROM virtual_accounts WHERE "tenantId" = $1 FOR UPDATE` (via `$queryRaw` dans la
> transaction) pour sérialiser les réservations concurrentes sur un même tenant. À implémenter
> avant d'activer les retraits réels et/ou le multi-instance.

---

## 🏛️ STRUCTURE DES DOSSIERS

```
scolapay/
├── backend/                    # API NestJS + Prisma + PostgreSQL
│   ├── prisma/                 # schema.prisma, migrations, seed.ts
│   └── src/
│       ├── common/             # guards, decorators, interceptors, security, validation
│       ├── modules/
│       │   ├── auth/           # login JWT
│       │   ├── audit/          # journalisation
│       │   ├── dashboard/      # reports (collections, students-debt)
│       │   ├── finance/        # virtual account + retraits (rails locaux)
│       │   ├── health/
│       │   ├── ledger/         # record-cash-payment, reçus + QR
│       │   ├── notifications/  # SMS Africa's Talking (+ mock)
│       │   ├── outbox/         # worker + scheduler (cron 1 min)
│       │   ├── students/       # CRUD + classes + import
│       │   ├── tenants/
│       │   ├── transactions/   # historique + void
│       │   └── users/          # gestion utilisateurs
│       └── prisma/             # PrismaModule / PrismaService
│
├── web/                        # Dashboard Next.js 15 (App Router)
│   └── src/{app,components,lib,hooks}/
│
├── android/                    # App Android Kotlin / Jetpack Compose
│   └── app/src/main/java/com/scolapay/mobile/{data,di,ui,util}/
│
├── nginx/                      # reverse proxy
├── docker-compose.yml
└── implementation_plan.md      # ce document
```

---

## 🔒 PRINCIPES À RESPECTER

- **Multi-tenant** : toute table métier porte `tenantId` ; `TenantGuard` valide le contexte ;
  clés `@@unique([tenantId, …])` contre les collisions inter-écoles ; relations `Restrict`.
- **Cash** : montants en `*Cents` (BigInt), jamais de float. `recordCashPayment` est ACID et
  idempotent ; reçu infalsifiable via `receiptHash` SHA-256 ; event outbox créé dans la **même
  transaction** que le paiement.
- **Sécurité** : routes sensibles protégées par auth + tenant + rôles. Ne pas versionner les
  builds (`android/app/build`, `web/tsconfig.tsbuildinfo`, artefacts de déploiement).
