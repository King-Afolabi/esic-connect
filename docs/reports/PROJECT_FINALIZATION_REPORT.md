# Rapport de finalisation — lot F2 → F6 (ESIC Connect)

| Élément | Valeur |
|---|---|
| Branche | `chore/project-finalization-f2-f6` |
| Base (`git merge-base HEAD main`) | `e44ccb1ecdfe95d7996fb021e8ce38421ae5a14f` |
| Date | 31 août 2026 |
| Nature | documentation, tests, CI, durcissement HTTP, démonstration — **aucune migration, aucune règle métier modifiée, aucune version de dépendance existante changée** |
| Source de vérité d'entrée | `docs/reports/PROJECT_FINAL_AUDIT.md` (checkpoint F1) |

Ce rapport clôt le lot. Il ne remplace pas l'audit F1 (constats
historiques du 31 août 2026 conservés) ; il en constitue l'**addendum de
fin de lot**.

---

## 1. Commits produits

Le lot compte **6 commits intentionnels** en avance sur `main` :
**5 commits de checkpoint** (un par checkpoint, F2 → F6) **+ 1 commit
documentaire de clôture** (ce rapport, `e9e6a70`). Aucun décompte
« 5 commits au total » ne doit être déduit de ce rapport : le nombre
*total* de commits n'était pas contraint, seule la règle « un commit par
checkpoint » l'était.

| Commit | Type | Titre |
|---|---|---|
| `d7d2bfe` | checkpoint F2 | `docs(finalization): aligner la documentation sur l'état réel (F2)` |
| `9de7612` | checkpoint F3 | `test(finalization): renforcer les preuves de qualité (F3)` |
| `e94a5f8` | checkpoint F4 | `ci(security): contrôler les dépendances et permissions (F4)` |
| `732da8a` | checkpoint F5 | `security(finalization): durcir les échanges HTTP (F5)` |
| `c93f56d` | checkpoint F6 | `docs(demo): préparer la démonstration finale du projet (F6)` |
| `e9e6a70` | clôture | `docs(finalization): rapport d'audit final du lot F2-F6` |

À ces 6 commits s'ajoute le commit correctif
`docs(finalization): corriger la synthèse finale du lot`, qui ne modifie
que des fichiers `docs/` déjà présents, puis le commit correctif CI
`fix(ci): synchroniser npm et le lockfile frontend` (voir §2 « Correctif
CI post-F6 » et §10) — workflow front-end + `frontend/package-lock.json`
(2 entrées ajoutées) + ce rapport + `docs/10-journal-ia.md`, **aucune
dépendance ni version applicative modifiée**.

`git diff --stat main...c93f56d` (les 5 checkpoints seuls) : **39
fichiers**, +6260 / −3739 (dont `docs/CURRENT-STATE.md` réduit de ~3900
lignes, archivées dans `docs/reports/PROJECT_HISTORY.md`). Le commit de
clôture `e9e6a70` ajoute ce seul rapport → `git diff --stat
main...e9e6a70` : **40 fichiers**, +6643 / −3739. Le présent commit
correctif ne touche que ce rapport (aucun nouveau fichier).

Aucun commit temporaire, aucun `fixup`, aucun `squash`. Historique de
`main` non réécrit. **Aucun push, aucune PR, aucune fusion.**

---

## 2. Fichiers modifiés par checkpoint

### F2 — vérité documentaire (`d7d2bfe`)

- **`README.md`** (nouveau) — pitch, périmètre livré / non livré,
  architecture réelle, prérequis, `.env`, lancement, tests, comptes
  démo, dépannage, décision d'exclusion du planning.
- **`docs/CURRENT-STATE.md`** — réécrit court (12 modules, V1–V11,
  capacités par statut, résultats du run F1).
- **`docs/reports/PROJECT_HISTORY.md`** (nouveau) — archive verbatim de
  l'ancien contenu de `CURRENT-STATE.md`.
- `docs/03-architecture.md` §7 — bloc « état réel » (12 modules ;
  `planning`/`claim`/`ai`/`iot`/`reporting`/`justification` = cible non
  implémentée) + correction §7.4.
