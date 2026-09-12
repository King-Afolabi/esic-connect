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
# Prérequis : bash, curl, jq, python3 (bibliothèque standard seulement —
# calcul du code TOTP, aucun paquet à installer). Back-end démarré (profil
# `demo`) et joignable sur $API_BASE. Variables ESIC_DEMO_PASSWORD et
# ESIC_DEMO_TOTP_SECRET identiques à celles du back-end.
#
# Le compte ADMIN de démonstration exige un second facteur (RG-007) : la
# connexion ne renvoie jamais un jeton contre le seul mot de passe
# (DEC-S2-005). ESIC_DEMO_TOTP_SECRET pilote, côté back-end
# (DemoDataInitializer), l'activation d'un facteur TOTP déterministe pour
# ce compte ; ce script calcule localement le même code et le soumet à la
# VRAIE route /mfa/verify — aucun contournement, aucun accès direct à la
# base (dette de démonstration liée au second facteur, docs/STATUS.md).
#
#   API_BASE=http://localhost:8080 ESIC_DEMO_PASSWORD=... \
#     ESIC_DEMO_TOTP_SECRET=... ./scripts/seed-demo.sh
#
# Point d'injection de test : la variable CURL permet de substituer un
# faux `curl` déterministe (voir scripts/test/test-seed-demo.sh).
set -euo pipefail

API_BASE="${API_BASE:-http://localhost:8080}"
API="${API_BASE%/}/api/v1"
ADMIN_EMAIL="admin@example.test"
CURL="${CURL:-curl}"
: "${ESIC_DEMO_PASSWORD:?Définissez ESIC_DEMO_PASSWORD (même valeur que le back-end).}"
: "${ESIC_DEMO_TOTP_SECRET:?Définissez ESIC_DEMO_TOTP_SECRET (même valeur que le back-end, profil demo) — nécessaire pour franchir le second facteur obligatoire du compte ADMIN (RG-007).}"

for tool in "$CURL" jq python3; do
  command -v "$tool" >/dev/null 2>&1 || { echo "Outil requis manquant : $tool" >&2; exit 1; }
done

# totp_code SECRET -> code à 6 chiffres pour l'instant courant (pas de 30 s).
# Port Python (bibliothèque standard uniquement) du même algorithme que
# TotpGenerator.java : RFC 6238 / RFC 4226, HMAC-SHA1, troncature
# dynamique. La fenêtre de 30 s et la tolérance de dérive d'horloge
# (±1 pas) sont gérées côté serveur (MfaService) : ce calcul n'a besoin
# que de l'heure système courante, comme n'importe quelle application
# d'authentification.
totp_code() {
  python3 - "$1" <<'PY'
import base64, hashlib, hmac, struct, sys, time

secret = sys.argv[1].strip().upper()
padding = "=" * ((8 - len(secret) % 8) % 8)
key = base64.b32decode(secret + padding)
step = int(time.time()) // 30
counter = struct.pack(">Q", step)
mac = hmac.new(key, counter, hashlib.sha1).digest()
offset = mac[-1] & 0x0F
binary = (((mac[offset] & 0x7F) << 24) | ((mac[offset + 1] & 0xFF) << 16)
          | ((mac[offset + 2] & 0xFF) << 8) | (mac[offset + 3] & 0xFF))
print(str(binary % 1_000_000).zfill(6))
PY
}

# seconds_until_next_totp_step -> secondes restantes avant le prochain pas
# de 30 s (+1 s de marge). Sert uniquement à la RÉ-EXÉCUTION rapprochée du
# script : le serveur refuse à bon droit (anti-rejeu, RG-054/055) un code
# déjà consommé pendant son propre pas de temps — ce n'est pas une erreur,
# c'est la protection qui fonctionne. Attendre le pas suivant est le seul
# comportement correct, pas une dérive d'horloge à corriger.
seconds_until_next_totp_step() {
  python3 -c 'import time; now = int(time.time()); print(30 - (now % 30) + 1)'
}

# Fichier temporaire sécurisé pour le corps des réponses (jamais le
# jeton : l'en-tête Authorization n'est pas une réponse). Nettoyé quoi
# qu'il arrive.
BODY_FILE="$(mktemp "${TMPDIR:-/tmp}/esic-seed.XXXXXX")"
cleanup() { rm -f "$BODY_FILE"; }
trap cleanup EXIT INT TERM

say() { printf '  %s\n' "$*"; }

