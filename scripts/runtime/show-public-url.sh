#!/usr/bin/env bash
# Affiche l'URL publique courante du déploiement (Quick Tunnel Cloudflare)
# et teste sa disponibilité. L'URL *.trycloudflare.com est ALÉATOIRE et
# CHANGE à chaque (re)démarrage du conteneur `cloudflared` — ce script la
# retrouve, il ne la fige pas.
#
# Ordre de résolution :
#   1. journaux du conteneur cloudflared (compose.prod.yaml) ;
#   2. fichier runtime non versionné .local/runtime/public-url.txt.
#
# Usage : bash scripts/runtime/show-public-url.sh
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
RUNTIME_FILE=".local/runtime/public-url.txt"
COMPOSE=(docker compose -f compose.prod.yaml)

url=""
if "${COMPOSE[@]}" ps --status running --services 2>/dev/null | grep -qx cloudflared; then
  url="$("${COMPOSE[@]}" logs cloudflared 2>/dev/null \
    | grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' | tail -1 || true)"
fi
if [ -z "$url" ] && [ -f "$RUNTIME_FILE" ]; then
  url="$(head -n1 "$RUNTIME_FILE")"
  echo "(URL lue depuis $RUNTIME_FILE — le conteneur cloudflared n'est pas joignable)" >&2
fi
if [ -z "$url" ]; then
  cat >&2 <<'MSG'
Aucune URL publique trouvée.
Le tunnel n'est probablement pas démarré. Pour (re)lancer la pile :
  docker compose --env-file .env.prod -f compose.prod.yaml up -d --build
puis relancer ce script. L'URL apparaît dans :
  docker compose -f compose.prod.yaml logs cloudflared | grep trycloudflare.com
MSG
  exit 1
fi

mkdir -p "$(dirname "$RUNTIME_FILE")"
printf '%s\n' "$url" > "$RUNTIME_FILE"

echo "URL publique : $url"
code="$(curl -s -o /dev/null -w '%{http_code}' -m 15 "$url" || echo 000)"
if [ "$code" = "200" ] || [ "$code" = "304" ]; then
  echo "État         : OK (HTTP $code)"
else
  echo "État         : INJOIGNABLE (HTTP $code) — le tunnel a peut-être changé d'URL."
  echo "               Relancer : docker compose -f compose.prod.yaml logs cloudflared | grep trycloudflare.com"
fi
echo
echo "Pour une adresse STABLE (domaine contrôlé), passer à un tunnel nommé :"
echo "  compose.prod.yaml → cloudflared: command: tunnel run ; environment: TUNNEL_TOKEN=\${CLOUDFLARE_TUNNEL_TOKEN}"
