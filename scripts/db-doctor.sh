#!/usr/bin/env bash
# Diagnostic LECTURE SEULE d'une base ESIC Connect.
#
# Répond à trois questions, sans jamais écrire :
#   1. la base existe-t-elle et à quelle version de schéma Flyway ?
#   2. quels sont les volumes réels des tables métier ?
#   3. la base est-elle POLLUÉE par des données de fixtures de test ?
#
# Motif (finding F-ENV-1, audit-report.md §3) : la base `esic_connect`
# contenait 27 105 lignes `user_account` portant les motifs de nommage des
# fixtures de la suite back-end (`att-*`, `alt-*`, `assign-*`, `acad-*`,
# `sec-*`, `auth-*`, `aud-*`, `applied.*`), signe qu'un `./mvnw test` a
# écrit dans la base applicative. Aucune démonstration n'est crédible dans
# cet état. Ce script rend ce contrôle reproductible.
#
# Sortie : 0 = base saine · 1 = erreur d'exécution · 2 = pollution détectée.
#
#   ./scripts/db-doctor.sh                    # base de .env (MYSQL_DATABASE)
#   ./scripts/db-doctor.sh esic_connect_demo  # une autre base
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

[ -f .env ] || { echo "Fichier .env absent. Copiez .env.example puis renseignez-le." >&2; exit 1; }
set -a; . ./.env; set +a

DB="${1:-${MYSQL_DATABASE:-esic_connect}}"
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD absent de .env}"

command -v docker >/dev/null 2>&1 || { echo "docker est requis." >&2; exit 1; }

# Toute requête passe par le conteneur MySQL du compose local ; le mot de
# passe transite par MYSQL_PWD (jamais en argument de ligne de commande,
# où il serait visible dans la liste des processus).
q() {
  docker compose exec -T -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
    mysql -uroot --silent --skip-column-names -e "$1" 2>/dev/null
}

docker compose ps --status running --services 2>/dev/null | grep -qx mysql || {
  echo "Le service mysql n'est pas démarré. Lancez : docker compose up -d" >&2; exit 1; }

exists="$(q "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='${DB}';")"
if [ "${exists:-0}" -eq 0 ]; then
  echo "Base '${DB}' : ABSENTE."
  exit 0
fi

echo "=== Base '${DB}' ==="
schema_version="$(q "SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM ${DB}.flyway_schema_history WHERE success=1;" || echo "?")"
tables="$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB}';")"
echo "Version de schéma Flyway : V${schema_version:-?}   |   Tables : ${tables}"
echo

echo "--- Volumes (tables non vides) ---"
q "SELECT CONCAT(LPAD(c, 9, ' '), '  ', t) FROM (
     SELECT table_name AS t, table_rows AS c FROM information_schema.tables
     WHERE table_schema='${DB}' AND table_rows > 0 ORDER BY table_rows DESC LIMIT 25) x;" \
  || echo "(indisponible)"
echo
echo "Note : information_schema.table_rows est une ESTIMATION InnoDB."
echo "Le décompte des comptes ci-dessous est, lui, exact."
echo

users="$(q "SELECT COUNT(*) FROM ${DB}.user_account;" || echo 0)"
fixtures="$(q "SELECT COUNT(*) FROM ${DB}.user_account WHERE
    email REGEXP '^(att|alt|assign|acad|sec|auth|aud|audforeign|plan|enr|imp)-' OR
    email LIKE 'applied.%' OR
    email LIKE '%@esic-connect.test';" || echo 0)"

echo "--- Détection de données de fixtures de test ---"
printf "Comptes au total          : %s\n" "$users"
printf "Comptes de motif fixture  : %s\n" "$fixtures"

if [ "${fixtures:-0}" -gt 0 ]; then
  echo
  echo "Exemples :"
  q "SELECT CONCAT('  ', email) FROM ${DB}.user_account WHERE
      email REGEXP '^(att|alt|assign|acad|sec|auth|aud|audforeign|plan|enr|imp)-' OR
      email LIKE 'applied.%' OR email LIKE '%@esic-connect.test' LIMIT 8;"
  echo
  echo "RÉSULTAT : POLLUÉE — ${fixtures} compte(s) de test dans '${DB}'."
  echo "Cette base ne doit pas servir à une démonstration en l'état."
  echo "Remise à zéro : ./scripts/db-reset.sh ${DB}"
  exit 2
fi

echo
echo "RÉSULTAT : SAINE — aucun motif de fixture détecté dans '${DB}'."
exit 0
