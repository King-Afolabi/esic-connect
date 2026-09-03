# ESIC Connect

Preuve de concept d'une plateforme web de **planification pédagogique,
d'émargement intelligent et de suivi de l'assiduité** pour l'ESIC
(certification RNCP 39394). Monolithe modulaire Spring Boot + front-end
Angular, infrastructure locale conteneurisée.

Ce dépôt est un **prototype**. Il implémente un sous-ensemble cohérent du
cahier des charges et **documente explicitement** ce qui n'est pas
réalisé (voir « Périmètre non livré » ci-dessous).

---

## Périmètre livré

Parcours implémenté et rejoué **au niveau API** de bout en bout par une
recette d'intégration automatisée (`PriorityPathRecetteIntegrationTests`,
appels HTTP réels) :

```text
Administration et référentiels (site / salle, année, formation, classe)
  → Import CSV contrôlé des apprenants (simulation → confirmation)
  → Activation d'un compte apprenant par invitation
  → Import CSV du planning → simulation (0 séance créée)
  → Publication versionnée → génération des séances
  → Ouverture de la séance par le formateur
  → Émargement de l'apprenant (QR opaque + code court, Redis)
  → Suivi et correction motivée / auditée des présences
  → Rapport d'assiduité (demi-journées) + export CSV
  → Justificatif avec pièce jointe → acceptation / refus
  → Notifications métier (formateurs, propriétaire du justificatif)
  → Tableaux de bord selon le rôle
```

**Mise à jour du 3 septembre 2026 — audit QA indépendant.** Ce parcours
est désormais rejoué **dans un vrai navigateur** de bout en bout : une
suite Playwright de **149 tests** (`tests/`) pilote Chromium contre
l'application réellement démarrée, avec deux apprenants réels, du QR à
l'historique d'assiduité. Rapport complet : [`audit-report.md`](audit-report.md),
résumé : [`TESTING-SUMMARY.txt`](TESTING-SUMMARY.txt), captures :
`captures/`. La décision `DEC-G1-011` (« pas de suite e2e navigateur ») est
donc **révisée** : la suite est conservée en **complément** de la recette
d'intégration API, pas en remplacement.

Ce qui n'a **pas** changé : **aucune manipulation humaine** n'est
consignée dans le dépôt — un navigateur piloté par un script n'est pas une
démonstration manuelle. Statut global du lot G1 :
`IMPLEMENTED_AND_TESTED (API + e2e navigateur) / PARTIAL`, démonstration
manuelle `NOT_PERFORMED`.

Lecture des statuts employés dans toute la documentation :

| Statut | Signification |
|---|---|
| `IMPLEMENTED_AND_TESTED` | code livré **et** couvert par des tests automatisés passants |
| `IMPLEMENTED_NOT_MANUALLY_DEMONSTRATED` | livré et testé, **jamais** manipulé manuellement de bout en bout |
| `PARTIAL` | une partie seulement de l'exigence est livrée — jamais à présenter comme complète |
| `NOT_IMPLEMENTED` | aucun code ; limite explicitement assumée |
| `NOT_PERFORMED` | action jamais exécutée (ex. démonstration manuelle, déploiement) |
| `HORS_PÉRIMÈTRE_ASSUMÉ` | exclusion décidée et documentée pour cette livraison |

Détail par capacité et statut : `docs/CURRENT-STATE.md`.
Audit vérifiable et matrices d'exigences :
`docs/reports/PROJECT_FINAL_AUDIT.md` (checkpoint F1, **antérieur à G1**)
et `docs/reports/G1_REQUIREMENTS_TRACEABILITY.md` (matrice G1).

