#!/usr/bin/env bash
# Preuve de non-régression pour scripts/prepare-planning-demo.sh.
#
#   1. avec un UUID en argument : les deux fichiers sont générés dans
#      OUT_DIR, contiennent l'UUID, ne contiennent plus le marqueur ;
#   2. les modèles versionnés docs/demo-data/*.csv restent INCHANGÉS ;
#   3. un argument non-UUID échoue (code de sortie non nul) ;
#   4. sans argument, la résolution via un faux `curl` (login + users)
#      fonctionne et produit les mêmes fichiers.
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
FAKE_CURL="$WORK/fake-curl"
cat >"$FAKE_CURL" <<FAKE
#!/usr/bin/env bash
set -euo pipefail
url=""
for a in "\$@"; do case "\$a" in http*) url="\$a";; esac; done
case "\$url" in
  *"/auth/login") printf '%s' '{"accessToken":"fake-token"}' ;;
  *"/users"*)     printf '%s' '{"content":[{"email":"formateur@example.test","publicId":"$FAKE_UUID"}]}' ;;
  *)              printf '%s' '{}' ;;
esac
FAKE
chmod +x "$FAKE_CURL"

OUT_DIR="$WORK/out4" CURL="$FAKE_CURL" ESIC_DEMO_PASSWORD="unused-in-fake" \
  bash "$SCRIPT" >"$WORK/out4.log" 2>&1 \
  || { cat "$WORK/out4.log"; fail "résolution via faux curl"; }
grep -q "$FAKE_UUID" "$WORK/out4/planning-demo.csv" \
  || fail "la résolution API n'a pas substitué l'UUID"

printf 'OK — prepare-planning-demo.sh : 4 vérifications passées.\n'
