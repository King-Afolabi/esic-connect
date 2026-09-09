#!/usr/bin/env bash
# Preuve de non-régression pour scripts/seed-demo.sh, sans back-end :
# un faux `curl` déterministe enregistre chaque requête. On vérifie que
#
#   1. aucun chemin ne reçoit deux POST (« un appel logique = un POST ») ;
#   2. sur une base vierge, exactement une séance PLANNED est POSTée ;
#   3. sur une base déjà amorcée (créations en 409, GET renvoyant
#      l'existant), le script réussit et ne POST NI séance NI inscription
#      supplémentaire.
#
# Aucun secret n'est affiché : le faux `curl` renvoie un jeton factice.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEED="$HERE/../seed-demo.sh"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/seed-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

CALLLOG="$WORK/calls.log"
FAKE_CURL="$WORK/fake-curl"
: >"$CALLLOG"

cat >"$FAKE_CURL" <<'FAKE'
#!/usr/bin/env bash
# Faux curl : journalise "METHOD PATH" dans $CALLLOG et rend une réponse
# déterministe selon $FAKE_MODE (clean|exists) et $FAKE_LOGIN_MODE
# (token|verify|enroll — second facteur ADMIN, RG-007). Gère les options
# réellement utilisées par seed-demo.sh : -sS -o FILE -w FMT -X POST
# -H ... -d BODY -G --data-urlencode k=v.
set -euo pipefail
method=GET url="" outfile="" want_status=0 q="" body=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    -X) method="$2"; shift 2 ;;
    -o) outfile="$2"; shift 2 ;;
    -w) want_status=1; shift 2 ;;
    -d) body="$2"; shift 2 ;;
    -H) shift 2 ;;
    -G) shift ;;
    --data-urlencode)
      case "$2" in q=*) q="${2#q=}" ;; esac
      shift 2 ;;
    -sS|-s|-S) shift ;;
    http*|https*) url="$1"; shift ;;
    *) shift ;;
  esac
done

path="${url#*/api/v1}"
# Normalise les segments d'identifiant pour un journal stable.
logpath="$(printf '%s' "$path" | sed -E 's#/[0-9a-fA-F-]{16,}#/{id}#g; s#/(programs)/[^/]+/levels#/\1/{id}/levels#')"
# $CALLLOG : "METHOD PATH" (comptage). $CALLLOG.full : + corps compacté
# (détection d'un vrai double POST du même appel logique).
compact_body="$(printf '%s' "$body" | tr -d '\n' | tr -s ' ')"
printf '%s %s\n' "$method" "$logpath" >>"$CALLLOG"
printf '%s %s %s\n' "$method" "$logpath" "$compact_body" >>"$CALLLOG.full"

emit() { # emit STATUS JSON
  if [ -n "$outfile" ]; then printf '%s' "$2" >"$outfile"; else printf '%s' "$2"; fi
  if [ "$want_status" = 1 ]; then printf '%s' "$1"; fi
}

# Même algorithme que totp_code() dans seed-demo.sh (RFC 6238/4226,
# implémentation Python indépendante) : vérifie que le code REÇU est
# arithmétiquement correct pour SECRET, sans jamais faire confiance au
# script testé pour se juger lui-même.
expected_code() {
  python3 - "$1" <<'PY'
import base64, hashlib, hmac, struct, sys, time
secret = sys.argv[1].strip().upper()
key = base64.b32decode(secret + "=" * ((8 - len(secret) % 8) % 8))
step = int(time.time()) // 30
mac = hmac.new(key, struct.pack(">Q", step), hashlib.sha1).digest()
offset = mac[-1] & 0x0F
binary = (((mac[offset] & 0x7F) << 24) | ((mac[offset + 1] & 0xFF) << 16)
          | ((mac[offset + 2] & 0xFF) << 8) | (mac[offset + 3] & 0xFF))
print(str(binary % 1_000_000).zfill(6))
PY
}

ENROLL_SECRET="KRSXG5CTMVRXEZLU"  # Distinct de ESIC_DEMO_TOTP_SECRET : simule un secret choisi par le serveur.