Autres briques livrées : authentification JWT, administration des comptes
et des rôles, invitation / activation par email (Mailpit), référentiels
organisationnel et académique, périmètre pédagogique, inscriptions
historisées, rythmes d'alternance, **import → publication versionnée du
planning** (module `planning`, G1-B), **justificatif avec pièce jointe**
(G1-E — dépôt / téléchargement sécurisés, réconciliation ; antivirus et
balayage des orphelins non implémentés), **tableaux de bord par rôle**
(module `dashboard`, G1-F ; contexte de rôle multi-rôle vérifié côté
serveur ; infrastructure et cartes `STUDENT` / `TEACHER`
`IMPLEMENTED_AND_TESTED`, mais bloc **global `PARTIAL`** — cartes
`PEDAGOGICAL_MANAGER` et `ADMINISTRATION` incomplètes, voir
`docs/CURRENT-STATE.md`), piste d'audit, **centre de notifications métier persistantes**
(G1-D /
G1-D.1 — planning publié / séance annulée / remplaçant affecté / remplacement
terminé → notifications after-commit pour les formateurs, idempotentes,
isolées par destinataire ; `/api/v1/me/notifications` + cloche + centre
Angular. Livraison « au mieux » après commit, sans reprise ;
`EF-NOTIF-002` / `RG-033` = `PARTIAL`).

## Périmètre non livré (décision de finalisation — assumée)

