# G1 — Plan d'implémentation

> Plan du grand lot produit G1, produit au bloc **G1-0**. Il consolide
> l'inventaire réel du dépôt, les exigences
> (`G1_REQUIREMENTS_TRACEABILITY.md`), les décisions
> (`G1_ARCHITECTURE_DECISIONS.md`) et l'ordre de travail. Il ne déclare
> aucune fonctionnalité livrée.

## Date

```text
31 août 2026
```

---

## 1. Inventaire réel (audit d'entrée du 31 août 2026)

### 1.1 Back-end — 12 modules Spring Modulith

`backend/src/main/java/com/esic/connect/` : `identity`, `organization`,
`academic`, `enrollment`, `alternation`, `coursesession`, `attendance`,
`studentimport`, `notification`, `audit`, `bootstrap`, `shared`.
`ModularityTests` (Spring Modulith 1.4.12) **vert**.

### 1.2 Migrations Flyway réelles

`V1` → `V11`. `spring.jpa.hibernate.ddl-auto = validate`. Dernier
numéro = **11**. Prochain disponible = **12**.

### 1.3 Contrôleurs REST (26)

`identity` : `AuthController`, `UserAccountController`,
`AccountInvitationController`.
`organization` : `SiteController`, `BuildingController`, `RoomController`,
`SiteNetworkRangeController`.
`academic` : `AcademicYearController`, `ProgramController`,
`ProgramLevelController`, `PromotionController`, `ClassGroupController`,
`PedagogicalAssignmentController`.
`alternation` : `WorkStudyPatternController`,
`ClassWorkStudyPatternController`, `StudentScheduleExceptionController`.
`enrollment` : `StudentProfileController`, `EnrollmentController`.
`coursesession` : `CourseSessionController`,
`AttendanceCheckpointController`.
`attendance` : `AttendanceController`, `AttendanceManagementController`,
`AttendanceReportController`, `AttendanceJustificationController`,
`StudentAttendanceController`.
`studentimport` : `StudentImportController`.

### 1.4 Ports publics inter-modules existants (à réutiliser)

| Port | Module | Usage G1 |
|---|---|---|
| `identity.CurrentUserResolver` | identity | auteur des écritures (tous blocs) |
| `identity.UserDirectory` | identity | résolution `UserRef` (notifications, dashboards) |
| `identity.TeacherDirectory` | identity | comptes `TEACHER` éligibles (G1-B, G1-C) |
| `academic.ClassGroupDirectory` | academic | résolution classe ↔ année (G1-B) |
| `academic.AcademicScopeDirectory` | academic | périmètre `PEDAGOGICAL_MANAGER` (G1-A, G1-B, G1-C, G1-F) |
| `enrollment.EnrollmentDirectory` | enrollment | inscriptions actives (G1-B roster, G1-F, G1-D destinataires) |
| `coursesession.CourseSessionDirectory` | coursesession | lecture séances + checkpoints (G1-C, G1-F) |
| `alternation.*Directory` (nom exact à confirmer) | alternation | contexte `SCHOOL`/`COMPANY`/`UNKNOWN` (G1-B) |

### 1.5 Ports publics à créer

| Port | Module hôte | Décision | Bloc |
|---|---|---|---|
| `coursesession.PlanningSessionWriter` | coursesession | DEC-G1-001 | G1-B |
| `attendance.JustificationFileStorage` | attendance | DEC-G1-008 | G1-E |
| (option) `coursesession.SessionLifecycleWriter` interne — **non** : le cycle avancé reste dans `coursesession.internal` | — | DEC-G1-004 | G1-C |

### 1.6 Événements métier existants

`*ChangeEvent` publiés dans la transaction, consommés par `audit`
(`@TransactionalEventListener` `AFTER_COMMIT`) : `AccountLifecycleEvent`,
`AcademicChangeEvent`, `EnrollmentChangeEvent`,
`alternation.AlternationChangeEvent`, `CourseSessionChangeEvent`,
`AttendanceCheckpointChangeEvent`, `attendance.*` (audit),
`StudentImportChangeEvent`, `LoginSucceededEvent`, `LoginFailedEvent`,
`AccountInvitationIssuedEvent`.

### 1.7 Événements à ajouter (G1)

