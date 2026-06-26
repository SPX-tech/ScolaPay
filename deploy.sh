#!/usr/bin/env bash
# ============================================================
# EduCash — Déploiement production (stack docker-compose)
# Usage : ./deploy.sh
# Pré-requis : .env présent à la racine, Docker + Docker Compose installés.
# ============================================================
set -euo pipefail

cd "$(dirname "$0")"          # se placer à la racine du projet
BRANCH="main"

# Compose v2 (docker compose) avec repli sur v1 (docker-compose)
if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
else
  COMPOSE="docker-compose"
fi

log() { printf '\n\033[1;34m=== %s ===\033[0m\n' "$1"; }

# 1) Garde-fous --------------------------------------------------------------
[ -f .env ] || { echo "❌ Fichier .env manquant à la racine."; exit 1; }

# 2) Récupérer la dernière version de main -----------------------------------
log "Git pull ($BRANCH)"
git fetch --all --prune
git checkout "$BRANCH"
git pull --ff-only origin "$BRANCH"

# 3) Builder les images Docker ----------------------------------------------
log "Build des images Docker"
$COMPOSE build

# 4) Démarrer PostgreSQL et attendre qu'il soit prêt -------------------------
log "Démarrage de PostgreSQL"
$COMPOSE up -d postgres
log "Attente de la disponibilité de PostgreSQL"
until $COMPOSE exec -T postgres pg_isready -U "${POSTGRES_USER:-postgres}" >/dev/null 2>&1; do
  printf '.'; sleep 2
done
echo " prêt."

# 5) Migrations Prisma (production, sans perte de données) -------------------
# `migrate deploy` applique uniquement les migrations en attente : pas de
# reset, pas de prompt, pas de suppression de données.
log "Migrations Prisma (migrate deploy)"
$COMPOSE run --rm backend npx prisma migrate deploy

# 6) Redémarrer proprement les services applicatifs --------------------------
# `up -d` recrée uniquement les conteneurs dont l'image/config a changé et
# conserve le volume `postgres_data` (aucune perte de données).
log "Redémarrage des services (backend, web, nginx)"
$COMPOSE up -d --remove-orphans

# 7) Nettoyage des images obsolètes (optionnel) ------------------------------
log "Nettoyage des images Docker inutilisées"
docker image prune -f >/dev/null || true

# 8) Vérification de santé ---------------------------------------------------
log "État des services"
$COMPOSE ps

echo
echo "✅ Déploiement terminé."
