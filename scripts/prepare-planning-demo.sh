#!/usr/bin/env bash
# Prépare des fichiers d'import de planning DÉMONSTRABLES à partir des
# modèles versionnés `docs/demo-data/planning-demo.csv` et
# `docs/demo-data/planning-conflicts-demo.csv`, qui portent le marqueur
# `__TEACHER_PUBLIC_ID__` (aucun identifiant réel dans Git).
#
# Le script substitue ce marqueur par le `publicId` d'un compte formateur
# fictif ACTIF et écrit les copies dans un répertoire de sortie NON
# VERSIONNÉ (par défaut `build/demo-data/`). Les modèles suivis par Git
# ne sont jamais modifiés. Aucun secret n'est écrit.
#
# Résolution du formateur, par ordre de priorité :
#   1. 1er argument positionnel (un UUID) ;
#   2. variable d'environnement TEACHER_PUBLIC_ID (un UUID) ;
#   3. appel API : login ADMIN de démonstration puis recherche du compte
#      `formateur@example.test` (même compte que scripts/seed-demo.sh).
#
# Usage :
#   ./scripts/prepare-planning-demo.sh <teacher-public-id>
#   TEACHER_PUBLIC_ID=<uuid> ./scripts/prepare-planning-demo.sh
#   API_BASE=http://localhost:8080 ESIC_DEMO_PASSWORD=... ./scripts/prepare-planning-demo.sh
#
# Sortie personnalisable : OUT_DIR=/tmp/x ./scripts/prepare-planning-demo.sh <uuid>
#
# Point d'injection de test : la variable CURL permet de substituer un
# faux `curl` déterministe (voir scripts/test/test-prepare-planning-demo.sh).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE_DIR="$REPO_ROOT/docs/demo-data"
OUT_DIR="${OUT_DIR:-$REPO_ROOT/build/demo-data}"
MARKER='__TEACHER_PUBLIC_ID__'
TEMPLATES=(planning-demo.csv planning-conflicts-demo.csv)

API_BASE="${API_BASE:-http://localhost:8080}"
API="${API_BASE%/}/api/v1"
ADMIN_EMAIL="admin@example.test"
TEACHER_EMAIL="formateur@example.test"
CURL="${CURL:-curl}"

UUID_RE='^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'

die() { printf 'prepare-planning-demo: %s\n' "$*" >&2; exit 1; }

