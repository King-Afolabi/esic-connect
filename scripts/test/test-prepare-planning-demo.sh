#!/usr/bin/env bash
# Preuve de non-régression pour scripts/prepare-planning-demo.sh.
#
#   1. avec un UUID en argument : les deux fichiers sont générés dans
#      OUT_DIR, contiennent l'UUID, ne contiennent plus le marqueur ;
#   2. les modèles versionnés docs/demo-data/*.csv restent INCHANGÉS ;
#   3. un argument non-UUID échoue (code de sortie non nul) ;
#   4. sans argument, la résolution via un faux `curl` (login + users)
#      fonctionne et produit les mêmes fichiers ;
#   5. la recherche du formateur interroge bien `/users?q=` — le
#      paramètre attendu par `UserAccountController` — et rien d'autre
#      (vérification statique du script + vérification de l'URL
#      réellement appelée par le faux `curl`) ;
#   6. formateur introuvable => échec explicite (code non nul + message) ;
#   7. authentification ADMIN refusée => message distinct de « introuvable » ;
#   8. compte formateur SUSPENDED => échec AVANT l'import (le back-end
#      n'accepte qu'un compte ACTIVE portant un rôle TEACHER actif) ;
#   9. compte sans rôle TEACHER => échec explicite ;
#  10. réponse /users non paginée => échec explicite ;
#  11. aucun jeton d'authentification n'est journalisé.
#
# Aucun secret affiché : le faux `curl` renvoie un jeton factice.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$HERE/../.." && pwd)"
SCRIPT="$HERE/../prepare-planning-demo.sh"
TEMPLATE_DIR="$REPO_ROOT/docs/demo-data"
MARKER='__TEACHER_PUBLIC_ID__'
FAKE_UUID='11111111-2222-4333-8444-555555555555'

WORK="$(mktemp -d "${TMPDIR:-/tmp}/prep-plan-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

# Empreintes des modèles avant exécution.
before_hashes="$(cd "$TEMPLATE_DIR" && shasum planning-demo.csv planning-conflicts-demo.csv)"

# --- 1. UUID en argument ---------------------------------------------------
OUT_DIR="$WORK/out1" bash "$SCRIPT" "$FAKE_UUID" >"$WORK/out1.log" 2>&1 \
  || { cat "$WORK/out1.log"; fail "exécution avec UUID en argument"; }
for name in planning-demo.csv planning-conflicts-demo.csv; do
  f="$WORK/out1/$name"
  [ -f "$f" ] || fail "fichier généré absent : $f"
  grep -q "$FAKE_UUID" "$f" || fail "$name ne contient pas l'UUID substitué"
  grep -q "$MARKER" "$f" && fail "$name contient encore le marqueur"
done

# --- 2. modèles inchangés ------------------------------------------------
after_hashes="$(cd "$TEMPLATE_DIR" && shasum planning-demo.csv planning-conflicts-demo.csv)"
[ "$before_hashes" = "$after_hashes" ] || fail "un modèle versionné a été modifié"

# --- 3. argument non-UUID rejeté --------------------------------------
if OUT_DIR="$WORK/out3" bash "$SCRIPT" "pas-un-uuid" >/dev/null 2>&1; then
  fail "un argument non-UUID aurait dû échouer"
fi

# --- 4. résolution via faux curl (login + users) --------------------
CURL_LOG="$WORK/curl-urls.log"
FAKE_CURL="$WORK/fake-curl"
# Le faux `curl` journalise chaque URL appelée et ne renvoie le formateur
# QUE si la recherche utilise le paramètre `q` attendu par l'API. Toute
# autre forme (`query=`, `email=`, …) produit une page vide : le script
# doit alors échouer, ce qui rend la régression impossible à ignorer.
cat >"$FAKE_CURL" <<FAKE
#!/usr/bin/env bash
set -euo pipefail
url=""
for a in "\$@"; do case "\$a" in http*) url="\$a";; esac; done
printf '%s\n' "\$url" >>"$CURL_LOG"
case "\$url" in
  *"/auth/login")  printf '%s' '{"accessToken":"fake-token"}' ;;
  *"/users?q="*)   printf '%s' '{"content":[{"email":"formateur@example.test","publicId":"$FAKE_UUID","status":"ACTIVE","roles":["TEACHER"]}]}' ;;
  *"/users"*)      printf '%s' '{"content":[]}' ;;
  *)               printf '%s' '{}' ;;
esac
FAKE
chmod +x "$FAKE_CURL"

OUT_DIR="$WORK/out4" CURL="$FAKE_CURL" ESIC_DEMO_PASSWORD="unused-in-fake" \
  bash "$SCRIPT" >"$WORK/out4.log" 2>&1 \
  || { cat "$WORK/out4.log"; fail "résolution via faux curl"; }
grep -q "$FAKE_UUID" "$WORK/out4/planning-demo.csv" \
  || fail "la résolution API n'a pas substitué l'UUID"