- `docs/09-matrice-rncp.md` — TR-023 fusionnée (PR #22), **TR-024**
  (import CSV, `studentimport`), TR-003 → `studentimport`, TR-004/005
  « planning : NON IMPLÉMENTÉ », note sur les totaux périmés.
- `docs/01-cadrage.md` §23.5, `docs/02-cahier-des-charges.md` §4.5.1 —
  addendum de réduction de périmètre assumée (planning).
- `docs/10-journal-ia.md` — entrée F2.

### F3 — qualité / tests / exploitabilité (`9de7612`)

- `frontend/.../session-detail.ts` + `.html` — correction `NG8107` /
  `NG8102` (`selectedCheckpoint: CheckpointView | null`, `computed`
  `manualTargetLabel`).
- `backend/pom.xml` — `maven-surefire-plugin` + propriétés
  `test.groups` / `test.excludedGroups` + profil `-Pperf` (tests `perf`
  **exclus** du `mvn test` par défaut).
- `backend/.../StudentImportSimulationPerfTests.java`,
  `.../AttendanceTokenPerfTests.java` (nouveaux, `@Tag("perf")`).
- `frontend/package.json` + `package-lock.json` — `axe-core ^4.13.0`
  (devDependency ; lockfile re-normalisé par npm 11, **aucune version
  existante modifiée**).
- `frontend/src/testing/axe.ts` (nouveau) + `login.a11y.spec.ts` +
  `attendance-check-in.a11y.spec.ts` (nouveaux).
- `scripts/dump-openapi.sh` (nouveau) + `.gitignore` (`docs/openapi.json`).
- `backend/src/test/resources/application-test.yml` — commentaire
  Testcontainers corrigé.
- `docs/07-securite-rgpd.md` §14 — état réel de la purge.
- `docs/reports/PERF_NOTES.md`, `docs/reports/TEST_ISOLATION_DECISION.md`
  (nouveaux). `docs/CURRENT-STATE.md`, `docs/10-journal-ia.md`,
  `README.md` — notes F3.

### F4 — CI / chaîne d'approvisionnement (`e94a5f8`)

- `.github/dependabot.yml` (nouveau) — Maven `/backend`, npm
  `/frontend`, GitHub Actions `/`.
- `.github/workflows/dependency-review.yml` (nouveau) —
  `actions/dependency-review-action@v4`, `pull_request`,
  `permissions: contents: read`, `fail-on-severity: high`.
- `.github/workflows/frontend-ci.yml` — étape `npm audit
  --audit-level=high`.
- `docs/07-securite-rgpd.md` §8 — contrôles + écart assumé (pas de SCA
  Maven de fond). `README.md` — tableau CI. `docs/10-journal-ia.md` — F4.

### F5 — durcissement applicatif (`732da8a`)

- `backend/.../SecurityConfig.java` — `CorsConfigurationSource`
  (`app.security.cors.allowed-origins`), `http.cors(...)`,
  `http.headers(...)` (CSP + `Referrer-Policy: no-referrer`), javadoc
  réécrite.
- `backend/src/main/resources/application.yml` — bloc `app.security.cors`.
- `backend/src/test/resources/application-test.yml` — origines de test.
- `backend/.../HttpSecurityHeadersIntegrationTests.java` (nouveau, 4
  tests).
- `.env.example` — commentaire `APP_ALLOWED_ORIGINS`.
- `docs/07-securite-rgpd.md` §5 (rate-limiting = dette assumée), §8
  (état des contrôles API). `docs/CURRENT-STATE.md`,
  `docs/10-journal-ia.md` — F5.

### F6 — démonstration / livraison jury (`c93f56d`)

- `backend/.../DemoDataInitializer.java` — 5ᵉ compte
  `responsable@example.test` (`PEDAGOGICAL_MANAGER` + `TEACHER`).
- `backend/.../DemoDataInitializerTests.java` — 4 → 5 comptes + 2 rôles.
- `scripts/seed-demo.sh` — affectation RP à `PRG-DEMO` (idempotente).
- `scripts/test/test-seed-demo.sh` — comptage + 409 en mode `exists`.
- `docs/demo-data/apprenants-demo.csv` + `docs/demo-data/README.md`
  (nouveaux).
- `docs/12-guide-utilisateur.md` (nouveau) — parcours par rôle.
- `docs/11-guide-demonstration.md` — §11 scénario bout en bout, §12
  checklist jury, §13 matrice, §11.8 vérification API réelle.
- `README.md`, `docs/CURRENT-STATE.md`, `docs/09-matrice-rncp.md`,
  `docs/10-journal-ia.md` — F6.

### Correctif CI post-F6 (`fix(ci): synchroniser npm et le lockfile frontend`)

Deux échecs CI observés sur la PR #27, corrigés au minimum sans toucher
au code applicatif :

1. **`dependency-review`** — « Dependency review is not supported on this
   repository. Please ensure that Dependency graph is enabled. » Le
   **Dependency Graph doit être activé manuellement** dans les
   paramètres GitHub du dépôt. Le workflow `dependency-review.yml` est
   **conservé tel quel** — pas de suppression, pas de `continue-on-error`.
2. **`frontend-ci`** — `npm ci` échouait : `frontend/package-lock.json`
   ne contenait pas `@emnapi/core@1.11.3` ni `@emnapi/runtime@1.11.3`
   (peer-dépendances de `@napi-rs/wasm-runtime`, tiré par l'optionnel
   `@rolldown/binding-wasm32-wasi` ; entrées résolues sous Linux, absentes
   d'un lock généré sous macOS).

- `.github/workflows/frontend-ci.yml` — étape « Align npm with
  package.json packageManager » (`npm install --global npm@11.6.2`,
  = `packageManager` de `frontend/package.json`) insérée entre
  `actions/setup-node` et `npm ci`, suivie de `node --version` /
  `npm --version`.
- `frontend/package-lock.json` — **+2 clés uniquement**
  (`node_modules/@emnapi/core`, `node_modules/@emnapi/runtime` @ `1.11.3`),
  obtenues via `npm@11.6.2 install --package-lock-only --os=linux
  --cpu=x64 --libc=glibc` puis reportées manuellement. Diff sémantique
  vs base : **0 clé supprimée, 0 clé modifiée**. `npm ci --os=linux
  --cpu=x64 --libc=glibc` passe désormais (échouait avant).
- Le lockfile a été (re)généré avec **la même version npm que la CI**
  (11.6.2). Report manuel des seules entrées manquantes pour éviter les
  bumps transitifs d'une régénération complète (`qs`,
  `electron-to-chromium`, `@csstools/*`).

---

## 3. Résultats exacts des vérifications (31 août 2026)

Machine : Apple M2, 8 cœurs, 16 Go, macOS 26.6.2 (arm64). OpenJDK 21,
MySQL 8.4 + Redis 7 (conteneurs `compose.yaml`), Node 24.13.0, npm 11.6.2.

| Commande | Résultat |
|---|---|
| `cd backend && ./mvnw clean test` | **`BUILD SUCCESS` — 686 tests, 0 échec, 0 erreur, 0 ignoré** — `ModularityTests` vert — ~3:02 min |
| `cd backend && ./mvnw -Pperf test -Dtest='StudentImportSimulationPerfTests,AttendanceTokenPerfTests'` | **2 tests, 0 échec** (mesures §5) |
| `cd frontend && npm test -- --watch=false` | **55 fichiers, 475 tests, 0 échec** |
| `cd frontend && npm run lint` | « All files pass linting » |
| `cd frontend && npm run build` | initial **483,26 kB** brut / 122,84 kB transféré — **0 alerte de budget** — **0 avertissement** (`NG8107` / `NG8102` corrigés) |
| `cd frontend && npm audit` | **0 vulnérabilité** (dev + prod) ; `npm audit --audit-level=high` → 0 |
| `cd frontend && npm ci` | reproduit proprement (608 paquets) |
| `bash scripts/test/test-seed-demo.sh` | 2 scénarios OK (« un appel logique = un POST », idempotence) |
| `git diff --check` | propre |
| `git status` | working tree propre |

Évolution des totaux sur le lot : back-end **682 → 686** (+4,
`HttpSecurityHeadersIntegrationTests`, F5) ; front-end **471 → 475** (+4,
2 fichiers `*.a11y.spec.ts`, F3). Les 2 tests `perf` (F3) sont **exclus**
du total par défaut.

---

## 4. Audits de dépendances

| Contrôle | État | Preuve |
|---|---|---|
| `npm audit` (front, dev + prod) | **0 vulnérabilité** | exécuté le 31/08 |
| `npm audit --audit-level=high` en CI | ajouté à `frontend-ci.yml` | non encore exécuté sur GitHub |
| Dependabot (Maven, npm, GitHub Actions) | `.github/dependabot.yml` | hebdomadaire, PR plafonnées |
| `actions/dependency-review-action@v4` (PR) | `.github/workflows/dependency-review.yml` | `fail-on-severity: high`, licences GPL/AGPL bloquées, `permissions: contents: read` |
| Scan SCA de fond de tout l'arbre Maven | **NON** — écart assumé | `docs/07` §8 (clé NVD + cache requis ; à planifier en job dédié) |

Workflows : tous en `permissions: contents: read`, `concurrency` +
annulation, `timeout-minutes`, actions épinglées `@v4`, **aucun secret**,
**aucun `pull_request_target`**, aucune exécution de code de PR non fiable
avec droits élevés.

---

## 5. Sécurité — CORS et en-têtes HTTP

Implémentés dans `SecurityConfig` (F5), vérifiés par
`HttpSecurityHeadersIntegrationTests` **et** en direct contre le back-end
en profil `demo` :

- **En-têtes sur une réponse `401`** (route métier sans jeton) :
  `X-Content-Type-Options: nosniff`,
  `X-Frame-Options: DENY`,
  `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`,
  `Pragma: no-cache`, `Expires: 0`,
  `Content-Security-Policy: default-src 'self'; script-src 'self';
  style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src
  'self'; connect-src 'self'; worker-src 'self' blob:; object-src 'none';
  base-uri 'self'; form-action 'self'; frame-ancestors 'none'`,
  `Referrer-Policy: no-referrer`.
  **Pas de `Strict-Transport-Security`** (HTTP — normal, HSTS sur HTTPS
  uniquement).
- **CSP** : aucun `script-src 'unsafe-inline'` ni `'unsafe-eval'`.
  `style-src 'unsafe-inline'` + `img-src data:` conservés **pour Swagger
  UI** (springdoc), écart documenté (`docs/07` §8) ; l'app Angular est
  servie séparément.
- **CORS** : origines de `APP_ALLOWED_ORIGINS` (**jamais `*`**),
  `allowCredentials=false`, méthodes `GET/POST/PUT/PATCH/DELETE/OPTIONS`,
  en-têtes `Authorization`/`Content-Type`/`Accept`/`X-Requested-With`,
  portée `/api/**`. Vérifié : preflight depuis `http://localhost:4200`
  → `200` + `Access-Control-Allow-Origin` ; depuis `http://evil.example`
  → **`403`**.

**Rate-limiting** (`/auth/login`) : `NOT_IMPLEMENTED` — dette assumée
documentée (`docs/07` §5). Refus déjà uniforme (email inconnu / mauvais
mot de passe / compte inactif, testé) + BCrypt.

---

## 6. Performances réellement mesurées (F3)

Tests `@Tag("perf")`, `./mvnw -Pperf test`. Machine ci-dessus,
mono-utilisateur, infra localhost. **Mesures indicatives, pas une
garantie contractuelle, aucun seuil CI.** Détail : `docs/reports/PERF_NOTES.md`.

| Mesure | min | p50 | p95 | max |
|---|---:|---:|---:|---:|
| Simulation d'un import de **100 apprenants** (`StudentImportSimulationService.simulate`) | 603,6 ms | **636,5 ms** | 696,5 ms | 696,5 ms |
| Émission + rotation d'un **jeton d'émargement** (`AttendanceTokenService.issue`, Redis réel) | 2,39 ms | **2,88 ms** | 3,28 ms | 3,32 ms |

Lecture : l'import 100 lignes ≈ 0,6 s (« délai acceptable »,
NFR-PERF-03) mais **pas** « < 100 ms » — opération non « simple servie
depuis le cache ». Le jeton d'émargement ≈ 3 ms, très en dessous de la
cible. Non mesuré : cache de planning (inexistant), latence
`validate` de bout en bout, rapport mensuel, charge (voir `PERF_NOTES.md`).

