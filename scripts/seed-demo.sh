#!/usr/bin/env bash
# Complète l'amorçage de démonstration : le back-end lancé avec le profil
# `demo` a déjà créé les 4 comptes fictifs (DemoDataInitializer). Ce
# script crée, via les API REST réelles et avec le compte ADMIN de
# démonstration, le référentiel académique minimal, deux profils
# apprenants, deux inscriptions et une séance PLANNED.
#
# Idempotent pour le référentiel à code fixe et les profils : un 409 de
# création est toléré UNIQUEMENT dans les fonctions `ensure_*`, qui
# retrouvent alors la ressource exacte par son code / numéro et échouent
# si elle reste introuvable. Les inscriptions et la séance sont créées
# après un GET de présence : aucune création n'est tentée si la ressource
# existe déjà (la séance de démonstration n'a pas de contrainte
# d'unicité, elle ne doit donc jamais être POSTée deux fois).
#
# Prérequis : bash, curl, jq. Back-end démarré (profil `demo`) et joignable
# sur $API_BASE. Variable ESIC_DEMO_PASSWORD identique à celle du back-end.
#
#   API_BASE=http://localhost:8080 ESIC_DEMO_PASSWORD=... ./scripts/seed-demo.sh
#
# Point d'injection de test : la variable CURL permet de substituer un
# faux `curl` déterministe (voir scripts/test/test-seed-demo.sh).
set -euo pipefail

API_BASE="${API_BASE:-http://localhost:8080}"
API="${API_BASE%/}/api/v1"
ADMIN_EMAIL="admin@example.test"
CURL="${CURL:-curl}"
: "${ESIC_DEMO_PASSWORD:?Définissez ESIC_DEMO_PASSWORD (même valeur que le back-end).}"

for tool in "$CURL" jq; do
  command -v "$tool" >/dev/null 2>&1 || { echo "Outil requis manquant : $tool" >&2; exit 1; }
done

# Fichier temporaire sécurisé pour le corps des réponses (jamais le
# jeton : l'en-tête Authorization n'est pas une réponse). Nettoyé quoi
# qu'il arrive.
BODY_FILE="$(mktemp "${TMPDIR:-/tmp}/esic-seed.XXXXXX")"
cleanup() { rm -f "$BODY_FILE"; }
trap cleanup EXIT INT TERM

say() { printf '  %s\n' "$*"; }

# --- Authentification -------------------------------------------------------
# Un seul appel. Le corps (qui contient le jeton) n'est jamais affiché :
# seul `.accessToken` est extrait par jq.
login_status="$("$CURL" -sS -o "$BODY_FILE" -w '%{http_code}' -X POST "$API/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ESIC_DEMO_PASSWORD\"}" || true)"
TOKEN="$(jq -r '.accessToken // empty' <"$BODY_FILE" 2>/dev/null || true)"
: >"$BODY_FILE"
if [ "$login_status" != 200 ] || [ -z "$TOKEN" ]; then
  echo "Échec de connexion ADMIN (HTTP ${login_status:-?}). Le back-end tourne-t-il avec le profil demo et le bon mot de passe ?" >&2
  exit 1
fi
AUTH=(-H "Authorization: Bearer $TOKEN")

# --- Helpers HTTP ---------------------------------------------------------
# http_post PATH JSON [--allow-conflict]
#   Effectue EXACTEMENT une requête POST. Le corps va dans $BODY_FILE, le
#   statut est capturé séparément. Rejet (exit 1) de tout statut >= 400,
#   SAUF 409 quand --allow-conflict est passé : dans ce cas la fonction
#   n'affiche rien et renvoie le code 9 (le seul appelant, ensure_*,
#   retrouve alors la ressource exacte). Le corps d'une erreur (DTO
#   ApiError : status/code/message/path…) ne contient aucun secret.
http_post() {
  local path="$1" body="$2" allow="${3:-}" status
  : >"$BODY_FILE"
  status="$("$CURL" -sS -o "$BODY_FILE" -w '%{http_code}' -X POST "$API$path" "${AUTH[@]}" \
    -H 'Content-Type: application/json' -d "$body")"
  if [ "$status" -ge 400 ]; then
    if [ "$status" = 409 ] && [ "$allow" = --allow-conflict ]; then
      return 9
    fi
    echo "POST $path -> HTTP $status" >&2
    sed 's/^/    /' "$BODY_FILE" >&2 || true
    exit 1
  fi
  cat "$BODY_FILE"
}

# http_get_q PATH KEY VALUE [KEY VALUE ...] -> corps
#   GET avec paramètres de requête correctement encodés (--data-urlencode).
http_get_q() {
  local path="$1"; shift
  local args=()
  while [ "$#" -ge 2 ]; do
    args+=(--data-urlencode "$1=$2")
    shift 2
  done
  "$CURL" -sS -G "$API$path" "${AUTH[@]}" "${args[@]}"
}

# ensure_by_code CREATE_PATH CODE JSON -> publicId
#   Crée la ressource ; sur 409, retrouve l'existante par son `code` via
#   le filtre `q` de la route de liste et échoue si le code ne correspond
#   à aucune ressource (le conflit ne portait pas sur cette ressource).
ensure_by_code() {
  local path="$1" code="$2" body="$3" out id rc
  out="$(http_post "$path" "$body" --allow-conflict)" && rc=0 || rc=$?
  if [ "$rc" -eq 0 ]; then
    id="$(printf '%s' "$out" | jq -r '.publicId // empty')"
  elif [ "$rc" -eq 9 ]; then
    id=""
  else
    exit "$rc"
  fi
  if [ -z "$id" ]; then
    id="$(http_get_q "$path" q "$code" | jq -r --arg c "$code" \
      '[.content[]? | select(.code==$c) | .publicId][0] // empty')"
  fi
  [ -n "$id" ] || { echo "Conflit sur $code mais ressource introuvable via la recherche : abandon." >&2; exit 1; }
  printf '%s' "$id"
}

