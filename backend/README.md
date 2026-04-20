# EDU-CASH Backend (NestJS + Prisma)

Backend API multi-tenant pour la numérisation et le suivi des paiements cash scolaires.

## Stack

- NestJS (API REST)
- PostgreSQL
- Prisma ORM

## Architecture fonctionnelle

- `Tenant` : établissement (université/école)
- `User` : admin, caissier, comptable, auditeur
- `Student` : étudiant rattaché à un tenant
- `Transaction` : ledger des opérations financières cash
- `OutboxEvent` : événements asynchrones pour notifications (SMS, etc.)

Toutes les entités métier critiques portent un `tenantId` pour garantir l'isolation stricte des données.

## Démarrage local

1. Copier `.env.example` vers `.env`.
2. Configurer `DATABASE_URL` vers PostgreSQL.
3. Générer Prisma Client.
4. Appliquer les migrations.
5. Lancer l'API.

Exemple de variables requises:

```env
DATABASE_URL="postgresql://postgres:postgres@localhost:5432/edu_cash?schema=public"
PORT=3000
ACCESS_TOKEN_SECRET="change-me-in-production"
ACCESS_TOKEN_EXPIRY_SECONDS=28800
```

## Endpoints principaux (v1)

Base path: `/api/v1`

- `GET /health` (public)
- `POST /auth/login` (public)
- `GET /tenants/me`
- `GET /users`
- `POST /students`
- `GET /students`
- `GET /students/:studentId`
- `POST /ledger/record-cash-payment`
- `GET /transactions`
- `GET /transactions/student/:studentId`
- `GET /transactions/:transactionId`
- `GET /outbox/pending`

## Workflow clé : RecordCashPayment

`POST /ledger/record-cash-payment`

Effets transactionnels ACID:

1. Vérifie tenant + étudiant actif.
2. Vérifie l'idempotence (`idempotencyKey`) si fournie.
3. Crée une `Transaction` (`CASH_PAYMENT`) avec reçu (`receiptNumber`, `receiptHash`).
4. Met à jour la dette de l'étudiant (`debtCents`) et le crédit éventuel (`creditCents`).
5. Enregistre un événement `cash.payment.recorded` dans `OutboxEvent`.

## Authentification

- `POST /auth/login` retourne un bearer token signé (HMAC-SHA256 custom).
- Les routes privées exigent `Authorization: Bearer <token>`.
- Le contexte tenant est extrait du token; `x-tenant-id` est accepté mais doit correspondre au token.

## Notes MVP

- Les montants financiers sont stockés en `BigInt` (`*Cents`) pour éviter les erreurs flottantes.
- `BigInt` est sérialisé en `string` dans les réponses JSON.
- Le service notifications est un placeholder prêt pour un provider SMS externe.
