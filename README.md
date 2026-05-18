# EduCash

EduCash est une application de gestion des paiements scolaires cash.

Le projet est organise autour de trois parties:

- `backend/`: API REST NestJS, Prisma et PostgreSQL.
- `web/`: dashboard admin Next.js.
- `android/`: application mobile Android native en Kotlin et Jetpack Compose.

La direction mobile du projet est Android-first. L'ancienne piste Flutter a ete retiree pour eviter de maintenir deux apps mobiles en parallele.

## Stack

### Backend

- NestJS
- Prisma
- PostgreSQL
- Jest

### Web

- Next.js
- React
- React Query
- Tailwind CSS

### Android

- Kotlin
- Jetpack Compose
- Hilt
- Retrofit / OkHttp
- Encrypted SharedPreferences pour le stockage du token

## Demarrage rapide

Copier d'abord les variables d'environnement:

```bash
cp .env.example .env
```

Renseigner au minimum:

```env
DATABASE_URL="postgresql://postgres:postgres@localhost:5432/edu_cash?schema=public"
ACCESS_TOKEN_SECRET="change-me-in-production"
NEXT_PUBLIC_API_URL="http://localhost:8080/api/v1"
```

Lancer toute la stack web/API avec Docker:

```bash
docker compose up --build
```

Par defaut:

- Dashboard web: `http://localhost:8080`
- API backend: `http://localhost:8080/api/v1`
- PostgreSQL: `localhost:5432`

## Backend local

```bash
cd backend
npm install
npm run prisma:generate
npm run prisma:migrate:dev
npm run prisma:seed
npm run start:dev
```

Tests et build:

```bash
npm test
npm run build
```

## Web local

```bash
cd web
npm install
npm run dev
```

Build:

```bash
npm run build
```

## Android

Ouvrir le dossier `android/` dans Android Studio.

Avant de lancer l'app sur emulateur, verifier l'URL API dans:

```text
android/app/src/main/java/com/educash/mobile/util/Constants.kt
```

Pour un emulateur Android, l'API locale de la machine hote se rejoint en general avec:

```text
http://10.0.2.2:8080/api/v1
```

## Notes de maintenance

- Ne pas versionner les dossiers de build Android (`android/app/build`, `android/.gradle`).
- Ne pas versionner les artefacts de build (`backend-deploy.tar.gz`, `web/tsconfig.tsbuildinfo`).
- Les montants financiers sont stockes en cents avec des entiers, pas en nombres flottants.
- Les routes sensibles doivent rester protegees par auth, tenant context et roles metier.