> **Mise à jour G1 (1er septembre 2026, fusionné sur `main` par la
> PR #40 — commit `d3450e6`).** Le lot produit G1 a **livré** deux des éléments
> ci-dessous : le **référentiel organisationnel Angular** (G1-A) et
> l'**import → simulation → publication versionnée du planning et la
> création des séances associées** (G1-B, module `planning` réel,
> migrations V12/V13, endpoints et écrans). `EF-PLAN-001..005`,
> `EF-PLAN-007`, `EF-SES-001`, `RG-016`, `RG-030..RG-035`, `AC-007`,
> `AC-008` sont désormais `IMPLEMENTED_AND_TESTED`. Détail :
> `docs/reports/G1_IMPLEMENTATION_PROGRESS.md` et
> `docs/CURRENT-STATE.md` (§ « Mise à jour G1 »). La liste ci-dessous
> reflète l'état **avant G1** ; les items barrés sont livrés.

Pour cette livraison de prototype, les éléments suivants **ne sont pas
implémentés** et ne doivent jamais être présentés comme livrés :

- ~~**Import du planning → prévisualisation → publication → versionnement →
  création automatique des séances depuis un planning.**~~ **Livré au bloc
  G1-B.** Seul `EF-PLAN-006` (création manuelle plein calendrier) reste
  `HORS_PÉRIMÈTRE_ASSUMÉ`.
- Séances : ~~annulation, affectation d'un remplaçant~~ — **livrés au
  bloc G1-C** (annulation `POST /sessions/{id}/cancel` ; remplacements
  `teacher_substitution` + `GET/POST …/substitutions` ; séance
  `CANCELLED` consultable, remplaçant actif visible et gestionnaire
  pendant sa période, audit `AFTER_COMMIT` — checkpoint G1-C.3). `PATCH`
  d'une séance manuelle `PLANNED` : non livré (non requis).
- QR fixe de salle + contrôle réseau CIDR (référentiel présent, non
  consommé) ; scan caméra mobile (code court uniquement).
- WebAuthn / passkeys, MFA TOTP, anti-bot (Turnstile).
- Réclamations / messagerie, départ anticipé, import Excel `.xlsx` /
  multifeuille.
- Justificatif **avec pièce jointe** : **livré (G1-E)** — dépôt multipart
  propriétaire, compensation base/fichier, réconciliation des
  `PENDING_STORAGE`, téléchargement `Content-Disposition: attachment` +
  `nosniff` (propriétaire + examinateur périmétré). **Non livré** :
  antivirus (`NOT_IMPLEMENTED`), balayage des fichiers orphelins
  (`NOT_IMPLEMENTED` — la réconciliation ne traite que les
  `PENDING_STORAGE`), remplacement direct d'une pièce, rétention `DELETED`
  (`À_DÉFINIR`).
- Service IA (FastAPI, mapping de colonnes, score d'anomalie).
- IoT / MQTT / Raspberry Pi (broker Mosquitto démarré, **aucun code**).
- Notifications : le centre in-app persistant est livré (G1-D / G1-D.1)
  pour l'audience **formateur** (principal + remplaçants `ACTIVE` +
  remplaçant tout juste terminé) ; notifications aux **apprenants** et
  **responsables pédagogiques** (dette G1-D-AUDIENCE), **garantie de
  livraison / reprise** (best effort après commit — dette G1-D-OUTBOX),
  préférences par type, email métier, push PWA, purge / rétention
  (`À_DÉFINIR`) — non livrés. `EF-NOTIF-002` / `RG-033` = `PARTIAL`.
- PWA installable / offline.
- Mot de passe oublié, `/auth/logout` + révocation de session (JWT
  stateless assumé).
- Déploiement cloud / staging / HTTPS / haute disponibilité.

Liste complète et justifications : `docs/reports/PROJECT_FINAL_AUDIT.md`
§0.3 et §7.4.

---

## Architecture réelle

- **Back-end** : Java 21, Spring Boot 3.5, Maven, **Spring Modulith 1.4**.
  Monolithe modulaire — **14 modules** :
  `identity`, `organization`, `academic`, `enrollment`, `alternation`,
  `planning`, `coursesession`, `attendance`, `studentimport`,
  `notification`, `dashboard`, `audit`, `bootstrap`, `shared`. Frontières
  vérifiées par `ModularityTests`.
- **Base** : MySQL 8, schéma géré **uniquement** par Flyway (`V1` → `V16`,
  schéma en version 16), `ddl-auto: validate`.
- **Cache / données temporaires** : Redis 7 — consommé **uniquement**
  pour les jetons d'émargement.
- **Front-end** : Angular 21.2 (standalone, zoneless, signaux, lazy
  routes), Angular Material. JWT et contexte de rôle **en mémoire seule**.
- **Email** : SMTP local Mailpit.
- **Sécurité** : Spring Security, JWT HS256 (signature + `exp` + `iss`),
  `@EnableMethodSecurity` + `@PreAuthorize` sur toutes les routes non
  publiques, contrôle de périmètre côté serveur.

Modules `claim`, `reporting`, `ai`, `iot` décrits dans
`docs/03-architecture.md` §7 = **architecture cible non implémentée**
(`planning` et `dashboard` sont désormais réels — G1-B, G1-F).

Architecture cible cloud (AWS) : documentée, **non déployée**
(`docs/03-architecture.md` §37).

---

## Prérequis

Versions réellement utilisées pour les vérifications :

| Outil | Version | Note |
|---|---|---|
| Java (JDK) | **21** (Temurin / OpenJDK) | le build échoue avec une autre version majeure |
| Maven | wrapper `./mvnw` fourni | pas d'installation Maven requise |
| Node.js | **24** (testé 24.13.0) | |
| npm | **11** (testé 11.6.2) | |
| Docker + Docker Compose | Docker ≥ 24 | pour MySQL / Redis / Mailpit / Mosquitto |

Si votre `java -version` par défaut n'est pas 21 :

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"  # macOS/Homebrew — adapter
export PATH="$JAVA_HOME/bin:$PATH"
```

---

## Installation

```bash
git clone <url-du-dépôt>
cd projet_final
cp .env.example .env
```

Éditez ensuite `.env` (fichier **non versionné**, jamais commité) :

| Variable | Obligatoire | Valeur |
|---|---|---|
| `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `REDIS_PASSWORD` | oui | valeurs locales de votre choix (remplacer les `change-*`) |
| `JWT_SECRET` | oui | chaîne aléatoire **≥ 32 octets** ; le back-end refuse de démarrer sinon |
| `ESIC_DEMO_PASSWORD` | oui (profil `demo` seulement) | mot de passe des comptes fictifs, **≥ 12 caractères**. Volontairement **vide** dans `.env.example` |
| `JUSTIFICATION_STORAGE_PATH` | **oui hors Docker** | répertoire d'écriture des pièces jointes de justificatifs (G1-E). Défaut `${UPLOAD_DIRECTORY:-/data/uploads}/justifications` — **inaccessible en écriture sur un macOS / Linux classique** : voir « Lancement » §2 |
| `MYSQL_TEST_DATABASE` | recommandé | base de la **suite de tests** (défaut `esic_test`). La renseigner explicitement évite qu'un `./mvnw test` écrive dans la base applicative — cause probable du finding F-ENV-1 |
| `APP_ALLOWED_ORIGINS` | non | origine(s) autorisée(s) du front (défaut `http://localhost:4200`) |
| `JWT_ACCESS_TOKEN_TTL_SECONDS`, `INVITATION_TOKEN_TTL`, `ATTENDANCE_TOKEN_TTL`, `JUSTIFICATION_MAX_FILE_BYTES`, `MAIL_HOST` / `MAIL_PORT`, `MULTIPART_MAX_*` | non | valeurs par défaut sûres dans `application.yml` ; `.env.example` les documente |

`.env.example` ne contient que des placeholders : `JWT_SECRET=` et
`ESIC_DEMO_PASSWORD=` y sont **délibérément vides**, les mots de passe
d'infrastructure valent `change-*`. **Ne jamais y mettre de secret réel**
ni committer `.env`.

Génération rapide d'un secret :

```bash
openssl rand -base64 48
```

---

## Lancement

### 1. Infrastructure (Docker)

```bash
docker compose config      # valider la syntaxe et les variables
docker compose up -d       # mysql, redis, mailpit, mosquitto
docker compose ps          # mysql / redis / mailpit doivent être "healthy"
```

Interface Mailpit : http://localhost:8025

### 2. Back-end (port 8080)

```bash
cd backend
set -a && source ../.env && set +a       # exporte les variables MySQL / Redis / JWT

# Pièces jointes des justificatifs (G1-E) : le défaut est /data/uploads/
# justifications, chemin NON inscriptible hors conteneur. Pointer un
# répertoire local, sinon tout dépôt de pièce jointe échoue en 503.
mkdir -p ../build/demo-data/justifications
export JUSTIFICATION_STORAGE_PATH="$(cd ../build/demo-data/justifications && pwd)"

SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
```

Le profil `demo` amorce **6 comptes fictifs** (voir plus bas) **sans
désactiver la sécurité** ; il exige `ESIC_DEMO_PASSWORD`. Sans ce profil
(`local`), aucun compte n'est créé.

Pour une démonstration **isolée de la base applicative**, lancer le
back-end sur une base dédiée :

```bash
MYSQL_DATABASE=esic_connect_demo SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
```

La base est créée vide puis peuplée par Flyway (`V1 → V16`). La suite de
tests, elle, lit `MYSQL_TEST_DATABASE` (défaut `esic_test`) et **n'écrit
jamais** dans `MYSQL_DATABASE` — voir « Bases de données » ci-dessous.

Aucune autre variable n'a besoin d'être exportée à la main : `set -a &&
source ../.env` couvre MySQL, Redis, JWT, Mailpit et CORS.

OpenAPI au runtime : http://localhost:8080/v3/api-docs —
Swagger UI : http://localhost:8080/swagger-ui.html —
Santé : http://localhost:8080/actuator/health

Aucun `openapi.json` n'est versionné (générer l'artefact au build
imposerait un plugin qui démarre le contexte Spring). Pour un export à la
demande, back-end démarré : `bash scripts/dump-openapi.sh` →
`docs/openapi.json` (fichier ignoré par Git).

