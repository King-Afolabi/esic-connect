# ESIC Connect

Plateforme web et mobile de **gestion pédagogique, d'émargement et de
suivi de l'assiduité** pour l'ESIC.

Elle couvre le cycle complet : import des apprenants, activation des
comptes, construction et publication du planning, création des séances,
émargement multicanal, calcul de l'assiduité, justificatifs,
réclamations, notifications, rapports et attestations.

| | |
|---|---|
| **Back-end** | Java 21, Spring Boot 3.5, Spring Modulith, MySQL 8, Redis 7 |
| **Front-end** | Angular 21 standalone / zoneless / signaux, Angular Material |
| **Infrastructure** | Docker Compose — MySQL, Redis, Mailpit, Mosquitto |
| **Tests** | JUnit, Vitest, Playwright |
| **État réel** | [`docs/CURRENT-STATE.md`](docs/CURRENT-STATE.md) — seule source de vérité sur ce qui est livré |

> **Règle du dépôt** : ce README décrit le produit et son installation.
> Il ne déclare jamais qu'une fonctionnalité est livrée. Cette
> information est exclusivement dans `docs/CURRENT-STATE.md`, mise à
> jour à chaque livraison et adossée à des tests exécutés.

---

## Sommaire

- [Ce que fait le produit](#ce-que-fait-le-produit)
- [Architecture](#architecture)
- [Prérequis](#prérequis)
- [Installation](#installation)
- [Lancement](#lancement)
- [Comptes de démonstration](#comptes-de-démonstration)
- [Tests et vérifications](#tests-et-vérifications)
- [Bases de données](#bases-de-données)
- [Structure du dépôt](#structure-du-dépôt)
- [Documentation](#documentation)
- [Conventions](#conventions)
- [Dépannage](#dépannage)

---

## Ce que fait le produit

| Domaine | Capacités |
|---|---|
| **Identité** | connexion mot de passe et passkey, MFA TOTP, anti-robot, limitation de débit, multi-rôles avec contexte vérifié côté serveur, invitations et activation |
| **Référentiels** | années, formations, niveaux, promotions, classes, groupes, matières, sites, bâtiments, salles, plages réseau |
| **Population** | import CSV et Excel avec simulation puis confirmation atomique, détection des doublons, inscriptions historisées, rythmes d'alternance |
| **Planning** | import multiformat, assistance IA au mapping, revue et correction ligne à ligne, détection des conflits, versionnement, retour arrière, publication atomique, calendrier interactif |
| **Séances** | création depuis un planning publié ou en exception, cycle de vie strict, annulation, report, remplacements datés |
| **Émargement** | QR dynamique rotatif, code court, QR fixe de salle avec contrôle réseau, borne connectée, saisie manuelle motivée, confirmation WebAuthn |
| **Assiduité** | quatre points de contrôle journaliers, demi-journées, paliers de retard, corrections auditées, alternance exclue du dénominateur |
| **Suivi** | justificatifs avec pièces jointes contrôlées, réclamations conversationnelles, départs anticipés, journal de transparence |
| **Communication** | centre de notifications, courriel, push PWA, préférences, outbox transactionnelle |
| **Pilotage** | tableaux de bord par rôle, rapports de classe et individuels, exports CSV / Excel / PDF, attestations, recherche globale |
| **Intelligence** | assistance à l'import, score de confiance, détection d'anomalies, alerte de décrochage — toujours validée par un humain |
| **Objets connectés** | borne MQTT authentifiée, télémétrie, file locale, rejeu, idempotence, simulateur logiciel |
| **Exploitation** | santé, métriques, journaux structurés, sauvegarde et restauration, intégration et livraison continues |

Le périmètre détaillé est dans
[`docs/02-cahier-des-charges.md`](docs/02-cahier-des-charges.md) —
142 exigences, 73 règles de gestion, 36 critères d'acceptation.

---

## Architecture

**Monolithe modulaire** Spring Boot, isolation des modules vérifiée
automatiquement par Spring Modulith : aucune dépendance vers l'interne
d'un autre module, aucun cycle, aucune entité JPA partagée. Les modules
communiquent par **ports publics** et par **événements**.

Un seul service séparé : le **service d'IA** Python / FastAPI, isolé
parce que son écosystème et son cycle de vie diffèrent.

```text
Navigateur / PWA
      │ HTTPS
      ▼
Angular ──▶ Spring Boot API ──┬──▶ MySQL     (source de vérité)
                              ├──▶ Redis     (jetons, cache, compteurs)
                              ├──▶ FastAPI   (assistance et anomalies)
                              ├──▶ SMTP      (courriels)
                              └──▶ MQTT ◀── Borne d'émargement
```

Détail : [`docs/03-architecture.md`](docs/03-architecture.md) et
[`docs/04-modele-donnees.md`](docs/04-modele-donnees.md).

---

## Prérequis

| Outil | Version | Note |
|---|---|---|
| Java (JDK) | **21** (Temurin / OpenJDK) | le build échoue avec une autre version majeure |
| Maven | wrapper `./mvnw` fourni | aucune installation requise |
| Node.js | **24** | testé en 24.13.0 |
| npm | **11** | testé en 11.6.2 |
| Docker + Docker Compose | Docker ≥ 24 | MySQL, Redis, Mailpit, Mosquitto |

Si `java -version` ne renvoie pas 21 :

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"  # macOS/Homebrew — à adapter
export PATH="$JAVA_HOME/bin:$PATH"
```

---

## Installation

```bash
git clone https://github.com/King-Afolabi/esic-connect.git
cd esic-connect
cp .env.example .env
```

Éditer `.env` — **fichier non versionné, jamais commité** :

| Variable | Obligatoire | Valeur |
|---|---|---|
| `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `REDIS_PASSWORD` | oui | valeurs locales de votre choix |
| `JWT_SECRET` | oui | chaîne aléatoire **≥ 32 octets** ; le back-end refuse de démarrer sinon |
| `ESIC_DEMO_PASSWORD` | profil `demo` | mot de passe des comptes fictifs, **≥ 12 caractères** |
| `JUSTIFICATION_STORAGE_PATH` | **oui hors Docker** | répertoire inscriptible pour les pièces jointes ; le défaut `/data/uploads/...` ne l'est pas |
| `MYSQL_TEST_DATABASE` | recommandé | base de la suite de tests (défaut `esic_test`) — évite qu'un `./mvnw test` écrive dans la base applicative |
| `APP_ALLOWED_ORIGINS` | non | origines autorisées du front (défaut `http://localhost:4200`) |

`.env.example` ne contient que des valeurs de remplacement :
`JWT_SECRET=` et `ESIC_DEMO_PASSWORD=` y sont **délibérément vides**.

```bash
openssl rand -base64 48    # génération d'un secret
```

---

## Lancement

### 1. Infrastructure

```bash
docker compose config      # valider la syntaxe et les variables
docker compose up -d       # mysql, redis, mailpit, mosquitto
docker compose ps          # mysql / redis / mailpit doivent être "healthy"
```

Boîte de réception locale : http://localhost:8025

### 2. Back-end — port 8080

```bash
cd backend
set -a && source ../.env && set +a

mkdir -p ../build/demo-data/justifications
export JUSTIFICATION_STORAGE_PATH="$(cd ../build/demo-data/justifications && pwd)"

SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
```

Le profil `demo` amorce six comptes fictifs **sans désactiver la
sécurité** ; il exige `ESIC_DEMO_PASSWORD`. Sans ce profil (`local`),
aucun compte n'est créé.

Démonstration isolée de la base applicative :

```bash
MYSQL_DATABASE=esic_connect_demo SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
```

- API : http://localhost:8080/v3/api-docs
- Swagger UI : http://localhost:8080/swagger-ui.html
- Santé : http://localhost:8080/actuator/health

Export OpenAPI à la demande, back-end démarré :
`bash scripts/dump-openapi.sh`.

### 3. Front-end — port 4200

```bash
cd frontend
npm ci
npm start          # http://localhost:4200
```

`ng serve` proxifie `/api` vers le back-end : aucune configuration CORS
n'est nécessaire en local.

### 4. Jeu de données

```bash
bash scripts/seed-demo.sh
```

---

## Comptes de démonstration

Six comptes créés par le profil `demo`. Domaine réservé `example.test`,
données strictement fictives. **Le mot de passe n'est pas dans le
dépôt** : il vaut `ESIC_DEMO_PASSWORD`.

| Email | Rôle(s) | Usage |
|---|---|---|
| `superadmin@example.test` | `SUPER_ADMIN` | routes techniques réservées |
| `admin@example.test` | `ADMIN` | administration des comptes, imports |
| `responsable@example.test` | `PEDAGOGICAL_MANAGER` + `TEACHER` | **multi-rôles** : sélecteur de contexte, périmètre `PRG-DEMO` |
| `formateur@example.test` | `TEACHER` | ouverture de séance, QR, présences |
| `apprenant1@example.test` | `STUDENT` | émargement, assiduité, justificatif |
| `apprenant2@example.test` | `STUDENT` | second émargement, anti-doublon |

`superadmin@example.test` reste séparé du compte d'administration
quotidienne (RG-003) et ne cumule aucun autre rôle.

Jeu d'import : `docs/demo-data/apprenants-demo.csv`.

---

## Tests et vérifications

```bash
# Back-end — nécessite l'infrastructure Docker et les variables .env
cd backend && ./mvnw clean test

# Front-end
cd frontend && npm test -- --watch=false
npm run lint
npm run build

# Recette navigateur — pile démarrée requise
npm run test:e2e

# Tout, en une commande, arrêt au premier échec
./scripts/verify-all.sh
```

Les tests marqués `perf` sont exclus par défaut :
`./mvnw test -Pperf` pour les exécuter.

Les résultats des dernières exécutions sont consignés dans
[`docs/CURRENT-STATE.md`](docs/CURRENT-STATE.md). Aucun chiffre de test
n'est écrit ailleurs sans avoir été exécuté.

---

## Bases de données

Quatre bases distinctes, **jamais confondues** :

| Base | Usage | Variable |
|---|---|---|
| `esic_connect` | runtime local | `MYSQL_DATABASE` |
| `esic_connect_demo` | démonstration | `MYSQL_DATABASE=esic_connect_demo` |
| `esic_test` | suite de tests | `MYSQL_TEST_DATABASE` |
| `esic_connect_ci` | intégration continue | imposée par le pipeline |

Le profil `test` lit `MYSQL_TEST_DATABASE` et non `MYSQL_DATABASE` : un
`./mvnw test` lancé pendant une démonstration n'écrit pas dans la base de
démonstration.

```bash
./scripts/db-doctor.sh            # diagnostic — code 2 = base polluée
./scripts/db-reset.sh <base>      # sauvegarde → recréation → Flyway → contrôle
```

---

## Structure du dépôt

```text
backend/          application Spring Boot, modules métier, migrations Flyway
frontend/         application Angular, fonctionnalités par domaine
tests/            recette de bout en bout Playwright
scripts/          vérification, diagnostic, remise à zéro, jeu de données
docs/             documentation produit et technique
infrastructure/   configuration des services d'appui
compose.yaml      MySQL, Redis, Mailpit, Mosquitto
```

---

## Documentation

| Fichier | Contenu |
|---|---|
| [`docs/CURRENT-STATE.md`](docs/CURRENT-STATE.md) | **état courant réel** — livré, partiel, non implémenté, avec preuves |
| [`docs/01-cadrage.md`](docs/01-cadrage.md) | vision, objectifs, acteurs, exclusions de conception |
| [`docs/02-cahier-des-charges.md`](docs/02-cahier-des-charges.md) | exigences, règles de gestion, critères d'acceptation |
| [`docs/03-architecture.md`](docs/03-architecture.md) | architecture logique et technique, décisions |
| [`docs/04-modele-donnees.md`](docs/04-modele-donnees.md) | modèle conceptuel et physique |
| [`docs/05-product-backlog.md`](docs/05-product-backlog.md) | backlog produit — 135 stories, 643 points |
| [`docs/06-roadmap-six-mois.md`](docs/06-roadmap-six-mois.md) | trajectoire en 13 sprints |
| [`docs/07-risques.md`](docs/07-risques.md) | registre des risques |
| [`docs/08-securite-rgpd.md`](docs/08-securite-rgpd.md) | analyse de sécurité et conformité |
| [`docs/09-strategie-tests.md`](docs/09-strategie-tests.md) | stratégie et plan de tests |
| [`docs/10-guide-utilisateur.md`](docs/10-guide-utilisateur.md) | guide fonctionnel par rôle |
| [`docs/11-guide-deploiement.md`](docs/11-guide-deploiement.md) | installation, exploitation, déploiement |
| [`docs/12-prerequis-externes.md`](docs/12-prerequis-externes.md) | comptes, clés et matériel à préparer |

---

## Conventions

- **Données fictives uniquement** — aucune donnée réelle d'apprenant.
- **Aucun secret dans Git** — même « de test » ou « de repli ».
- **Aucune fonctionnalité déclarée sans preuve** — un test exécuté, une
  mesure, une capture.
- **Autorisations côté serveur** — l'affichage Angular n'est jamais un
  contrôle d'accès.
- **Tests écrits avec le code**, jamais après coup.
- **Documentation à jour** — une fonction non documentée n'est pas
  terminée.
- Branches : `sprint/SNN-<thème>` ou `feature/<domaine>`, fusionnées par
  demande de tirage revue.

Voir [`CLAUDE.md`](CLAUDE.md) pour les règles complètes de contribution.

---

## Dépannage

| Symptôme | Cause probable | Correctif |
|---|---|---|
| Le back-end refuse de démarrer | `JWT_SECRET` absent ou trop court | `openssl rand -base64 48` dans `.env` |
| Dépôt de pièce jointe en `503` | `JUSTIFICATION_STORAGE_PATH` non inscriptible | pointer un répertoire local existant |
| `401` sur toutes les routes | jeton absent ou expiré | se reconnecter ; le jeton vit en mémoire seule |
| Émargement en `503` | Redis indisponible | `docker compose up -d redis` — aucune validation dégradée n'est acceptée |
| Comptes de démonstration absents | profil `demo` non actif | `SPRING_PROFILES_ACTIVE=demo` et `ESIC_DEMO_PASSWORD` défini |
| Base applicative polluée | tests exécutés sur la mauvaise base | `./scripts/db-doctor.sh` puis `./scripts/db-reset.sh` |

---

## Licence et usage

Projet applicatif de l'ESIC. Données de développement et de
démonstration strictement fictives.
