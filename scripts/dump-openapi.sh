#!/usr/bin/env bash
#
# Récupère la spécification OpenAPI du back-end ESIC Connect **au
# runtime** (springdoc-openapi expose `/v3/api-docs`, route publique).
#
# Aucun plugin Maven n'est ajouté au build : générer l'artefact au build
# imposerait de démarrer le contexte Spring pendant `mvn package`
# (springdoc-openapi-maven-plugin), ce qui est lourd et fragile en CI.
# On préfère un export explicite, reproductible, à la demande.
#
# Usage :
#   1. Démarrer l'infra + le back-end (voir README.md), p. ex. :
#        docker compose up -d
#        cd backend && set -a && source ../.env && set +a
#        SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
#   2. Depuis la racine du dépôt :
#        bash scripts/dump-openapi.sh                 # -> docs/openapi.json
#        bash scripts/dump-openapi.sh http://host:8080 chemin/sortie.json
#
# Le fichier produit n'est pas versionné par défaut (voir .gitignore) :
# c'est une commodité de revue d'API, pas une source de vérité.

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
OUT="${2:-docs/openapi.json}"
DOCS_PATH="${OPENAPI_DOCS_PATH:-/v3/api-docs}"

url="${BASE_URL%/}${DOCS_PATH}"

echo "GET ${url}"
http_code="$(curl -sS -o "${OUT}.tmp" -w '%{http_code}' "${url}" || true)"

if [ "${http_code}" != "200" ]; then
  echo "Échec : HTTP ${http_code}. Le back-end est-il démarré sur ${BASE_URL} ?" >&2
  rm -f "${OUT}.tmp"
  exit 1
fi

# Reformatage lisible si jq est disponible (sinon, JSON brut).
if command -v jq >/dev/null 2>&1; then
  jq . "${OUT}.tmp" > "${OUT}"
  rm -f "${OUT}.tmp"
else
  mv "${OUT}.tmp" "${OUT}"
fi

echo "OpenAPI écrit dans ${OUT}"
echo "Swagger UI interactif : ${BASE_URL%/}/swagger-ui.html"
