# ScolaPay

ScolaPay est une application de gestion des paiements scolaires cash.

**Commencer par [`docs/PROJECT_CONTEXT.md`](docs/PROJECT_CONTEXT.md)** : vision, état réel du projet, priorités, risques et prochaines étapes. Les décisions majeures sont dans [`docs/decisions/`](docs/decisions/README.md).

Le projet est organise autour de trois parties:

- `backend/`: API REST NestJS, Prisma et PostgreSQL.
- `web/`: dashboard admin Next.js.
- `android/`: application mobile Android native en Kotlin et Jetpack Compose.

La direction mobile du projet est Android-first. L'ancienne piste Flutter a ete retiree pour eviter de maintenir deux apps mobiles en parallele.

## Stack

Le socle de la mission 001 utilise Node.js 24.22.1, NestJS 12.3.3, Prisma 7.10.1,
PostgreSQL 16.15, Next.js 16.4.5 et React 19.3.1. Les versions npm directes et les
lockfiles sont figés. Android utilise Kotlin 2.4.21, AGP 9.3.3, Gradle 9.6.0 et JDK 17.

Voir le [journal de validation](docs/mission-001-validation.md) et les
[décisions pour ScolaPay 2.0](docs/architecture-scolapay-2.md).

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
NEXT_PUBLIC_API_URL="/api/v1"
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

Les compilations et suites de tests se lancent sur les runners GitHub ou un hôte
de développement adapté. Le ThinkPad de 8 Go reste réservé aux éditions légères.
La CI utilise une base PostgreSQL jetable dont le nom se termine par `_test`.

```bash
cd backend
npm ci --strict-peer-deps
npm run prisma:generate
npm run prisma:migrate:dev
npm run prisma:seed
npm run start:dev
```

Tests et build:

```bash
npm test
npm run test:e2e -- --runInBand
npm run lint
npm run build
```

## Web local

```bash
cd web
npm ci --strict-peer-deps
npm run dev
```

Build:

```bash
npm run build
npm run typecheck
npm test
```

Tests navigateur Playwright, sur le rendu autonome et une API simulée, comme en CI :

```bash
cp -r .next/static .next/standalone/.next/static
cp -r public .next/standalone/public
npx playwright install chromium
npm run test:e2e
```

## Android

Ouvrir le dossier `android/` dans Android Studio.

Avant de lancer l'app sur emulateur, verifier l'URL API dans:

```text
android/app/src/main/java/com/scolapay/mobile/util/Constants.kt
```

Pour un emulateur Android, l'API locale de la machine hote se rejoint en general avec:

```text
http://10.0.2.2:3000/api/v1/
```

Le build debug cible cette URL d'émulateur. Le build release interdit HTTP et
reçoit une URL explicite, terminée par `/`, via `-PscolapayApiUrl=https://.../api/v1/`.
Sans ce paramètre, il cible uniquement `https://localhost/api/v1/`.

```bash
cd android
./gradlew --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

## Vérification continue

Les workflows GitHub Actions vérifient les trois applications et les conteneurs.
Ils ne contiennent aucun déploiement, secret de production ou accès financier réel.
Le workflow de maintenance des lockfiles produit des artefacts à examiner, sans
écrire automatiquement dans une branche.

Prisma 7 génère le client dans `backend/src/generated/prisma`, exclu de Git. Dans
Docker, le service `migrate` utilise une image avec le CLI et termine avant l'API.
L'image API n'exécute pas `npx prisma` au démarrage. Une montée majeure PostgreSQL
est une opération distincte ; ne pas changer le format d'un volume existant sans
répétition de la restauration ou de `pg_upgrade`.

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

# --- Paiements numériques ---
# Seule valeur admise en production. mock et kadryza y empêchent le démarrage.
PAYMENT_PROVIDER=disabled
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
git clone https://github.com/SPX-tech/ScolaPay.git
cd ScolaPay

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
