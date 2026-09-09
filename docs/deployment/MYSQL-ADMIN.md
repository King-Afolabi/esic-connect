# Administration visuelle de MySQL — via tunnel SSH uniquement

> **Règle absolue.** MySQL et tout client web de base (Adminer, phpMyAdmin)
> ne sont **jamais** exposés publiquement : ni sur `0.0.0.0`, ni dans le
> tunnel Cloudflare, ni sur le réseau local. Le seul chemin d'accès est un
> **tunnel SSH** depuis un poste autorisé.

## Ce qui est fourni

- `compose.admin.yaml` (racine du dépôt) — surcouche facultative ajoutant
  un service **Adminer** (`adminer:5`, client MySQL mono-fichier) :
  - profil Compose `admin-tools` → ne démarre **pas** avec la production
    ni au redémarrage de la Pi ;
  - `restart: "no"` → ne revient jamais tout seul ;
  - port publié sur **`127.0.0.1:8081`** de la Pi uniquement ;
  - rattaché au seul `data-network` → **invisible de `cloudflared`** ;
  - **aucun identifiant** dans le fichier : on les saisit sur l'écran de
    connexion d'Adminer.

## Démarrer l'outil (sur la Pi)

```bash
ssh <utilisateur>@<hote-pi>
cd ~/esic-connect

docker compose -f compose.prod.yaml -f compose.admin.yaml \
  --profile admin-tools up -d adminer

# Vérifier
docker compose -f compose.prod.yaml -f compose.admin.yaml ps
docker port esic-connect-adminer        # doit afficher 127.0.0.1:8081 -> 8080
```

## Ouvrir le tunnel SSH (depuis le poste d'administration)

```bash
ssh -L 8081:127.0.0.1:8081 <utilisateur>@<hote-pi>
```

Laisser cette session ouverte, puis, dans un navigateur **du poste d'administration** :

```
http://127.0.0.1:8081
```

Écran de connexion Adminer :

| Champ | Valeur |
|---|---|
| Système | MySQL |
| Serveur | `mysql` (pré-rempli) |
| Utilisateur | *le compte d'administration limité (voir plus bas), sinon `${MYSQL_USER}` de `.env`* |
| Mot de passe | *celui du compte utilisé* |
| Base de données | `${MYSQL_DATABASE}` de `.env` |

> Les valeurs `${MYSQL_*}` sont dans le `.env` de la Pi. **Ne pas les
> recopier ici** ni ailleurs : `ssh <utilisateur>@<hote-pi> 'grep MYSQL_ ~/esic-connect/.env'`
> sur la Pi elle-même si besoin, jamais dans un fichier versionné, un
> ticket ou un message.

## Arrêter l'outil quand l'administration est finie

```bash
# Sur la Pi
docker compose -f compose.prod.yaml -f compose.admin.yaml \
  --profile admin-tools down adminer
```

(`stop adminer` au lieu de `down adminer` si on prévoit de le relancer
bientôt.) Fermer aussi la session `ssh -L` côté poste d'administration.

## Compte SQL d'administration à privilèges limités (recommandé)

Ne pas se connecter en `root`. Créer, une fois, un compte dédié à la
lecture/écriture de la seule base applicative, sans privilèges
d'administration du serveur :

```bash
ssh <utilisateur>@<hote-pi>
cd ~/esic-connect
docker compose -f compose.prod.yaml exec mysql \
  mysql -uroot -p"$(: ne pas inliner le mot de passe)" # voir ci-dessous
```

En pratique, ouvrir un shell MySQL interactif (le mot de passe root est
demandé, jamais affiché) :

```bash
docker compose -f compose.prod.yaml exec mysql sh -c \
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'
```

puis :

```sql
-- Adapter <DB> à MYSQL_DATABASE. Choisir un mot de passe fort, hors dépôt.
CREATE USER IF NOT EXISTS 'esic_dba'@'%' IDENTIFIED BY '<mot de passe fort>';
GRANT SELECT, INSERT, UPDATE, DELETE, SHOW VIEW ON `<DB>`.* TO 'esic_dba'@'%';
FLUSH PRIVILEGES;
```

Ce compte suffit à inspecter et corriger ponctuellement des données ; il
ne peut ni changer le schéma (réservé à Flyway), ni gérer d'autres bases,
ni administrer le serveur. Le supprimer quand il n'est plus utile :
`DROP USER 'esic_dba'@'%';`.

## Ce qu'il ne faut pas faire

- ❌ publier le port sur `0.0.0.0` ou une IP de LAN ;
- ❌ ajouter Adminer/phpMyAdmin à `compose.prod.yaml` ou au routage
  `cloudflared` ;
- ❌ laisser le conteneur `adminer` tourner en permanence ;
- ❌ écrire un mot de passe MySQL dans un fichier versionné, un ticket, un
  message ou cette documentation ;
- ❌ se connecter en `root` pour de l'inspection courante ;
- ❌ exécuter `DROP DATABASE`, `TRUNCATE`, ni supprimer le volume
  `mysql-data`.