# --- Authentification -------------------------------------------------------
# Le corps (qui peut contenir le jeton) n'est jamais affiché : seuls les
# champs nécessaires sont extraits par jq.
login_status="$("$CURL" -sS -o "$BODY_FILE" -w '%{http_code}' -X POST "$API/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ESIC_DEMO_PASSWORD\"}" || true)"
if [ "$login_status" != 200 ]; then
  echo "Échec de connexion ADMIN (HTTP ${login_status:-?}). Le back-end tourne-t-il avec le profil demo et le bon mot de passe ?" >&2
  exit 1
fi
TOKEN="$(jq -r '.accessToken // empty' <"$BODY_FILE" 2>/dev/null || true)"
CHALLENGE_ID="$(jq -r '.mfa.challengeId // empty' <"$BODY_FILE" 2>/dev/null || true)"
CHALLENGE_PURPOSE="$(jq -r '.mfa.purpose // empty' <"$BODY_FILE" 2>/dev/null || true)"
: >"$BODY_FILE"

# ADMIN exige un second facteur (RG-007) : le mot de passe seul ne renvoie
# jamais de jeton (DEC-S2-005). `TOKEN` est donc vide ici en usage normal
# et `CHALLENGE_ID`/`CHALLENGE_PURPOSE` portent le défi à franchir.
if [ -z "$TOKEN" ] && [ -n "$CHALLENGE_ID" ]; then
  case "$CHALLENGE_PURPOSE" in
    VERIFY)
      # Facteur déjà actif (cas nominal avec ESIC_DEMO_TOTP_SECRET aligné
      # sur le back-end) : un appel avec le code calculé localement.
      CODE="$(totp_code "$ESIC_DEMO_TOTP_SECRET")"
      verify_status="$("$CURL" -sS -o "$BODY_FILE" -w '%{http_code}' -X POST "$API/auth/mfa/verify" \
        -H 'Content-Type: application/json' \
        -d "{\"challengeId\":\"$CHALLENGE_ID\",\"code\":\"$CODE\"}" || true)"
      # Anti-rejeu (RG-054/055) : une ré-exécution du script à moins de
      # 30 s d'intervalle recalcule le MÊME code (même pas de temps), que
      # le serveur refuse à bon droit — pas une erreur, la protection
      # fonctionne. On attend le pas suivant et on retente UNE fois avec
      # un nouveau défi (l'ancien challengeId reste valide, docs/02 §16.10).
      if [ "$verify_status" = 401 ] && jq -e '.code == "CODE_ALREADY_USED"' <"$BODY_FILE" >/dev/null 2>&1; then
        wait_s="$(seconds_until_next_totp_step)"
        echo "Code TOTP déjà consommé (ré-exécution rapprochée) : nouvelle tentative dans ${wait_s}s." >&2
        sleep "$wait_s"
        : >"$BODY_FILE"
        CODE="$(totp_code "$ESIC_DEMO_TOTP_SECRET")"
        verify_status="$("$CURL" -sS -o "$BODY_FILE" -w '%{http_code}' -X POST "$API/auth/mfa/verify" \
          -H 'Content-Type: application/json' \
          -d "{\"challengeId\":\"$CHALLENGE_ID\",\"code\":\"$CODE\"}" || true)"
      fi
      if [ "$verify_status" != 200 ]; then
        echo "Échec de vérification du second facteur ADMIN (HTTP ${verify_status:-?})." >&2
        sed 's/^/    /' "$BODY_FILE" >&2 || true
        exit 1
      fi
      TOKEN="$(jq -r '.accessToken // empty' <"$BODY_FILE")"
      ;;
    ENROLL)
      # Aucun facteur actif côté back-end (ESIC_DEMO_TOTP_SECRET absent ou
      # différent au démarrage du back-end) : parcours d'enrôlement réel en
      # deux appels, avec le secret ALÉATOIRE renvoyé par le serveur — pas
      # celui de cette variable, qu'un enrôlement normal ne connaît pas.
      enroll_status="$("$CURL" -sS -o "$BODY_FILE" -w '%{http_code}' -X POST "$API/auth/mfa/enroll" \
        -H 'Content-Type: application/json' \
        -d "{\"challengeId\":\"$CHALLENGE_ID\"}" || true)"
      if [ "$enroll_status" != 200 ]; then
        echo "Échec de démarrage de l'enrôlement du second facteur ADMIN (HTTP ${enroll_status:-?})." >&2
        sed 's/^/    /' "$BODY_FILE" >&2 || true
        exit 1
      fi
      SERVER_SECRET="$(jq -r '.secret // empty' <"$BODY_FILE")"
      : >"$BODY_FILE"
      [ -n "$SERVER_SECRET" ] || { echo "Réponse d'enrôlement MFA sans secret." >&2; exit 1; }
      CODE="$(totp_code "$SERVER_SECRET")"
      confirm_status="$("$CURL" -sS -o "$BODY_FILE" -w '%{http_code}' -X POST "$API/auth/mfa/enroll/confirm" \
        -H 'Content-Type: application/json' \
        -d "{\"challengeId\":\"$CHALLENGE_ID\",\"code\":\"$CODE\"}" || true)"
      if [ "$confirm_status" != 200 ]; then
        echo "Échec de confirmation d'enrôlement du second facteur ADMIN (HTTP ${confirm_status:-?})." >&2
        sed 's/^/    /' "$BODY_FILE" >&2 || true
        exit 1
      fi
      TOKEN="$(jq -r '.session.accessToken // empty' <"$BODY_FILE")"
      echo "Note : second facteur ADMIN enrôlé avec un secret aléatoire côté serveur (le back-end" >&2
      echo "n'avait pas ESIC_DEMO_TOTP_SECRET actif à son démarrage). Alignez cette variable des deux" >&2
      echo "côtés et redémarrez le back-end pour repasser par le parcours VERIFY, plus rapide." >&2
      ;;
    *)
      echo "Défi de second facteur ADMIN de type inattendu : '${CHALLENGE_PURPOSE:-vide}'." >&2
      exit 1
      ;;
  esac