| Événement | Publié par | Consommé par | Bloc |
|---|---|---|---|
| `PlanningPublishedEvent` | planning | audit, notification | G1-B / G1-D |
| `PlanningEntryChangedEvent` (champs modifiés) | planning | notification | G1-B / G1-D |
| `CourseSessionChangeEvent` (étendu : `CANCELLED`, `SUBSTITUTED`, `UPDATED`) | coursesession | audit, notification | G1-C / G1-D |
| `notification` : pas d'événement sortant (terminal) | — | — | G1-D |

### 1.8 Front-end — routes existantes

`/login`, `/activation` (public), `/dashboard`, `/administration(/:publicId)`,
`/students/import(/:publicId)`, `/students(/:publicId)`,
`/academic/**` (lecture seule), `/alternation/**` (R/W),
`/sessions(/new|/:publicId)`, `/attendance` (`STUDENT`),
`/my-attendance/**` (`STUDENT`), `/attendance-management/**`.
Gardes : `authGuard`, `guestGuard`, `roleGuard([...])`.

### 1.9 Tests de référence (mis à jour après le checkpoint G1-0.1, 1er sept. 2026)

- Front : `npm test -- --watch=false` → **55 fichiers / 475 tests / 0
  échec** ; `npm run lint` OK ; `npm run build` `483.26 kB` (0 alerte de
  budget) ; `npm audit --audit-level=high` → 0 vuln.
- Back : `./mvnw clean test` → **693 tests / 0 échec / 0 erreur**
  (686 → 693 : +7 de `AttendanceServiceSessionDateTests`), **vert dans
  les trois modes de fuseau** (`TZ` non forcé, `TZ=UTC`,
  `TZ=Europe/Paris`). Le blocage de fenêtre horaire décrit dans les
  versions antérieures de ce plan est **corrigé au checkpoint G1-0.1**
  (`G1_IMPLEMENTATION_PROGRESS.md` §9 + section « Correctif G1-0.1 »).
  Le contournement « runs back sous `TZ=UTC` » n'est plus nécessaire.

---

## 2. Ordre des blocs et des commits

| # | Bloc | Commit | Dépend de |
|---|---|---|---|
| 1 | G1-0 | `docs(g1): figer les exigences et décisions d'architecture` | — |
| 2 | G1-A | `feat(ui): exposer les fonctions administratives existantes (G1-A)` | G1-0 |
| 3 | G1-B | `feat(planning): importer et publier un planning versionné (G1-B)` | G1-0 (DEC-G1-001..006, 012) |
| 4 | G1-C | `feat(session): compléter le cycle de vie des séances (G1-C)` | G1-B (port `PlanningSessionWriter`, séances planning) |
| 5 | G1-D | `feat(notification): ajouter le centre de notifications métier (G1-D)` | G1-B, G1-C (événements) |
| 6 | G1-F | `feat(dashboard): fournir des tableaux de bord par rôle (G1-F)` | G1-B, G1-C, G1-D |
| 7 | G1-E | `feat(attendance): sécuriser les pièces jointes des justificatifs (G1-E)` | G1-0 (DEC-G1-008, 009) |
| 8 | G1-G | `docs(demo): documenter la recette globale du lot G1` | tous |
| (9) | rapport final | `docs(g1): publier le rapport final du grand lot` | après les 8 |

Chaque bloc : implémenter → tester (back + front) → documenter →
`git diff --check` → commit. Jamais de coquille vide, jamais de `501`.

---

## 3. G1-A — plan détaillé

### 3.1 Audit endpoint → écran (à compléter au démarrage du bloc)

