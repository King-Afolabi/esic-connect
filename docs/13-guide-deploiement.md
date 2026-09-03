# Guide d'installation, de remise à zéro et de déploiement — ESIC Connect

| | |
|---|---|
| Objet | Rendre le dépôt installable, vérifiable et déployable **sans étape manuelle devinée** |
| Version | 1.0 — 3 septembre 2026 |
| Statut du déploiement | **`NOT_PERFORMED`** — aucune instance n'est déployée, aucune URL n'existe |
| Sources | `audit-report.md` (audit QA du 3 septembre 2026), `docs/CURRENT-STATE.md`, `compose.yaml`, `.github/workflows/**` |

> **Ce que ce document ne fait pas.** Il ne déclare aucun déploiement
> réalisé. Il décrit ce qui est **outillé et reproductible aujourd'hui**
> (installation locale, remise à zéro de la base, vérification complète)
> et énumère, sans les masquer, les **verrous qui restent** avant une mise
> en service réelle (§6). Tant qu'aucune URL n'est en service, le statut
> reste `NOT_PERFORMED`.

---

## 1. Prérequis

| Outil | Version | Vérification |
|---|---|---|
| Java (JDK) | **21** (Temurin / OpenJDK) | `java -version` — une autre version majeure fait échouer le build |
| Maven | wrapper `./mvnw` fourni | aucune installation requise |
| Node.js | **24** | `node --version` |
| npm | **11.6.2** | `npm --version` — une version différente régénère un arbre incompatible avec `frontend/package-lock.json` |
| Docker + Compose | Docker ≥ 24 | `docker compose version` |

Si le JDK par défaut n'est pas le 21 :

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"  # macOS / Homebrew — adapter
export PATH="$JAVA_HOME/bin:$PATH"
```

---

## 2. Installation locale

```bash
git clone https://github.com/King-Afolabi/esic-connect.git
cd esic-connect
cp .env.example .env
```

Renseigner `.env` — le fichier n'est **jamais** versionné :

| Variable | Obligatoire | Contrainte |
|---|---|---|
| `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `REDIS_PASSWORD` | oui | remplacer les valeurs `change-*` |
| `JWT_SECRET` | oui | **≥ 32 octets** — le back-end refuse de démarrer sinon (`openssl rand -base64 48`) |
| `ESIC_DEMO_PASSWORD` | oui pour le profil `demo` | **≥ 12 caractères**. Lu aussi par la suite e2e |
| `MYSQL_TEST_DATABASE` | recommandé | `esic_test`. Garantit qu'un `./mvnw test` n'écrit jamais dans la base applicative — cause probable du finding F-ENV-1 |
| `JUSTIFICATION_STORAGE_PATH` | oui hors Docker | le défaut `/data/uploads/justifications` n'est pas inscriptible sur macOS / Linux |

Puis :

```bash
docker compose up -d       # mysql, redis, mailpit, mosquitto
docker compose ps          # mysql / redis / mailpit doivent être "healthy"
```

---

## 3. Remise à zéro de la base — finding F-ENV-1

L'audit QA du 3 septembre 2026 a relevé **27 105 comptes** dans la base
applicative `esic_connect`, portant les motifs de nommage des fixtures de
la suite back-end : un `./mvnw test` y a écrit. Aucune démonstration
n'est crédible dans cet état, et un nettoyage ligne à ligne n'est pas
fiable (les fixtures touchent des dizaines de tables liées).

**Diagnostic — lecture seule, aucun écrit :**

```bash
./scripts/db-doctor.sh                    # base de .env
./scripts/db-doctor.sh esic_connect_demo  # une autre base
```

Codes de sortie : `0` saine · `1` erreur d'exécution · `2` **polluée**.

**Remise à zéro — sauvegarde, recréation, migration, contrôle :**

```bash
./scripts/db-reset.sh esic_connect        # confirmation demandée
./scripts/db-reset.sh esic_connect --yes  # non interactif
```

Le script, dans l'ordre : sauvegarde compressée dans
`build/db-backups/`, `DROP` puis `CREATE` en `utf8mb4_0900_ai_ci`,
restitution des droits à `MYSQL_USER`, démarrage du back-end pour rejouer
Flyway `V1 → V16`, puis contrôle de la version de schéma atteinte, de
l'absence de migration en échec et des volumes finaux.

Options utiles : `--profile local|demo`, `--keep-running` (laisse le
back-end démarré), `--no-migrate` (recrée la base vide sans démarrer le
back-end), `--no-backup`.

La base `MYSQL_TEST_DATABASE` est **refusée** par le script : elle est
gérée par la suite de tests elle-même.

---

## 4. Démarrage

### 4.1 Back-end (port 8080)

```bash
cd backend
set -a && source ../.env && set +a
mkdir -p ../build/demo-data/justifications
export JUSTIFICATION_STORAGE_PATH="$(cd ../build/demo-data/justifications && pwd)"

SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
```

Pour une démonstration isolée de la base applicative :

```bash
MYSQL_DATABASE=esic_connect_demo SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
```