# Résout le `publicId` du formateur de démonstration via l'API et
# distingue explicitement chaque cause d'échec. Sans cela, toute erreur
# (API éteinte, mauvais mot de passe, compte absent, compte inactif…)
# se terminait par le même « introuvable », et l'opérateur découvrait le
# problème seulement à l'import, sous la forme opaque
# `PLAN_TEACHER_NOT_ELIGIBLE`.
#
# L'éligibilité contrôlée ici est CELLE DU BACK-END
# (`PlanningReferenceResolver#resolveTeacher` → compte `ACTIVE` portant un
# rôle `TEACHER` actif) : on échoue à la préparation plutôt qu'à l'import.
#
# Aucun jeton n'est journalisé : `$token` ne sort jamais du script.
resolve_from_api() {
  command -v "$CURL" >/dev/null 2>&1 || die "curl requis pour la resolution API - sinon fournir l'UUID en argument."
  command -v jq >/dev/null 2>&1 || die "jq requis pour la resolution API - sinon fournir l'UUID en argument."
  [ -n "${ESIC_DEMO_PASSWORD:-}" ] || die "ESIC_DEMO_PASSWORD requis pour la resolution API - sinon fournir l'UUID en argument."

  local body token users count status roles id
  # --- 1. API jointe ? -----------------------------------------------
  if ! body="$("$CURL" -sS -X POST "$API/auth/login" -H 'Content-Type: application/json' \
        -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ESIC_DEMO_PASSWORD\"}" 2>/dev/null)"; then
    die "API injoignable sur $API_BASE — démarrer le back-end, ou fournir l'UUID en argument."
  fi

  # --- 2. authentification ADMIN -------------------------------------
  token="$(printf '%s' "$body" | jq -r '.accessToken // empty' 2>/dev/null || true)"
  [ -n "$token" ] || die "authentification ADMIN ($ADMIN_EMAIL) refusée — vérifier ESIC_DEMO_PASSWORD et le profil 'demo'."

  # --- 3. recherche : `q` est le paramètre de UserAccountController ---
  if ! users="$("$CURL" -sS "$API/users?q=$TEACHER_EMAIL&size=50" \
        -H "Authorization: Bearer $token" 2>/dev/null)"; then
    die "recherche du formateur impossible (appel /users en échec)."
  fi
  printf '%s' "$users" | jq -e 'has("content")' >/dev/null 2>&1 \
    || die "réponse inattendue de /users (JSON invalide ou non paginé) — vérifier la version de l'API."

  # --- 4. correspondance exacte sur l'e-mail -------------------------
  count="$(printf '%s' "$users" | jq --arg e "$TEACHER_EMAIL" '[.content[]? | select(.email==$e)] | length')"
  [ "$count" != "0" ] \
    || die "compte formateur $TEACHER_EMAIL introuvable — exécuter d'abord scripts/seed-demo.sh."
  [ "$count" = "1" ] \
    || die "$count comptes portent l'e-mail $TEACHER_EMAIL — base de démonstration incohérente, assainir avant la démonstration."

  # --- 5. éligibilité back-end : ACTIVE + rôle TEACHER ---------------
  status="$(printf '%s' "$users" | jq -r --arg e "$TEACHER_EMAIL" '[.content[]? | select(.email==$e) | .status // empty][0] // ""')"
  [ -z "$status" ] || [ "$status" = "ACTIVE" ] \
    || die "le compte $TEACHER_EMAIL est '$status' et non ACTIVE — l'import échouerait en PLAN_TEACHER_NOT_ELIGIBLE."
  roles="$(printf '%s' "$users" | jq -r --arg e "$TEACHER_EMAIL" '[.content[]? | select(.email==$e) | .roles // empty][0] // null')"
  if [ "$roles" != "null" ]; then
    printf '%s' "$roles" | jq -e 'index("TEACHER")' >/dev/null 2>&1 \
      || die "le compte $TEACHER_EMAIL ne porte pas le rôle TEACHER actif — l'import échouerait en PLAN_TEACHER_NOT_ELIGIBLE."
  fi

  # --- 6. identifiant public exploitable -----------------------------
  id="$(printf '%s' "$users" | jq -r --arg e "$TEACHER_EMAIL" '[.content[]? | select(.email==$e) | .publicId][0] // empty')"
  [ -n "$id" ] || die "le compte $TEACHER_EMAIL n'expose pas de publicId — réponse API inattendue."
  printf '%s' "$id"
}

TEACHER_ID="${1:-${TEACHER_PUBLIC_ID:-}}"
if [ -z "$TEACHER_ID" ]; then
  echo "Aucun identifiant fourni — résolution via l'API ($TEACHER_EMAIL)…" >&2
  TEACHER_ID="$(resolve_from_api)"
fi
[ -n "$TEACHER_ID" ] || die "identifiant de formateur introuvable."
[[ "$TEACHER_ID" =~ $UUID_RE ]] || die "« $TEACHER_ID » n'est pas un UUID valide."

mkdir -p "$OUT_DIR"
generated=()
for name in "${TEMPLATES[@]}"; do
  src="$TEMPLATE_DIR/$name"
  [ -f "$src" ] || die "modèle absent : $src"
  grep -q "$MARKER" "$src" || die "le modèle $name ne contient pas $MARKER (déjà substitué ?)."
  dst="$OUT_DIR/$name"
  # Substitution sans sed -i : on n'écrit que la copie, jamais la source.
  awk -v m="$MARKER" -v v="$TEACHER_ID" '{ gsub(m, v); print }' "$src" > "$dst"
  grep -q "$MARKER" "$dst" && die "substitution incomplète dans $dst"
  generated+=("$dst")
done

printf 'Formateur : %s\n' "$TEACHER_ID"
printf 'Fichiers générés (non versionnés) :\n'
for f in "${generated[@]}"; do printf '  %s\n' "$f"; done