| Endpoint réel | Méthode | Rôles `@PreAuthorize` | Périmètre | Écran cible | Écriture UI | Test existant | Test à ajouter |
|---|---|---|---|---|---|---|---|
| `/api/v1/sites` | GET/POST | (à relever) | — | `/organization/sites` | oui | `SiteController` IT | service + composant + garde |
| `/api/v1/sites/{id}` (+ archive/restore) | GET/PATCH/POST | (à relever) | — | `/organization/sites/:siteId` | oui | idem | idem |
| `/api/v1/buildings/**` | … | … | — | `/organization/buildings/:buildingId` | oui | … | … |
| `/api/v1/rooms/**` | … | … | — | `/organization/rooms/:roomId` | oui | … | … |
| `/api/v1/site-network-ranges` (nom à confirmer) | … | `SUPER_ADMIN` ? | — | `/organization/network-ranges` | oui | … | … |
| `/api/v1/pedagogical-assignments` (nom à confirmer) | GET/POST/DELETE | `ADMIN`/`SUPER_ADMIN`/`PEDAGOGICAL_MANAGER` ? | RP | `/academic/assignments(/new)` | oui | `PedagogicalAssignmentIntegrationTests` | garde + `403` périmètre |
| `/api/v1/academic-years` (+ POST/PATCH/archive) | … | `AcademicWeb.WRITE_ROLES` | RP | `/academic/academic-years/new` + `/:id/edit` | oui | `AcademicIntegrationTests` | formulaire + garde |
| `/api/v1/programs` … `/class-groups` | … | idem | RP | routes `new`/`edit` | oui | idem | idem |
| `/api/v1/student-profiles` (POST) | … | `EnrollmentWeb.MANAGE_ROLES` | — | `/students/new` | oui | `EnrollmentController` IT | formulaire |
| `/api/v1/enrollments` (POST) | … | idem | — | `/students/:publicId/enrollments/new` | oui | idem | formulaire |
| `/api/v1/enrollments/{id}/transfer` (nom à confirmer) | POST | idem | — | `/enrollments/:publicId/transfer` | oui | idem | conflit `409` |
| invitation (émission) | ? | ? | ? | `/administration/invitations/new` **si endpoint réel** | oui/non | `AccountInvitationController` IT | contrat |

> Les cases « à relever / à confirmer » sont renseignées **avant** d'écrire
> le premier composant, en lisant chaque contrôleur, service, DTO,
> exception et test. Aucun nom de route ou d'endpoint n'est inventé.

### 3.2 Règles UI (rappel)