---

## 7. Démonstrations réellement exécutées

**Parcours API du §11 (`docs/11-guide-demonstration.md` §11.8)** —
exécuté en direct le 31 août 2026, back-end en profil `demo` (MySQL /
Redis locaux), après `scripts/seed-demo.sh` :

| Étape | Résultat observé |
|---|---|
| `scripts/seed-demo.sh` | site / `PRG-DEMO` / `C-DEMO` / 2 profils / 2 inscriptions / séance `PLANNED` / **responsable affecté à `PRG-DEMO`** |
| import — simulation `apprenants-demo.csv` | `201` ; `summary { total 11, valid 7, warning 2, error 2 }` ; `confirmable=false` |
| import — lignes | 7 `VALID`, 2 `WARNING` (`IMP_EMAIL_DUPLICATE_IN_FILE`), 2 `ERROR` (`IMP_EMAIL_INVALID`, `IMP_CLASS_UNKNOWN`) |
| import — simulation fichier réduit (8 lignes) | `201` ; `valid 8, error 0` ; `confirmable=true` |
| import — confirmation | **`200`** ; `appliedSummary { created 8, invited 8, ignored 0 }` ; job `APPLIED` |
| import — reconfirmation | **`200`** ; `alreadyApplied=true` |
| Mailpit | **8** e-mails d'activation |
| séance `open` (formateur) | `204` |
| jeton d'émargement | `200` ; `shortCode` 8 car., `ttlSeconds=30` |
| `validate {shortCode}` (apprenant 1) | `200` ; `status=PRESENT`, `source=SHORT_CODE` |
| re-`validate` | **`409`** (anti-doublon) |
| rapports `classes` / `students` | `200` / `200` |
| export CSV | `200` ; `text/csv` ; `Content-Disposition: attachment` ; `X-Content-Type-Options: nosniff` |
| `responsable@example.test` → `GET /sessions` + `GET /student-imports` | `200` / `200` (les 2 contextes de rôle exploitables) |
| en-têtes durcis + CORS | vérifiés live (§5) |