### 3. Front-end (port 4200)

```bash
cd frontend
npm ci
npm start          # ng serve — http://localhost:4200
```

`ng serve` proxifie `/api` vers `http://localhost:8080`
(`proxy.conf.json`) : aucune configuration CORS n'est nécessaire en
local.

### 4. Jeu de données de démonstration (optionnel)

Après le démarrage du back-end en profil `demo` :

```bash
bash scripts/seed-demo.sh   # crée via l'API : site, formation, classe, 2 profils, 2 inscriptions, 1 séance PLANNED
```

---

## Tests et vérifications

```bash
# Back-end (nécessite l'infra Docker + les variables .env)
cd backend
set -a && source ../.env && set +a
./mvnw clean test            # BUILD SUCCESS attendu — voir docs/CURRENT-STATE.md pour le total de référence

# Front-end
cd frontend
npm ci
npm test -- --watch=false    # Vitest + jsdom
npm run lint                 # angular-eslint
npm run build                # build de production (budget initial 500 kB)
npm audit --audit-level=high # doit passer : 0 haute / 0 critique

# Non-régression des scripts de démonstration (faux curl déterministe)
bash scripts/test/test-seed-demo.sh
bash scripts/test/test-prepare-planning-demo.sh

# Tout ce qui précède, en une seule commande, arrêt au premier échec
./scripts/verify-all.sh
./scripts/verify-all.sh --quick   # sans la suite back-end (la plus longue)
```

### Recette end-to-end navigateur (Playwright)

Exige la pile **complète démarrée** (infra + back-end profil `demo` +
`ng serve`) :

