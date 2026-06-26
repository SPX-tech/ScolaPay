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

## Guide de Déploiement (MVP Cash)

Déploiement de la stack web/API en production via `docker compose`, piloté par le
script `deploy.sh` à la racine.

### 1. Prérequis serveur

- Un serveur Linux (Ubuntu/Debian recommandé).
- **Docker Engine** + le plugin **Docker Compose v2** (`docker compose version`).
- **Git** (le script fait un `git pull` de `main`).
- **Bash** (pour exécuter `deploy.sh`).
- Le port HTTP exposé par Nginx ouvert sur le pare-feu (`8080` par défaut, configurable via `NGINX_PORT`).

### 2. Fichier `.env` de production

Créer un fichier `.env` à la racine du projet. Structure minimale pour le MVP cash :

```env
# --- PostgreSQL ---
POSTGRES_USER=postgres
POSTGRES_PASSWORD=<mot-de-passe-fort>
POSTGRES_DB=edu_cash

# --- Backend API ---
# L'hôte est "postgres" (nom du service docker-compose), pas localhost.
# Le mot de passe doit être identique à POSTGRES_PASSWORD ci-dessus.
DATABASE_URL=postgresql://postgres:<mot-de-passe-fort>@postgres:5432/edu_cash?schema=public
ACCESS_TOKEN_SECRET=<secret-long-et-aléatoire>
ACCESS_TOKEN_EXPIRY_SECONDS=28800

# --- Web Dashboard ---
# Domaine ou IP publique du serveur, suivi du préfixe API.
NEXT_PUBLIC_API_URL=http://<domaine-ou-ip>:8080/api/v1

# --- Reverse proxy ---
NGINX_PORT=8080
```

> **Sécurité** : générer `ACCESS_TOKEN_SECRET` aléatoirement (ex. `openssl rand -hex 32`)
> et utiliser un mot de passe PostgreSQL robuste. Ne jamais versionner le `.env`.

**SMS (optionnel)** : par défaut les notifications SMS tournent en mode *mock* (logs).
Pour activer l'envoi réel via Africa's Talking, renseigner `AT_API_KEY`, `AT_USERNAME`
(et `AT_SENDER_ID`) dans `.env` **et** les transmettre au service `backend` dans
`docker-compose.yml`. Le polling de l'outbox (cron, 1 min) est actif par défaut ;
le désactiver avec `OUTBOX_POLLING_ENABLED=false`.

### 3. Premier déploiement

```bash
# Cloner le dépôt sur le serveur
git clone https://github.com/willyldx/edu-cash.git
cd edu-cash

# Créer et renseigner le fichier .env (voir section 2)
cp .env.example .env
nano .env

# Rendre le script exécutable (si nécessaire) et lancer
chmod +x deploy.sh
./deploy.sh
```

Le script `deploy.sh` enchaîne : `git pull origin main` → build des images Docker →
attente de PostgreSQL → migrations Prisma (`prisma migrate deploy`) → redémarrage des
services (`backend`, `web`, `nginx`).

Une fois terminé :

- Dashboard web : `http://<domaine-ou-ip>:8080`
- API : `http://<domaine-ou-ip>:8080/api/v1`

### 4. Déploiements suivants

Relancer simplement le script depuis la racine du projet :

```bash
./deploy.sh
```

Les migrations sont appliquées automatiquement et le volume PostgreSQL (`postgres_data`)
est conservé : **aucune perte de données**.
