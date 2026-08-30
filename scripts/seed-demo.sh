#!/usr/bin/env bash
# Complète l'amorçage de démonstration : le back-end lancé avec le profil
# `demo` a déjà créé les 4 comptes fictifs (DemoDataInitializer). Ce
# script crée, via les API REST réelles et avec le compte ADMIN de
# démonstration, le référentiel académique minimal, deux profils
# apprenants, deux inscriptions et une séance PLANNED.
#
# Idempotent : ré-exécutable sans créer de doublon (codes fixes, 409
# toléré et ressource existante récupérée).
#
# Prérequis : bash, curl, jq. Back-end démarré (profil `demo`) et joignable
# sur $API_BASE. Variable ESIC_DEMO_PASSWORD identique à celle du back-end.
#
#   API_BASE=http://localhost:8080 ESIC_DEMO_PASSWORD=... ./scripts/seed-demo.sh
set -euo pipefail

API_BASE="${API_BASE:-http://localhost:8080}"
API="${API_BASE%/}/api/v1"
ADMIN_EMAIL="admin@example.test"
: "${ESIC_DEMO_PASSWORD:?Définissez ESIC_DEMO_PASSWORD (même valeur que le back-end).}"

for tool in curl jq; do
  command -v "$tool" >/dev/null 2>&1 || { echo "Outil requis manquant : $tool" >&2; exit 1; }
done

say() { printf '  %s\n' "$*"; }

# --- Authentification -------------------------------------------------------
TOKEN="$(curl -sS -X POST "$API/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ESIC_DEMO_PASSWORD\"}" | jq -r '.accessToken')"
if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "Échec de connexion ADMIN. Le back-end tourne-t-il avec le profil demo et le bon mot de passe ?" >&2
  exit 1
fi
AUTH=(-H "Authorization: Bearer $TOKEN")

# post PATH JSON -> corps de la réponse (un seul appel). Échoue sur un
# code >= 400 autre que 409 (conflit = ressource déjà présente).
post() {
  local path="$1" body="$2" raw code out
  raw="$(curl -sS -w $'\n%{http_code}' -X POST "$API$path" "${AUTH[@]}" \
    -H 'Content-Type: application/json' -d "$body")"
  code="${raw##*$'\n'}"
  out="${raw%$'\n'*}"
  if [ "$code" -ge 400 ] && [ "$code" != 409 ]; then
    echo "POST $path -> $code : $out" >&2
    exit 1
  fi
  printf '%s' "$out"
}
# get PATH -> corps
get() { curl -sS "$API$1" "${AUTH[@]}"; }

# ensure_by_code CREATE_PATH CODE JSON -> publicId
# Crée la ressource ; si le code est déjà pris (409), récupère l'existante
# via le filtre `q` de la même route de liste.
ensure_by_code() {
  local path="$1" code="$2" body="$3" out id
  out="$(post "$path" "$body")"
  id="$(printf '%s' "$out" | jq -r '.publicId // empty')"
  if [ -z "$id" ]; then
    id="$(get "${path}?q=${code}" | jq -r --arg c "$code" \
      '.content[]? | select(.code==$c) | .publicId' | head -n1)"
  fi
  [ -n "$id" ] || { echo "Impossible d'obtenir la ressource $code" >&2; exit 1; }
  printf '%s' "$id"
}

user_id() { get "/users?q=$1" | jq -r --arg e "$1" '.content[]? | select(.email==$e) | .publicId' | head -n1; }

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
  local user="$1" number="$2" out id
  out="$(post /student-profiles "{\"userPublicId\":\"$user\",\"studentNumber\":\"$number\"}")"
  id="$(printf '%s' "$out" | jq -r '.publicId // empty')"
  [ -n "$id" ] || id="$(get "/student-profiles?q=$number" | jq -r '.content[0]?.publicId // empty')"
  [ -n "$id" ] || { echo "Profil apprenant $number introuvable" >&2; exit 1; }
  printf '%s' "$id"
}
PROFILE1_ID="$(ensure_profile "$STUDENT1_ID" 'ESIC-DEMO-001')"
PROFILE2_ID="$(ensure_profile "$STUDENT2_ID" 'ESIC-DEMO-002')"
say "profils     $PROFILE1_ID / $PROFILE2_ID"

# Inscriptions (une par apprenant si absente).
ensure_enrollment() {
  local profile="$1" existing
  existing="$(get "/enrollments?student=$profile&status=ACTIVE" | jq -r '.content[0]?.publicId // empty')"
  if [ -z "$existing" ]; then
    post /enrollments "{\"studentProfilePublicId\":\"$profile\",\"classGroupPublicId\":\"$CLASS_ID\"}" >/dev/null
  fi
}
ensure_enrollment "$PROFILE1_ID"
ensure_enrollment "$PROFILE2_ID"
say "inscriptions ok (2 apprenants dans C-DEMO)"

# Séance PLANNED de démonstration (créée seulement si aucune séance PLANNED
# de ce formateur n'existe déjà).
EXISTING_SESSION="$(get "/sessions?status=PLANNED" | jq -r --arg t "$TEACHER_ID" \
  '.content[]? | select(.teacher.publicId==$t) | .publicId' | head -n1)"
if [ -z "$EXISTING_SESSION" ]; then
  SESSION_ID="$(post /sessions "{\"teacherPublicId\":\"$TEACHER_ID\",\"classPublicIds\":[\"$CLASS_ID\"],\"startsAt\":\"2026-09-10T06:00:00Z\",\"endsAt\":\"2026-09-10T10:00:00Z\",\"timeZoneId\":\"Europe/Paris\",\"reason\":\"Séance de démonstration\",\"title\":\"Atelier émargement (démo)\"}" | jq -r '.publicId')"
  say "séance      $SESSION_ID (PLANNED)"
else
  say "séance      $EXISTING_SESSION (déjà présente, PLANNED)"
fi

echo "Amorçage terminé. Connectez-vous à l'interface avec les comptes de démonstration."