```bash
set -a && source .env && set +a     # ESIC_DEMO_PASSWORD obligatoire
npm ci
npx playwright install --with-deps chromium
npm run test:e2e                    # 149 tests, chromium, ~18-20 min
npm run test:e2e:report             # rapport HTML
```

La suite ne porte **aucun mot de passe de repli** : sans
`ESIC_DEMO_PASSWORD` exporté, elle s'arrête avec un message explicite.
Détail de la portée, de ce qui est volontairement **non** testé et des
limites de fiabilité de l'environnement : `audit-report.md` §0, §2 et §4.

### Bases de données — isolation test / démonstration

Trois bases distinctes, jamais confondues :

| Base | Qui l'utilise | Variable |
|---|---|---|
| `esic_connect` | runtime `local` (base applicative) | `MYSQL_DATABASE` |
| `esic_connect_demo` | runtime `demo` (démonstration) | `MYSQL_DATABASE=esic_connect_demo` |
| `esic_test` | **suite de tests** back-end (profil `test`) | `MYSQL_TEST_DATABASE` (défaut `esic_test`) |

Le profil `test` lit **`MYSQL_TEST_DATABASE`**, jamais `MYSQL_DATABASE` :
un `./mvnw test` lancé pendant une démonstration n'écrit donc **pas**
dans la base de démonstration (les tests créent des milliers de comptes
et tronquent des tables). En CI, `.github/workflows/backend-ci.yml`
impose explicitement `MYSQL_TEST_DATABASE: esic_connect_ci`, base
éphémère jetée à chaque run.

Créer la base de démonstration (une seule fois) :

```bash
docker compose exec mysql mysql -uroot -p \
  -e "CREATE DATABASE IF NOT EXISTS esic_connect_demo
      CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
      GRANT ALL PRIVILEGES ON esic_connect_demo.* TO '<MYSQL_USER>'@'%';"
```

Flyway applique ensuite `V1 → V16` au premier démarrage du profil `demo`.

### Diagnostic et remise à zéro d'une base

```bash
./scripts/db-doctor.sh                     # lecture seule : version de schéma, volumes, pollution
./scripts/db-reset.sh esic_connect         # sauvegarde → DROP/CREATE → Flyway → contrôle
./scripts/db-reset.sh esic_connect --yes   # non interactif
```

`db-doctor.sh` sort en code **2** quand la base contient des comptes issus
des fixtures de test. C'est exactement l'état dans lequel l'audit QA du
3 septembre 2026 a trouvé `esic_connect` : **27 105 comptes**
(finding F-ENV-1). Détail complet : `docs/13-guide-deploiement.md` §3.

Totaux de référence mesurés sur ce dépôt (HEAD `d3450e6`, 2 septembre 2026) :

| Suite | Résultat |
|---|---|
| `./mvnw clean test` | **811 tests, 0 échec, 0 erreur, 0 ignoré** — 96 classes, `ModularityTests` vert (14 modules), schéma Flyway V16 |
| `npm test -- --watch=false` | **71 fichiers / 602 tests / 0 échec** (Vitest + jsdom) |
| `npm run lint` | « All files pass linting » |
| `npm run build` | initial **484,52 kB** brut — 0 alerte de budget |
| `npm audit --audit-level=high` | **passe** (0 haute, 0 critique) — 1 vulnérabilité **modérée** sur `qs`, tirée par `@angular/cli` (outillage de développement, absent du bundle servi) ; suivie dans l'issue de migrations majeures |

Détail et conditions de reproduction : `docs/CURRENT-STATE.md` →
« Résultats de tests » ; `docs/reports/G1_FINAL_REPORT.md` §11.
Les tests marqués `perf` sont exclus du run par défaut
(`./mvnw test -Pperf` pour les exécuter).

### Intégration continue (GitHub Actions)

| Workflow | Déclencheur | Rôle |
|---|---|---|
| `backend-ci.yml` | push `main`, PR `main` | `./mvnw test` sur MySQL / Redis éphémères |
| `frontend-ci.yml` | push/PR touchant `frontend/**` | `npm audit --audit-level=high`, `lint`, `test`, `build` |
| `dependency-review.yml` | PR `main` | échec si une dépendance ajoutée/modifiée par la PR introduit une CVE ≥ `high` ou une licence interdite |
| `e2e.yml` | **manuel** (`workflow_dispatch`) | monte la pile complète (MySQL, Redis, back-end `demo`, `ng serve`) et exécute les 149 tests Playwright. Manuel assumé : ~20 min, état partagé, `workers: 1` |
| `dependabot.yml` | hebdomadaire | montées de version + alertes de sécurité (Maven, npm, GitHub Actions) |

