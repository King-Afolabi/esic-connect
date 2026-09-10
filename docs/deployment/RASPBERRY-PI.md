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

## 5. Récupérer l'URL publique (tunnel nommé, domaine possédé)

`compose.prod.yaml` lance `cloudflared` en mode **tunnel nommé** :
l'URL publique est **stable** (elle ne change jamais au redémarrage),
contrairement au *Quick Tunnel* (`*.trycloudflare.com`, aléatoire à
chaque démarrage — utile seulement pour un tout premier essai sans
domaine).

**Une fois, côté Cloudflare** (dashboard ou CLI `cloudflared`) :

1. Ajouter le domaine à un compte Cloudflare et **déléguer les
   serveurs de noms (NS)** chez le registrar vers ceux indiqués par
   Cloudflare (plus rapide et plus sûr qu'un simple CNAME externe :
   proxy + TLS gérés automatiquement — voir Zero Trust > Networks >
   Tunnels si le domaine a déjà d'autres enregistrements, ex. MX pour
   l'email, à recréer dans Cloudflare après la migration).
2. Créer le tunnel : Zero Trust → Networks → Tunnels → *Create a
   tunnel* → type *Docker* → nommer le tunnel (ex. `esic-connect`).
3. Router le sous-domaine choisi vers le tunnel (ex.
   `app.esic-connect.courses`) — Cloudflare propose ce champ à la
   création.
4. Copier le jeton affiché et le renseigner dans `.env` :
   `CLOUDFLARE_TUNNEL_TOKEN=...` (jamais commité, voir `SECRETS.md`).

**Sur le Pi**, avec `CLOUDFLARE_TUNNEL_TOKEN` renseigné :

```bash
docker compose -f compose.prod.yaml up -d cloudflared
docker compose -f compose.prod.yaml logs cloudflared   # "Registered tunnel connection"
```

Reporter l'URL stable choisie à l'étape 3 dans `.env`
(`APP_ALLOWED_ORIGINS`, `APP_ACTIVATION_BASE_URL` **et**
`APP_PASSWORD_RESET_BASE_URL` — deux variables distinctes, toutes les
deux à renseigner, sans quoi le lien de réinitialisation pointe par
défaut vers `localhost`, injoignable) puis recréer le back-end pour
qu'il en tienne compte :

```bash
docker compose -f compose.prod.yaml up -d backend
```

> Repli sans domaine (dépannage ponctuel uniquement) : remplacer dans
> `compose.prod.yaml` `command: tunnel run` / `environment: TUNNEL_TOKEN`
> par `command: tunnel --no-autoupdate --url http://frontend:80`, puis
> relever l'URL aléatoire dans les journaux
> (`docker compose -f compose.prod.yaml logs cloudflared | grep trycloudflare.com`).
> À éviter en usage courant : c'est justement l'URL qui change à chaque
> redémarrage que le tunnel nommé élimine.

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
