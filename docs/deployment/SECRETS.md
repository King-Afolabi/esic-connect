# Secrets — ESIC Connect

**Aucun secret n'est dans le dépôt et aucun ne doit y entrer** (règle
CLAUDE.md, absolue). Tout est fourni par l'environnement, via un fichier
`.env` **non versionné** à la racine, chargé par `docker compose`.

Modèle : `.env.prod.example` (versionné, valeurs vides). Copier puis
remplir :

```bash
cp .env.prod.example .env
chmod 600 .env
```

## Secrets à générer soi-même

| Variable | Rôle | Génération |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | compte root MySQL du conteneur | `openssl rand -base64 24` |
| `MYSQL_PASSWORD` | compte applicatif MySQL (`MYSQL_USER`) | `openssl rand -base64 24` |
| `REDIS_PASSWORD` | `--requirepass` de Redis | `openssl rand -base64 24` |
| `JWT_SECRET` | signature des jetons d'accès (HS256) ; **≥ 32 octets** | `openssl rand -base64 48` |
| `ESIC_DEMO_PASSWORD` | mot de passe commun des 6 comptes de démonstration (profil `demo`) | `openssl rand -base64 18` — **fictif, jamais un vrai mot de passe** |
| `ESIC_DEMO_TOTP_SECRET` | secret TOTP déterministe des comptes `ADMIN` / `SUPER_ADMIN` de démonstration | base32 A–Z/2–7, ex. `openssl rand 20 \| base32 \| tr -d '='` |

## Secrets fournis par un tiers

| Variable | Source |
|---|---|
| `MAIL_USERNAME`, `MAIL_PASSWORD` | fournisseur SMTP (Brevo, Mailgun, SES…). En l'absence, laisser vides : le back-end démarre sans envoi réel (`MAIL_SMTP_AUTH=false`). |
| `CLOUDFLARE_TUNNEL_TOKEN` | **uniquement** pour un tunnel *nommé* (domaine possédé). Le mode « Quick Tunnel » par défaut n'en a pas besoin. |

## Valeurs non secrètes mais à adapter

`APP_ALLOWED_ORIGINS`, `APP_ACTIVATION_BASE_URL` : l'URL publique
effective (voir `RASPBERRY-PI.md` — en Quick Tunnel elle change à chaque
redémarrage de `cloudflared`).

## Règles

- `.env` : permissions `600`, jamais commité (déjà dans `.gitignore`).
- Ne jamais passer un secret en argument de ligne de commande (visible
  dans `ps`, l'historique shell, `docker inspect`). Toujours par `.env`
  ou variable d'environnement du shell.
- `docker compose -f compose.prod.yaml config` ne doit **jamais** faire
  apparaître un secret en clair dans la définition d'un service. Le
  mot de passe Redis est déjà protégé de l'interpolation Compose
  (`$${REDIS_PASSWORD}`, étendu dans le conteneur — voir le commentaire
  du fichier).
- Rotation : changer une valeur dans `.env` puis
  `docker compose -f compose.prod.yaml up -d` recrée les conteneurs
  concernés. Une rotation de `JWT_SECRET` invalide toutes les sessions
  en cours (comportement voulu).
- Pas de gestionnaire de secrets (Vault, SSM…) à ce stade : la cible est
  une Raspberry Pi de recette. `docs/08-securite-rgpd.md` prévoit la
  migration vers un gestionnaire pour une production réelle.