Santé : http://localhost:8080/actuator/health ·
Swagger UI : http://localhost:8080/swagger-ui.html

### 4.2 Front-end (port 4200)

```bash
cd frontend
npm ci
npm start          # ng serve, proxy /api → :8080
```

### 4.3 Jeu de démonstration

```bash
bash scripts/seed-demo.sh   # référentiel, 2 profils apprenants, 1 séance PLANNED
```

---

## 5. Vérification

### 5.1 Tout en une commande

```bash
./scripts/verify-all.sh            # infra, back, front, types e2e, scripts, base
./scripts/verify-all.sh --quick    # sans la suite back-end
```

### 5.2 Recette end-to-end navigateur

Exige la pile **complète démarrée** (§4.1 et §4.2) :

```bash
set -a && source .env && set +a     # ESIC_DEMO_PASSWORD doit être exporté
npm ci
npx playwright install --with-deps chromium
npm run test:e2e                    # 149 tests, ~18-20 min
npm run test:e2e:report             # rapport HTML
```

`ESIC_DEMO_PASSWORD` est **obligatoire** : la suite ne porte aucun mot de
passe de repli (`tests/support/accounts.ts`).

En intégration continue, le workflow `.github/workflows/e2e.yml` monte la
pile entière et exécute la même suite — **déclenchement manuel**
(`workflow_dispatch`) assumé, pour ne pas ajouter 20 minutes à chaque PR.

### 5.3 Intégration continue permanente

| Workflow | Déclencheur | Rôle |
|---|---|---|
| `backend-ci.yml` | push / PR sur `main` | `./mvnw test` sur MySQL + Redis éphémères |
| `frontend-ci.yml` | push / PR touchant `frontend/**` | `npm audit`, `lint`, `test`, `build` |
| `dependency-review.yml` | PR sur `main` | échec si une dépendance introduit une CVE ≥ `high` |
| `e2e.yml` | **manuel** | recette e2e navigateur sur pile complète |

---

## 6. Ce qui manque avant une mise en service réelle

Aucun de ces points n'est implémenté aujourd'hui. Ils sont listés pour
qu'aucune décision de mise en service ne soit prise sans les connaître.

| Verrou | État | Effet si ignoré |
|---|---|---|
| **HTTPS / TLS** | `NOT_IMPLEMENTED` | jetons JWT et mots de passe en clair sur le réseau. Bloquant absolu |
| **Rate-limiting `/auth/login`** | `NOT_IMPLEMENTED` (`docs/07` §5) | attaque par force brute non freinée |
| **Persistance de session** | `NOT_IMPLEMENTED` (finding F-ENV-2) | tout rechargement de page déconnecte l'utilisateur |
| **Sauvegarde / restauration testée** | `NOT_PERFORMED` | `scripts/db-reset.sh` produit un dump, mais **aucune restauration n'a jamais été rejouée** |
| **Antivirus sur les pièces jointes** | `NOT_IMPLEMENTED` (`DEC-G1-E-ANTIVIRUS`) | ne jamais écrire « garanti sans malware » |
| **Rétention RGPD des pièces `DELETED`** | `À_DÉFINIR` (`R-G1-30`) | politique à arrêter avant tout usage sur données réelles |
| **Stockage des pièces jointes** | système de fichiers local | non persistant sur un hébergement éphémère |
| **Supervision** | `/actuator/health` seul | ni métriques, ni logs structurés, ni alerte |
| **Secrets** | `.env` local | aucun coffre ; à remplacer par le gestionnaire de secrets de la cible |
| **Notifications** | audience formateur, sans reprise (`G1-D-OUTBOX`) | une panne du writer perd la notification |

### 6.1 Cible de déploiement

**Non arrêtée à ce jour.** Le dépôt est rendu déployable (image
d'infrastructure `compose.yaml`, configuration entièrement par variables
d'environnement, build de production front sous budget, migrations
Flyway idempotentes), mais aucune plateforme n'a été choisie ni testée.

Les trois options envisagées, sans recommandation à ce stade :

| Option | Ce qu'elle exige en plus |
|---|---|
| VPS Linux + Docker | reverse proxy TLS, sauvegardes planifiées, supervision |
| Raspberry Pi 4 en réseau local | ARM64 pour toutes les images, TLS interne, volumétrie limitée |
| Plateforme managée (Railway / Render / Fly) | base Postgres ou MySQL managée, stockage objet pour les pièces jointes |

Ce choix doit être tranché **avant** d'écrire un `compose.prod.yaml` : la
forme du fichier dépend entièrement de la cible.

---

## 7. Dépannage

Voir le tableau de dépannage du `README.md`. Deux symptômes propres à ce
guide :

| Symptôme | Cause / solution |
|---|---|
| `db-reset.sh` : « le back-end n'a pas démarré » | 40 dernières lignes du journal affichées ; le plus souvent `JWT_SECRET` < 32 octets ou `ESIC_DEMO_PASSWORD` absent |
| `db-doctor.sh` sort en code 2 | comportement **attendu** sur une base polluée — c'est le diagnostic, pas une panne |