user_id() {
  http_get_q /users q "$1" | jq -r --arg e "$1" \
    '[.content[]? | select(.email==$e) | .publicId][0] // empty'
}

echo "Amorçage de démonstration via $API"

TEACHER_ID="$(user_id 'formateur@example.test')"
STUDENT1_ID="$(user_id 'apprenant1@example.test')"
STUDENT2_ID="$(user_id 'apprenant2@example.test')"
for v in TEACHER_ID STUDENT1_ID STUDENT2_ID; do
  [ -n "${!v}" ] || { echo "Compte $v introuvable. Lancez d'abord le back-end en profil demo." >&2; exit 1; }
done

SITE_ID="$(ensure_by_code /sites 'SITE-DEMO' \
  '{"code":"SITE-DEMO","name":"Campus démonstration","timeZoneId":"Europe/Paris"}')"
say "site        $SITE_ID"

PROGRAM_ID="$(ensure_by_code /programs 'PRG-DEMO' \
  '{"code":"PRG-DEMO","name":"BTS SIO (démo)","programType":"BTS"}')"
say "formation   $PROGRAM_ID"

LEVEL_ID="$(ensure_by_code "/programs/$PROGRAM_ID/levels" 'N1-DEMO' \
  '{"code":"N1-DEMO","name":"BTS 1","sequenceNumber":1}')"
say "niveau      $LEVEL_ID"

YEAR_ID="$(ensure_by_code /academic-years 'AY-DEMO' \
  '{"code":"AY-DEMO","name":"2026-2027 (démo)","startDate":"2026-09-01","endDate":"2027-08-31"}')"
say "année       $YEAR_ID"

PROMO_ID="$(ensure_by_code /promotions 'P-DEMO' \
  "{\"programPublicId\":\"$PROGRAM_ID\",\"academicYearPublicId\":\"$YEAR_ID\",\"code\":\"P-DEMO\",\"name\":\"Promotion démo\"}")"
say "promotion   $PROMO_ID"

CLASS_ID="$(ensure_by_code /class-groups 'C-DEMO' \
  "{\"promotionPublicId\":\"$PROMO_ID\",\"programLevelPublicId\":\"$LEVEL_ID\",\"sitePublicId\":\"$SITE_ID\",\"code\":\"C-DEMO\",\"name\":\"Classe démo\"}")"
say "classe      $CLASS_ID"

# Profils apprenants (numéro étudiant = code fixe).
ensure_profile() {
  local user="$1" number="$2" out id rc
  out="$(http_post /student-profiles "{\"userPublicId\":\"$user\",\"studentNumber\":\"$number\"}" --allow-conflict)" && rc=0 || rc=$?
  if [ "$rc" -eq 0 ]; then
    id="$(printf '%s' "$out" | jq -r '.publicId // empty')"
  elif [ "$rc" -eq 9 ]; then
    id=""
  else
    exit "$rc"
  fi
  if [ -z "$id" ]; then
    id="$(http_get_q /student-profiles q "$number" | jq -r --arg n "$number" \
      '[.content[]? | select(.studentNumber==$n) | .publicId][0] // empty')"
  fi
  [ -n "$id" ] || { echo "Conflit sur le profil $number mais profil introuvable : abandon." >&2; exit 1; }
  printf '%s' "$id"
}
PROFILE1_ID="$(ensure_profile "$STUDENT1_ID" 'ESIC-DEMO-001')"
PROFILE2_ID="$(ensure_profile "$STUDENT2_ID" 'ESIC-DEMO-002')"
say "profils     $PROFILE1_ID / $PROFILE2_ID"

# Inscriptions (une par apprenant si absente).
ensure_enrollment() {
  local profile="$1" existing
  existing="$(http_get_q /enrollments student "$profile" status ACTIVE \
    | jq -r '.content[0]?.publicId // empty')"
  if [ -z "$existing" ]; then
    http_post /enrollments "{\"studentProfilePublicId\":\"$profile\",\"classGroupPublicId\":\"$CLASS_ID\"}" >/dev/null
  fi
}
ensure_enrollment "$PROFILE1_ID"
ensure_enrollment "$PROFILE2_ID"
say "inscriptions ok (2 apprenants dans C-DEMO)"

# Séance PLANNED de démonstration. Créée UNIQUEMENT si aucune séance
# PLANNED de ce formateur n'existe déjà (pas de contrainte d'unicité
# côté serveur : ne jamais POSTer deux fois).
EXISTING_SESSION="$(http_get_q /sessions status PLANNED teacher "$TEACHER_ID" \
  | jq -r '[.content[]? | .publicId][0] // empty')"
if [ -z "$EXISTING_SESSION" ]; then
  SESSION_ID="$(http_post /sessions "{\"teacherPublicId\":\"$TEACHER_ID\",\"classPublicIds\":[\"$CLASS_ID\"],\"startsAt\":\"2026-09-10T06:00:00Z\",\"endsAt\":\"2026-09-10T10:00:00Z\",\"timeZoneId\":\"Europe/Paris\",\"reason\":\"Séance de démonstration\",\"title\":\"Atelier émargement (démo)\"}" | jq -r '.publicId')"
  say "séance      $SESSION_ID (PLANNED, créée)"
else
  say "séance      $EXISTING_SESSION (déjà présente, PLANNED)"
fi

echo "Amorçage terminé. Connectez-vous à l'interface avec les comptes de démonstration."
