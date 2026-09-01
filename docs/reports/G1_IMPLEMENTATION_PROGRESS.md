# G1 — Suivi d'implémentation (grand lot produit)

> Journal de reprise du **grand lot produit G1** (« montée en gamme
> fonctionnelle d'ESIC Connect »). Mis à jour **à la fin de chaque bloc**
> (G1-0 → G1-G) et à chaque interruption de session. Ne remplace ni
> `docs/CURRENT-STATE.md` (état du dépôt) ni
> `docs/reports/G1_FINAL_REPORT.md` (rapport final, produit à la fin).

## Date de référence

```text
31 août 2026 — checkpoint G1-0
1er septembre 2026 — checkpoint correctif G1-0.1
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

### Back-end — VERT dans les trois modes de fuseau (après correctif G1-0.1)

| Run | Résultat |
|---|---|
| `./mvnw clean test` (sans `TZ` forcé — machine `Europe/Paris`) | **`Tests run: 693, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS** |
| `TZ=UTC ./mvnw clean test` | **`Tests run: 693, Failures: 0` — BUILD SUCCESS** |
| `TZ=Europe/Paris ./mvnw clean test` | **`Tests run: 693, Failures: 0` — BUILD SUCCESS** |

> **Conclusion baseline (1er sept. 2026, après G1-0.1).** Front-end vert
> (475). Back-end vert (**693 / 0**) dans les trois modes de fuseau, y
> compris exécuté dans la fenêtre `00:00–02:00 CEST` autrefois cassante.
> Les 686 → 693 = +7 tests déterministes de
> `AttendanceServiceSessionDateTests` (horloge figée). Le contournement
> « runs back sous `TZ=UTC` » n'est **plus nécessaire** : voir §9.

> **Historique (avant G1-0.1).** Baseline G1-0 : back « vert sous
> `TZ=UTC` uniquement ; 7 échecs `AttendanceIntegrationTests` sous
> `TZ=Europe/Paris` dans la fenêtre `00:00–02:00 CEST` » (686 tests). Ce
> défaut est corrigé au checkpoint G1-0.1.

## §9 — Défaut temporel du socle : corrigé (checkpoint G1-0.1)

**Cause racine (bug latent PRÉ-EXISTANT).**

- `EnrollmentService.enroll`
  (`backend/.../enrollment/internal/EnrollmentService.java:101`) fixe par
  défaut `start_date = LocalDate.now(clock)` avec une `Clock` en **zone
  système** (`Clock.systemDefaultZone()` — `ClockConfig`). Machine en
  `Europe/Paris` → `2026-09-01`.
- `AttendanceService.validate`
  (`backend/.../attendance/internal/AttendanceService.java:131`, avant
  correctif) résolvait les inscriptions actives avec
  `LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)` → **`2026-08-31`**
  tant que l'heure UTC n'avait pas franchi minuit. **Double erreur** :
  (a) c'est la date *courante*, pas la date *de la séance* ; (b) elle est
  projetée en UTC, pas dans le fuseau de la séance.
- `DefaultEnrollmentDirectory.coversDate` évaluait alors
  `startDate(2026-09-01) <= date(2026-08-31)` → `false` →
  `ATT_NOT_ENROLLED` (`409`).
- `AttendanceIntegrationTests` n'installait **pas** de `Clock` figée : le
  décalage ne se manifestait que dans la fenêtre où la date locale
  (Paris, été = UTC+2) diffère de la date UTC, soit **`00:00–02:00 CEST`**
  (6 échecs `ATT_NOT_ENROLLED` + 1 concurrence dépendante).

**Politique retenue (date métier d'une séance).** Pour décider si une
inscription couvre une validation de présence, utiliser la **date civile
de la séance** :

```java
session.startsAt().atZone(ZoneId.of(session.timeZoneId())).toLocalDate()
```

Jamais la date courante, jamais une projection UTC. C'est déjà la
convention de `AttendanceManagementService.sessionLocalDate` et de
`AttendanceReportService.persistedZone` (correctif PR #22). Un fuseau
persisté invalide lève une erreur interne contrôlée (jamais un repli
silencieux sur UTC).

**Corrections de code (checkpoint G1-0.1).**

| Fichier | Avant | Après |
|---|---|---|
| `AttendanceService.validate` | `LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)` | `sessionLocalDate(session)` (startsAt projeté dans le fuseau persisté) + helpers `sessionLocalDate` / `persistedZone` |
| `AttendanceJustificationService.resolveOwnEnrollment` | `LocalDate.ofInstant(session.startsAt(), ZoneOffset.UTC)` | `session.startsAt().atZone(persistedZone(session.timeZoneId())).toLocalDate()` (même défaut de fuseau, plus discret : une séance commençant juste après minuit local était rattachée à la veille) |
| `AttendanceIntegrationTests` (fixtures) | inscriptions sans `startDate` (défaut `LocalDate.now`) | `startDate = 2026-08-01` explicite (antérieure à toutes les dates de séance des fixtures) → suite indépendante de l'heure d'exécution |

`ClockConfig` **n'est pas** modifié : `Clock.systemDefaultZone()` reste
correct pour les usages légitimes de « maintenant » (`EnrollmentService`
date de début par défaut, `AcademicScopeGuard`, calcul du retard réel
dans `AttendanceService`, purge d'import…). Aucune bascule globale vers
UTC.

**Tests ajoutés — `AttendanceServiceSessionDateTests` (7, déterministes,
horloge figée, aucune dépendance à l'heure réelle) :**

- couverture d'inscription évaluée à la date civile de la séance
  `2026-03-30` (séance saisie à `Europe/Paris`, début `2026-03-29T22:30Z`
  = `00:30` heure locale) — vérifiée pour **5 valeurs d'horloge**
  (`@ParameterizedTest`), dont `2026-08-31T23:30:00Z` (la fenêtre de la
  panne, UTC = 31/08 ≠ Paris = 01/09) et deux instants très éloignés de
  la séance : la valeur de l'horloge n'entre jamais dans la décision ;
- la date de décision n'est **jamais** égale à la date UTC de `startsAt`
  (`2026-03-29`) ni à `LocalDate.now(clock)` ;
- séance dont la date métier **est** couverte par l'inscription → `200`
  (`PRESENT` ou `LATE`) ;
- séance dont la date métier **n'est pas** couverte → `AttendanceException`
  `NOT_ENROLLED`, même si une couverture existerait à la date UTC (elle
  n'est jamais consultée) ;
- l'horloge fournie aux tests est `Clock.fixed(...)`.

**Statut.** **Résolu.** Suite back **verte (693 / 0)** dans les trois
modes de fuseau, y compris exécutée dans la fenêtre autrefois cassante.
`docs/06-risques.md` R-G1-20 mis à jour (résolu).

## Blocs

| Bloc | Intitulé | Statut | Commit |
|---|---|---|---|
| G1-0 | Gel des exigences et décisions d'architecture | `DONE` (documentaire) | `f3691bd` — `docs(g1): figer les exigences et décisions d'architecture` |
| G1-0.1 | Correctif : dates métier + audit documentaire du socle | `DONE` | `01a6068` — `fix(g1): stabiliser les dates métier et corriger le socle` |
| G1-A | Interfaces Angular des API existantes | `IMPLEMENTED_FULL_SUITE_GREEN` (référentiel organisationnel livré ; écritures `academic`/`enrollment`/affectations/invitation = dette assumée, cf. plan §3.1) | `2cf1416` — `feat(frontend): exposer les parcours administratifs existants` |
| G1-B | Module `planning` complet | `IMPLEMENTED_FULL_SUITE_GREEN` — back-end (schéma `e4793e7`, simulation `24cc9f5`, publication `dafd23a`) **+ parcours Angular** (`429f45b`). Reste post-G1 : avertissements d'alternance (`DEC-G1-006`), création manuelle plein calendrier (`EF-PLAN-006`, hors périmètre G1). | `e4793e7` + `24cc9f5` + `dafd23a` + `429f45b` |
| G1-B.1 | Audit correctif du bloc `planning` | `IMPLEMENTED_FULL_SUITE_GREEN` — identité de créneau (`planning_slot_public_id`), concurrence idempotente durcie, rollback+`FAILED` déterministe, garde centralisée « séance supersédée = inactive », conflit vs séances déjà publiées (formateur/classe), traçabilité reclassée, script de démo. Suites **719/0** (3 fuseaux) / **550/0**. | `fix(planning): consolider l'identité et la publication atomique` + `docs(g1): corriger la traçabilité après audit du planning` |
| G1-C.1 | Annulation des séances (`CANCELLED`) | `IMPLEMENTED_FULL_SUITE_GREEN` — V14, `SessionLifecycle.CANCELLED`, `POST /sessions/{id}/cancel`, garde `operational()`, purge jetons, UI. Back **723/0**, front **554/0**. | `feat(coursesession): gérer l'annulation des séances` |
| G1-C.2 | Remplacements de formateur | `IN_PROGRESS` — table `teacher_substitution` en base (V14), code à écrire | — |
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

## Correctif G1-0.1 (1er septembre 2026)

**Défaut.** Baseline G1-0 verte **uniquement** sous `TZ=UTC` : 7 échecs
`AttendanceIntegrationTests` sous `TZ=Europe/Paris` dans la fenêtre
`00:00–02:00 CEST`. La baseline masquait le défaut au lieu de le
corriger.

**Cause racine.** `AttendanceService.validate` décidait la couverture
d'une inscription avec `LocalDate.ofInstant(clock.instant(),
ZoneOffset.UTC)` — la date *courante* en UTC — au lieu de la **date
civile de la séance**. Dans la fenêtre où la date locale diffère de la
date UTC, une inscription tout juste créée pour le jour local était
écartée → `ATT_NOT_ENROLLED` (`409`). Variante latente identique dans
`AttendanceJustificationService` (`session.startsAt()` projeté en UTC et
non dans le fuseau de la séance). Détail : §9.

**Solution.** Projeter `session.startsAt()` dans
`ZoneId.of(session.timeZoneId())` (convention déjà en place dans
`AttendanceManagementService` / `AttendanceReportService`). Helpers
privés `sessionLocalDate` / `persistedZone` ajoutés aux deux services
(fuseau invalide → erreur interne contrôlée, jamais de repli UTC).
`ClockConfig` **inchangé** (aucune bascule globale UTC). Fixtures
`AttendanceIntegrationTests` : `startDate` d'inscription explicite
(`2026-08-01`) → suite indépendante de l'heure d'exécution.

**Tests ajoutés.** `AttendanceServiceSessionDateTests` (7 ; Mockito ;
`Clock.fixed` ; `@ParameterizedTest` sur 5 valeurs d'horloge dont la
fenêtre de la panne et deux instants très éloignés ; couvert / non
couvert ; assertion « ≠ date UTC » et « ≠ `LocalDate.now(clock)` »).

**Validation.** `./mvnw clean test` **693 / 0** sans `TZ`, sous `TZ=UTC`
et sous `TZ=Europe/Paris` (exécuté dans la fenêtre autrefois cassante).
Front : `npm run lint` OK, `npm test -- --watch=false` **475 / 0**,
`npm run build` `483.26 kB` (0 alerte de budget), `npm audit
--audit-level=high` 0 vuln.

**Contradictions documentaires corrigées (audit factuel du socle) :**

| Réf | Constat vérifié dans le code | Correction |
|---|---|---|
| A — listeners d'audit | 10 classes, **9** en `@EventListener` (synchrone) + `@Transactional(REQUIRES_NEW)` ; **1 seule** (`StudentImportAuditListener`) en `@TransactionalEventListener(AFTER_COMMIT)`. `SecurityAuditEventListener` a 2 méthodes → 11 *méthodes* de handler. Les javadoc du code disent explicitement « contrairement au motif … du reste du projet » et « migration globale vers `AFTER_COMMIT` … reste à faire ». | `G1_ARCHITECTURE_DECISIONS.md` §Contexte + `DEC-G1-007` : « 11 listeners `AFTER_COMMIT` » → motif réel décrit ; `DEC-G1-007` note que le nouveau listener de notifications sera `AFTER_COMMIT` + `REQUIRES_NEW` (aligné sur le seul `StudentImportAuditListener`, pas sur la majorité). |
| B — RG-012 / RG-015 | Deux numérotations : `CAD §24` (`RG-01..RG-30`) et `CDC §43` (`RG-001..RG-088`). `CDC §43 RG-012` = « un apprenant appartient à une seule classe principale active » ; « un remplacement est autorisé et audité » est `CAD §24 RG-12`. `CDC §43 RG-015` = « une séance peut posséder un remplaçant autorisé » (citation correcte). | `G1_REQUIREMENTS_TRACEABILITY.md` §1 + §4 : RG-012 re-cité en `CAD §24 RG-12` ; note sur les deux numérotations ; `CDC §43 RG-015` + `RG-017` conservés. |
| C — identité d'un créneau | `DEC-G1-002` met `start_time`/`end_time` dans la `business_key` **et** `DEC-G1-004` règle 5 présente un changement d'horaire comme une *modification* du même créneau → contradiction (avec l'horaire dans la clé, un changement d'horaire = nouvelle clé). | `DEC-G1-002` : identité stable explicite = colonne `slot_key` **obligatoire dans le CSV G1** (extension assumée de `CDC §13.3`, qui ne l'interdit pas) ; unicité `(planning_schedule_id, slot_key)` ; date/horaire/titre/formateur/salle = propriétés modifiables. **Repli documenté** si `slot_key` refusé : un changement d'horaire devient `REMOVED` + `ADDED` (pas de reconnaissance de modification sans identité stable). `DEC-G1-004` règle 5 alignée. |
| D — port inter-modules | `DEC-G1-001` expose `long teacherUserId` (clé SQL interne) dans `PlannedSession`. | `DEC-G1-001` : `UUID teacherPublicId` ; `roomPublicId` conservé (nullable, déjà prévu) ; `coursesession` résout l'UUID en interne. Aucune clé SQL dans un port public. |
| E — publication atomique / `FAILED` | `DEC-G1-003` : « rollback tout **et** le job passe `FAILED` » dans la même transaction → impossible (le `FAILED` serait annulé). | `DEC-G1-003` : publication = **une** transaction atomique ; l'orchestrateur externe, après rollback, écrit `FAILED` dans une transaction `REQUIRES_NEW` **distincte, sans donnée métier publiée** ; conflits métier → `ProblemDetail` contrôlé, jamais `500`. Tests correspondants listés. |
| F — contrainte MySQL | `DEC-G1-001` parle d'« unique `(planning_entry_public_id)` **partielle** » — MySQL n'a pas d'index partiel. | `DEC-G1-001` : `UNIQUE (planning_entry_public_id)` simple ; documenté que MySQL autorise plusieurs `NULL` sous un index `UNIQUE` et garde les UUID non nuls uniques. |
| G — ordre des migrations | `DEC-G1-012` : `V13` étiquetée « G1-B + G1-C » (mélange planning + `teacher_substitution`). | `DEC-G1-012` + plan §11 : un domaine par migration — `V12` planning ; `V13` lien `course_session ↔ planning_entry` + discriminant d'origine (G1-B) ; `V14` cycle de vie + `teacher_substitution` (G1-C) ; `V15` `notification` (G1-D) ; `V16` `justification_attachment` (G1-E). Références `V14`/`V15` propagées dans les autres docs. |
| H — matrice de rôles | Matrice planning (plan §4.6) sans distinction « exigence explicite » vs « décision d'architecture ». | Plan §4.6 : colonne « Source » (RG-030/031 explicites ; le reste = `DEC-G1` faute d'exigence numérotée) ; aucune hypothèse présentée comme règle existante. |
| I — schéma `course_session` réel | `DEC-G1-001` / `DEC-G1-004` parlent de `exceptional = true/false` comme d'une colonne. Réel (V9 + entité) : **pas** de colonne `exceptional`, **pas** de `room`/`site`/`subject` ; `exception_reason` `VARCHAR(500) NOT NULL` sur **toute** séance ; `status ∈ {PLANNED,OPEN,CLOSED}`. | `DEC-G1-001` / `DEC-G1-004` : « séance exceptionnelle » = description, pas un champ ; G1-B **ajoute** en `V13` un discriminant d'origine (`planning_entry_public_id IS NULL` ⇒ exceptionnelle) ; les colonnes `room`/`site` supposées sont retirées du texte. |

**Décisions réellement encore ouvertes (inchangées) :**
- `DEC-G1-002` — attributs exacts de `slot_key` / `business_key` : à
  confirmer sur données réelles au bloc G1-B ; le repli (`REMOVED` +
  `ADDED` sur changement d'horaire) est arrêté.
- `DEC-G1-003` — guard CSV : extraction vers `shared` **ou** duplication
  minimale — décidé à la lecture de `studentimport.internal` en G1-B.
- `DEC-G1-003` — correction planning ligne à ligne **ou** annulation +
  réimport — décidé en G1-B.
- `DEC-G1-007` — Event Publication Registry vs listeners idempotents :
  **listeners retenus pour G1** ; registry tracé comme dette post-G1.
- `DEC-G1-011` — Playwright vs démonstration API automatisée : vérifié au
  bloc G1-G.

## Bloc G1-A — référentiel organisationnel Angular (1er septembre 2026)

- **HEAD de départ** : `01a6068`.
- **État** : `IMPLEMENTED_FULL_SUITE_GREEN`.
- **Périmètre livré** : le module back-end `organization` était le seul
  sans **aucun** écran Angular (CS « API seule »). Livré de bout en bout :
  `EF-ROOM-001` (sites / bâtiments / salles) + plages réseau CIDR
  (`SUPER_ADMIN`).
- **Fichiers front principaux** (`frontend/src/app/features/organization/`) :
  `organization.models.ts`, `organization-api.service.ts` (+ `.spec`),
  `organization-errors.ts` (+ `.spec`), `organization-paginator.ts`,
  `organization.shared.scss`, `site-list/` (+ `.spec`), `site-form/`
  (+ `.spec` + `.a11y.spec`), `site-detail/` (+ `.spec`).
- **Câblage** : `app.routes.ts` (arbre `/organization/**`, gardes
  `ORGANIZATION_READ_ROLES` / `ORGANIZATION_WRITE_ROLES`),
  `core/navigation/navigation.ts` (entrée « Organisation »),
  `navigation.spec.ts` + `app-shell.spec.ts` mis à jour.
- **Migrations** : aucune (bloc front-only).
- **Endpoints consommés** (tous préexistants, aucun inventé) :
  `GET/POST /api/v1/sites`, `GET/PATCH /api/v1/sites/{id}`,
  `POST /api/v1/sites/{id}/archive|restore`,
  `GET/POST /api/v1/sites/{id}/buildings`,
  `PATCH /api/v1/buildings/{id}`, `POST /api/v1/buildings/{id}/archive|restore`,
  `GET/POST /api/v1/sites/{id}/rooms`, `PATCH /api/v1/rooms/{id}`,
  `POST /api/v1/rooms/{id}/archive|restore`,
  `GET/POST /api/v1/sites/{id}/network-ranges`,
  `POST /api/v1/network-ranges/{id}/activate|deactivate`.
- **Routes UI ajoutées** : `/organization/sites`, `/organization/sites/new`,
  `/organization/sites/:publicId`, `/organization/sites/:publicId/edit`.
- **Tests ajoutés** : front **+48** (`475 → 523`), 7 fichiers de specs
  (`organization-api.service`, `organization-errors`, `site-list`,
  `site-form`, `site-detail`, `site-form.a11y`, + 2 cas de navigation).
- **Commandes exécutées** :
  - `npm run lint` → « All files pass linting » ;
  - `npm test -- --watch=false` → **61 fichiers / 523 tests / 0 échec** ;
  - `npm run build` → **484,07 kB** brut (0 alerte de budget < 500 kB) ;
  - `npm audit --audit-level=high` → **0 vulnérabilité** ;
  - back-end (non modifié par ce bloc) `./mvnw clean test` → surefire
    **693 tests / 0 échec / 0 erreur** (l'exit code 1 observé venait du
    `grep` final sous `mvnw -q` qui masque la ligne de résumé, pas d'un
    test).
- **Décisions** :
  - `DEC-G1-A-SCOPE` (session autonome) : seul le référentiel
    organisationnel est livré cette session ; `EF-ACA-001..005`,
    `EF-USER-001`, `EF-AUTH-004` restent une **dette assumée de G1-A**,
    l'audit complet des rôles réels est figé dans le plan §3.1 (aucune
    UI ne simule un endpoint absent).
  - `window.prompt` pour le motif d'archivage d'un bâtiment / d'une
    salle (confirmation native, clavier-accessible) ; le motif
    d'archivage d'un **site** utilise un panneau de confirmation en
    ligne (motif obligatoire, cohérent avec `user-detail`).
  - Panneau « Plages réseau » rendu uniquement pour un contexte
    `SUPER_ADMIN` (masquage ergonomique ; `SiteNetworkRangeController`
    reste l'autorité, lecture comprise).
- **Limites connues G1-A** : pas d'écran d'édition de bâtiment / salle
  (seulement création + archivage/restauration) ; pas de pagination des
  sous-listes (chargées à `size=100`) ; écritures académiques /
  inscriptions / affectations / émission d'invitation non livrées.
- **Prochain bloc** : G1-B (module `planning`).

## Bloc G1-B — module `planning` : checkpoint schéma + modèle (1er septembre 2026)

- **HEAD de départ** : `2cf1416`.
- **État** : `IMPLEMENTED_TARGETED_TESTS_GREEN` pour le checkpoint
  « schéma + modèle » ; le bloc G1-B dans son ensemble reste
  `IN_PROGRESS` (simulation, publication atomique via le port,
  versionnement, endpoints, écrans Angular = checkpoints suivants).
- **Migrations créées** :
  - `V12__create_planning_tables.sql` — 7 tables (`planning_schedule`,
    `planning_version`, `planning_entry`, `planning_import_job`,
    `planning_import_job_issue`, `planning_import_row`,
    `planning_import_row_issue`) + index + `CHECK`. `CASCADE` limité à la
    chaîne technique `planning_import_*`. Aucune donnée métier.
  - `V13__link_course_session_to_planning.sql` — additif non destructif
    sur `course_session` : `+ planning_entry_public_id BINARY(16) NULL
    UNIQUE` (lien **et** discriminant d'origine, DEC-G1-001),
    `+ superseded_by_scheduling BOOLEAN NOT NULL DEFAULT FALSE`
    (DEC-G1-004 règle 4), `exception_reason` rendue **nullable**.
- **Fichiers back principaux** :
  - `com/esic/connect/planning/package-info.java` (`@ApplicationModule`) ;
  - `planning/internal/` : 6 enums (`PlanningScheduleStatus`,
    `PlanningVersionStatus`, `PlanningImportJobStatus`,
    `PlanningRowStatus`, `PlannedAction`, `PlanningIssueSeverity`),
    7 entités JPA, 7 repositories (verrous `FOR UPDATE` sur
    `PlanningScheduleRepository` / `PlanningImportJobRepository`),
    `PlanningWeb` (rôles `@PreAuthorize`), `PlanningException` +
    `Kind`, `PlanningIssueCodes`, `PlanningProperties` (préfixe
    `app.planning`, validée) + `PlanningConfig` ;
  - `com/esic/connect/coursesession/PlanningSessionWriter.java` — **port
    d'écriture public** (DEC-G1-001) : `sync(PlanningSyncCommand)`,
    records `PlannedSession` / `PlanningSyncResult` / `SyncedSession` /
    `SupersededSession`, `PlanningSessionSyncException` (UUID publics
    uniquement, jamais de clé SQL) — **pas encore d'implémentation** ;
  - `com/esic/connect/coursesession/internal/CourseSession.java` —
    `exception_reason` nullable + champs `planningEntryPublicId` /
    `supersededByScheduling` mappés (getters) ;
  - `application.yml` — bloc `app.planning` (bornes de durée + fenêtre
    horaire configurables).
- **Endpoints / routes UI** : aucun à ce checkpoint (pas de coquille
  vide, pas de `501`).
- **Tests ajoutés** : aucun test unitaire propre au module à ce
  checkpoint (schéma + modèle) ; la couverture arrive avec la
  simulation. Régression vérifiée sur toute la suite existante.
- **Décisions (à confirmer à l'implémentation de la simulation)** :
  - `DEC-G1-B-CSV` : le CSV G1 porte une colonne
    **`teacher_public_id`** (identifiant public du compte formateur),
    pas `teacher_email` — résolue par `TeacherDirectory.findEligibleTeacher(UUID)`,
    aucun port de résolution par e-mail à ajouter à `identity`, légère
    réduction de PII. `slot_key` reste obligatoire (DEC-G1-002).
  - `DEC-G1-B-ROOM` : la salle reste un **code fonctionnel**
    (`planning_entry.room_code VARCHAR`), pas une FK vers `room` : salle
    affectable après l'import (RG-035), sert seulement à la détection de
    conflit ; aucun port de résolution de salle requis en G1.
  - `DEC-G1-B-SUPERSEDE` : la « supersession » d'une séance planning
    retirée (DEC-G1-004 règle 4) se limite en G1-B à
    `superseded_by_scheduling = true` (la séance reste `PLANNED` : l'état
    `CANCELLED` n'existe pas avant G1-C ; enum `SessionLifecycle` =
    `PLANNED/OPEN/CLOSED`, `V13` n'introduit aucun élément de cycle de
    vie G1-C). Une séance `superseded_by_scheduling = true` est filtrée
    de l'affichage.
  - `csv_separator` mappé en `char` primitif, `file_sha256` avec
    `@JdbcTypeCode(SqlTypes.CHAR)`, `file_size_bytes` en `int` — aligné
    sur `StudentImportJob` (`ddl-auto=validate` strict char/varchar/int).
- **Commandes exécutées** :
  - `./mvnw compile` → OK ;
  - `DROP/CREATE DATABASE esic_test` puis
    `./mvnw test -Dtest='ModularityTests,AuditEventTests'` → **BUILD
    SUCCESS** (Flyway rejoue `V1 → V13`, `ddl-auto=validate` sans erreur,
    `ModularityTests` vert : le module `planning` ne viole aucune
    frontière) ;
  - `./mvnw clean test` (suite complète) → **`Tests run: 693, Failures:
    0, Errors: 0, Skipped: 0` — BUILD SUCCESS** (aucune régression ;
    seul `CourseSession` a changé, de façon additive).
- **Limites connues (checkpoint)** : parsing CSV, simulation (T1),
  détection de conflit (DEC-G1-005), alternance (DEC-G1-006), publication
  atomique + `PlanningSessionWriter` impl (DEC-G1-001/003), versionnement,
  endpoints REST, purge `@Scheduled`, écrans Angular — **non
  implémentés**.

## Bloc G1-B — checkpoint simulation CSV (1er septembre 2026)

- **HEAD de départ** : `e4793e7`.
- **État** : `IMPLEMENTED_TARGETED_TESTS_GREEN` pour le checkpoint
  simulation ; publication / versionnement / UI = checkpoints suivants.
- **Fichiers back principaux ajoutés** (`planning/internal/`) :
  `PlanningColumn` (8 colonnes, `slot_key` + `teacher_public_id` +
  `room_code`), `PlanningCsvGuard` (duplication minimale de
  `CsvFileGuard`, `DEC-G1-003`), `PlanningCsvValues` (date / heure /
  fuseau / SHA-256 / troncature), `PlanningCsvParser` +
  `ParsedPlanningCsv` (RFC 4180, séparateur auto, en-tête par nom),
  `PlanningReferenceResolver` (ports `ClassGroupDirectory` /
  `AcademicScopeDirectory` / `TeacherDirectory`),
  `PlanningSimulationService` (invariant T1 : n'écrit que
  `planning_import_*` ; valeurs, formateur, doublon `slot_key`
  intra-fichier, conflits formateur/classe/salle + hors plage horaire —
  `DEC-G1-005` ; comparaison `ADDED`/`MODIFIED`/`UNCHANGED` + compteur de
  retraits — `DEC-G1-002/004`), `PlanningQueryService` (get / rows
  paginé / cancel idempotent ; périmètre serveur : jobs de l'appelant),
  `PlanningResponses` / `PlanningPageResponse` / `PlanningQuerySupport`,
  `PlanningExceptionHandler` (codes `PLAN_*`), `PlanningImportController`.
- **Endpoints ajoutés** :
  `POST /api/v1/planning-imports` (multipart `file` + `classGroupPublicId`,
  `201`), `GET /api/v1/planning-imports/{id}`,
  `GET /api/v1/planning-imports/{id}/rows` (paginé, tri liste blanche),
  `POST /api/v1/planning-imports/{id}/cancel` (`204`, idempotent).
- **Migrations** : aucune (V12/V13 déjà en place).
- **Tests ajoutés** : `PlanningCsvParserTests` (7, pur) +
  `PlanningImportIntegrationTests` (7, `@SpringBootTest` MySQL réel :
  simulation `ADDED` sans fuite d'`id`, formateur non éligible + doublon
  `slot_key` bloquants, conflit de chevauchement classe/formateur/salle,
  colonne manquante → `400` avant tout job, upload non-CSV → `415`,
  cancel idempotent, sécurité `401`/`403` STUDENT/TEACHER + périmètre
  `PEDAGOGICAL_MANAGER` → `403 PLAN_SCOPE_FORBIDDEN`). **+14** tests back.
- **Commandes** :
  `./mvnw test -Dtest='PlanningCsvParserTests,PlanningImportIntegrationTests,ModularityTests'`
  → **15 / 0 — BUILD SUCCESS** ; `./mvnw clean test` (suite complète) →
  **`Tests run: 707, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS**
  (693 → 707 : +14, aucune régression).
- **Décisions confirmées** : `DEC-G1-003` → guard CSV **dupliqué**
  (pas d'extraction `shared`) ; le job cible **une classe** portée par
  la requête (`classGroupPublicId`), l'année dérivée de la classe ;
  correction ligne à ligne **non retenue** en G1-B — un fichier fautif se
  corrige et se re-téléverse (annulation + réimport, `DEC-G1-003`).
- **Non couvert (checkpoints suivants)** : publication atomique
  (`PlanningPublicationService` + `DefaultPlanningSessionWriter`,
  `DEC-G1-001/003`), versionnement + comparaison de versions
  (EF-PLAN-005/007), conflit avec des séances **déjà publiées**,
  avertissements d'alternance (`DEC-G1-006`), purge `@Scheduled`, écrans
  Angular `/planning/**`.

## Bloc G1-B — checkpoint publication + versionnement (1er septembre 2026)

- **HEAD de départ** : `24cc9f5`.
- **État** : back-end du module `planning` **fonctionnellement complet**
  (`IMPLEMENTED_TARGETED_TESTS_GREEN`) ; reste = écrans Angular
  `/planning/**` (checkpoint `feat(frontend): ajouter le parcours
  planning`).
- **Fichiers back ajoutés** :
  - `coursesession/PlanningSessionWriter.java` déjà présent (schéma) —
    **implémenté** par `coursesession/internal/DefaultPlanningSessionWriter`
    (création / réutilisation / supersession de `course_session` d'origine
    planning, idempotence par identifiant de créneau stable, jamais de
    réécriture d'une séance `OPEN`/`CLOSED`, `@Transactional(MANDATORY)`) ;
  - `coursesession/internal/CourseSession` : `fromPlanningEntry(...)`,
    `applyPlanningUpdate(...)`, `markSupersededByScheduling(...)` ;
    `CourseSessionRepository` : `findByPlanningEntryPublicId`,
    `findPlanningSessionsForClass` (JPQL) ;
    `CourseSessionSpecifications.notSupersededByScheduling()` +
    `CourseSessionService.list` l'applique (une séance supersédée n'est
    plus listée — DEC-G1-004 règle 4) ;
  - `planning/PlanningPublishedEvent.java` (public, pour G1-D) ;
  - `planning/internal/` : `PlanningChangePublisher`,
    `PlanningPublicationService` (**une** transaction atomique,
    `@Transactional(REQUIRES_NEW)` ; verrou `FOR UPDATE` du job + du
    `planning_schedule` ; re-validation périmètre + anomalies bloquantes ;
    `planning_version` N/N+1 + `planning_entry` ; appel synchrone du port ;
    lien `planning_entry.session_public_id` ; ancienne version
    `SUPERSEDED` ; `PlanningPublishedEvent` in-transaction),
    `PlanningPublicationOrchestrator` (hors transaction : conflit métier
    → propagé ; échec inattendu → `FAILED` via bean séparé +
    `PLAN_PUBLICATION_FAILED` 409), `PlanningPublicationFailureRecorder`
    (`REQUIRES_NEW` ; ne réécrit pas un job déjà publié par une requête
    concurrente), `PlanningVersionService` + `PlanningVersionController` +
    `PlanningResponses` étendu, `PlanningPurgeService` (`@Scheduled`,
    `@EnableScheduling` réactivé).
- **Endpoints ajoutés** :
  `POST /api/v1/planning-imports/{id}/publish` (`200`, idempotent),
  `GET /api/v1/planning/versions?classGroupPublicId=…` (paginé, tri liste
  blanche), `GET /api/v1/planning/versions/{id}` (détail + entrées).
- **Migrations** : aucune (V12/V13 suffisent — décision de ne PAS ajouter
  de colonne `slot_uid` : l'identité stable d'un créneau passée au port
  est un `UUID.nameUUIDFromBytes(schedule.public_id + "|" + slot_key)`
  déterministe, `planning_entry.public_id` reste un identifiant de ligne
  aléatoire ; `course_session.planning_entry_public_id` porte l'identité
  stable de créneau — d'où l'idempotence inter-versions du writer).
- **Tests ajoutés** : `PlanningPublicationIntegrationTests` (6,
  `@SpringBootTest` MySQL) : publication → version 1 + séances planning
  `PLANNED` sans motif d'exception ; **AC-007** (simulation ⇒ 0 séance) ;
  idempotence (double publish → `alreadyPublished=true`, pas de nouvelle
  séance) ; **AC-008** (republication modifiée → version 2, version 1
  `SUPERSEDED` + `replacedByVersion`, S1 réutilisée / S2 supersédée
  filtrée / S3 créée) ; ligne bloquante → `409 PLAN_BLOCKING_ISSUES`,
  job resté `SIMULATED` ; **concurrence** (2 publish parallèles → `200` /
  `409`, jamais `5xx`, 1 seule séance) ; `403` TEACHER / STUDENT.
  **+6** tests back (707 → 713).
- **Commandes** :
  `./mvnw test -Dtest='PlanningPublicationIntegrationTests,PlanningImportIntegrationTests,PlanningCsvParserTests,CourseSessionIntegrationTests,ModularityTests'`
  → **31 / 0 — BUILD SUCCESS** ; `./mvnw clean test` → **`Tests run:
  713, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS** (707 → 713).
- **Décision tranchée** : la « supersession » (DEC-G1-004 règle 4) se
  matérialise par `course_session.superseded_by_scheduling = true` + une
  spécification d'exclusion de liste ; l'état `CANCELLED` reste pour
  G1-C. Optimistic-lock sur publication concurrente → le perdant part en
  `FAILED` (dette mineure : re-import requis ; le gagnant n'est jamais
  écrasé).
- **Non couvert** : écrans Angular `/planning/import`,
  `/planning/import/:jobId`, `/planning/versions`, `/planning/versions/:id`,
  `/my-planning` ; avertissements d'alternance (`DEC-G1-006`) ; conflit
  avec des séances **déjà publiées** hors du fichier courant.

## Bloc G1-B — checkpoint parcours Angular planning (1er septembre 2026)

- **HEAD de départ** : `dafd23a`.
- **État** : `IMPLEMENTED_FULL_SUITE_GREEN` — **G1-B est complet**
  (back-end + front) hors éléments explicitement post-G1.
- **Fichiers front** (`frontend/src/app/features/planning/`) :
  `planning.models.ts` (miroir exact des DTO), `planning-api.service.ts`
  (+ `.spec`), `planning-errors.ts` (liste blanche `PLAN_*`, +`.spec`),
  `planning.shared.scss`, `planning-import/` (upload + choix de la
  classe via `AcademicApiService.listClassGroups`, validation
  `.csv`/2 Mo côté client, `.spec` + `.a11y.spec`),
  `planning-import-review/` (synthèse job + tableau des lignes/anomalies
  + publication avec confirmation en ligne + annulation, `.spec`),
  `planning-versions/` (choix classe → liste des versions → détail des
  créneaux déplié).
- **Câblage** : `app.routes.ts` (`/planning/**`, garde
  `PLANNING_MANAGE_ROLES` = `PlanningWeb.MANAGE_ROLES`),
  `core/navigation/navigation.ts` (entrée « Planning »),
  `navigation.spec.ts` + `app-shell.spec.ts` mis à jour.
- **Routes UI** : `/planning/import`, `/planning/import/:jobId`,
  `/planning/versions`.
- **Tests ajoutés** : front **+25** (523 → 548) — service, mapper,
  `planning-import` (chargement classes, rejet non-CSV, simulation →
  navigation, erreur serveur, storage), `planning-import-review`
  (synthèse + lignes, publication + confirmation + rechargement, `409`
  bloquant, action masquée si non confirmable, `404`), a11y axe-core.
- **Commandes** : `npm run lint` OK ; `npm test -- --watch=false` →
  **66 fichiers / 548 tests / 0 échec** ; `npm run build` → **484,68 kB**
  brut (0 alerte de budget < 500 kB) ; `npm audit --audit-level=high` →
  **0 vulnérabilité** ; back-end inchangé (713/0 depuis `dafd23a`).
- **Décision** : `DEC-G1-B-UI` respecté — pas de bibliothèque calendrier,
  la revue et le détail des versions sont des tableaux Material ; la
  « vue semaine » riche est reportée (non requise).
- **Non livré (post-G1, documenté)** : `EF-PLAN-006` (création manuelle
  plein calendrier) reste `HORS_PÉRIMÈTRE_ASSUMÉ` ; avertissements
  d'alternance (`DEC-G1-006`) ; conflit avec des séances déjà publiées
  hors du fichier courant.

## Audit G1-B.1 — 1er septembre 2026

Audit correctif du bloc G1-B **avant** G1-C. Commits :
`fix(planning): consolider l'identité et la publication atomique`
(+ `docs(g1): corriger la traçabilité après audit du planning`).

### Identité des créneaux — avant / après

| | Avant (G1-B) | Après (G1-B.1) |
|---|---|---|
| Colonne séance | `course_session.planning_entry_public_id` — **nom trompeur** : contenait un UUID déterministe `UUIDv3(schedule.public_id\|slot_key)`, jamais un `planning_entry.public_id` | **renommée** `course_session.planning_slot_public_id` — identité **stable** du créneau |
| `planning_entry` | `public_id` aléatoire par ligne de version, pas d'identité stable explicite | **+ `slot_public_id BINARY(16) NOT NULL`** (déterministe), `public_id` inchangé |
| Port `PlanningSessionWriter` | `entryPublicId` / `previousEntryPublicId` | `slotPublicId` / `previousSlotPublicId` |
| DTO version | `VersionEntryResponse.publicId` seul | `+ slotPublicId` explicite |
| Formule | inline dans `PlanningPublicationService` | `planning.internal.PlanningSlotIds` (simulation **et** publication) |

### Migrations

Aucune nouvelle migration. **V12 et V13 corrigées en place** (jamais
poussées ni appliquées hors base jetable — cf. en-tête de `V13`). V14
reste **libre** pour G1-C. Une base de dev `esic_connect` déjà à V13 doit
être recréée ou `flyway repair`.

### Publication concurrente — durcissement

- `PlanningPublicationService.publish` : `entityManager.refresh(job)` juste
  après `FOR UPDATE` (défait l'instantané périmé du contexte de
  persistance).
- Nouveau `alreadyPublishedResult(jobPublicId)` (`REQUIRES_NEW`, lecture) ;
  `PlanningPublicationOrchestrator` l'appelle sur toute `RuntimeException`
  inattendue : **si le job est déjà `PUBLISHED`** ⇒ renvoie le résultat
  idempotent (`alreadyPublished=true`), **jamais `FAILED`**. `FAILED` est
  réservé à un échec réel sans job publié.
- `PlanningQueryService.toJobResponse` peuple enfin
  `publishedVersionPublicId` (était toujours `null`).
- Test `concurrentPublishOfSameJobIsStrictlyIdempotent` : assertions
  **exactes** — 2×`200`, exactement `{false,true}` sur `alreadyPublished`,
  même `versionPublicId`, job `PUBLISHED`, `publishedVersionPublicId` non
  nul, `failureReason` nul, 1 version, 1 séance.

### Rollback + FAILED — test déterministe

`PlanningPublicationFailureIntegrationTests` (package `planning.internal`,
faux `PlanningSessionWriter` `@Primary` qui lève après le début de la
publication) :
- transaction métier rollbackée : 0 `planning_version`, 0 `planning_entry`,
  0 séance planning, `current_version_number` inchangé, **0
  `PlanningPublishedEvent`** consommé (recorder `@EventListener`) ;
- transaction séparée : job `FAILED`, `failure_reason` non sensible,
  **exactement 1** `planning_import_job_issue` `PLAN_PUBLICATION_FAILED`
  sans cellule CSV / PII ;
- API : `409` `PLAN_PUBLICATION_FAILED`, **sans** marqueur de faute, sans
  `IllegalStateException`, sans nom SQL, sans package interne ;
- `FAILED` non republiable ;
- test complémentaire : une ligne `ERROR` (conflit métier attendu) laisse
  le job `SIMULATED`, **jamais `FAILED`**.

### Séances supersédées — garde centralisée

`CourseSession.isOperational()` (`= !supersededByScheduling` ;
`CANCELLED` de G1-C viendra s'y ajouter). Appliquée :
- `DefaultCourseSessionDirectory` : `resolve` → `NOT_FOUND` ;
  `findForAttendance`, `findCheckpointForAttendance`,
  `findSessionByCheckpointPublicId` → filtrés ; `findSessionsForClasses`,
  `findSessionsInRange` → `operational()` spec ;
- `CourseSessionService.require` (get / open / close) → `SESSION_NOT_FOUND` ;
  `list` → `operational()` spec.
Une séance supersédée n'est donc : ni listée, ni ouvrable, ni
tokenisable, ni émargeable, ni comptée en assiduité, ni dans les
rapports. **Seul** l'historique des versions de planning (module
`planning`, lit `planning_entry`) continue de la montrer avec son
`sessionPublicId`. Test :
`supersededSessionIsInactiveButRemainsInPlanningVersionHistory`.

### Conflits avec les séances déjà publiées (RG-034, partiel)

Nouveau port `CourseSessionDirectory.findOperationalSessionWindows(from,to)`
(records `ExistingSessionWindow`, **100 % UUID publics**).
`PlanningSimulationService.detectPublishedConflicts` : conflit
**formateur** et **classe** contre les séances opérationnelles existantes
qui chevauchent, **hors** le même créneau republié (`slotPublicId`).
Salle **non** vérifiée contre l'existant (`coursesession` sans `room_code`
— limite documentée). Test
`simulationDetectsConflictWithAlreadyPublishedSessionButNotWithTheSameSlot`.

### Exigences reclassées

Voir `G1_REQUIREMENTS_TRACEABILITY.md` §3bis. En bref :
`EF-PLAN-007`/`RG-032` → **`PARTIAL`** (pas de test « ≥ 3 versions ») ;
`RG-033` → **`PARTIAL`** (pas de notification persistante, G1-D) ;
`RG-034` → **`PARTIAL`** (gap réduit : reste salle vs existant) ;
`RG-035` → **`PARTIAL`** (`room_code` string sans action d'affectation) ;
`AC-008` → versionnement `IMPLEMENTED_AND_TESTED`, devenir des séances
`PARTIAL` (supersédée = inactive, pas encore `CANCELLED`).
`EF-PLAN-001/002/004/005`, `EF-SES-001`, `RG-016`, `RG-030/031`, `AC-007`
restent `IMPLEMENTED_AND_TESTED`. G1-A **bloc** = `PARTIAL`.

### Données de démonstration

`scripts/prepare-planning-demo.sh` : substitue `__TEACHER_PUBLIC_ID__`
(résolu via l'API ou passé en argument, **validé UUID**) dans des copies
sous `build/demo-data/` — les modèles versionnés ne sont jamais touchés.
Test `scripts/test/test-prepare-planning-demo.sh` (4 vérifications, sans
back-end). `docs/demo-data/README.md` mis à jour (résultats **attendus**,
non « réellement observés »).

### Tests ajoutés

- Back : `PlanningSlotIdentityIntegrationTests` (2),
  `PlanningPublicationFailureIntegrationTests` (2),
  `PlanningPublicationIntegrationTests` + concurrence stricte / séance
  supersédée / conflit publié (net **713 → 719**).
- Front : `planning-import-review.spec` (course idempotente → succès),
  `planning-api.service.spec` (`slotPublicId`, pas d'`entryPublicId`)
  (**548 → 550**).
- Shell : `test-prepare-planning-demo.sh`.

### Validation G1-B.1

| Commande | Résultat |
|---|---|
| `./mvnw clean test` (Europe/Paris, défaut) | **719 / 0 — BUILD SUCCESS** |
| `TZ=UTC ./mvnw clean test` | **719 / 0 — BUILD SUCCESS** |
| `TZ=Europe/Paris ./mvnw clean test` | **719 / 0 — BUILD SUCCESS** |
| `npm test -- --watch=false` | **66 fichiers / 550 tests / 0 échec** |
| `npm run lint` | « All files pass linting » |
| `npm run build` | initial **484,68 kB** — 0 alerte de budget |
| `npm audit --audit-level=high` | **0 vulnérabilité** |
| `bash scripts/test/test-prepare-planning-demo.sh` | **OK — 4 vérifications** |

### Limites restantes après G1-B.1

- `EF-PLAN-007` : pas de test « ≥ 3 versions consultables » (gap mineur, G1-G).
- `RG-034` : conflit **salle** vs séances déjà publiées non fait (`coursesession` sans `room_code`).
- `RG-035` : aucune action / écran d'affectation de salle post-import.
- `RG-033` : notification persistante = G1-D.
- Séances supersédées : encore `PLANNED` + drapeau (inactives via garde) ;
  bascule vers `CANCELLED` prévue **dans la migration G1-C**.

## G1-C — 1er septembre 2026

Cycle de vie avancé des séances. **Découpé en deux checkpoints** :
`G1-C.1` (annulation — livré) et `G1-C.2` (remplacements — en cours).

### Migration

**`V14__create_session_lifecycle_tables.sql`** (un domaine = cycle de vie
des séances) :
- `course_session` : `+ cancellation_reason VARCHAR(500)`,
  `+ cancelled_at TIMESTAMP(6)`, `+ cancelled_by_id BIGINT UNSIGNED`
  (FK RESTRICT) ; `CHECK chk_course_session_open_state` **remplacé** pour
  accepter `CANCELLED` (`closed_at IS NULL`, `cancelled_at IS NOT NULL`,
  `cancellation_reason IS NOT NULL` ; `opened_at` libre — une séance
  `OPEN` est annulable) ;
- `teacher_substitution` (créée en `V14`, **consommée en G1-C.2**) :
  `public_id`, `course_session_id`, `substitute_teacher_user_id`,
  `original_teacher_user_id` (figé), `reason`, `valid_from`,
  `valid_until`, `status ∈ {ACTIVE, ENDED}`, `ended_*`, `version` +
  `CHECK` (période, substitut ≠ principal, cohérence `ended_at`).
- **Pas** de `session_cancellation_request` : annulation **directe** par
  rôle autorisé (les documents n'exigent pas de workflow de demande —
  CDC §15.5 « demande d'annulation par le formateur » reportée).

### G1-C.1 — Annulation (livré)

Commit `feat(coursesession): gérer l'annulation des séances`.

- `SessionLifecycle.CANCELLED` ; `CourseSession.cancel/isCancellable/isCancelled` ;
  `isOperational()` exclut désormais `CANCELLED` **et** `superseded`.
- `POST /api/v1/sessions/{publicId}/cancel {reason}` → `204` ;
  `MANAGE_ROLES` (`SCHOOL_ADMINISTRATION` et `STUDENT` exclus → `403`).
  Motif vide → `400 SESSION_CANCEL_REASON_REQUIRED`. `PLANNED`/`OPEN` →
  `CANCELLED` ; `CLOSED` ou déjà `CANCELLED` → `409 SESSION_INVALID_STATE`
  (transitions strictes, **pas** d'idempotence — cohérent avec
  `open`/`close` ; décision documentée).
- Effets : points de contrôle non terminaux → `CANCELLED` ; jetons Redis
  purgés (l'événement `CourseSessionChangeAction.CANCELLED` est écouté par
  `attendance.CourseSessionCloseListener`, comme `CLOSED`) ; **aucune
  absence dérivée** (la garde `operational()` retire la séance de
  `findSessionsForClasses` / `findSessionsInRange` → reporting et
  assiduité l'ignorent) ; audit `SESSION_CANCELLED` (le motif, nominatif
  possible, **n'entre pas** dans l'événement — il reste sur l'entité,
  lisible aux seuls rôles autorisés).
- **Garde renforcée** : `AttendanceCheckpointService.requireSession`
  rejette désormais une séance non `isOperational()` (gap G1-B.1 +
  G1-C) ; `DefaultCourseSessionDirectory` déjà couvert.
- **Course concurrente** ouvrir vs annuler : nouvel
  `@ExceptionHandler(OptimisticLockingFailureException)` sur
  `CourseSessionExceptionHandler` → `409 SESSION_INVALID_STATE`, jamais
  `500` (corrige aussi le cas pré-existant open vs close).
- DTO `CourseSessionResponse` : `+ cancellationReason`, `+ cancelledAt`.
- Front : `sessions.models` (`SESSION_STATUSES += CANCELLED`, champs DTO),
  `SessionsApiService.cancelSession`, `SessionDetail` (bouton « Annuler la
  séance » si `PLANNED`/`OPEN` + droit de gestion ; panneau de
  confirmation avec **motif obligatoire** ; pas de rechargement après
  succès — le serveur renverrait `404` — état patché en `CANCELLED` ;
  `409` rendu en ligne). Specs : `session-detail` (+3), `sessions-api`
  (+1).
- Tests back : `CourseSessionIntegrationTests` **+4** (`PLANNED→CANCELLED`
  + inactive partout + audit ; `OPEN→CANCELLED` OK / `CLOSED` refusé ;
  motif + rôles ; concurrence open vs cancel sans `500`). Suites :
  **719 → 723** back, **550 → 554** front.

### G1-C.2 — Remplacements (à faire)

Table `teacher_substitution` déjà en base (V14). Reste :
`TeacherSubstitution` (entité + repo), `SubstitutionService`, endpoints
`POST/GET /api/v1/sessions/{id}/substitutions` +
`POST .../substitutions/{substitutionId}/end`, extension de
`CourseSessionAccessGuard` (le substitut actif peut ouvrir / gérer la
séance pendant sa période), historique borné, UI (panneau remplacements
sur `/sessions/:publicId`), tests (éligibilité, chevauchement, fin, accès,
concurrence, sécurité, audit).

## État de reprise autonome

- **Branche** : `feature/master-level-product-expansion`.
- **HEAD attendu** : `feat(coursesession): gérer l'annulation des séances`
  (chaîne : `db80beb` → `fix(planning): consolider l'identité…` →
  `docs(g1): corriger la traçabilité…` → ce commit).
- **Working tree** : propre après commit.
- **Bloc courant** : **G1-C.2** — remplacements de formateur (EF-SES-005,
  CAD §24 RG-12, CDC §43 RG-015).
- **Fichiers non terminés** : aucun. À créer en **G1-C.2** :
  `coursesession/internal/TeacherSubstitution` (+ repo),
  `SubstitutionService` + `SubstitutionController`,
  `CourseSessionRequests.CreateSubstitution`, extension de
  `CourseSessionAccessGuard` (substitut actif = accès `MANAGE`),
  DTO `SubstitutionResponse`, endpoints, écran remplacements + specs.
- **Tests verts** : back **723/0** (3 fuseaux à re-vérifier au prochain
  checkpoint) ; front **554/0** ; `ModularityTests` vert.
- **Tests rouges** : aucun.
- **Commande suivante** : lire `identity.TeacherDirectory` (éligibilité)
  + `CourseSessionAccessGuard`, puis créer `TeacherSubstitution` + repo,
  puis `SubstitutionService`.
- **Risques** : « une seule substitution ACTIVE applicable à un instant »
  = contrôle applicatif (MySQL sans index partiel) — bien tester la
  concurrence. L'extension de l'`AccessGuard` doit rester **serveur**
  (jamais un paramètre client) et ne pas élargir la lecture.
- **Décisions tranchées (G1-C.1)** : annulation **directe** (pas de
  workflow) ; `PLANNED` **et** `OPEN` annulables, `CLOSED` non ; double
  annulation → `409` (pas idempotent) ; motif hors audit/événement ;
  garde `operational()` = source unique d'exclusion (supersédée +
  `CANCELLED`).

## Dernier commit produit

```text
1bed88c  docs(g1): consigner la livraison des blocs G1-A et G1-B
```

Commits de la session autonome (1er septembre 2026), sur
`feature/master-level-product-expansion`, parent `01a6068` :

| # | Hash | Message |
|---|---|---|
| 1 | `2cf1416` | `feat(frontend): exposer les parcours administratifs existants` (G1-A) |
| 2 | `e4793e7` | `feat(planning): créer le schéma et le modèle du module planning` (G1-B) |
| 3 | `24cc9f5` | `feat(planning): simuler les imports CSV de planning` (G1-B) |
| 4 | `dafd23a` | `feat(planning): publier des plannings versionnés en séances` (G1-B) |
| 5 | `429f45b` | `feat(frontend): ajouter le parcours planning` (G1-B) |
| 6 | `1bed88c` | `docs(g1): consigner la livraison des blocs G1-A et G1-B` (G1-G partiel) |

**Aucun `push`, aucune PR.** Suites : back **713/0**, front **548/0**,
`lint`/`build`/`audit` verts.

## Commandes de reprise

```bash
cd /Users/kingafolabi/Desktop/projet_final
git switch feature/master-level-product-expansion
git log --oneline main..HEAD

# Base back jetable (évite de polluer esic_connect ; cf. TEST_ISOLATION_DECISION.md)
set -a && source .env && set +a
docker exec -i esic-connect-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
  -e "CREATE DATABASE IF NOT EXISTS esic_test; GRANT ALL ON esic_test.* TO '$MYSQL_USER'@'%';"

# Backend — le contournement TZ=UTC n'est plus nécessaire depuis G1-0.1
# (cf. §9). Les trois modes doivent être verts.
cd backend
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
set -a && source ../.env && set +a
export MYSQL_DATABASE=esic_test
./mvnw clean test
TZ=UTC ./mvnw clean test
TZ=Europe/Paris ./mvnw clean test

# Frontend
cd ../frontend
npm ci && npm test -- --watch=false && npm run lint && npm run build && npm audit --audit-level=high
```

## Prochaine étape

1. G1-0 commité (`f3691bd`). G1-0.1 commité (`fix(g1): stabiliser les
   dates métier et corriger le socle`). **Rien n'est poussé, aucune PR.**
2. **Avant G1-A** : suite back **verte (693 / 0)** dans les trois modes
   de fuseau, confirmée au checkpoint G1-0.1 — plus de contournement
   `TZ=UTC`.
3. Démarrer G1-A : audit endpoint → écran (§3.1 du plan), puis écrans
   `organization`, écritures `academic`, affectations pédagogiques,
   profils / inscriptions / transferts, émission d'invitation.