Back-end arrêté proprement en fin de vérification ; infrastructure Docker
laissée en l'état.

**Non exécuté** : la démonstration **UI de bout en bout** (navigateur) —
statut `IMPLEMENTED_NOT_MANUALLY_DEMONSTRATED`. Les composants front sont
couverts par 475 tests Vitest. Le §11 est le mode opératoire du jour J.

---

## 8. Backlog `FINAL-001..030` — statut final

| ID | Titre (abrégé) | CP | Statut final |
|---|---|---|---|
| FINAL-001 | `CURRENT-STATE.md` désynchronisé | F2 | **CLÔTURÉ** — réécrit sur `main` |
| FINAL-002 | Pas de `README.md` racine | F2 | **CLÔTURÉ** — `README.md` créé |
| FINAL-003 | Import planning + publication + séances absent | F2 (doc) | **HORS_PÉRIMÈTRE_ASSUMÉ, DOCUMENTÉ** — README, `docs/01` §23.5, `docs/02` §4.5.1, `docs/03` §7, `docs/12` §6. Non implémenté. |
| FINAL-004 | `09-matrice-rncp.md` §6 incohérent | F2 | **CLÔTURÉ** — TR-023/024/003/004/005 corrigés |
| FINAL-005 | `03-architecture.md` §7 modules faux | F2 | **CLÔTURÉ** — bloc « état réel » |
| FINAL-006 | Aucun scan de dépendances en CI | F4 | **CLÔTURÉ (PARTIEL) — exécution CI À VÉRIFIER SUR LA PR** — workflows livrés (Dependabot + `dependency-review` + `npm audit`) ; dépôt **public**, donc `dependency-review-action` fonctionne **sans GitHub Advanced Security** ; **jamais exécuté sur GitHub** (aucune PR ouverte) ; pas de SCA Maven de fond (assumé). Ne pas considérer totalement clos avant un premier run de CI vert. |
| FINAL-007 | CORS non configuré | F5 | **CLÔTURÉ** — implémenté + testé + vérifié live |
| FINAL-008 | Pas de CSP / `Referrer-Policy` | F5 | **CLÔTURÉ** — ajoutées + `HttpSecurityHeadersIntegrationTests` |
| FINAL-009 | Pas de mesure de perf reproductible | F3 | **CLÔTURÉ** — 2 tests `perf` + `PERF_NOTES.md` (chiffres réels) |
| FINAL-010 | Données démo import + scénario absents | F6 | **CLÔTURÉ** — `apprenants-demo.csv` + `docs/11` §11 + API exécutée live |
| FINAL-011 | Comptes démo mono-rôle | F6 | **CLÔTURÉ** — `responsable@example.test` (2 rôles) + affectation via seed |
| FINAL-012 | Chiffres de tests périmés | F2/F3 | **CLÔTURÉ** — notes de renvoi dans `CURRENT-STATE.md` et `docs/09` |
| FINAL-013 | Guides installation + utilisation absents | F2/F6 | **CLÔTURÉ** — `README.md` + `docs/12-guide-utilisateur.md` |
| FINAL-014 | Javadoc `SecurityConfig` périmée | F5 | **CLÔTURÉ** — réécrite (26 contrôleurs, `@PreAuthorize` par module) |
| FINAL-015 | Purge / rétention non implémentée hors import CSV | F3 | **CLÔTURÉ (DOCUMENTATION)** — `docs/07` §14 tableau réel vs cible. Une seule purge implémentée. |
| FINAL-016 | Avertissements `NG8107` / `NG8102` | F3 | **CLÔTURÉ** — build front sans avertissement |
| FINAL-017 | Rate-limiting absent | F5 | **DETTE ASSUMÉE, DOCUMENTÉE** (`docs/07` §5) — non implémenté |
| FINAL-018 | Pas d'`openapi.json` versionné | F3 | **CLÔTURÉ** — `scripts/dump-openapi.sh` (runtime) + doc ; aucun artefact committé (choix) |
| FINAL-019 | Écrans manquants pour endpoints livrés | F5 | **DETTE ASSUMÉE, DOCUMENTÉE** (`CURRENT-STATE.md`, `docs/12` §6) |
| FINAL-020 | Pas de tests d'accessibilité outillés | F3 | **CLÔTURÉ (MINIMAL)** — `axe-core` sur 2 écrans (login, émargement) |
| FINAL-021 | Profil `test` non isolé / Testcontainers | F3 | **DIFFÉRÉ, DOCUMENTÉ** — `docs/reports/TEST_ISOLATION_DECISION.md` |
| FINAL-022 | Journal IA : F1 non tracé | F2 | **CLÔTURÉ** — entrées F1 (déjà) + F2 → F6 ajoutées |
| FINAL-023 | `CURRENT-STATE.md` trop long | F2 | **CLÔTURÉ** — archivé dans `docs/reports/PROJECT_HISTORY.md`, état courant ~380 lignes |
| FINAL-024 | Service IA / FastAPI | — | **HORS_PÉRIMÈTRE_ASSUMÉ** — listé (`docs/12` §6, `CURRENT-STATE.md`) |
| FINAL-025 | IoT / MQTT / Raspberry Pi | — | **HORS_PÉRIMÈTRE_ASSUMÉ** — listé |
| FINAL-026 | WebAuthn / MFA TOTP / Turnstile | — | **HORS_PÉRIMÈTRE_ASSUMÉ** — listé |
| FINAL-027 | PWA installable / offline / push | — | **HORS_PÉRIMÈTRE_ASSUMÉ** — listé |
| FINAL-028 | Réclamations, départ anticipé, justif. pièce jointe, Excel, groupes | — | **HORS_PÉRIMÈTRE_ASSUMÉ** — listé |
| FINAL-029 | Déploiement cloud AWS / staging / HTTPS / HA | — | **HORS_PÉRIMÈTRE_ASSUMÉ** — listé |
| FINAL-030 | `/auth/logout` + révocation, mot de passe oublié | — | **HORS_PÉRIMÈTRE_ASSUMÉ** — listé |

