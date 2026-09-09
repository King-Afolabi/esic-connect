# Pré-vol — avant toute mise en service ESIC Connect

À parcourir **entièrement** avant `docker compose -f compose.prod.yaml
up -d`. Chaque case non cochée est un motif de report.

## Cible et architecture

- [ ] Raspberry Pi 4 (≥ 4 Go RAM) ou 5, sous un OS 64 bits
      (`uname -m` → `aarch64`). 2 Go de RAM est insuffisant pour MySQL 8
      + JVM.
- [ ] Docker Engine + plugin Compose v2 installés
      (`docker compose version`).
- [ ] Toutes les images de base sont multi-arch `arm64` : `mysql:8.4`,
      `redis:7.4-alpine`, `node:24-alpine`, `eclipse-temurin:21-*`,
      `nginx:alpine`, `cloudflare/cloudflared:latest`. **Aucune ligne
      `platform: linux/amd64`** dans `compose.prod.yaml` (contrairement
      au service optionnel `clamav` de `compose.yaml`, absent ici).
- [ ] Espace disque : ≥ 8 Go libres (images ~2 Go, volume MySQL qui
      croît). `df -h /`.
- [ ] Carte SD de qualité (endurance) ou, mieux, SSD USB — la rotation
      des journaux est configurée (`x-logging`, 10 Mo × 3 par conteneur)
      mais MySQL et les justificatifs écrivent en continu.

## Secrets et configuration

- [ ] `.env` créé depuis `.env.prod.example`, permissions `600`, **non
      commité**.
- [ ] `SECRETS.md` parcouru ; `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD`,
      `REDIS_PASSWORD`, `JWT_SECRET` (≥ 32 octets) générés
      aléatoirement.
- [ ] `SPRING_PROFILES_ACTIVE` : `demo` pour une recette (jeu de données
      fictives) ; **jamais `demo` pour une vraie production** — il amorce
      des comptes de démonstration.
- [ ] `ESIC_DEMO_PASSWORD` / `ESIC_DEMO_TOTP_SECRET` : fictifs si profil
      `demo`, sinon vides.
- [ ] `docker compose -f compose.prod.yaml config` : valide, **aucun
      secret en clair** dans la sortie.
- [ ] `git status` : arbre propre, `.env` non suivi.

## Build et tests

- [ ] `cd backend && ./mvnw clean test` : vert (contexte : lancer avec
      les variables du `.env` sourcées).
- [ ] `cd frontend && npm run lint && npm test -- --watch=false &&
      npx ng build --configuration production` : vert, aucune alerte de
      budget.
- [ ] `docker build ./backend` et `docker build ./frontend` : réussis
      **sur la Pi elle-même** (build natif arm64, jamais d'émulation).
- [ ] Recette Playwright : état d'exécution consigné dans
      `docs/STATUS.md` (§6) ; à rejouer contre la pile de démonstration
      avant de présenter la recette comme passée.

## Réseau et exposition

- [ ] Aucun port entrant ouvert sur la box : seul `cloudflared` établit
      une connexion **sortante**. Le back-end n'écoute que sur
      `127.0.0.1:8080` (shell local uniquement).
- [ ] Mode tunnel choisi : « Quick Tunnel » (URL `*.trycloudflare.com`
      aléatoire, change à chaque redémarrage de `cloudflared`) **ou**
      tunnel nommé (`CLOUDFLARE_TUNNEL_TOKEN` + domaine).
- [ ] Si Quick Tunnel : procédure connue pour récupérer l'URL et
      recaler `APP_ALLOWED_ORIGINS` / `APP_ACTIVATION_BASE_URL`
      (`RASPBERRY-PI.md`).
- [ ] HTTPS : assuré par Cloudflare en bout de tunnel — rien à
      configurer côté Pi. Le cookie de renouvellement est `Secure` :
      l'accès **doit** passer par l'URL HTTPS du tunnel, pas par
      `http://<ip-pi>`.

## Données

- [ ] Rien à préserver, **ou** une sauvegarde validée existe
      (`ROLLBACK.md` § Sauvegarde). `scripts/db-reset.sh` ne doit
      **jamais** être lancé sur une base contenant des données à
      conserver.
- [ ] DNS et messagerie : décisions distinctes, hors de cette
      composition (documentées à part si un domaine est acquis).

## Rollback

- [ ] `ROLLBACK.md` lu ; le commit / tag de la version précédente est
      noté ; la procédure de restauration est comprise **avant** de
      déployer, pas pendant l'incident.