Tous les workflows : `permissions: contents: read`, sans secret, sans
`pull_request_target`. Détail : `docs/07-securite-rgpd.md` §8.

---

## Comptes de démonstration

**Six** comptes créés et resynchronisés par le profil `demo`
(`DemoDataInitializer`). Domaine réservé `example.test` (données
strictement fictives). **Le mot de passe n'est pas dans le dépôt** : il
vaut la valeur de `ESIC_DEMO_PASSWORD` de votre `.env`, et ne doit
jamais être écrit dans un document du dépôt.

| Email | Nom affiché | Rôle(s) | Usage |
|---|---|---|---|
| `superadmin@example.test` | Super Administrateur Démo | `SUPER_ADMIN` | routes techniques réservées (plages réseau CIDR), inaccessibles même à `ADMIN` |
| `admin@example.test` | Administrateur Démo | `ADMIN` | administration des comptes, import CSV, création de séance |
| `formateur@example.test` | Formateur Démo | `TEACHER` | ouverture de séance, QR / code court, présences |
| `apprenant1@example.test` | Alice Martin | `STUDENT` | émargement, « mes présences », justificatif |
| `apprenant2@example.test` | Karim Diallo | `STUDENT` | second émargement, anti-doublon |
| `responsable@example.test` | Responsable Pédagogique Démo | `PEDAGOGICAL_MANAGER` + `TEACHER` | **sélecteur de contexte de rôle** (EF-AUTH-003) ; périmètre `PRG-DEMO` |

`superadmin@example.test` reste **séparé** du compte d'administration
quotidienne (RG-003) : il ne cumule aucun autre rôle.

Le compte `responsable@example.test` est **multi-rôles** : après
connexion, le sélecteur de contexte apparaît et permet de basculer entre
« gérer mes formations » et « mes séances de formateur ». Le cumul de
rôles **n'élargit jamais** le JWT (Spring Security reste l'autorité).
`scripts/seed-demo.sh` l'affecte à la formation `PRG-DEMO` pour rendre
son périmètre exploitable.

Jeu de données d'import : `docs/demo-data/apprenants-demo.csv` (voir
`docs/demo-data/README.md`). Scénario de démonstration bout en bout :
`docs/11-guide-demonstration.md` §11. Guide par rôle :
`docs/12-guide-utilisateur.md`.

---

## Documentation