Récapitulatif (30 entrées, `FINAL-001..030`) : **16 clôturés**,
**3 clôturés partiels / minimaux / documentaires** (FINAL-006,
FINAL-015, FINAL-020), **3 dettes assumées ou différées, documentées**
(FINAL-017, FINAL-019, FINAL-021), **8 hors périmètre assumé**
(FINAL-003 + FINAL-024..030). Contrôle : 16 + 3 + 3 + 8 = 30.
FINAL-006 est comptée parmi les « clôturés partiels » et **non** parmi
les clôturés pleins, son exécution CI restant à vérifier.

---

## 9. Éléments hors périmètre (rappel)

Non implémentés, **assumés et documentés** (jamais présentés comme
livrés) — `README.md`, `docs/12` §6, `docs/reports/PROJECT_FINAL_AUDIT.md`
§0.3 / §7.4 :

- **import du planning → publication → création automatique des séances**
  (EF-PLAN-001..007, EF-SES-001, RG-016, AC-007, AC-008) ;
- séance : `PATCH` / annulation / remplaçant ;
- QR fixe de salle, contrôle réseau CIDR, scan caméra ;
- WebAuthn / passkeys, MFA TOTP, Cloudflare Turnstile ;
- réclamations / messagerie, départ anticipé, justificatif **avec pièce
  jointe**, import Excel / multifeuille ;
