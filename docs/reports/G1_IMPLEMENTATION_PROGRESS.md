# G1 — Suivi d'implémentation (grand lot produit)

> Journal de reprise du **grand lot produit G1** (« montée en gamme
> fonctionnelle d'ESIC Connect »). Mis à jour **à la fin de chaque bloc**
> (G1-0 → G1-G) et à chaque interruption de session. Ne remplace ni
> `docs/CURRENT-STATE.md` (état du dépôt) ni
> `docs/reports/G1_FINAL_REPORT.md` (rapport final, produit à la fin).

## Date de référence

```text
31 août 2026
```

## Base Git

| Élément | Valeur |
|---|---|
| Branche de travail | `feature/master-level-product-expansion` |
| Base (`merge-base` avec `main`) | `4580b4489dceca08bd171d10f2f84820962e8031` |
| `main` au démarrage | `4580b4489dceca08bd171d10f2f84820962e8031` (Merge PR #27) |
| Pousser / PR / fusion | **Non** — travail local uniquement |

## Tests de référence (avant tout code métier)

Relevés le 31 août 2026 → 1er septembre 2026 (nuit), services Docker
`mysql` 8.4 / `redis` 7.4 up, OpenJDK 21.0.12, Vitest 4.1.11,
npm 11.6.2, `.env` local chargé, base back jetable `esic_test`.

### Front-end — VERT

| Commande | Résultat |
|---|---|
| `npm ci` | OK |
| `npm test -- --watch=false` | **55 fichiers, 475 tests, 0 échec** |
| `npm run lint` | « All files pass linting » |
| `npm run build` | bundle OK, 0 alerte de budget (< 500 kB) |
| `npm audit --audit-level=high` | **0 vulnérabilité** |

### Back-end — VERT sous `TZ=UTC` ; 7 échecs sous `TZ=Europe/Paris` dans la fenêtre 00:00–02:00 CEST

| Run | Résultat |
|---|---|
| `./mvnw clean test` (TZ machine = `Europe/Paris`, ~01:30 CEST, base `esic_connect`) | `Tests run: 686, Failures: 7, Errors: 0` — **7 échecs** dans `AttendanceIntegrationTests` |
| `./mvnw clean test` (idem, base **jetable** `esic_test`) | **mêmes 7 échecs** → reproductible, indépendant de la contamination de base |
| `./mvnw test -Dtest=AttendanceIntegrationTests` **`TZ=UTC`** | **`Tests run: 25, Failures: 0`** → vert |
| `./mvnw clean test` **`TZ=UTC`** (base `esic_test`) | **`Tests run: 686, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS** → **baseline back VERTE confirmée** (1er sept. 2026, ~01:55 CEST) |

> **Conclusion baseline.** Front-end vert (475). Back-end vert (686/0)
> sous `TZ=UTC` (= environnement CI). Les 7 échecs observés en
> `TZ=Europe/Paris` sont l'artefact de fenêtre horaire décrit au §9, sur
> un bug latent hors périmètre G1. **La base est verte** : les blocs de
> code G1 peuvent démarrer, runs back sous `TZ=UTC`.

## §9 — Analyse du blocage back-end (fenêtre horaire)

**Cause racine (bug latent PRÉ-EXISTANT, hors périmètre G1) :**

- `EnrollmentService.create`
  (`backend/.../enrollment/internal/EnrollmentService.java:101`) fixe par
  défaut `start_date = LocalDate.now(clock)` avec une `Clock` en **zone
  système** (`Clock.systemDefaultZone()` — `ClockConfig`). Machine en
  `Europe/Paris` → `2026-09-01`.
- `AttendanceService.validate`
  (`backend/.../attendance/internal/AttendanceService.java:130`) résout
  les inscriptions actives avec
  `LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)` → **`2026-08-31`**
  tant que l'heure UTC n'a pas franchi minuit.
- `DefaultEnrollmentDirectory.coversDate` évalue alors
  `startDate(2026-09-01) <= date(2026-08-31)` → `false` →
  `ATT_NOT_ENROLLED` (`409`).
- `AttendanceIntegrationTests` n'installe **pas** de `Clock` figée : le
  décalage se manifeste uniquement dans la fenêtre où la date locale
  (Paris, été = UTC+2) diffère de la date UTC, soit **`00:00–02:00 CEST`**.

**Portée.** 6 échecs `ATT_NOT_ENROLLED` + 1
(`twoConcurrentValidationsYieldExactlyOne200AndOne409`, qui dépend d'une
validation réussie). Tous dans `AttendanceIntegrationTests`. Les
baselines F1 / F6 (« 682 » puis « 686 ») étaient vertes car exécutées
hors de cette fenêtre.

**Statut.** **Non bloquant.** La suite back est **verte** (686 / 0) sous
`TZ=UTC` — l'environnement de la CI. Les blocs de code G1 démarrent, avec
runs back sous `TZ=UTC`. Le point reste une **dette identifiée** (bug
latent hors périmètre G1).

**Contournements (aucun changement de code) :**
1. `TZ=UTC ./mvnw clean test` — c'est déjà l'environnement de la CI
   (`compose.yaml` : `mysql` `TZ: UTC`, `--default-time-zone=+00:00` ;
   service MySQL CI éphémère). Retenu comme mode de run local pendant G1.
2. Attendre 02:00 CEST (réalignement date locale / UTC).

**Décision.** Ne **pas** corriger ce bug dans G1 (hors périmètre
« montée en gamme fonctionnelle » ; toucher `attendance` /
`enrollment` sans exigence viole les règles anti-régression). Le
consigner ici, dans `docs/06-risques.md` (R-G1-20) et dans le rapport
final comme dette identifiée. Les runs back de G1 sont faits sous
`TZ=UTC` et ce point est rappelé à chaque bloc.

## Blocs

| Bloc | Intitulé | Statut | Commit |
|---|---|---|---|
| G1-0 | Gel des exigences et décisions d'architecture | `DONE` (documentaire) | _à venir : `docs(g1): figer les exigences et décisions d'architecture`_ |
| G1-A | Interfaces Angular des API existantes | `NOT_STARTED` | — |
| G1-B | Module `planning` complet | `NOT_STARTED` | — |
| G1-C | Cycle de vie avancé des séances | `NOT_STARTED` | — |
| G1-D | Notifications métier persistantes | `NOT_STARTED` | — |
| G1-F | Tableaux de bord par rôle | `NOT_STARTED` | — |
| G1-E | Pièces jointes des justificatifs | `NOT_STARTED` | — |
| G1-G | Recette globale, e2e, documentation finale | `NOT_STARTED` | — |

## Livrables G1-0

| Livrable | Fichier | État |
|---|---|---|
| 1 — Traçabilité des exigences | `docs/reports/G1_REQUIREMENTS_TRACEABILITY.md` | créé |
| 2 — Décisions d'architecture `DEC-G1-001..012` | `docs/reports/G1_ARCHITECTURE_DECISIONS.md` | créé |
| 3 — Backlog G1-A..G1-G | `docs/05-product-backlog.md` §9bis | mis à jour |
| 4 — Risques G1 | `docs/06-risques.md` §7bis (`R-G1-01..24`) | mis à jour |
| 5 — Plan d'implémentation | `docs/reports/G1_IMPLEMENTATION_PLAN.md` | créé |
| 6 — Suivi d'avancement | ce fichier | créé |

## Décisions ouvertes (à trancher au moment de leur bloc)

- `DEC-G1-002` — forme exacte de la `business_key` (attributs retenus) :
  esquisse figée, à confirmer sur données réelles au bloc G1-B.
- `DEC-G1-003` — extraction du guard CSV vers `shared` **ou** duplication
  minimale : décidé à la lecture du code `studentimport.internal` en G1-B.
- `DEC-G1-003` — correction ligne à ligne du planning **ou** annulation +
  réimport : décidé en G1-B selon la complexité réelle.
- `DEC-G1-007` — Event Publication Registry (`spring-modulith-starter-jpa`)
  vs listeners applicatifs idempotents : **listeners retenus pour G1** ;
  registry tracé comme dette post-G1.
- `DEC-G1-011` — Playwright vs démonstration API automatisée : vérifié au
  bloc G1-G.
- Bug latent de fuseau (§9) : **non corrigé en G1**, tracé comme dette.

## Dernier commit produit

```text
(aucun — G1-0 prêt à être commité)
```

## Commandes de reprise

```bash
cd /Users/kingafolabi/Desktop/projet_final
git switch feature/master-level-product-expansion
git log --oneline main..HEAD

# Base back jetable (évite de polluer esic_connect ; cf. TEST_ISOLATION_DECISION.md)
set -a && source .env && set +a
docker exec -i esic-connect-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
  -e "CREATE DATABASE IF NOT EXISTS esic_test; GRANT ALL ON esic_test.* TO '$MYSQL_USER'@'%';"

# Backend — TOUJOURS sous TZ=UTC pendant G1 (cf. §9)
cd backend
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
set -a && source ../.env && set +a
export MYSQL_DATABASE=esic_test
TZ=UTC ./mvnw clean test

# Frontend
cd ../frontend
npm ci && npm test -- --watch=false && npm run lint && npm run build && npm audit --audit-level=high
```

## Prochaine étape

1. `git add` des six livrables G1-0, `git diff --check`, commit
   `docs(g1): figer les exigences et décisions d'architecture`.
2. **Avant G1-A** : confirmer la suite back verte (686 / 0 échec) via
   `TZ=UTC ./mvnw clean test` (run en cours) ou après 02:00 CEST.
3. Démarrer G1-A : audit endpoint → écran (§3.1 du plan), puis écrans
   `organization`, écritures `academic`, affectations pédagogiques,
   profils / inscriptions / transferts, émission d'invitation.
