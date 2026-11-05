#!/usr/bin/env bash
# À exécuter uniquement sur un runner ou un hôte de test distant.
set -uo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
results=${1:-/tmp/scolapay-baseline}
mkdir -p "$results"
failures=0
run() {
  local name=$1
  shift
  "$@" >"$results/$name.log" 2>&1
  local status=$?
  printf '%s=%s\n' "$name" "$status" | tee -a "$results/status.txt"
  if ((status != 0)); then failures=$((failures + 1)); fi
}
: >"$results/status.txt"
db="scolapay-baseline-$$"
trap 'docker rm -f "$db" >/dev/null 2>&1' EXIT
docker run -d --name "$db" --memory=512m -e POSTGRES_PASSWORD=baseline -e POSTGRES_DB=scolapay_test -p 127.0.0.1::5432 postgres:16-alpine >"$results/database.log" 2>&1 || exit 1
for attempt in {1..30}; do
  docker exec "$db" pg_isready -U postgres >/dev/null 2>&1 && break
  sleep 1
done
port=$(docker port "$db" 5432/tcp | cut -d: -f2)
export DATABASE_URL="postgresql://postgres:baseline@127.0.0.1:$port/scolapay_test?schema=public"
export NODE_ENV=test OUTBOX_POLLING_ENABLED=false PAYMENT_PROVIDER=mock
export ACCESS_TOKEN_SECRET=scolapay-isolated-baseline-only
cd "$root/backend"
run backend-install npm ci
run prisma-generate npm run prisma:generate
run migrations npm run prisma:migrate:deploy
run backend-build npm run build
run backend-seed node dist/prisma/seed.js
run backend-unit npm test -- --runInBand
run backend-e2e npm run test:e2e -- --runInBand
run backend-lint npx --no-install eslint '{src,test}/**/*.ts'
run backend-audit npm audit --json
cd "$root/web"
run web-install npm ci
run web-types npx --no-install tsc --noEmit
run web-build npm run build
run web-lint npm run lint
run web-audit npm audit --json
if ((failures > 0)); then exit 1; fi
