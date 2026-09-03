#!/usr/bin/env bash
# Remise à zéro complète et traçable d'une base ESIC Connect.
#
# Traite le finding CRITIQUE F-ENV-1 (audit-report.md §3) : la base
# `esic_connect` a été polluée par ~27 000 comptes issus des fixtures de la
# suite back-end. Aucune démonstration n'est crédible dessus, et aucun
# nettoyage ligne à ligne n'est fiable (les fixtures touchent des dizaines
# de tables liées). La seule remise en état sûre est la recréation.
#
# Séquence, dans cet ordre, sans aucune étape manuelle :
#   1. sauvegarde compressée de la base existante (sauf --no-backup) ;
#   2. DROP puis CREATE de la base (utf8mb4 / utf8mb4_0900_ai_ci) ;
#   3. droits rendus à MYSQL_USER ;
#   4. rejeu des migrations Flyway V1 → V16 en démarrant le back-end ;
#   5. contrôle : version de schéma atteinte + volumes finaux.
#
# DESTRUCTIF. Demande confirmation, sauf avec --yes.
#
#   ./scripts/db-reset.sh                          # base de .env, profil demo
#   ./scripts/db-reset.sh esic_connect --yes
#   ./scripts/db-reset.sh esic_connect_demo --profile demo --keep-running
#   ./scripts/db-reset.sh --no-migrate             # recrée sans démarrer le back-end
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

[ -f .env ] || { echo "Fichier .env absent. Copiez .env.example puis renseignez-le." >&2; exit 1; }
set -a; . ./.env; set +a

DB=""
PROFILE="demo"
ASSUME_YES=0
DO_BACKUP=1
DO_MIGRATE=1
KEEP_RUNNING=0

while [ $# -gt 0 ]; do
  case "$1" in
    --yes|-y)       ASSUME_YES=1 ;;
    --no-backup)    DO_BACKUP=0 ;;
    --no-migrate)   DO_MIGRATE=0 ;;
    --keep-running) KEEP_RUNNING=1 ;;
    --profile)      PROFILE="${2:?--profile attend une valeur}"; shift ;;
    -h|--help)      sed -n '2,25p' "$0"; exit 0 ;;
    -*)             echo "Option inconnue : $1" >&2; exit 1 ;;
    *)              DB="$1" ;;
  esac
  shift
done

DB="${DB:-${MYSQL_DATABASE:-esic_connect}}"
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD absent de .env}"
: "${MYSQL_USER:?MYSQL_USER absent de .env}"

# Garde-fou : la base de la suite de tests ne se remet pas à zéro ici (elle
# est recréée par la suite elle-même), et on refuse tout nom vide.
[ -n "$DB" ] || { echo "Nom de base vide." >&2; exit 1; }
if [ "$DB" = "${MYSQL_TEST_DATABASE:-esic_test}" ]; then
  echo "Refus : '${DB}' est la base de la SUITE DE TESTS. Elle est gérée par ./mvnw test." >&2
  exit 1
fi

command -v docker >/dev/null 2>&1 || { echo "docker est requis." >&2; exit 1; }
docker compose ps --status running --services 2>/dev/null | grep -qx mysql || {
  echo "Le service mysql n'est pas démarré. Lancez : docker compose up -d" >&2; exit 1; }

mysql_root() {
  docker compose exec -T -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql mysql -uroot "$@"
}
q() { mysql_root --silent --skip-column-names -e "$1"; }

exists="$(q "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='${DB}';")"
before_users=0
if [ "${exists:-0}" -eq 1 ]; then
  before_users="$(q "SELECT COUNT(*) FROM ${DB}.user_account;" 2>/dev/null || echo 0)"
fi

echo "Base ciblée        : ${DB}"
echo "État actuel        : $([ "${exists:-0}" -eq 1 ] && echo "présente, ${before_users} compte(s)" || echo "absente")"
echo "Profil de migration: ${PROFILE}"
echo "Sauvegarde         : $([ "$DO_BACKUP" -eq 1 ] && echo oui || echo non)"
echo

if [ "$ASSUME_YES" -ne 1 ]; then
  echo "Cette opération SUPPRIME définitivement le contenu de '${DB}'."
  printf "Tapez le nom de la base pour confirmer : "
  read -r answer
  [ "$answer" = "$DB" ] || { echo "Annulé."; exit 1; }
