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

Parcours implémenté et testé **au niveau API** de bout en bout :

```text
Import CSV des apprenants (simulation → confirmation)
  → Création d'une séance exceptionnelle (manuelle)
  → Ouverture de la séance par le formateur
  → Émargement de l'apprenant (QR opaque + code court, Redis)
  → Consultation des présences
  → Correction motivée et auditée
  → Rapport d'assiduité (demi-journées)
  → Export CSV
```

Détail par capacité et statut : `docs/CURRENT-STATE.md`.
Audit vérifiable et matrices d'exigences :
`docs/reports/PROJECT_FINAL_AUDIT.md`.

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

> **Mise à jour G1 (1er septembre 2026).** Le lot produit G1 (branche
> `feature/master-level-product-expansion`) a **livré** deux des éléments
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
| `ESIC_DEMO_PASSWORD` | oui (profil `demo` seulement) | mot de passe des comptes fictifs, **≥ 12 caractères** |
| `APP_ALLOWED_ORIGINS` | non | origine(s) autorisée(s) du front (défaut `http://localhost:4200`) |

`.env.example` ne contient que des placeholders. **Ne jamais y mettre de
secret réel** ni committer `.env`.

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
SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
```

Le profil `demo` amorce 4 comptes fictifs (voir plus bas) **sans
désactiver la sécurité**. Sans ce profil (`local`), aucun compte n'est
créé.

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
npm audit                    # 0 vulnérabilité attendue

# Non-régression du script de seed (faux curl déterministe)
bash scripts/test/test-seed-demo.sh
```

Totaux de référence du dernier audit : voir
`docs/CURRENT-STATE.md` → « Résultats du dernier audit ».

### Intégration continue (GitHub Actions)

| Workflow | Déclencheur | Rôle |
|---|---|---|
| `backend-ci.yml` | push `main`, PR `main` | `./mvnw test` sur MySQL / Redis éphémères |
| `frontend-ci.yml` | push/PR touchant `frontend/**` | `npm audit --audit-level=high`, `lint`, `test`, `build` |
| `dependency-review.yml` | PR `main` | échec si une dépendance ajoutée/modifiée par la PR introduit une CVE ≥ `high` ou une licence interdite |
| `dependabot.yml` | hebdomadaire | montées de version + alertes de sécurité (Maven, npm, GitHub Actions) |

Tous les workflows : `permissions: contents: read`, sans secret, sans
`pull_request_target`. Détail : `docs/07-securite-rgpd.md` §8.

---

## Comptes de démonstration

Créés par le profil `demo`. Domaine réservé `example.test` (données
strictement fictives). **Le mot de passe n'est pas dans le dépôt** : il
vaut la valeur de `ESIC_DEMO_PASSWORD` de votre `.env`.

| Email | Rôle(s) | Usage |
|---|---|---|
| `admin@example.test` | `ADMIN` | administration des comptes, import CSV, création de séance |
| `formateur@example.test` | `TEACHER` | ouverture de séance, QR / code court, présences |
| `apprenant1@example.test` | `STUDENT` | émargement, « mes présences », justificatif |
| `apprenant2@example.test` | `STUDENT` | second émargement, anti-doublon |
| `responsable@example.test` | `PEDAGOGICAL_MANAGER` + `TEACHER` | **sélecteur de contexte de rôle** (EF-AUTH-003) ; périmètre `PRG-DEMO` |

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
| `docs/demo-data/` | jeu de données CSV fictif pour la démonstration de l'import |
| `docs/reports/PERF_NOTES.md` · `TEST_ISOLATION_DECISION.md` | mesures de performance ; décision Testcontainers |
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

---

## Licence / usage

Projet pédagogique. Données **strictement fictives** (`example.test`).
Aucun secret n'est versionné.
