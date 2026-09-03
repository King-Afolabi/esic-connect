#!/usr/bin/env bash
# Vérification complète du dépôt en une seule commande.
#
# Enchaîne, dans l'ordre, tout ce qui doit être vert avant une livraison
# ou un déploiement, et s'arrête au premier échec en le nommant :
#   1. infrastructure Docker démarrée et saine ;
#   2. suite de tests back-end (MySQL + Redis réels, base MYSQL_TEST_DATABASE) ;
#   3. lint, tests unitaires, build et audit du front-end ;
#   4. contrôle de type de la suite e2e ;
#   5. non-régression des scripts de démonstration ;
#   6. diagnostic de la base applicative (F-ENV-1).
#
# La suite e2e navigateur n'est PAS lancée ici : elle exige la pile
# complète démarrée (back-end + ng serve). Voir docs/13-guide-deploiement.md
# §5 et .github/workflows/e2e.yml.
#
#   ./scripts/verify-all.sh          # tout
#   ./scripts/verify-all.sh --quick  # sans le back-end (le plus long)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

QUICK=0
[ "${1:-}" = "--quick" ] && QUICK=1

[ -f .env ] || { echo "Fichier .env absent. Copiez .env.example puis renseignez-le." >&2; exit 1; }
set -a; . ./.env; set +a

step=0
run() {
  step=$((step + 1))
  printf '\n=== [%d] %s ===\n' "$step" "$1"
  shift
  if ! "$@"; then
    printf '\nÉCHEC à l étape %d.\n' "$step" >&2
    exit 1
  fi
}

check_infra() {
  docker compose ps --status running --services | grep -qx mysql || {
    echo "mysql n'est pas démarré : docker compose up -d" >&2; return 1; }
  docker compose ps --status running --services | grep -qx redis || {
    echo "redis n'est pas démarré : docker compose up -d" >&2; return 1; }
  docker compose ps
}

backend_tests() { ( cd backend && ./mvnw --batch-mode clean test ); }

frontend_checks() {
  cd frontend
  npm ci
  npm audit --audit-level=high
  npm run lint
  npm test -- --watch=false
  npm run build
}

e2e_typecheck() {
  [ -d node_modules ] || npm ci
  ./frontend/node_modules/.bin/tsc -p tsconfig.json --noEmit
}

demo_script_tests() {
  bash scripts/test/test-seed-demo.sh
  bash scripts/test/test-prepare-planning-demo.sh
}

db_health() {
  # Sortie 2 = base polluée : c'est un échec de vérification, pas une erreur
  # d'exécution (finding F-ENV-1).
  ./scripts/db-doctor.sh "${MYSQL_DATABASE:-esic_connect}"
}

run "Infrastructure Docker" check_infra
[ "$QUICK" -eq 1 ] || run "Tests back-end" backend_tests
run "Front-end : audit, lint, tests, build" frontend_checks
run "Contrôle de type de la suite e2e" e2e_typecheck
run "Scripts de démonstration" demo_script_tests
run "Diagnostic de la base applicative" db_health

printf '\nTout est vert (%d étapes).\n' "$step"