fi
: >"$BODY_FILE"

if [ -z "$TOKEN" ]; then
  echo "Échec de connexion ADMIN : aucun jeton obtenu après authentification et second facteur." >&2
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
RESP_ID="$(user_id 'responsable@example.test')"
for v in TEACHER_ID STUDENT1_ID STUDENT2_ID RESP_ID; do
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

# Affectation du responsable pédagogique (responsable@example.test) à la
# formation de démonstration : rend son périmètre exploitable et permet
# de démontrer le sélecteur de contexte de rôle (PEDAGOGICAL_MANAGER +
# TEACHER). Idempotent : un 409 ACAD_PRIMARY_MANAGER_EXISTS = déjà fait.
ensure_primary_manager() {
  local rc
  http_post /pedagogical-assignments \
    "{\"programPublicId\":\"$PROGRAM_ID\",\"userPublicId\":\"$RESP_ID\",\"type\":\"PRIMARY_MANAGER\",\"reason\":\"Démonstration\"}" \
    --allow-conflict >/dev/null && rc=0 || rc=$?
  [ "$rc" -eq 0 ] || [ "$rc" -eq 9 ] || exit "$rc"
}
ensure_primary_manager
say "responsable pédagogique affecté à PRG-DEMO"

# Inscription (rattachée directement au COMPTE apprenant — refonte
# 2026-09 — jamais à un profil : `student` désigne ici le
# `user_account.public_id`).
ensure_enrollment() {
  local user="$1" existing
  existing="$(http_get_q /enrollments student "$user" status ACTIVE \
    | jq -r '.content[0]?.publicId // empty')"
  if [ -z "$existing" ]; then
    http_post /enrollments "{\"studentUserPublicId\":\"$user\",\"classGroupPublicId\":\"$CLASS_ID\"}" >/dev/null
  fi
}

# Trois profils d'apprenant délibérément distincts (refonte 2026-09 : le
# rôle STUDENT est l'unique source de vérité du statut apprenant — ni le
# numéro étudiant ni l'inscription ne le sont) :
#   - apprenant1@example.test : apprenant COMPLET (numéro étudiant posé par
#     DemoDataInitializer au démarrage du back-end + inscription ici) ;
#   - apprenant2@example.test : apprenant MINIMAL (rôle STUDENT seul,
#     aucun numéro, aucune inscription — reste pleinement visible dans
#     Apprenants) ;
#   - formateur@example.test (et les comptes d'administration) : non-apprenants.
# Le numéro étudiant n'est plus posé ici : il n'existe plus de route dédiée
# pour l'attribuer après coup (colonne de user_account, refonte 2026-09) ;
# DemoDataInitializer le pose directement à la création du compte.
ensure_enrollment "$STUDENT1_ID"
say "apprenant complet   apprenant1@example.test (numéro ESIC-DEMO-001, inscrit dans C-DEMO)"
say "apprenant minimal   apprenant2@example.test (rôle STUDENT seul, aucun numéro ni inscription)"

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