# --- 5. le script interroge bien /users?q= ---------------------------------
# 5a. vérification statique : aucune autre forme de paramètre de recherche.
if grep -nE '/users\?' "$SCRIPT" | grep -qvE '/users\?q='; then
  grep -nE '/users\?' "$SCRIPT" >&2
  fail "le script doit interroger /users?q= (paramètre de UserAccountController)"
fi
grep -qE '/users\?q=' "$SCRIPT" || fail "aucun appel à /users?q= trouvé dans le script"

# 5b. vérification dynamique : l'URL réellement appelée porte bien `q=`.
users_url="$(grep '/users' "$CURL_LOG" || true)"
[ -n "$users_url" ] || fail "aucune requête /users n'a été émise"
case "$users_url" in
  *"/users?q="*) : ;;
  *) fail "URL de recherche inattendue : $users_url (attendu /users?q=)" ;;
esac

# --- 6. formateur introuvable => échec explicite ---------------------------
FAKE_CURL_EMPTY="$WORK/fake-curl-empty"
cat >"$FAKE_CURL_EMPTY" <<'FAKE'
#!/usr/bin/env bash
set -euo pipefail
url=""
for a in "$@"; do case "$a" in http*) url="$a";; esac; done
case "$url" in
  *"/auth/login") printf '%s' '{"accessToken":"fake-token"}' ;;
  *)              printf '%s' '{"content":[]}' ;;
esac
FAKE
chmod +x "$FAKE_CURL_EMPTY"

if OUT_DIR="$WORK/out6" CURL="$FAKE_CURL_EMPTY" ESIC_DEMO_PASSWORD="unused-in-fake" \
  bash "$SCRIPT" >"$WORK/out6.log" 2>&1; then
  fail "un formateur introuvable aurait dû faire échouer le script"
fi
grep -q "introuvable" "$WORK/out6.log" \
  || { cat "$WORK/out6.log"; fail "l'échec « formateur introuvable » doit être explicite"; }
if [ -e "$WORK/out6/planning-demo.csv" ]; then
  fail "aucun fichier ne doit être généré sans formateur"
fi

# --- 7..10 : chaque cause d'échec doit produire SON message ----------------
# Un faux `curl` paramétrable par la charge utile renvoyée sur /users.
make_fake_curl() { # $1 = fichier, $2 = corps /auth/login, $3 = corps /users
  cat >"$1" <<FAKE
#!/usr/bin/env bash
set -euo pipefail
url=""
for a in "\$@"; do case "\$a" in http*) url="\$a";; esac; done
case "\$url" in
  *"/auth/login") printf '%s' '$2' ;;
  *)              printf '%s' '$3' ;;
esac
FAKE
  chmod +x "$1"
}

# $1 = libellé, $2 = corps login, $3 = corps users, $4 = motif attendu
expect_failure() {
  local label="$1" login="$2" users="$3" needle="$4"
  local dir="$WORK/neg-$label"
  make_fake_curl "$WORK/curl-$label" "$login" "$users"
  if OUT_DIR="$dir" CURL="$WORK/curl-$label" ESIC_DEMO_PASSWORD="unused-in-fake" \
    bash "$SCRIPT" >"$WORK/$label.log" 2>&1; then
    fail "[$label] le script aurait dû échouer"
  fi
  grep -q "$needle" "$WORK/$label.log" \
    || { cat "$WORK/$label.log"; fail "[$label] message attendu manquant : $needle"; }
  if [ -e "$dir/planning-demo.csv" ]; then
    fail "[$label] aucun fichier ne doit être généré en cas d'échec"
  fi
}

OK_USERS='{"content":[{"email":"formateur@example.test","publicId":"'"$FAKE_UUID"'","status":"ACTIVE","roles":["TEACHER"]}]}'

# 7. authentification refusée : message distinct de « introuvable »
expect_failure auth '{"error":"bad credentials"}' "$OK_USERS" "authentification ADMIN"

# 8. compte non ACTIVE : détecté à la préparation, pas à l'import
expect_failure suspended '{"accessToken":"fake-token"}' \
  '{"content":[{"email":"formateur@example.test","publicId":"'"$FAKE_UUID"'","status":"SUSPENDED","roles":["TEACHER"]}]}' \
  "et non ACTIVE"

# 9. rôle TEACHER absent : même cause racine que PLAN_TEACHER_NOT_ELIGIBLE
expect_failure norole '{"accessToken":"fake-token"}' \
  '{"content":[{"email":"formateur@example.test","publicId":"'"$FAKE_UUID"'","status":"ACTIVE","roles":["STUDENT"]}]}' \
  "rôle TEACHER"

# 10. réponse API inattendue (non paginée)
expect_failure badjson '{"accessToken":"fake-token"}' '[]' "réponse inattendue"

# --- 11. aucun jeton ne fuit dans la sortie --------------------------------
# Le jeton d'authentification ne doit apparaître dans aucun journal.
for log in "$WORK"/*.log; do
  [ -e "$log" ] || continue
  if grep -q "fake-token" "$log"; then
    fail "un jeton d'authentification a fuité dans $log"
  fi
done

printf 'OK — prepare-planning-demo.sh : 11 vérifications passées.\n'