- service IA (FastAPI, mapping de colonnes, score) ; IoT / MQTT /
  Raspberry Pi ; PWA / offline / push ;
- mot de passe oublié, `/auth/logout` + révocation de session ;
- déploiement cloud / staging / HTTPS / HA ;
- rapports « officiels » (logo, PDF), export Excel ;
- sauvegarde / restauration outillée et testée.

---

## 10. Problèmes encore ouverts

1. **Rate-limiting `/auth/login`** — non implémenté (FINAL-017).
2. **Écrans manquants** pour `organization`, `pedagogical-assignments`,
   écritures `academic` / `enrollment`, émission d'invitation
   (FINAL-019).
3. **Isolation des tests** — profil `test` sur les mêmes conteneurs que
   `local` ; Testcontainers différé (FINAL-021).
4. **SCA de fond Maven** — absent (dependency-review différentielle +
   Dependabot seulement).
5. **Rétention / purge** — seule la purge de l'import CSV est outillée ;
   audit, invitations `PENDING` échues, présences : non purgés.
6. **Dette transactionnelle de l'audit** (héritée) — la plupart des
   listeners d'audit sont `@EventListener` + `REQUIRES_NEW` ; migration
   globale vers `@TransactionalEventListener(AFTER_COMMIT)` à planifier
   (le chemin d'import y échappe déjà).
7. **Démonstration UI de bout en bout** — non rejouée automatiquement ;
   parcours API vérifié, composants front couverts par 475 tests.
8. **Workflows CI F4** (dont `dependency-review`, FINAL-006) — premier
   run observé sur la PR #27. `dependency-review-action` échoue tant que
   le **Dependency Graph du dépôt n'est pas activé manuellement**
   (Settings → Security → Code security → *Dependency graph*) : « Please
   ensure that Dependency graph is enabled ». Le workflow est conservé
   (pas de `continue-on-error`) ; il redeviendra vert une fois le graphe
   activé. `npm audit --audit-level=high` en CI : OK (0 vulnérabilité).
   Le correctif CI post-F6 (§2) traite l'échec `frontend-ci`
   (`npm ci` / lockfile `@emnapi`).

---

## 11. Critères de livraison (`PROJECT_FINAL_AUDIT.md` §9)

| # | Critère | Satisfait ? |
|---|---|---|
| 9.1.1 | `git merge-base HEAD main == base` sur la branche de livraison | **OUI** (`e44ccb1`) |
| 9.1.2 | `CURRENT-STATE.md` = tête de `main`, aucune tranche fusionnée décrite « non fusionnée », chiffres alignés | **OUI** |
| 9.1.3 | `09-matrice-rncp.md` : une ligne `TR-*` par tranche fusionnée, reliée à des tests existants, pas de module inexistant sans mention « cible » | **OUI** (TR-024 ajoutée, TR-004/005 annotées) |
| 9.1.4 | `03-architecture.md` §7 : modules = modules réels | **OUI** (bloc « état réel ») |
| 9.1.5 | `README.md` racine suffisant pour cloner → lancer → se connecter | **OUI** |
| 9.2.6 | `./mvnw clean test` → `BUILD SUCCESS`, 0 échec, `ModularityTests` vert | **OUI** — 686 tests |
| 9.2.7 | `npm test` 0 échec ; `lint` pass ; `build` 0 alerte ; `npm audit` 0 haute/critique | **OUI** — 475 tests, 483,26 kB, 0 vuln. |
| 9.2.8 | Aucune anomalie bloquante ouverte | **OUI** (voir §10 : dettes assumées, non bloquantes) |
| 9.3.9 | `git log --all -- .env` vide ; aucun secret dans les fichiers suivis ; `.env.example` sans valeur réelle | **OUI** (F1) — lot F2–F6 n'ajoute aucun secret |
| 9.3.10 | Routes non publiques `@PreAuthorize` ; matrices `*SecurityTests` passent | **OUI** — inchangé + `HttpSecurityHeadersIntegrationTests` |
| 9.3.11 | CORS restrictif configuré et testé **ou** absence justifiée | **OUI** — configuré + testé (F5) |
| 9.3.12 | En-têtes de sécurité vérifiés par un test d'intégration ; CSP + `Referrer-Policy` ajoutés ou écart documenté | **OUI** — ajoutés + testés |
| 9.3.13 | CI : au moins un contrôle automatique des dépendances vulnérables | **OUI pour la configuration** — workflows `dependency-review` + `npm audit` livrés (F4) ; dépôt public, donc pas de dépendance à GitHub Advanced Security. **Exécution CI encore non observée** (aucune PR) → à confirmer au premier run vert : voir §10 point 8 et FINAL-006. |
| 9.4.14 | Parcours `CLAUDE.md` démontrable de bout en bout **ou** chaque maillon non réalisé listé explicitement hors périmètre | **OUI (2ᵉ branche)** — import planning listé hors périmètre partout |
| 9.4.15 | `docs/11` couvre connexion, import CSV (fichier d'exemple), séance + émargement + correction, rapport + export, résultats attendus | **OUI** — §11 + `docs/demo-data/` |
| 9.4.16 | Sélecteur de contexte de rôle démontrable (compte multi-rôles) | **OUI** — `responsable@example.test` |
| 9.4.17 | Données strictement fictives (`example.test`), vérifiées | **OUI** — CSV + comptes vérifiés |
| 9.5.18 | Chaque bloc RNCP a au moins une preuve exécutable ou versionnée ; fonctions simulées / conçues marquées comme telles | **OUI** — `docs/09` (dont notes F5/F6), `docs/11` §13 |

**Les critères de fin de projet sont satisfaits sur le fond** (code,
tests, sécurité applicative, documentation), avec deux réserves
explicites :

1. le critère **9.3.13** n'est satisfait qu'au niveau de la
   *configuration* CI — l'exécution réelle des workflows F4 (dont
   `dependency-review`, FINAL-006) n'a **jamais eu lieu** et reste **à
   vérifier sur la première PR** ;
2. les autres réserves du §10 (dettes assumées et documentées, non
   bloquantes pour la soutenance d'un prototype).

---

## 12. Interdictions respectées

- Aucun `git push`, aucune PR, aucune fusion, aucun `--force`.
- Aucun amendement d'un commit fusionné, aucun `squash`.
- Aucune migration Flyway ajoutée ou modifiée (`V1`–`V11` intactes).
- Aucune fonctionnalité `planning` / IA / IoT / PWA / WebAuthn / MFA
  présentée comme livrée.
- Aucune donnée personnelle réelle ; aucun secret affiché ou committé.
- Aucun test supprimé pour faire passer la suite.
- Aucun affaiblissement d'autorisation ou de validation.
- Aucun wildcard CORS ; aucun workflow `pull_request_target` ; aucun
  traitement de code de PR avec permission d'écriture.
- 5 commits de checkpoint (F2 → F6) + 1 commit documentaire de clôture
  (`e9e6a70`) = **6 commits intentionnels** ; le présent commit
  correctif `docs/` s'y ajoute. Aucun nombre *total* de commits n'était
  imposé — seulement « un commit par checkpoint ».
