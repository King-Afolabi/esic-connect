# Image de production du front-end ESIC Connect.
#
# Build multi-étapes : compilation Angular dans une image Node jetable,
# puis service des fichiers statiques par Nginx, qui fait aussi office
# de reverse proxy vers le back-end (`environment.ts` : `apiBaseUrl` est
# volontairement relatif, « /api », pour ce montage — docs/03-architecture.md).
# Les images node:24 et nginx:alpine sont multi-architecture (amd64 et
# arm64) : fonctionne à l'identique sur une Raspberry Pi.

# --- Étape 1 : compilation ------------------------------------------------
FROM node:24-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
# `npm install` plutôt que `npm ci` : certaines dépendances optionnelles
# (bindings natifs de l'outillage de build) diffèrent selon l'architecture
# CPU — un lockfile figé sur une machine de développement (ex. macOS
# arm64) peut légitimement manquer les entrées nécessaires à Linux arm64
# (Raspberry Pi). `npm install` les résout pour LA plateforme réelle du
# build, ici toujours native (jamais d'émulation sur la cible).
RUN npm install

COPY . .
RUN npm run build

# --- Étape 2 : service statique + reverse proxy --------------------------
FROM nginx:alpine
COPY --from=build /build/dist/esic-connect-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