fi

# --- 1. Sauvegarde -------------------------------------------------------
if [ "$DO_BACKUP" -eq 1 ] && [ "${exists:-0}" -eq 1 ]; then
  mkdir -p build/db-backups
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  dump="build/db-backups/${DB}-${stamp}.sql.gz"
  echo "[1/5] Sauvegarde → ${dump}"
  docker compose exec -T -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
    mysqldump -uroot --single-transaction --routines --events --databases "$DB" \
    | gzip > "$dump"
  echo "      $(du -h "$dump" | cut -f1) écrits."
else
  echo "[1/5] Sauvegarde ignorée."
fi

# --- 2 et 3. Recréation + droits ----------------------------------------
echo "[2/5] DROP puis CREATE de '${DB}'"
q "DROP DATABASE IF EXISTS \`${DB}\`;"
q "CREATE DATABASE \`${DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
echo "[3/5] Droits rendus à '${MYSQL_USER}'"
q "GRANT ALL PRIVILEGES ON \`${DB}\`.* TO '${MYSQL_USER}'@'%'; FLUSH PRIVILEGES;"

if [ "$DO_MIGRATE" -ne 1 ]; then
  echo "[4/5] Migrations ignorées (--no-migrate)."
  echo "[5/5] Base '${DB}' vide et prête. Flyway s'appliquera au prochain démarrage du back-end."
  exit 0
fi

# --- 4. Migrations Flyway via le démarrage du back-end -------------------
echo "[4/5] Rejeu des migrations Flyway (démarrage du back-end, profil ${PROFILE})"
mkdir -p build/demo-data/justifications build/logs
export JUSTIFICATION_STORAGE_PATH="${JUSTIFICATION_STORAGE_PATH:-$ROOT/build/demo-data/justifications}"
export MYSQL_DATABASE="$DB"
export SPRING_PROFILES_ACTIVE="$PROFILE"
if [ "$PROFILE" = "demo" ]; then
  : "${ESIC_DEMO_PASSWORD:?Le profil demo exige ESIC_DEMO_PASSWORD (>= 12 caractères) dans .env}"
fi

log="build/logs/db-reset-$(date -u +%Y%m%dT%H%M%SZ).log"
( cd backend && ./mvnw --batch-mode spring-boot:run ) > "$log" 2>&1 &
backend_pid=$!

stop_backend() {
  if kill -0 "$backend_pid" 2>/dev/null; then
    kill "$backend_pid" 2>/dev/null || true
    wait "$backend_pid" 2>/dev/null || true
  fi
}
[ "$KEEP_RUNNING" -eq 1 ] || trap stop_backend EXIT INT TERM

ready=0
for _ in $(seq 1 120); do
  if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then ready=1; break; fi
  kill -0 "$backend_pid" 2>/dev/null || break
  sleep 2
done

if [ "$ready" -ne 1 ]; then
  echo "      ÉCHEC : le back-end n'a pas démarré. 40 dernières lignes de ${log} :" >&2
  tail -40 "$log" >&2
  exit 1
fi
echo "      Back-end démarré."

# --- 5. Contrôle ---------------------------------------------------------
echo "[5/5] Contrôle"
version="$(q "SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM ${DB}.flyway_schema_history WHERE success=1;")"
failed="$(q "SELECT COUNT(*) FROM ${DB}.flyway_schema_history WHERE success=0;")"
tables="$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB}';")"
users="$(q "SELECT COUNT(*) FROM ${DB}.user_account;")"

echo "      Version de schéma : V${version}   (migrations en échec : ${failed})"
echo "      Tables            : ${tables}"
echo "      Comptes           : ${before_users} → ${users}"

if [ "${failed:-1}" -ne 0 ]; then
  echo "      ÉCHEC : au moins une migration Flyway est en échec." >&2
  exit 1
fi

echo
echo "Base '${DB}' remise à zéro et migrée."
if [ "$KEEP_RUNNING" -eq 1 ]; then
  echo "Le back-end reste démarré (PID ${backend_pid}, journal ${log})."
  echo "Jeu de démonstration complémentaire : bash scripts/seed-demo.sh"
else
  echo "Le back-end lancé pour la migration va être arrêté."
  echo "Vérification indépendante : ./scripts/db-doctor.sh ${DB}"
fi