| Fichier | Contenu |
|---|---|
| `docs/CURRENT-STATE.md` | **état courant réel** (court) : modules, migrations, capacités livrées / partielles / hors périmètre, résultats des tests |
| `docs/reports/PROJECT_FINAL_AUDIT.md` | audit vérifiable (checkpoint F1) : matrices EF-* / RG-* / AC-*, endpoints, backlog `FINAL-*` |
| `docs/reports/PROJECT_HISTORY.md` | chronologie détaillée archivée des tranches PR #1 → #26 |
| `docs/01-cadrage.md` | vision, objectifs, acteurs, périmètre, contraintes |
| `docs/02-cahier-des-charges.md` | exigences fonctionnelles et techniques |
| `docs/03-architecture.md` | architecture réelle et cible |
| `docs/04-modele-donnees.md` | modèle de données |
| `docs/05-product-backlog.md` | backlog produit |
| `docs/07-securite-rgpd.md` | sécurité et RGPD |
| `docs/08-tests-recette.md` | plan de tests et recette |
| `docs/09-matrice-rncp.md` | traçabilité RNCP 39394 (blocs BC01–BC04) |
| `docs/10-journal-ia.md` | journal d'utilisation de l'IA |
| `docs/11-guide-demonstration.md` | guide de démonstration pas à pas (+ checklist jury §12, matrice §13) |
| `docs/12-guide-utilisateur.md` | ce que chaque rôle peut faire dans l'application |
| `docs/13-guide-deploiement.md` | **installation, remise à zéro de la base, vérification, et ce qui manque avant une mise en service** |
| `audit-report.md` · `TESTING-SUMMARY.txt` | audit QA indépendant du 3 septembre 2026 (rapport complet et résumé d'une page) |
| `tests/` · `playwright.config.ts` | recette end-to-end navigateur (149 tests) |
| `captures/` | captures d'écran produites par la campagne d'audit |
| `docs/demo-data/` | jeu de données CSV fictif pour la démonstration de l'import |
| `docs/reports/PERF_NOTES.md` · `TEST_ISOLATION_DECISION.md` | mesures de performance ; décision Testcontainers |
| `docs/reports/G1_FINAL_REPORT.md` | **rapport final du lot G1** : anomalies corrigées, garanties transactionnelles, coûts SQL mesurés, dettes résiduelles |
| `docs/reports/G1_REQUIREMENTS_TRACEABILITY.md` | matrice EF-* / RG-* / AC-* du lot G1 et statuts finaux |
| `docs/reports/G1_ARCHITECTURE_DECISIONS.md` | décisions `DEC-G1-*` (frontières de modules, identité de créneau, compensation base/fichier, e2e…) |
| `docs/reports/G1_IMPLEMENTATION_PROGRESS.md` · `G1_IMPLEMENTATION_PLAN.md` | journal bloc par bloc ; plan du lot |
| `docs/reports/SOUTENANCE_TECHNICAL_SOURCE.md` | **source technique consolidée** pour la rédaction du mémoire de soutenance |
| `CLAUDE.md` | règles de travail assisté par IA |

---

## Dépannage minimal

| Symptôme | Cause probable / solution |
|---|---|
| Back-end : `JWT_SECRET` / `Failed to bind` au démarrage | `JWT_SECRET` absent ou < 32 octets dans `.env` ; `set -a && source ../.env && set +a` non exécuté |
| Back-end : `ESIC_DEMO_PASSWORD ... obligatoire` | profil `demo` sans `ESIC_DEMO_PASSWORD` (≥ 12 caractères) dans `.env` |
| Back-end : `Access denied for user` / `Communications link failure` | conteneurs pas démarrés / pas `healthy` (`docker compose ps`) ; mauvais `MYSQL_*` dans `.env` |
| Back-end : erreur Flyway `validate` / version de schéma | base dans un état incohérent — `docker compose down -v` puis `up -d` recrée un volume vierge |
| Build back-end échoue immédiatement | `java -version` ≠ 21 — exporter `JAVA_HOME` vers un JDK 21 |
| Front : `/api/...` en 404 ou erreur réseau | back-end non lancé sur `:8080`, ou `npm start` pas utilisé (le proxy `/api` vient de `proxy.conf.json`) |
| Front : session perdue au rechargement | comportement **attendu** — JWT en mémoire seule, pas de refresh token (prototype) |
| Aucun email reçu | consulter Mailpit http://localhost:8025 ; l'envoi est asynchrone, un échec est seulement journalisé côté serveur |
| `scripts/seed-demo.sh` en `401` | comptes `demo` non amorcés (profil `demo`) ou `ESIC_DEMO_PASSWORD` différent de celui utilisé au démarrage |
| Dépôt d'une pièce jointe en `503 ATT_ATTACHMENT_STORAGE_FAILED`, ou erreur d'écriture sur `/data/...` | back-end lancé **hors Docker** avec le `JUSTIFICATION_STORAGE_PATH` par défaut (`/data/uploads/justifications`), inexistant ou en lecture seule sur macOS / Linux — exporter un chemin local inscriptible (voir « Lancement » §2) |
| `docker compose down -v` : comptes et données disparus | `-v` **supprime les volumes**, donc toute la base MySQL. Utiliser `docker compose down` (sans `-v`) pour un arrêt simple ; `-v` est réservé à la remise à zéro volontaire (recréation d'un schéma Flyway propre) |
| Journaux Redis (`Lettuce`, reconnexion) au démarrage | informatifs tant que `docker compose ps` montre `redis` **healthy** ; Redis n'est consommé que par les jetons d'émargement. Une indisponibilité réelle donne `503 ATT_TOKEN_BACKEND_UNAVAILABLE` sur la génération de jeton, jamais une validation dégradée |

---

## Licence / usage

Projet pédagogique. Données **strictement fictives** (`example.test`).
Aucun secret n'est versionné.