if [ "$method" = POST ]; then
  case "$path" in
    */auth/login)
      case "${FAKE_LOGIN_MODE:-token}" in
        verify)
          emit 200 '{"mfa":{"challengeId":"chal-verify-1","purpose":"VERIFY","expiresInSeconds":300}}' ;;
        enroll)
          emit 200 '{"mfa":{"challengeId":"chal-enroll-1","purpose":"ENROLL","expiresInSeconds":300}}' ;;
        *)
          emit 200 '{"accessToken":"FAKE-JWT-NOT-A-REAL-SECRET"}' ;;
      esac
      exit 0 ;;
    */auth/mfa/verify)
      challenge_id="$(printf '%s' "$body" | jq -r '.challengeId')"
      code="$(printf '%s' "$body" | jq -r '.code')"
      if [ "$challenge_id" = "chal-verify-1" ] && [ "$code" = "$(expected_code "${ESIC_DEMO_TOTP_SECRET:-}")" ]; then
        emit 200 '{"accessToken":"FAKE-JWT-NOT-A-REAL-SECRET"}'
      else
        emit 401 '{"status":401,"code":"MFA_INVALID_CODE","message":"invalid","path":"x","details":[]}'
      fi
      exit 0 ;;
    */auth/mfa/enroll)
      challenge_id="$(printf '%s' "$body" | jq -r '.challengeId')"
      if [ "$challenge_id" = "chal-enroll-1" ]; then
        emit 200 "$(jq -n --arg s "$ENROLL_SECRET" '{secret:$s,provisioningUri:"otpauth://totp/x",periodSeconds:30}')"
      else
        emit 409 '{"status":409,"code":"MFA_ALREADY_ENROLLED","message":"x","path":"x","details":[]}'
      fi
      exit 0 ;;
    */auth/mfa/enroll/confirm)
      challenge_id="$(printf '%s' "$body" | jq -r '.challengeId')"
      code="$(printf '%s' "$body" | jq -r '.code')"
      if [ "$challenge_id" = "chal-enroll-1" ] && [ "$code" = "$(expected_code "$ENROLL_SECRET")" ]; then
        emit 200 '{"recoveryCodes":["AAAAA-BBBBB"],"session":{"accessToken":"FAKE-JWT-NOT-A-REAL-SECRET"}}'
      else
        emit 401 '{"status":401,"code":"MFA_INVALID_CODE","message":"invalid","path":"x","details":[]}'
      fi
      exit 0 ;;
  esac
  if [ "${FAKE_MODE:-clean}" = exists ]; then
    case "$path" in
      /sites|/programs|/programs/*/levels|/academic-years|/promotions|/class-groups|/student-profiles|/pedagogical-assignments)
        emit 409 '{"status":409,"code":"DUPLICATE","message":"already exists","path":"x","details":[]}'
        exit 0 ;;
    esac
  fi
  emit 201 '{"publicId":"created-'"$RANDOM"'"}'
  exit 0
fi

# GET
if [ -n "$q" ]; then
  # Un item qui satisfait tous les select() du script (.email/.studentNumber/.code).
  emit 200 '{"content":[{"email":"'"$q"'","studentNumber":"'"$q"'","code":"'"$q"'","publicId":"got-'"$q"'"}]}'
  exit 0
fi
if [ "${FAKE_MODE:-clean}" = exists ]; then
  emit 200 '{"content":[{"publicId":"existing-1"}]}'
else
  emit 200 '{"content":[]}'
fi
exit 0
FAKE
chmod +x "$FAKE_CURL"

export CALLLOG
export ESIC_DEMO_PASSWORD="fake-password-not-used"
export ESIC_DEMO_TOTP_SECRET="JBSWY3DPEHPK3PXP"

run_seed() { # run_seed FAKE_MODE [FAKE_LOGIN_MODE]
  : >"$CALLLOG"; : >"$CALLLOG.full"
  FAKE_MODE="$1" FAKE_LOGIN_MODE="${2:-token}" CURL="$FAKE_CURL" bash "$SEED" >/dev/null
}

fail() { printf 'ÉCHEC : %b\n' "$*" >&2; exit 1; }

# Un vrai double POST = deux requêtes identiques (même chemin, même corps)
# pour un même appel logique. Deux POST /student-profiles de corps
# distincts (deux apprenants) sont légitimes.
assert_no_double_post() {
  local dup
  dup="$(grep '^POST ' "$CALLLOG.full" | sort | uniq -d || true)"
  [ -z "$dup" ] || { echo "--- $CALLLOG.full ---"; cat "$CALLLOG.full"; fail "requête POST identique émise deux fois :\n$dup"; }
}

count_post() { grep -c "^POST $1\$" "$CALLLOG" || true; }

# --- Scénario 1 : base vierge --------------------------------------------
run_seed clean
assert_no_double_post
[ "$(count_post '/auth/login')" = 1 ] || fail "login POSTé $(count_post '/auth/login') fois (attendu 1)"
[ "$(count_post '/sessions')" = 1 ] || { cat "$CALLLOG"; fail "séance POSTée $(count_post '/sessions') fois (attendu 1)"; }
[ "$(count_post '/student-profiles')" = 2 ] || fail "profils POSTés $(count_post '/student-profiles') fois (attendu 2)"
[ "$(count_post '/enrollments')" = 2 ] || fail "inscriptions POSTées $(count_post '/enrollments') fois (attendu 2)"
[ "$(count_post '/pedagogical-assignments')" = 1 ] || fail "affectation RP POSTée $(count_post '/pedagogical-assignments') fois (attendu 1)"
echo "Scénario 1 (vierge) : OK — 1 séance créée, 1 affectation RP, aucun double POST."

# --- Scénario 2 : base déjà amorcée ------------------------------------
run_seed exists
assert_no_double_post
[ "$(count_post '/sessions')" = 0 ] || { cat "$CALLLOG"; fail "séance re-POSTée alors qu'elle existe"; }
[ "$(count_post '/enrollments')" = 0 ] || fail "inscription re-POSTée alors qu'elle existe"
# L'affectation RP est re-POSTée (409 ACAD_PRIMARY_MANAGER_EXISTS toléré),
# jamais deux fois pour un même appel logique.
[ "$(count_post '/pedagogical-assignments')" = 1 ] || fail "affectation RP POSTée $(count_post '/pedagogical-assignments') fois (attendu 1)"
echo "Scénario 2 (ré-exécution) : OK — aucune séance ni inscription supplémentaire, affectation RP en 409 toléré."

# --- Scénario 3 : second facteur ADMIN déjà actif (VERIFY) --------------
# Simule le cas nominal : ESIC_DEMO_TOTP_SECRET aligné avec le back-end
# (DemoDataInitializer), qui a donc déjà activé le facteur. Un seul appel
# supplémentaire à /mfa/verify, avec un code arithmétiquement correct.
run_seed exists verify
assert_no_double_post
[ "$(count_post '/auth/mfa/verify')" = 1 ] || fail "mfa/verify POSTé $(count_post '/auth/mfa/verify') fois (attendu 1)"
[ "$(count_post '/auth/mfa/enroll')" = 0 ] || fail "mfa/enroll POSTé alors que le facteur était déjà actif (VERIFY)"
echo "Scénario 3 (second facteur VERIFY) : OK — code TOTP calculé localement accepté en un appel."

# --- Scénario 4 : second facteur ADMIN à enrôler (ENROLL) ---------------
# Simule un back-end démarré sans ESIC_DEMO_TOTP_SECRET actif : le script
# doit driver le parcours réel /mfa/enroll -> /mfa/enroll/confirm avec le
# secret ALÉATOIRE renvoyé par le serveur, jamais celui de la variable
# d'environnement locale.
run_seed exists enroll
assert_no_double_post
[ "$(count_post '/auth/mfa/enroll')" = 1 ] || fail "mfa/enroll POSTé $(count_post '/auth/mfa/enroll') fois (attendu 1)"
[ "$(count_post '/auth/mfa/enroll/confirm')" = 1 ] || fail "mfa/enroll/confirm POSTé $(count_post '/auth/mfa/enroll/confirm') fois (attendu 1)"
[ "$(count_post '/auth/mfa/verify')" = 0 ] || fail "mfa/verify POSTé alors qu'aucun facteur n'était actif (ENROLL)"
echo "Scénario 4 (second facteur ENROLL) : OK — enrôlement réel avec le secret renvoyé par le serveur, confirmé."

# Aucun jeton réel ne doit transiter dans le journal.
! grep -qi 'bearer\|accessToken' "$CALLLOG" || fail "journal contient un jeton"

echo "test-seed-demo.sh : tous les scénarios passent."
