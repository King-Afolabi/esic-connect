# Rollback et sauvegarde — ESIC Connect (Raspberry Pi)

Toutes les commandes se lancent depuis la racine du dépôt sur la Pi,
avec `compose.prod.yaml`.

## Principe

Deux choses peuvent devoir revenir en arrière, **indépendamment** :

1. **le code** (images backend / frontend) — revenir à un commit / tag ;
2. **les données** (base MySQL, justificatifs) — restaurer une
   sauvegarde.

Une migration Flyway appliquée n'est **jamais** annulée en place. Un
retour de code qui attend un schéma antérieur exige la restauration
d'une sauvegarde prise **avant** la migration. C'est pourquoi on
sauvegarde toujours avant `up -d --build`.

---

## Sauvegarde (avant chaque déploiement)

```bash
ts=$(date -u +%Y%m%dT%H%M%SZ)
mkdir -p backups/$ts

# 1. Base MySQL (dump cohérent, transactionnel)
docker compose -f compose.prod.yaml exec -T mysql \
  sh -c 'exec mysqldump --single-transaction --routines --triggers \
         -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  | gzip > backups/$ts/mysql.sql.gz

# 2. Justificatifs (volume nommé)
docker run --rm \
  -v esic-connect_justification-data:/data:ro \
  -v "$PWD/backups/$ts":/out \
  alpine tar czf /out/justifications.tar.gz -C /data .

# 3. Configuration (SANS le .env en clair — noter seulement le commit)
git rev-parse HEAD > backups/$ts/git-commit.txt
cp .env.prod.example backups/$ts/            # modèle, pas le vrai .env

ls -la backups/$ts
```

Conserver au moins les **3 dernières** sauvegardes, hors de la Pi si
possible (`scp`, disque USB). Une sauvegarde jamais restaurée n'est pas
une sauvegarde : voir « Test de restauration » plus bas.

---

## Rollback du code seul

Quand la nouvelle version démarre mais se comporte mal, **sans**
migration de schéma entre les deux :

```bash
git fetch --tags
git checkout <tag-ou-commit-precedent>
docker compose -f compose.prod.yaml up -d --build
docker compose -f compose.prod.yaml ps        # tout doit repasser healthy
```

`git checkout` ne touche pas `.env` ni les volumes.

---

## Rollback complet (code + données)

Quand la nouvelle version a migré le schéma ou corrompu des données :

```bash
# 1. Arrêt applicatif (on garde mysql/redis debout)
docker compose -f compose.prod.yaml stop backend frontend cloudflared

# 2. Retour du code
git checkout <tag-ou-commit-precedent>

# 3. Restauration de la base depuis la sauvegarde correspondante
gunzip -c backups/<ts>/mysql.sql.gz | \
  docker compose -f compose.prod.yaml exec -T mysql \
  sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'

# 4. Restauration des justificatifs
docker run --rm \
  -v esic-connect_justification-data:/data \
  -v "$PWD/backups/<ts>":/in \
  alpine sh -c 'rm -rf /data/* && tar xzf /in/justifications.tar.gz -C /data'

# 5. Redémarrage + contrôle
docker compose -f compose.prod.yaml up -d --build
docker compose -f compose.prod.yaml exec backend \
  wget -qO- http://localhost:8080/actuator/health
```

Flyway au démarrage voit un schéma déjà à la version de la sauvegarde et
ne rejoue rien. Si `flyway_schema_history` contient une migration que le
code restauré ne connaît pas, Flyway échoue **volontairement** : la
sauvegarde restaurée n'est pas assez ancienne, en prendre une antérieure
à la migration fautive.

---

## Test de restauration (obligatoire avant la première mise en service)

À faire une fois, sur une base jetable, et à consigner
(`docs/CURRENT-STATE.md` — actuellement `NOT_PERFORMED`) :

```bash
# base de contrôle, séparée
docker compose -f compose.prod.yaml exec -T mysql \
  sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "CREATE DATABASE esic_restore_check"'
gunzip -c backups/<ts>/mysql.sql.gz | \
  docker compose -f compose.prod.yaml exec -T mysql \
  sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" esic_restore_check'
docker compose -f compose.prod.yaml exec -T mysql \
  sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" esic_restore_check \
         -e "SELECT COUNT(*) FROM flyway_schema_history; SHOW TABLES;"'
docker compose -f compose.prod.yaml exec -T mysql \
  sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE esic_restore_check"'
```

Résultat attendu : le nombre de migrations et la liste des tables
correspondent à la version d'où vient la sauvegarde. **Noter la date,
la commande, le résultat.**
