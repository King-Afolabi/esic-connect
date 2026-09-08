# Déploiement Raspberry Pi — ESIC Connect

Cible : **recette / démonstration** sur une Raspberry Pi 4 (≥ 4 Go) ou 5,
OS 64 bits, exposée par un tunnel Cloudflare sortant (aucun port entrant
ouvert). Jeu de données **fictives** (profil Spring `demo`).

Documents liés : `PRE-FLIGHT.md` (à cocher avant), `SECRETS.md`,
`ROLLBACK.md`, `docs/11-guide-deploiement.md` (exploitation générale),
`docs/12-prerequis-externes.md` (comptes et matériel à préparer par le
porteur).

---

## 1. Prérequis (une fois)

```bash
uname -m                      # aarch64 attendu
docker compose version        # v2 attendu
sudo apt-get update && sudo apt-get install -y git
git clone <url-du-depot> esic-connect && cd esic-connect
```

## 2. Configuration

```bash
cp .env.prod.example .env && chmod 600 .env
# Éditer .env : voir SECRETS.md pour chaque variable.
docker compose -f compose.prod.yaml config >/dev/null   # doit être valide, sans secret en clair
```

## 3. Sauvegarde préalable

Si une version tourne déjà : suivre `ROLLBACK.md` § Sauvegarde **avant**
toute mise à jour.

## 4. Installation / démarrage

```bash
docker compose -f compose.prod.yaml up -d --build
docker compose -f compose.prod.yaml ps          # attendre mysql/redis/backend/frontend "healthy"
docker compose -f compose.prod.yaml logs -f backend   # Flyway applique les migrations au 1er démarrage
```

Amorçage du jeu de démonstration (profil `demo`, une fois `backend`
healthy) :

```bash
set -a && source .env && set +a
API_BASE=http://localhost:8080 bash scripts/seed-demo.sh
```

## 5. Récupérer l'URL publique (Quick Tunnel)

L'URL `*.trycloudflare.com` est **aléatoire et change à chaque
redémarrage** de `cloudflared` :

```bash
docker compose -f compose.prod.yaml logs cloudflared | grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' | tail -1
```

Reporter cette URL dans `.env` (`APP_ALLOWED_ORIGINS`,
`APP_ACTIVATION_BASE_URL`) puis recréer le back-end pour qu'il en tienne
compte :

```bash
docker compose -f compose.prod.yaml up -d backend
```

> Pour une URL stable : tunnel **nommé** (domaine possédé). Remplacer la
> ligne `command:` de `cloudflared` par `tunnel run` et ajouter
> `environment: TUNNEL_TOKEN: ${CLOUDFLARE_TUNNEL_TOKEN}` (voir le
> commentaire dans `compose.prod.yaml`). DNS et domaine : décision et
> configuration **distinctes**, non couvertes ici.

## 6. Contrôles post-démarrage

```bash
docker compose -f compose.prod.yaml exec backend wget -qO- http://localhost:8080/actuator/health
curl -sI https://<url-tunnel>/            # 200, servi par Nginx
```

- Ouvrir l'URL HTTPS du tunnel dans un navigateur (le cookie de
  renouvellement est `Secure` : `http://<ip-pi>` ne maintient pas la
  session).
- Se connecter avec un compte de démonstration (`scripts/seed-demo.sh`
  affiche les adresses ; le mot de passe est `ESIC_DEMO_PASSWORD`).

---

## Exploitation courante (runbook)

| Action | Commande |
|---|---|
| **Démarrer** | `docker compose -f compose.prod.yaml up -d` |
| **Arrêter** (garde les données) | `docker compose -f compose.prod.yaml down` |
| **Arrêter + supprimer les volumes** ⚠️ perte de données | `docker compose -f compose.prod.yaml down -v` — **ne jamais faire** sur des données à conserver |
| **État** | `docker compose -f compose.prod.yaml ps` |
| **Journaux** | `docker compose -f compose.prod.yaml logs -f [service]` (rotation 10 Mo × 3) |
| **Mise à jour** | `ROLLBACK.md` § Sauvegarde, puis `git pull`, puis `docker compose -f compose.prod.yaml up -d --build`, puis contrôles § 6 |
| **Rollback** | `ROLLBACK.md` |
| **Sauvegarde** | `ROLLBACK.md` § Sauvegarde |
| **Restauration** | `ROLLBACK.md` § Rollback complet |
| **Redémarrer un service** | `docker compose -f compose.prod.yaml restart backend` |
| **Courriel (Brevo) / changer l'adresse d'expédition** | éditer `MAIL_*` / `APP_MAIL_FROM` dans `.env`, puis `docker compose -f compose.prod.yaml up -d backend` — détail : `BREVO-EMAIL.md` |
| **Shell d'admin** | `docker compose -f compose.prod.yaml exec backend sh` |
| **Migrations à jour ?** | `docker compose -f compose.prod.yaml exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5"'` |

## Redémarrage automatique

Tous les services portent `restart: unless-stopped` : ils repartent au
boot de la Pi et après un crash, sauf s'ils ont été arrêtés
explicitement (`stop` / `down`). Aucune configuration `systemd`
supplémentaire n'est nécessaire ; s'assurer seulement que le service
Docker démarre au boot (`sudo systemctl enable docker`).

## Volumes persistants

| Volume | Contenu | Sauvegarde |
|---|---|---|
| `mysql-data` | source de vérité MySQL | `ROLLBACK.md` (mysqldump) |
| `redis-data` | jetons/cache **temporaires** (AOF) — reconstruit seul | inutile |
| `justification-data` | pièces jointes des justificatifs, hors base | `ROLLBACK.md` (tar du volume) |

## Sécurité — rappels

- Aucun port entrant ; `cloudflared` sort seul. Le back-end n'écoute que
  sur `127.0.0.1`.
- HTTPS assuré par Cloudflare en bout de tunnel.
- `SPRING_PROFILES_ACTIVE=demo` amorce des comptes fictifs : **jamais**
  pour une vraie production.
- Secrets : `SECRETS.md`. `.env` en `600`, jamais commité.
- `scripts/db-reset.sh` **détruit et recrée** une base : ne l'utiliser
  que sur une base sans données à conserver (typiquement
  `esic_connect_demo` fraîchement amorcée).