Standalone + Material ; réutiliser `shared/components` (sélecteurs
asynchrones, dialogue de confirmation, gestion d'erreur) ; formulaires
typés ; états chargement / vide / `400` / `401` / `403` / `404` / `409` /
`5xx` neutre ; responsive ; clavier ; axe-core sur ≥ 1 formulaire admin.

### 3.3 Back-end

Modifs **minimales** : compléter un endpoint réellement inexploitable,
ajouter une recherche paginée/DTO public/contrat manquant. Ne pas
réécrire un service correct.

---

## 4. G1-B — plan détaillé

### 4.1 Module

`com.esic.connect.planning` + `package-info.java`
(`@ApplicationModule(displayName = "Planning")`). Dépendances déclarées :
`identity`, `academic`, `enrollment`, `coursesession` (port
`PlanningSessionWriter`), `alternation` (port contexte). Types
d'implémentation dans `planning.internal`.

### 4.2 Migration `V12__create_planning_tables.sql`

Tables (conventions V11) :
`planning_import_job` (statuts `SIMULATED`/`PUBLISHED`/`CANCELLED`/`EXPIRED`/`FAILED` —
`FAILED` écrit hors transaction de publication, cf. DEC-G1-003),
`planning_import_job_issue`, `planning_import_row`
(`row_status ∈ {VALID,WARNING,ERROR}`, `planned_action ∈ {ADDED,MODIFIED,UNCHANGED,REMOVED,CONFLICT}`,
`slot_key`), `planning_import_row_issue`,
`planning_schedule` (`class_group_id`, `academic_year_id`,
`current_version_number`, statut), `planning_version`
(`version_number`, `status ∈ {DRAFT,PUBLISHED,SUPERSEDED}`,
`source_import_job_id`, `replaced_by_version_id`, unique
`(planning_schedule_id, version_number)`),
`planning_entry` (`slot_key`, `class_group_id`, `teacher_user_id`
(clé SQL interne au module, **jamais exposée** — cf. DEC-G1-001),
`room_id` NULL, `title`, `starts_at_utc`, `ends_at_utc`, `time_zone_id`,
`source`, `state`, `session_public_id` NULL, unicité
`(planning_schedule_id, slot_key)` — cf. DEC-G1-002).
Fichier jamais persisté (SHA-256 seul). `CASCADE` sur la chaîne
`planning_import_*` uniquement.

### 4.3 Migration `V13__link_course_session_to_planning.sql` (G1-B)

`course_session` : `+ planning_entry_public_id BINARY(16) NULL UNIQUE`
(index) — **lien vers l'entrée de planning ET discriminant d'origine**
(`NULL` ⇒ séance exceptionnelle manuelle) ;
`+ superseded_by_scheduling BOOLEAN NOT NULL DEFAULT FALSE` ;
`exception_reason` **rendue nullable** (`ALTER … MODIFY … NULL`, additif —
aujourd'hui `NOT NULL` sur toute séance). Aucun `teacher_substitution`
ici : il relève de `V14` (G1-C). Le schéma `course_session` réel ne
comporte **pas** de `room_id` / `site_id` : rien n'est retiré, rien
n'est supposé.

### 4.4 Services

`PlanningCsvGuard` (réutilise la logique `studentimport` — extraction
partagée vers `shared` **ou** duplication minimale, décidée à
l'implémentation, cf. DEC-G1-003), `PlanningCsvParser`,
`PlanningSimulationService` (invariant T1), `PlanningConflictDetector`
(DEC-G1-005), `PlanningRowCorrectionService` **ou** annulation+réimport
(DEC-G1-003), `PlanningPublicationService` (transaction, verrou
`FOR UPDATE`, port `PlanningSessionWriter`, idempotence, `409` métier),
`PlanningVersionService` (liste, détail, comparaison),
`PlanningQueryService` (planning enseignant / étudiant),
`PlanningPurgeService` (`@Scheduled`).

### 4.5 Endpoints (créés seulement quand terminés)

`POST /api/v1/planning-imports`, `GET /api/v1/planning-imports`,
`GET /api/v1/planning-imports/{id}`, `GET /api/v1/planning-imports/{id}/rows`,
`POST /api/v1/planning-imports/{id}/revalidate`,
`POST /api/v1/planning-imports/{id}/publish`,
`POST /api/v1/planning-imports/{id}/cancel`,
(`PATCH …/rows/{n}` **si** correction ligne à ligne retenue),
`GET /api/v1/planning/versions`, `GET /api/v1/planning/versions/{id}`,
`GET /api/v1/planning/versions/{id}/compare?to=…`,
`GET /api/v1/me/planning`, `GET /api/v1/teachers/me/planning`.

### 4.6 Matrice rôle × action (planning)

| Action | SUPER_ADMIN | ADMIN | SCHOOL_ADMIN | PEDAGOGICAL_MANAGER | TEACHER | STUDENT | Source |
|---|---|---|---|---|---|---|---|
| Importer / simuler | ✅ | ✅ | ✅ | ✅ (périmètre) | ❌ | ❌ | `CDC §10.1` autorise `ADMIN`/`SCHOOL_ADMINISTRATION`/`PEDAGOGICAL_MANAGER` pour l'**import apprenant** ; pour le **planning**, `CDC §10.1 / §13.1` désigne le `PEDAGOGICAL_MANAGER` comme propriétaire — l'ouverture aux 3 rôles administratifs est une **décision `DEC-G1-B`** (cohérence avec l'import apprenant), pas une exigence explicite |
| Revue / revalidation / annulation du job | ✅ | ✅ | ✅ | ✅ (périmètre) | ❌ | ❌ | `DEC-G1-B` (même périmètre que « importer ») |
| Publier | ✅ | ✅ | ✅ | ✅ (périmètre) | ❌ | ❌ | **`CDC §43 RG-030`** (« le RP publie son planning ») + **`RG-031`** (« le formateur ne publie pas ») — explicites. Ouverture à `ADMIN`/`SCHOOL_ADMINISTRATION` = `DEC-G1-B` |
| Lire versions | ✅ | ✅ | ✅ | ✅ (périmètre) | ❌ | ❌ | `DEC-G1-B` (silence documentaire ; aligné sur « publier ») |
| Lire son planning publié | ✅ | ✅ | ✅ | ✅ | ✅ (ses séances) | ✅ (ses inscriptions) | `CDC §6.6 / §6.7` (le formateur consulte son planning, l'apprenant le sien) — explicite |

Autorité dérivée : JWT + rôles + `AcademicScopeDirectory` +
`EnrollmentDirectory`. Jamais d'un `userPublicId` client. Les cases sans
exigence numérotée sont des **décisions d'architecture `DEC-G1-B`**
(colonne « Source »), jamais présentées comme des règles préexistantes.

### 4.7 Front

Routes `/planning/import`, `/planning/import/:jobId`,
`/planning/versions`, `/planning/versions/:versionId`, `/my-planning`.
Vue semaine en CSS grid (pas de lib calendrier). Gardes alignées sur la
matrice §4.6.

### 4.8 Tests

Back : migration + contraintes ; parseur + `CsvFileGuard` ; simulation
sans écriture métier (T1) ; erreurs de référence ; périmètre ;
conflits (DEC-G1-005) ; alternance (DEC-G1-006) ; publication ;
rollback total (T3) ; idempotence ; concurrence (2 publications) ;
versionnement + comparaison ; `401/403/200` ; audit `AFTER_COMMIT` ;
`ModularityTests`.
Front : services API ; upload ; revue + filtres ; revalidation ;
publication ; historique + comparaison ; gardes ; planning enseignant ;
planning étudiant ; erreurs ; axe-core.

---

## 5. G1-C — plan détaillé

Migration **`V14__create_session_lifecycle_tables.sql`**
(`teacher_substitution` ; `session_cancellation_request` **seulement si**
le workflow de demande est retenu — sinon annulation directe, pas de
table). Services : `CourseSessionService.update` (séance **d'origine
manuelle** — `planning_entry_public_id IS NULL` — **et** `PLANNED`
uniquement ; verrou optimiste), `.cancel` (motif obligatoire ;
`PLANNED`/`OPEN` → `CANCELLED` ; aucun jeton ; aucune absence dérivée),
`SubstitutionService` (compte actif `TEACHER` ; formateur initial
conservé ; exception historisée ; notification après commit). Endpoints :
`PATCH /api/v1/sessions/{id}`,
`POST /api/v1/sessions/{id}/cancel`,
`POST /api/v1/sessions/{id}/substitute`,
`GET /api/v1/sessions/{id}/history`. Front : actions contextuelles,
formulaires, confirmation, timeline, badge « origine planning ».
Tests : transitions ; `OPEN`/`CLOSED` non modifiables ; planning vs
exceptionnel (DEC-G1-004) ; conflits revalidés ; concurrence ; sécurité ;
audit ; front.

---

## 6. G1-D — plan détaillé

Migration `V15__create_notification_table.sql` (DEC-G1-007). Listener
`@TransactionalEventListener(AFTER_COMMIT)` en `REQUIRES_NEW`, idempotent
(`dedup_key` UNIQUE) — motif du **seul** `StudentImportAuditListener`
(pas celui des 9 autres listeners d'audit, en `@EventListener` synchrone
— cf. `G1_ARCHITECTURE_DECISIONS.md` §Contexte). Destinataires dérivés
serveur. Événements source :
planning publié / séance modifiée / annulée / remplaçant / invitation
émise / justificatif accepté-refusé / import apprenant appliqué.
Endpoints `GET /api/v1/me/notifications`, `…/unread-count`,
`…/{id}/read`, `…/read-all`. Front : cloche + badge + liste dans
`app-shell`. Tests : after-commit, transaction indépendante, idempotence,
rollback métier, destinataires, isolation (AC-017), contenu sans PII,
`401/403`, front.

---

## 7. G1-F — plan détaillé

`GET /api/v1/me/dashboard` typé par rôle de contexte (DEC-G1-010).
Requêtes agrégat dédiées (`COUNT`/`GROUP BY`/`LIMIT ≤ 10`), `readOnly`,
périmètre serveur. Cartes = liste exacte de
`G1_REQUIREMENTS_TRACEABILITY.md` §6. Front : cartes + listes courtes
cliquables, remplace `features/dashboard/dashboard`. Tests : par rôle,
périmètre, bornes, données vides, absence de N+1 (≥ 1 endpoint via
compteur Hibernate), `401/403`, axe-core.

---

## 8. G1-E — plan détaillé

Migration `V16__create_justification_attachment_table.sql`. Port
`attendance.JustificationFileStorage` + `LocalFilesystemJustificationFileStorage`
(DEC-G1-008). Séquence upload → validation (extension, MIME, **magic
bytes**, taille, nom neutralisé, SHA-256, anti-polyglotte) → transaction
DB (`PENDING_STORAGE`) → déplacement atomique → `STORED` → compensation
`@Scheduled` (DEC-G1-009). Variables `JUSTIFICATION_STORAGE_PATH`,
`JUSTIFICATION_MAX_FILE_BYTES`. Endpoints : upload, liste, téléchargement
(`Content-Disposition: attachment` + `nosniff`, MIME re-dérivé),
suppression logique. Front : dépôt + progression + liste + téléchargement,
aucune prévisualisation HTML. Tests : PDF/JPEG/PNG OK ; extension
trompeuse ; magic bytes ; taille (`413`) ; traversal ; accès croisé
(`403`) ; en-têtes ; nettoyage ; rollback étape DB ; audit ; front.

---

## 9. G1-G — plan détaillé

E2E : Playwright (Chromium unique) **après** vérif compat + `npm audit` ;
repli = démonstration API automatisée (`PARTIAL`). Données :
`docs/demo-data/planning-demo.csv`, `docs/demo-data/planning-conflicts-demo.csv`,
fichiers justificatifs fictifs générés par script. Seed idempotent
étendu. Démonstration API bout en bout (statuts HTTP réels consignés).
Documentation finale : voir §16 du prompt du lot (README,
CURRENT-STATE, docs 01–12, matrices, addendum daté aux rapports
historiques — **sans réécrire l'histoire**).

---

## 10. Stratégies transverses

- **Transactions.** Toute écriture multi-table dans une transaction ;
  publication planning = tout-ou-rien (verrou `FOR UPDATE`, port
  synchrone) ; notifications / fichiers = compensation documentée
  (DEC-G1-007, DEC-G1-009). Rollback total testé par bloc.
- **Concurrence.** Verrou optimiste (`version`) + verrou de ligne aux
  points critiques ; conflit métier attendu → `409`, **jamais** `500`.
  Test de concurrence par écriture sensible.
- **Sécurité.** `@PreAuthorize` sur toute route non publique ; périmètre
  décidé serveur (`AcademicScopeDirectory`, `EnrollmentDirectory`,
  `CourseSessionDirectory`) ; `publicId` partout, aucun `id` SQL exposé ;
  CORS/CSP/`Referrer-Policy` inchangés ; audit append-only sans PII ;
  aucun secret / jeton / IP en base ou en log.
- **Tests.** Unitaire + intégration + sécurité (`401/403/200`) +
  concurrence + rollback + (fichiers) + (a11y axe-core) par bloc ;
  `ModularityTests` vert à chaque commit ; totaux consignés dans
  `G1_IMPLEMENTATION_PROGRESS.md` puis `G1_FINAL_REPORT.md`.
- **Démo.** Données `example.test` fictives ; seed idempotent ;
  scénario bout en bout consigné avec statuts réels.
- **Reprise.** `G1_IMPLEMENTATION_PROGRESS.md` mis à jour à chaque fin de
  bloc et à chaque interruption ; jamais démarrer un bloc qu'on ne peut
  pas finir + tester + documenter + commiter.

---

## 11. Migrations prévues (récapitulatif — DEC-G1-012, renumérotées en G1-0.1)

Un domaine par fichier ; chaque checkpoint a ses migrations ; jamais la
structure d'un bloc ultérieur dans la migration d'un bloc antérieur.

| Fichier | Bloc | Nature | Destructive ? |
|---|---|---|---|
| `V12__create_planning_tables.sql` | G1-B | `CREATE TABLE` ×7 | non |
| `V13__link_course_session_to_planning.sql` | G1-B | `ADD COLUMN` (dont `planning_entry_public_id` UNIQUE, discriminant d'origine) + `MODIFY exception_reason … NULL` | non |
| `V14__create_session_lifecycle_tables.sql` | G1-C | `CREATE TABLE teacher_substitution` (+ `session_cancellation_request` si workflow retenu) | non |
| `V15__create_notification_table.sql` | G1-D | `CREATE TABLE` | non |
| `V16__create_justification_attachment_table.sql` | G1-E | `CREATE TABLE` | non |
| `V17__…` | G1-F | index de couverture **si** justifié par un test | non |

Aucune modification de `V1`–`V11`. Un fichier par numéro. Numéro écrit
seulement avec son fichier.
