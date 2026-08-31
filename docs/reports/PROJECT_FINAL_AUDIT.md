# Audit final du projet — ESIC Connect (checkpoint F1)

| Élément | Valeur |
|---|---|
| Checkpoint | F1 — Finalisation, audit de l'état réel |
| Branche | `chore/project-finalization-v2` |
| `HEAD` | `9c5affae757c2a51385df373b50bad50042bfb2c` |
| `git merge-base HEAD main` | `9c5affae757c2a51385df373b50bad50042bfb2c` (branche alignée sur `main`) |
| Working tree | propre au lancement de l'audit |
| Date | 31 août 2026 |
| Nature | audit documentaire — aucune fonctionnalité métier, aucune migration, aucun changement de dépendance |

Ce document est la **source de vérité de fin de tranche**. En cas de
contradiction avec `docs/CURRENT-STATE.md` ou `docs/09-matrice-rncp.md`,
c'est cet audit (adossé aux commandes ci-dessous) qui fait foi jusqu'à
leur mise à jour (voir §7, backlog `FINAL-*`).

---

## 0. Résumé exécutif

### 0.1 Ce qui est vérifié

- **Back-end** : `cd backend && ./mvnw clean test` →
  **`BUILD SUCCESS`, 682 tests, 0 échec, 0 erreur, 0 ignoré** (80 classes
  de test, `ModularityTests` vert, schéma Flyway en version 11, MySQL 8.4
  + Redis 7 locaux). Durée ≈ 2 min 50 s.
- **Front-end** : `cd frontend && npm test -- --watch=false` →
  **53 fichiers, 471 tests, 0 échec** (Vitest + jsdom).
  `npm run lint` → « All files pass linting ».
  `npm run build` → bundle initial **483,26 kB** brut / **122,84 kB**
  transféré, **aucune alerte de budget** (seuil 500 kB) ; **2
  avertissements de template non bloquants** (`NG8107` / `NG8102` dans
  `session-detail.html`).
- **Dépendances** : `npm audit` (dev + prod) → **0 vulnérabilité**.
- **Secrets** : `.env` **non suivi** par Git, **jamais** présent dans
  l'historique ; aucun secret en dur dans le code suivi ; `.gitignore`
  couvre `.env`, `.env.*`, `*.pem`, `*.key`, `*.p12`, `*.jks`.

### 0.2 Ce qui est réellement livré (12 modules Spring Modulith)

`identity`, `academic`, `enrollment`, `alternation`, `organization`,
`coursesession`, `attendance`, `studentimport`, `notification`, `audit`,
`bootstrap`, `shared`. Le parcours **import apprenants → séance
exceptionnelle → ouverture → émargement → présences → correction →
rapport → export CSV** est implémenté et testé de bout en bout au niveau
API. Front-end Angular 21 couvrant tous ces domaines (dont un parcours
d'écriture : administration des comptes, alternance, import CSV,
séances/émargement).

### 0.3 Ce qui n'est PAS implémenté (exigences `MUST` / `SHOULD` du cahier)

| Domaine | Statut | Exigences concernées |
|---|---|---|
| **Import du planning + publication + création des séances depuis un planning** | `NOT_IMPLEMENTED` | EF-PLAN-001..007, EF-SES-001, TR-004/TR-005, RG-010/RG-016, AC-007/AC-008 — **cœur du parcours prioritaire** |
| Séances : `PATCH` / annulation / remplaçant | `NOT_IMPLEMENTED` | EF-SES-004, EF-SES-005, RG-015 |
| Rythmes d'alternance ↔ calcul d'assiduité | `PARTIAL` (contexte résolu, non branché sur un vrai planning) | docs/02 §8.4 |
| QR fixe de salle + contrôle réseau CIDR | `NOT_IMPLEMENTED` (référentiel `site_network_range` présent, non consommé) | EF-ROOM-002, EF-ATT-008, RG-040..042, AC-009 |
| Scan caméra mobile | `NOT_IMPLEMENTED` (code court uniquement) | docs/02 §12.1 |
| WebAuthn / passkeys | `NOT_IMPLEMENTED` | EF-AUTH-006, AC-018, TR-007 |
| MFA TOTP | `NOT_IMPLEMENTED` | EF-AUTH-008, RG-082 |
| Cloudflare Turnstile / anti-bot | `NOT_IMPLEMENTED` | docs/02 §28 |
| Réclamations / messagerie | `NOT_IMPLEMENTED` | EF-CLAIM-001/002 |
| Notifications (in-app / email métier / push) | `PARTIAL` (email d'activation seulement) | EF-NOTIF-001/002 |
| Justificatif avec **pièce jointe** | `NOT_IMPLEMENTED` (justificatif = métadonnée sans fichier) | docs/02 §21, TF-001..006 |
| Départ anticipé | `NOT_IMPLEMENTED` | docs/02 §20 |
| Service IA (FastAPI / mapping colonnes / score d'anomalie) | `NOT_IMPLEMENTED` | EF-AI-001..003, TR-010 |
| IoT / MQTT / Raspberry Pi | `NOT_IMPLEMENTED` (broker Mosquitto démarré, aucun code) | EF-IOT-001/002, TR-011/TR-012 |
| PWA installable / offline | `NOT_IMPLEMENTED` | docs/02 §4.3 |
| Import Excel `.xlsx` / multifeuille | `NOT_IMPLEMENTED` (CSV uniquement) | EF-IMP-003/004 |
| Mot de passe oublié / réinitialisation | `NOT_IMPLEMENTED` | EF-AUTH-005, docs/02 §27 |
| `/auth/logout` + révocation de session | `NOT_IMPLEMENTED` (JWT stateless sans état serveur) | TA-005/TA-006 |
| Sauvegarde / restauration documentée et testée | `NOT_IMPLEMENTED` | docs/02 §50, TR-013, TR-007 |
| Rapports : mise en page « officielle » (logo, PDF) | `PARTIAL` (calcul demi-journées + CSV livrés, pas la mise en forme) | docs/02 §24.5, EF-REP-004 |

### 0.4 Problèmes documentaires majeurs (détail §6)

1. **`docs/CURRENT-STATE.md` est gravement désynchronisé** : il décrit la
   tranche V10 (PR #22) et l'import CSV (PR #23/#24/#25) comme
   « branche non fusionnée / non poussée », alors que **les cinq PR sont
   fusionnées sur `main`** (`35bd04b`, `e8fd16d`, `31acb09`, `9c5affa`).
   « Dernier commit stable » y est encore `5874f5a`.
2. **Aucun `README.md` à la racine** du dépôt (seul `frontend/README.md`
   existe). Un jury clonant le dépôt n'a pas de point d'entrée.
3. `docs/09-matrice-rncp.md` §6 : TR-023 marqué « non fusionnée »,
   aucune ligne `TR-024` pour l'import CSV fusionné, en-tête
   « Avancement vérifié — 28 août 2026 ».
4. `docs/03-architecture.md` §7 décrit des modules **inexistants**
   (`planning`, `room`, `justification`, `claim`, `reporting`, `ai`,
   `iot`) et **omet** `studentimport`, `bootstrap`, `organization`.
5. Chiffres de tests obsolètes disséminés (548 / 567 / 454 / 682 / 471…).

---

## 1. Matrice fonctionnelle

Statuts : `IMPLEMENTED_AND_TESTED` (code + tests automatisés passants),
`IMPLEMENTED_NOT_MANUALLY_DEMONSTRATED` (code + tests, pas de preuve
manuelle de bout en bout dans ce dépôt), `PARTIAL`,
`DOCUMENTATION_ONLY`, `NOT_IMPLEMENTED`.

### 1.1 Identité / authentification

| Capacité | Statut | Preuve code | Preuve test | Démo manuelle | Limite |
|---|---|---|---|---|---|
| Connexion email/mot de passe → JWT HS256 stateless | `IMPLEMENTED_AND_TESTED` | `identity/internal/AuthController`, `AuthenticationService`, `SecurityConfig` | `AuthenticationIntegrationTests`, `AuthenticationServiceTests`, `AuthenticationSecurityTests`, `UserAccountUserDetailsServiceTests` | Scénario API `docs/11` §7 (login implicite) | Pas de refresh token, pas de cookie `HttpOnly` (cible docs/07 §6) ; réponse uniforme email inconnu / mauvais mdp / compte inactif ✔ |
| Multi-rôles + autorités `ROLE_*` dans le JWT | `IMPLEMENTED_AND_TESTED` | `V1`/`V2`, `UserRole`, `AuthenticationService` (claim `roles`) | `RoleSeedDataTests`, `UserRoleConstraintsTests`, `UserManagement*Tests` | — | — |
| Sélecteur de contexte de rôle (EF-AUTH-003) | `IMPLEMENTED_AND_TESTED` (front) | `core/auth/role-context.service.ts`, `role-context-menu` | `role-context.service.spec`, `role-context-menu.spec` | comptes démo mono-rôle → non exerçable manuellement (documenté) | ergonomie seule, n'élargit jamais le JWT |
| Invitation + activation de compte | `IMPLEMENTED_AND_TESTED` | `AccountInvitationController`, `AccountInvitationService`, `InvitationTokenService`, `V3` | `AccountInvitation{Integration,Security}Tests`, `AccountInvitationServiceTests`, `InvitationTokenServiceTests`, `InvitationEmailListenerTests` | Mailpit (`docs/09` TR-015) ; front `account-activation.spec` | Envoi email synchrone après commit, échec seulement journalisé (dette) ; émission cible un compte déjà existant |
| Administration des comptes (suspend/restore/archive/roles) | `IMPLEMENTED_AND_TESTED` | `UserAccountController`, `UserManagementService` | `UserManagement{Service,Integration,Security}Tests` (35+5+8), `user-list.spec`, `user-detail.spec`, `administration-*.spec` | — | `PEDAGOGICAL_MANAGER` exclu (pas de port de périmètre) ; archivage irréversible |
| Mot de passe oublié / réinitialisation | `NOT_IMPLEMENTED` | — | — | — | EF-AUTH-005, docs/02 §27 |
| `/auth/logout`, révocation de session | `NOT_IMPLEMENTED` | commentaires `auth.service.ts` | — | — | JWT stateless ; TA-005/TA-006 non couverts |
| WebAuthn / passkeys | `NOT_IMPLEMENTED` | — | — | — | EF-AUTH-006, AC-018, TR-007 |
| MFA TOTP | `NOT_IMPLEMENTED` | — | — | — | EF-AUTH-008, RG-082 |
| Anti-bot (Turnstile), limitation des tentatives | `NOT_IMPLEMENTED` | — | — | — | docs/02 §28, §16.5 ; Redis présent mais non utilisé pour le rate-limit |

### 1.2 Organisation (site / bâtiment / salle / plage réseau)

| Capacité | Statut | Preuve code | Preuve test | Démo | Limite |
|---|---|---|---|---|---|
| CRUD + archivage/restauration site/bâtiment/salle | `IMPLEMENTED_AND_TESTED` | `organization/internal/*Controller`, `V4` | `Organization{Service,Constraints,Integration,Security}Tests`, `CidrValidatorTests` (32) | — | **Aucun écran Angular** (endpoints back-end seuls) |
| Plages réseau CIDR IPv4/IPv6 (validées, sans DNS) | `IMPLEMENTED_AND_TESTED` | `SiteNetworkRangeController`, `CidrValidator` | `CidrValidatorTests`, `OrganizationSecurityTests` | — | **Non consommé** par l'émargement (aucun contrôle réseau) |
| QR fixe de salle | `NOT_IMPLEMENTED` | — | — | — | EF-ROOM-002, RG-040..042 |

### 1.3 Référentiels académiques

| Capacité | Statut | Preuve code | Preuve test | Démo | Limite |
|---|---|---|---|---|---|
| CRUD année/formation/niveau/promotion/classe + archivage | `IMPLEMENTED_AND_TESTED` | `academic/internal/*Controller`, `V5` | `Academic{Service,Constraints,Integration,Security}Tests` | `scripts/seed-demo.sh` crée la chaîne via API | Front-end **lecture seule** (aucune écriture consommée) |
| Périmètre pédagogique (`pedagogical_assignment`) + contrôle d'accès | `IMPLEMENTED_AND_TESTED` | `PedagogicalAssignmentController`, `AcademicScopeGuard`, `V6` | `PedagogicalAssignment{Service,Constraints,Integration}Tests`, `PedagogicalScopeIntegrationTests`, `AcademicScopeGuardTests`, `AcademicScopeDirectoryTests` | — | **Aucun écran** ; affectation créée via API/fixtures |
| Matières (`Subject`) | `NOT_IMPLEMENTED` | — | — | — | docs/04 §12 ; jamais modélisé |

### 1.4 Apprenants / inscriptions

| Capacité | Statut | Preuve code | Preuve test | Démo | Limite |
|---|---|---|---|---|---|
| Profil apprenant + inscription + changement de classe (historique conservé) | `IMPLEMENTED_AND_TESTED` | `enrollment/internal/*`, `V7` | `Enrollment{Service,Constraints,Integration,Security}Tests`, `StudentProfileServiceTests`, `EnrollmentDirectoryTests`, `ClassGroupDirectoryTests` | `scripts/seed-demo.sh` | Front-end **lecture seule** ; `PEDAGOGICAL_MANAGER` exclu ; numéro étudiant obligatoire à la création manuelle |
| Concurrence (une seule inscription active / année) | `IMPLEMENTED_AND_TESTED` | `EnrollmentPersister` (`REQUIRES_NEW`), contrainte `uq_enrollment_active_per_year` | `EnrollmentIntegrationTests` (2 créations concurrentes → 201/409), `EnrollmentConstraintsTests` | — | AC-005/AC-006, TD-008 ✔ |

### 1.5 Alternance

| Capacité | Statut | Preuve code | Preuve test | Démo | Limite |
|---|---|---|---|---|---|
| Modèles de rythme (4 types) + validation/canonicalisation JSON | `IMPLEMENTED_AND_TESTED` | `alternation/internal/*`, `AlternationConfigParser`, `V8` | `AlternationConfigParserTests` (35), `WorkStudyPatternServiceTests` | front `pattern-*.spec`, `pattern-config.spec` | — |
| Affectation historisée d'un rythme à une classe + clôture bornée | `IMPLEMENTED_AND_TESTED` | `ClassWorkStudyPatternService`, `ClassAssignmentPersister` | `ClassWorkStudyPatternServiceTests`, `AlternationConstraintsTests`, `AlternationIntegrationTests` | front `class-alternation.spec` | course résiduelle sur périodes **bornées** (pas de contrainte de plage SQL) — documentée |
| Exceptions individuelles + résolution `SCHOOL`/`COMPANY`/`UNKNOWN` | `IMPLEMENTED_AND_TESTED` | `StudentScheduleExceptionService`, `AlternationResolver`, `AlternationContextService` | `AlternationResolverTests`, `AlternationContextServiceTests`, `StudentScheduleExceptionServiceTests` | front `enrollment-alternation.spec` | exceptions **collectives** non couvertes ; deux exceptions concurrentes de même type peuvent coexister (documenté) |
| Branchement sur le calcul d'assiduité réel | `PARTIAL` | port `alternation.AlternationDirectory` consommé par `attendance` reporting | `AttendanceReportSortTests`, assertions dans `AttendanceIntegrationTests` | — | pas de module `planning` → « demi-journées attendues » repose sur des séances exceptionnelles saisies à la main |
| Front-end : garde d'écriture des modèles hors `PEDAGOGICAL_MANAGER` | `IMPLEMENTED_AND_TESTED` | `app.routes.ts` (`ALTERNATION_PATTERN_WRITE_ROLES`) | `app.routes.spec` | — | `GET /api/v1/enrollments` fermé au PM → `EnrollmentPicker` propose une saisie d'ID en repli |

### 1.6 Séances

| Capacité | Statut | Preuve code | Preuve test | Démo | Limite |
|---|---|---|---|---|---|
| Séance **exceptionnelle** (création manuelle, motif obligatoire), cycle `PLANNED→OPEN→CLOSED` | `IMPLEMENTED_AND_TESTED` | `coursesession/internal/CourseSessionController`, `V9` | `CourseSessionConstraintsTests` (10), `CourseSessionIntegrationTests` (10) | `docs/11` §7 (statuts HTTP relevés) ; front `session-*.spec` | pas de `PATCH`, pas d'annulation, pas de remplaçant, pas de réouverture |
| Points de contrôle multiples par séance (`START`/`END`/`CUSTOM`) | `IMPLEMENTED_AND_TESTED` | `AttendanceCheckpointController` (V10) | `CourseSessionConstraintsTests`, `CourseSessionIntegrationTests` (transitions concurrentes → 409) | `docs/11` §10 | — |
| Contrôle d'accès fin (`TEACHER` ↔ ses séances, `PEDAGOGICAL_MANAGER` ↔ périmètre) | `IMPLEMENTED_AND_TESTED` | `CourseSessionAccessGuard` | `CourseSessionIntegrationTests`, `AttendanceSecurityTests` | — | — |
| Création de séances **depuis un planning publié** | `NOT_IMPLEMENTED` | — | — | — | EF-SES-001, RG-010/RG-016, AC-007 — module `planning` absent |

### 1.7 Émargement

| Capacité | Statut | Preuve code | Preuve test | Démo | Limite |
|---|---|---|---|---|---|
| Jeton dynamique **opaque** + **code court** dans Redis (TTL, rotation, purge à la fermeture) | `IMPLEMENTED_AND_TESTED` | `attendance/internal/AttendanceTokenService` | `AttendanceTokenServiceTests` (19), `AttendanceIntegrationTests` (25) | `docs/11` §7 + invariant de rotation relevé | pas de QR fixe, pas de contrôle réseau |
| Validation par un `STUDENT` inscrit ; anti-double présence par contrainte SQL | `IMPLEMENTED_AND_TESTED` | `AttendanceController`, `AttendanceRecordPersister`, `uq_attendance_record_checkpoint_enrollment` | `AttendanceRecordConstraintsTests` (7), `AttendanceIntegrationTests` (2 validations concurrentes → 200/409/0×500) | `docs/11` §7 (`409 ATT_ALREADY_RECORDED`) | TE-007/TD-005 ✔ |
| Classement `PRESENT` / `LATE` (seuil unique `PT10M`) | `IMPLEMENTED_AND_TESTED` | `AttendanceService` (V10) | `AttendanceIntegrationTests` | `docs/11` §10 (émargement 20 min après → `LATE`) | seuil unique (docs/02 §17.6 simplifié) ; pas de « validation manuelle après 30 min » automatique |
| Présence manuelle / correction / annulation logique + historique append-only | `IMPLEMENTED_AND_TESTED` | `AttendanceManagementController`, `attendance_correction` | `AttendanceManagementConstraintsTests` (9), `AttendanceIntegrationTests` (corrections/annulations concurrentes) | `docs/11` §10 | motif obligatoire ✔ ; RG-018/RG-019, TE-014 ✔ |
| Redis indisponible → `503 ATT_TOKEN_BACKEND_UNAVAILABLE` (jamais de validation dégradée) | `IMPLEMENTED_AND_TESTED` | `AttendanceTokenService` | `AttendanceTokenServiceTests` | `docs/11` §7 (Redis en pause) | TR-001 (résilience) ✔ |
| Scan caméra | `NOT_IMPLEMENTED` | — | — | — | code court uniquement |
| WebAuthn à l'émargement / distanciel individuel / apprenant provisoire | `NOT_IMPLEMENTED` | — | — | — | EF-ATT-007, TE-008..013 |

### 1.8 Gestion de l'assiduité

| Capacité | Statut | Preuve code | Preuve test | Démo | Limite |
|---|---|---|---|---|---|
| Justificatif **métier sans fichier** (dépôt / modif `PENDING` / examen ; `ABSENT → EXCUSED_ABSENCE`) | `IMPLEMENTED_AND_TESTED` | `AttendanceJustificationController`, `StudentAttendanceController`, `attendance_justification` | `AttendanceIntegrationTests`, `AttendanceManagementConstraintsTests` | `docs/11` §10 ; front `my-attendance-list.spec` | **aucune pièce jointe** (docs/02 §21, TF-* non couverts) ; `TEACHER` exclu de l'examen ✔ |
| Espace apprenant `/me/attendance*` (absences dérivées non persistées) | `IMPLEMENTED_AND_TESTED` | `StudentAttendanceController`, `StudentAttendanceService` | `AttendanceSecurityTests` (8), `AttendanceIntegrationTests` | front `my-attendance-*.spec` | AC-017 ✔ (aucun accès croisé) |
| Calcul de demi-journées (contexte `COMPANY` exclu, `UNKNOWN` signalé) | `IMPLEMENTED_AND_TESTED` | `AttendanceReportService` | `AttendanceIntegrationTests`, `AttendanceReportSortTests` | `docs/11` §10 | rapport « utile » ⇒ rythme d'alternance affecté à la classe |
| Départ anticipé | `NOT_IMPLEMENTED` | — | — | — | docs/02 §20 |

### 1.9 Reporting / export

| Capacité | Statut | Preuve code | Preuve test | Démo | Limite |
|---|---|---|---|---|---|
| Rapports séance / classe / apprenant / synthèse (JSON paginé) | `IMPLEMENTED_AND_TESTED` | `AttendanceReportController` (`/api/v1/attendance/reports/*`) | `AttendanceIntegrationTests`, `AttendanceReportSortTests` (4) | `docs/11` §10.2 (statuts HTTP) ; front `attendance-management.spec` | `TEACHER` exclu des rapports agrégés |
| Export CSV (UTF-8+BOM, `;`, **neutralisation d'injection de formule**) | `IMPLEMENTED_AND_TESTED` | `AttendanceCsvWriter`, `/reports/{kind}/export`, `/sessions/{id}/attendance/export` | `AttendanceIntegrationTests` (assertions BOM/`'=`) | `docs/11` §10.2 (13-14) | EF-REP-003 ✔ |
| Tri serveur borné (`400 ATT_REPORT_INVALID_SORT`) | `IMPLEMENTED_AND_TESTED` | `AttendanceReportSort` | `AttendanceReportSortTests` | `docs/11` §10.2 (11) | — |
| Mise en page officielle (logo ESIC, PDF, identifiant de document) | `NOT_IMPLEMENTED` | — | — | — | docs/02 §24.5, EF-REP-004 |
| Rapport des invitations non activées / anomalies / réclamations | `NOT_IMPLEMENTED` | — | — | — | docs/02 §15.2 |

### 1.10 Import CSV des apprenants

| Capacité | Statut | Preuve code | Preuve test | Démo | Limite |
|---|---|---|---|---|---|
| Lecture CSV sécurisée (RFC 4180 maison, UTF-8 strict, rejet ZIP/OLE2/PDF, séparateur auto, fichier jamais écrit) | `IMPLEMENTED_AND_TESTED` | `studentimport/internal/{CsvFileGuard,CsvParser,CsvValueNormalizer,CsvRowNormalizer}` | `CsvFileGuardTests`, `CsvParserTests`, `CsvValueNormalizerTests`, `CsvRowNormalizerTests` (36) | — | Excel `.xlsx` / multifeuille hors périmètre |
| Simulation sans écriture métier (invariant T1) | `IMPLEMENTED_AND_TESTED` | `StudentImportSimulationService`, `V11` | `StudentImportSimulationIntegrationTests`, `StudentImportSchemaConstraintsTests` (19) | — | — |
| Confirmation transactionnelle unique (verrou `FOR UPDATE`, re-validation, idempotence `APPLIED`, rollback total, e-mail `AFTER_COMMIT`) | `IMPLEMENTED_AND_TESTED` | `StudentImportConfirmationService`, `StudentNumberAllocator`, `StaleRevalidationPersister` | `StudentImportConfirmation{Integration,Rollback}Tests`, `StudentNumberAllocatorTests`, `StudentImportProvisionerContractTests` | — | TI-005/006/012, AC-004/005/006 ✔ |
| Audit `AFTER_COMMIT` + `REQUIRES_NEW` (aucune trace si rollback, T5) ; purge planifiée | `IMPLEMENTED_AND_TESTED` | `StudentImportAuditListener`, `StudentImportPurgeService` | `StudentImportAuditIntegrationTests`, `StudentImportPurgeTests` | — | seul listener d'audit conforme ; les autres modules gardent la dette (§5) |
| 6 endpoints REST + décision fine de périmètre `PEDAGOGICAL_MANAGER` | `IMPLEMENTED_AND_TESTED` | `StudentImportController`, `StudentImportQueryService` | `StudentImportApiIntegrationTests`, `StudentImportLifecycleApiIntegrationTests`, `StudentImportRecetteTests` (TI-007/010, `401` sur les 6) | — | — |
| Front `/students/import` (+ `/:publicId`) | `IMPLEMENTED_AND_TESTED` | `features/students/import/*` | `student-import-*.spec` (17) | — | pas de test e2e Angular → Spring Boot |
| Assistant IA de correspondance de colonnes | `NOT_IMPLEMENTED` | `work_study_pattern` ignoré avec avertissement | — | — | EF-IMP-005, EF-AI-001/002, TR-010 |

### 1.11 Import du planning

`NOT_IMPLEMENTED` — aucun module `planning`, aucune table, aucun
endpoint, aucun écran. C'est la **plus grosse lacune du parcours
prioritaire** (`CLAUDE.md` : « Import planning → Publication → Création
des séances »). EF-PLAN-001..007, EF-SES-001, TR-004/TR-005,
RG-010/RG-016/RG-021, AC-007/AC-008, TI-013..017 : tous `NOT_IMPLEMENTED`.

### 1.12 Front-end

| Capacité | Statut | Preuve | Limite |
|---|---|---|---|
| Socle Angular 21.2 zoneless / standalone / Material, gardes `authGuard`/`guestGuard`/`roleGuard`, intercepteurs jeton + erreurs | `IMPLEMENTED_AND_TESTED` | `core/**`, `app.routes.spec`, `*.interceptor.spec`, `*.guard.spec` | JWT en mémoire seule → rechargement = perte de session |
| Écrans livrés : login, activation, dashboard, administration (R/W), students (R/O), students/import (R/W), academic (R/O), alternation (R/W), sessions (R/W), attendance, my-attendance, attendance-management | `IMPLEMENTED_AND_TESTED` | 53 fichiers de test, 471 tests | aucune route placeholder résiduelle ✔ |
| Build de production | `IMPLEMENTED_AND_TESTED` | `npm run build` 483,26 kB < 500 kB | **2 avertissements de template** `NG8107`/`NG8102` dans `session-detail.html` (non bloquants) |
| PWA installable / service worker / notifications push | `NOT_IMPLEMENTED` | — | docs/02 §4.3 |
| Tests e2e Angular → Spring Boot | `NOT_IMPLEMENTED` | — | TestBed / Vitest isolés uniquement |
| Accessibilité (structure, labels, `role="alert"`, clavier) | `PARTIAL` (revendiquée par composant, **non auditée** : pas de axe-core, pas de test lecteur d'écran) | commentaires + specs unitaires | docs/08 §16 non exécuté |

### 1.13 Audit

| Capacité | Statut | Preuve code | Preuve test | Limite |
|---|---|---|---|---|
| Piste d'audit `audit_event` alimentée par flux métier réels (identité, organisation, académique, inscriptions, alternance, séances, émargement, import) | `IMPLEMENTED_AND_TESTED` | `audit/internal/*Listener`, `V1` | `AuditEventTests`, assertions dans chaque `*IntegrationTests` | pas de rétention / purge configurée ; pas d'écran de consultation d'audit (`GET /api/v1/audit-events` inexistant) |
| Contenu sans PII / jeton / IP | `IMPLEMENTED_AND_TESTED` | motifs non sensibles dans les listeners | assertions dans `*AuditIntegrationTests` | RG-086 ✔ |

### 1.14 Notifications

| Capacité | Statut | Preuve | Limite |
|---|---|---|---|
| Email d'activation via SMTP local (Mailpit) | `IMPLEMENTED_AND_TESTED` | `notification/internal/InvitationEmailListener` ; `InvitationEmailListenerTests` | envoi synchrone après commit, échec seulement journalisé, **pas de file persistante ni DLQ** (dette) |
| Notifications in-app / centre de notifications / push PWA / email métier (planning, remplacement…) | `NOT_IMPLEMENTED` | — | EF-NOTIF-001/002, docs/02 §23 |

---

## 2. Matrice des exigences

### 2.1 Exigences fonctionnelles (`docs/02` §44) — priorité `MUST`

| ID | Exigence | Statut | Preuve |
|---|---|---|---|
| EF-AUTH-001 | Connexion email + mot de passe | `IMPLEMENTED_AND_TESTED` | `AuthenticationIntegrationTests` |
| EF-AUTH-002 | Gérer plusieurs rôles | `IMPLEMENTED_AND_TESTED` | `RoleSeedDataTests`, `UserManagementServiceTests` |
| EF-AUTH-003 | Choisir un contexte de rôle | `IMPLEMENTED_AND_TESTED` (front) | `role-context.service.spec` |
| EF-USER-001 | Créer un utilisateur | `PARTIAL` | création via invitation/fixtures ; pas d'endpoint `POST /users` de création `PENDING_ACTIVATION` (dette documentée) |
| EF-USER-002 | Suspendre un utilisateur | `IMPLEMENTED_AND_TESTED` | `UserManagementIntegrationTests` |
| EF-USER-005 | Détecter les doublons | `IMPLEMENTED_AND_TESTED` | `EnrollmentConstraintsTests`, `StudentImport*Tests`, `FileDuplicateDetectorTests` |
| EF-ACA-001..005 | Formations / niveaux / promotions / classes / années | `IMPLEMENTED_AND_TESTED` | `Academic*Tests` |
| EF-ACA-006 | 3 rythmes d'alternance | `IMPLEMENTED_AND_TESTED` | `AlternationConfigParserTests`, `AlternationResolverTests` |
| EF-IMP-001 | Simuler un import apprenant CSV | `IMPLEMENTED_AND_TESTED` | `StudentImportSimulationIntegrationTests` |
| EF-IMP-002 | Confirmer un import apprenant | `IMPLEMENTED_AND_TESTED` | `StudentImportConfirmationIntegrationTests` |
| **EF-PLAN-001** | **Importer un planning CSV** | **`NOT_IMPLEMENTED`** | — |
| **EF-PLAN-002** | **Prévisualiser le planning** | **`NOT_IMPLEMENTED`** | — |
| **EF-PLAN-004** | **Publier le planning** | **`NOT_IMPLEMENTED`** | — |
| EF-ROOM-001 | Gérer les salles | `IMPLEMENTED_AND_TESTED` (API, sans UI) | `OrganizationIntegrationTests` |
| **EF-SES-001** | **Créer des séances depuis le planning** | **`NOT_IMPLEMENTED`** | séances exceptionnelles seulement |
| EF-SES-002 / EF-SES-003 | Ouvrir / clôturer une séance | `IMPLEMENTED_AND_TESTED` | `CourseSessionIntegrationTests` |
| EF-ATT-001 | Générer un QR dynamique | `IMPLEMENTED_AND_TESTED` | `AttendanceTokenServiceTests` |
| EF-ATT-002 | Valider une présence | `IMPLEMENTED_AND_TESTED` | `AttendanceIntegrationTests` |
| EF-ATT-003 | 4 points de contrôle | `PARTIAL` | N points de contrôle par séance supportés ; les 4 types nommés (`MORNING_ARRIVAL`…) et le calcul journée/demi-journée strict ne sont pas modélisés tels quels |
| EF-ATT-004 | Calculer les demi-journées | `IMPLEMENTED_AND_TESTED` | `AttendanceReportService`, `AttendanceIntegrationTests` |
| EF-ATT-005 | Gérer les retards | `PARTIAL` | seuil unique `PT10M` → `LATE` ; pas de paliers 15/30 min ni validation manuelle auto |
| EF-ATT-006 | Saisir manuellement une présence | `IMPLEMENTED_AND_TESTED` | `AttendanceManagementConstraintsTests` |
| EF-REP-001 / EF-REP-002 | Rapport de classe / individuel | `IMPLEMENTED_AND_TESTED` (calcul + JSON) | `AttendanceIntegrationTests` |
| EF-REP-003 | Export CSV | `IMPLEMENTED_AND_TESTED` | `AttendanceCsvWriter` + assertions |
| EF-AUD-001 | Auditer les opérations critiques | `IMPLEMENTED_AND_TESTED` | `*AuditIntegrationTests` |

### 2.2 Exigences fonctionnelles — `SHOULD` / `COULD`

| ID | Exigence | Statut |
|---|---|---|
| EF-AUTH-004 | Activation par invitation | `IMPLEMENTED_AND_TESTED` |
| EF-AUTH-005 | Réinitialiser un mot de passe | `NOT_IMPLEMENTED` |
| EF-AUTH-006 / 007 / 008 | Passkey / auth adaptative / MFA privilégié | `NOT_IMPLEMENTED` |
| EF-USER-003 / 004 | Archiver / opération de masse | `PARTIAL` (archivage unitaire ✔ ; masse `NOT_IMPLEMENTED`) |
| EF-IMP-003 / 004 / 005 | Excel / multifeuille / mapping IA | `NOT_IMPLEMENTED` |
| EF-PLAN-003 / 005 / 006 / 007 | Corriger lignes / versionner / créer dans l'UI / 3 versions | `NOT_IMPLEMENTED` |
| EF-ROOM-002 | QR fixe par salle | `NOT_IMPLEMENTED` |
| EF-SES-004 / 005 | Annuler une séance / affecter un remplaçant | `NOT_IMPLEMENTED` |
| EF-ATT-007 | Ajouter un apprenant provisoire | `NOT_IMPLEMENTED` |
| EF-ATT-008 | Contrôler le réseau local | `NOT_IMPLEMENTED` (référentiel présent, non consommé) |
| EF-JUS-001 / 002 | Déposer / valider un justificatif | `PARTIAL` (métadonnée sans fichier) |
| EF-CLAIM-001 / 002 | Réclamations | `NOT_IMPLEMENTED` |
| EF-NOTIF-001 / 002 | Notifications internes / de modification | `NOT_IMPLEMENTED` |
| EF-REP-004 | Export Excel | `NOT_IMPLEMENTED` |
| EF-IOT-001 / 002 | MQTT / identité borne | `NOT_IMPLEMENTED` |
| EF-AI-001 / 002 / 003 | Mapping / score / anomalie | `NOT_IMPLEMENTED` |

### 2.3 Règles de gestion (`docs/02` §43)

| RG | Objet | Statut / preuve |
|---|---|---|
| RG-001 | Email unique par utilisateur | `IMPLEMENTED_AND_TESTED` — `UserAccountConstraintsTests` |
| RG-002 | Plusieurs rôles | `IMPLEMENTED_AND_TESTED` — `UserRoleConstraintsTests` |
| RG-004 | RP limité à son périmètre | `IMPLEMENTED_AND_TESTED` — `PedagogicalScopeIntegrationTests` |
| RG-005 / RG-08 | Invitation expire (P30D) | `IMPLEMENTED_AND_TESTED` — `AccountInvitationServiceTests` |
| RG-006 / RG-023 | Historique conservé au changement de classe | `IMPLEMENTED_AND_TESTED` — `EnrollmentIntegrationTests` |
| RG-010 | 1 responsable principal / formation | `IMPLEMENTED_AND_TESTED` — `PedagogicalAssignmentConstraintsTests` |
| RG-012 | 1 inscription active / apprenant / année | `IMPLEMENTED_AND_TESTED` — `EnrollmentConstraintsTests`, concurrence |
| RG-015 | 1 présence / séance / apprenant | `IMPLEMENTED_AND_TESTED` — `AttendanceRecordConstraintsTests` |
| RG-016 | Séance issue d'un planning publié | **`NOT_IMPLEMENTED`** — séances exceptionnelles seulement |
| RG-018 / RG-019 | Correction motivée + auditée | `IMPLEMENTED_AND_TESTED` — `AttendanceManagementConstraintsTests` |
| RG-020 / RG-021 | Import simulé avant application / conflits signalés | `IMPLEMENTED_AND_TESTED` (apprenants) — `StudentImport*Tests` ; **planning : N/A (non implémenté)** |
| RG-022 | Résultats IA validés par un humain | `N/A` — pas d'IA |
| RG-024 | Donnée biométrique brute non stockée | `IMPLEMENTED` (par absence — pas de WebAuthn) |
| RG-026 | Délivrabilité = retour prestataire | `PARTIAL` — statuts internes seulement, Mailpit local |
| RG-028 / RG-087 | Le cache ne contourne jamais les autorisations | `IMPLEMENTED` — Redis ne sert qu'aux jetons d'émargement, autorisation revalidée côté serveur |
| RG-080 | Aucune PII dans le QR | `IMPLEMENTED_AND_TESTED` — jeton opaque, `AttendanceTokenServiceTests` |
| RG-085 | Jeton sensible hors `localStorage` | `IMPLEMENTED_AND_TESTED` — assertions front (aucun accès storage) |
| RG-086 | Pas d'IP dans l'audit métier | `IMPLEMENTED` — motifs non sensibles |

### 2.4 Critères d'acceptation (`docs/02` §45)

| AC | Statut | Preuve |
|---|---|---|
| AC-001 Authentification (actif OK, suspendu refusé neutre) | `IMPLEMENTED_AND_TESTED` | `AuthenticationSecurityTests` |
| AC-002 Périmètre pédagogique → `403` | `IMPLEMENTED_AND_TESTED` | `PedagogicalScopeIntegrationTests`, `AcademicSecurityTests` |
| AC-003 Cumul de rôles sans perte de périmètre | `IMPLEMENTED_AND_TESTED` | `PedagogicalScopeIntegrationTests` |
| AC-004 Import 100 apprenants → simulation chiffrée | `IMPLEMENTED_AND_TESTED` | `StudentImportSimulationIntegrationTests` |
| AC-005 Anti-doublon | `IMPLEMENTED_AND_TESTED` | `StudentImportConfirmationIntegrationTests`, `EnrollmentConstraintsTests` |
| AC-006 Historique consultable après changement de classe | `IMPLEMENTED_AND_TESTED` | `EnrollmentIntegrationTests` |
| **AC-007 Séances créées seulement après confirmation + publication** | **`NOT_IMPLEMENTED`** | pas de planning |
| **AC-008 Modification d'un planning publié → nouvelle version** | **`NOT_IMPLEMENTED`** | — |
| AC-009 QR fixe refusé (après début / hors réseau / sans séance) | `NOT_IMPLEMENTED` | pas de QR fixe |
| AC-010 QR dynamique change + refusé après expiration | `IMPLEMENTED_AND_TESTED` | `AttendanceTokenServiceTests`, `AttendanceIntegrationTests` |
| AC-011 Validation 20 min après → `LATE` | `IMPLEMENTED_AND_TESTED` | `AttendanceIntegrationTests` |
| AC-012 / AC-013 Demi-journée / journée | `PARTIAL` | calcul demi-journées ✔ ; « 4 contrôles nommés → journée » non modélisé strictement |
| AC-014 Justificatif accepté → `EXCUSED` | `IMPLEMENTED_AND_TESTED` | `AttendanceIntegrationTests` |
| AC-015 Correction affiche ancienne/nouvelle/auteur/date/motif | `IMPLEMENTED_AND_TESTED` | `AttendanceManagementConstraintsTests` |
| AC-016 Rapport individuel affiche les demi-journées | `IMPLEMENTED_AND_TESTED` | `AttendanceIntegrationTests` |
| AC-017 Un étudiant ne voit pas le rapport d'un autre | `IMPLEMENTED_AND_TESTED` | `AttendanceSecurityTests` |
| AC-018 Aucune donnée biométrique brute reçue | `N/A` (pas de WebAuthn) | — |
| AC-019 Événement MQTT rejoué ignoré | `NOT_IMPLEMENTED` | pas d'IoT |
| AC-020 Suggestion IA faible non appliquée sans confirmation | `N/A` (pas d'IA) | — |

### 2.5 Exigences techniques (`docs/01` §5.3, `docs/02` §22)

| Exigence | Statut | Preuve / écart |
|---|---|---|
| Java 21 + Spring Boot 3.5 + Maven | `IMPLEMENTED_AND_TESTED` | `backend/pom.xml` (`3.5.16`), build OK |
| Monolithe modulaire (Spring Modulith 1.4) | `IMPLEMENTED_AND_TESTED` | `ModularityTests` vert, 12 modules |
| Angular + Angular Material + PWA | `PARTIAL` | Angular 21.2 + Material ✔ ; **PWA non amorcée** |
| MySQL 8 (source de vérité) + Flyway | `IMPLEMENTED_AND_TESTED` | `V1`–`V11` appliquées, `ddl-auto: validate` |
| Redis 7 (cache / temporaire) | `PARTIAL` | consommé **uniquement** pour les jetons d'émargement ; ni cache de planning, ni rate-limit, ni droits calculés |
| Python / FastAPI (IA) | `NOT_IMPLEMENTED` | aucun service |
| MQTT (Mosquitto) | `PARTIAL` | broker démarré par `compose.yaml`, **aucun code** back-end |
| Docker Compose local | `IMPLEMENTED_AND_TESTED` | `compose.yaml` valide, 4 services `healthy` |
| API REST documentée (OpenAPI) | `PARTIAL` | `springdoc` présent, `/v3/api-docs` + `/swagger-ui` exposés **au runtime uniquement** ; **aucun `openapi.json` versionné** |
| Migrations + tests automatisés prioritaires | `IMPLEMENTED_AND_TESTED` | 682 + 471 tests |
| Actuator / supervision | `PARTIAL` | seul `/actuator/health` exposé, `show-details: never` ; pas de métriques, pas de logs structurés JSON |
| Déploiement cloud / staging | `NOT_IMPLEMENTED` | aucun IaC, aucun environnement |

### 2.6 Sécurité / RGPD (`docs/07`)

| Contrôle | Statut | Preuve / écart |
|---|---|---|
| Hachage mot de passe (BCrypt via `DelegatingPasswordEncoder`) | `IMPLEMENTED_AND_TESTED` | `SecurityConfig`, `AuthenticationServiceTests` |
| JWT HS256 vérifié (signature + `exp` + `iss`), `401` nu | `IMPLEMENTED_AND_TESTED` | `SecurityConfig`, `AuthenticationSecurityTests` |
| `@EnableMethodSecurity` + `@PreAuthorize` sur toutes les routes métier | `IMPLEMENTED_AND_TESTED` | 136 mappings de méthode (`@GetMapping`/`@PostMapping`/…) sur 26 contrôleurs REST, tous `@PreAuthorize` sauf les 3 routes publiques (`/auth/login`, `/account-invitations/validate`, `/account-invitations/activate`) ; matrices `*SecurityTests` (401/403/200) |
| Contrôle de périmètre côté serveur (jamais d'après un paramètre client) | `IMPLEMENTED_AND_TESTED` | `AcademicScopeGuard`, `CourseSessionAccessGuard`, `StudentImportQueryService` |
| Erreurs `5xx` neutralisées (aucune trace exposée) + `correlationId` | `IMPLEMENTED_AND_TESTED` | `GlobalExceptionHandler` |
| Refus par défaut (`anyRequest().authenticated()`) | `IMPLEMENTED` | `SecurityConfig` |
| Jeton sensible hors `localStorage` (RG-085) | `IMPLEMENTED_AND_TESTED` | assertions front |
| Anti-injection formule à l'export CSV | `IMPLEMENTED_AND_TESTED` | `AttendanceCsvWriter` |
| Téléversement (import CSV) : extension + magic bytes + taille (2 MiB) + jamais écrit sur disque | `IMPLEMENTED_AND_TESTED` | `CsvFileGuardTests`, `spring.servlet.multipart` borné, `413 IMP_FILE_TOO_LARGE` |
| Audit sans PII / jeton / IP | `IMPLEMENTED_AND_TESTED` | listeners + assertions |
| Données fictives uniquement (`example.test`) | `IMPLEMENTED` | `DemoDataInitializerTests` |
| **CORS restrictif** | **`NOT_IMPLEMENTED`** | aucun bean CORS ; `.env` déclare `APP_ALLOWED_ORIGINS` mais **rien ne le lit** ; OK en local (proxy `ng serve`), **bloquant pour un déploiement cross-origin** |
| **Limitation des tentatives / rate-limiting (Redis)** | **`NOT_IMPLEMENTED`** | docs/07 §5, §8 ; RG-084 |
| **CSP explicite + `Referrer-Policy`** | **`NOT_IMPLEMENTED`** | `SecurityConfig` n'appelle pas `HttpSecurity.headers(...)` et ne les désactive pas : Spring Security ajoute donc **par défaut** `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, les en-têtes anti-cache (`Cache-Control` / `Pragma` / `Expires`) et `Strict-Transport-Security` (HSTS émis **uniquement sur les réponses HTTPS**). Écarts réels : **aucune `Content-Security-Policy`** ni **`Referrer-Policy`** configurée. À confirmer par un test d'intégration sur les en-têtes de réponse. |
| HTTPS hors local | `NOT_IMPLEMENTED` | pas de terminaison TLS (hors périmètre prototype, à documenter) |
| Révocation de session après changement de mot de passe / suspension | `NOT_IMPLEMENTED` | JWT stateless, pas de liste de révocation |
| Politique de conservation / purge (audit, présences, invitations) | `PARTIAL` | purge planifiée **uniquement** pour l'import CSV ; rien pour audit / invitations `PENDING` / présences |
| Scan de vulnérabilités des dépendances en CI | `NOT_IMPLEMENTED` | ni Dependabot, ni dependency-review, ni OWASP dependency-check, ni CodeQL |
| Droits des personnes (accès / rectification / export / effacement) | `DOCUMENTATION_ONLY` | docs/07 §18 ; aucune procédure outillée |

### 2.7 Performance (`docs/02` §38.1, `docs/08` §14)

| ID | Cible | Statut |
|---|---|---|
| TP-004 / NFR-PERF-03 | Import 100 apprenants analysé en temps acceptable | `PARTIAL` — `CURRENT-STATE.md` affirme « simulation et confirmation < 1 s sur MySQL local » ; **aucun test de perf automatisé, aucune trace chiffrée reproductible dans le dépôt** |
| TP-001 / TP-002 / NFR-PERF-01 | Lecture planning en cache / génération jeton < 100 ms local | `NOT_MEASURED` — pas de cache de planning ; génération de jeton non instrumentée |
| TP-003 / TP-006 | p50/p95 validation présence ; 20 scans simultanés sans doublon | `PARTIAL` — concurrence fonctionnelle testée (`AttendanceIntegrationTests`), **pas de mesure de latence** |
| TP-005 | Rapport mensuel — temps documenté | `NOT_MEASURED` |

### 2.8 Accessibilité (`docs/02` §38.5, `docs/08` §16)

`PARTIAL` — les composants revendiquent structure sémantique, labels
Material, `role="alert"`/`role="status"`, navigation clavier, tables
défilables, alternative « code court » au scan. **Aucune vérification
outillée** : pas d'`axe-core`, pas de test lecteur d'écran, pas d'audit
contraste, pas de test de zoom. Les 2 avertissements `NG8107`/`NG8102`
sont cosmétiques (pas d'impact a11y).

### 2.9 Documentation & exploitation (`docs/02` §52, §50, §28 Livrables)

| Livrable | Statut |
|---|---|
| Cadrage / cahier des charges / architecture / modèle de données / backlog / risques / sécurité-RGPD / tests-recette / matrice RNCP / journal IA / guide démo | `PRÉSENTS` — mais plusieurs **désynchronisés du code** (§6) |
| `README.md` racine | **`MANQUANT`** |
| Guide d'installation | `PARTIAL` — `docs/11-guide-demonstration.md` couvre le lancement ; pas de guide d'installation dédié, pas de guide utilisateur |
| Guide d'utilisation | `NOT_IMPLEMENTED` |
| Diagrammes (11 fichiers `docs/diagrams/`) | `PRÉSENTS` — non revérifiés vs code dans cet audit |
| Rapport de soutenance / présentation / vidéo de secours | `NOT_IMPLEMENTED` |
| Sauvegarde / restauration + preuve de test | `NOT_IMPLEMENTED` (docs/02 §50, TR-013) |
| ADR (Architecture Decision Records) | `NOT_IMPLEMENTED` — décisions dispersées dans `CURRENT-STATE.md` et les messages de commit |
| OpenAPI versionné | `NOT_IMPLEMENTED` (runtime seulement) |

---

## 3. Inventaire endpoints & routes

### 3.1 Endpoints REST (136 mappings de méthode, 26 contrôleurs REST, tous `/api/v1`)

> Compte établi par `grep` : 26 classes `@RestController` (les 10 classes
> `@RestControllerAdvice` de gestion d'erreurs **ne sont pas** comptées) et
> 136 méthodes annotées `@GetMapping` / `@PostMapping` / `@PutMapping` /
> `@PatchMapping` / `@DeleteMapping`.

Routes **publiques** (`SecurityConfig.PUBLIC_PATHS`) : `POST /auth/login`,
`GET /account-invitations/validate`, `POST /account-invitations/activate`,
`/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`. Toute autre
route exige un JWT valide + `@PreAuthorize`.

| Domaine | Endpoints | Rôles (`@PreAuthorize`) | Périmètre serveur | Écran Angular |
|---|---|---|---|---|
| **auth** | `POST /auth/login` | public | — | `features/auth/login` |
| **account-invitations** | `POST /` ; `GET /validate` ; `POST /activate` | `POST` : ADMIN/SUPER_ADMIN/PEDAGOGICAL_MANAGER/SCHOOL_ADMINISTRATION ; reste public | — | `POST` : **aucun écran** ; validate/activate : `features/account-activation` |
| **users** | `GET /` ; `GET /{id}` ; `POST /{id}/suspend` ; `/restore` ; `/archive` ; `/roles` ; `/roles/{code}/revoke` | READ = ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION ; LIFECYCLE = idem ; ADMIN_ROLES = ADMIN/SUPER_ADMIN | gardes fines `UserManagementService` (SUPER_ADMIN protégé, auto-action, dernier rôle) | `features/administration` (list + detail, lecture **et** écriture) |
| **academic-years / programs / program-levels / promotions / class-groups** | CRUD + `/archive` + `/restore` (~34) | READ = 4 rôles ; WRITE = ADMIN/SUPER_ADMIN ; SCOPED_WRITE = + PEDAGOGICAL_MANAGER borné | `AcademicScopeGuard` | `features/academic` (**lecture seule** — écritures non consommées) |
| **pedagogical-assignments** | `GET /` ; `GET /{id}` ; `POST /` ; `POST /{id}/close` | ASSIGNMENT_ROLES = ADMIN/SUPER_ADMIN | — | **aucun écran** |
| **sites / buildings / rooms** | CRUD + `/archive` + `/restore` (~18) | READ = 4 rôles ; WRITE = ADMIN/SUPER_ADMIN | — | **aucun écran** |
| **network-ranges** | `GET` ×2 ; `POST` ; `/activate` ; `/deactivate` | `hasRole('SUPER_ADMIN')` (toutes) | — | **aucun écran** |
| **student-profiles** | `GET /` ; `GET /{id}` ; `POST /` | MANAGE_ROLES = ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION | — | `features/students` (**lecture seule** — `POST` non consommé) |
| **enrollments** | `GET /` ; `GET /{id}` ; `POST /` ; `POST /{id}/transfer` ; `POST /{id}/close` | MANAGE_ROLES | — | `features/students` (lecture) + `features/alternation` (lecture d'une classe) ; **écritures non consommées** |
| **alternation** | patterns CRUD+archive/restore ; class-assignments CRUD+close ; classes/{id}/assignments + /context ; student-exceptions +cancel ; enrollments/{id}/exceptions + /context (~20) | PATTERN_READ = 4 rôles ; PATTERN_WRITE = ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION ; SCOPED = + PEDAGOGICAL_MANAGER borné | `AcademicScopeDirectory` | `features/alternation` (**R/W** complet) |
| **student-imports** | `POST /` (multipart) ; `GET /` ; `GET /{id}` ; `GET /{id}/rows` ; `POST /{id}/confirm` ; `POST /{id}/cancel` | MANAGE_ROLES = ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION/PEDAGOGICAL_MANAGER | `StudentImportQueryService` (PM ↔ ses jobs) | `features/students/import` (**R/W**) |
| **sessions** | `GET /` ; `GET /teachers` ; `GET /{id}` ; `POST /` ; `POST /{id}/open` ; `POST /{id}/close` | READ = 5 rôles ; CREATE = ADMIN/SUPER_ADMIN/PEDAGOGICAL_MANAGER ; MANAGE | `CourseSessionAccessGuard` | `features/sessions` (**R/W**) |
| **checkpoints** | `GET /` ; `POST /` ; `POST /{id}/open` ; `/close` ; `/cancel` | READ / MANAGE (CourseSessionWeb) | idem sessions | `features/sessions/session-detail` |
| **attendance-token** | `POST /sessions/{id}/attendance-token` ; `POST /sessions/{sid}/checkpoints/{cid}/attendance-token` | MANAGE_ROLES | délégué à `coursesession` | `session-detail` |
| **attendance/validate** | `POST /attendance/validate` | VALIDATE_ROLE = STUDENT | apprenant résolu du seul JWT | `features/attendance-check-in` |
| **sessions/{id}/attendance** | `GET` (liste) ; `/candidates` ; `/export` ; `/manual` ; `/{aid}/correct` ; `/{aid}/cancel` ; `/{aid}/history` | READ_ROLES / MANAGE_ROLES | contrôle fin lecture/gestion | `session-detail` |
| **attendance/justifications** | `GET /` ; `GET /{id}` ; `POST /{id}/review` | REVIEW_LIST / REVIEW (TEACHER exclu de review) | `AcademicScopeDirectory` | `features/attendance/management/justification-queue` |
| **me/attendance** | `GET /` ; `GET /{id}` ; `POST /justifications` ; `PUT /justifications/{id}` ; `GET /justifications` ×2 | STUDENT_ROLE | apprenant = JWT | `features/attendance/my-attendance` |
| **attendance/reports** | `/sessions` `/classes` `/students` `/summary` + `/{kind}/export` (7) | REPORT_ROLES (TEACHER exclu) | périmètre PM | `features/attendance/management` |

### 3.2 Endpoints sans écran Angular

- **Tout le module `organization`** (`sites`, `buildings`, `rooms`,
  `network-ranges`) — aucun écran de gestion des salles / du réseau.
- **`pedagogical-assignments`** — affectation d'un RP à une formation :
  API seule.
- **Écritures `academic`** (`POST`/`PATCH`/`archive`/`restore` sur
  années/formations/niveaux/promotions/classes) — front en lecture seule.
- **Écritures `enrollment`** (`POST` profil, `POST` inscription,
  `transfer`, `close`) — front en lecture seule.
- **`POST /account-invitations`** (émission) — aucun écran d'émission /
  relance d'invitation.

### 3.3 Écrans sans endpoint / routes mortes

- **Aucun.** Toutes les routes Angular consomment un endpoint réel ;
  aucune route placeholder ne subsiste (`NavItem.placeholder` conservé
  comme mécanisme mais plus aucune entrée ne l'utilise). Les URLs
  `/api/v1/auth/logout` et `/api/v1/auth/refresh` n'apparaissent que dans
  des **commentaires** (`auth.service.ts`) comme points d'extension
  futurs, jamais dans un appel.

### 3.4 Routes Angular (récapitulatif)

`''`→`/dashboard` · `/login` (guest) · `/activation` (**public, sans
garde**) · sous coquille `AppShell` (authGuard) : `/dashboard`,
`/administration` (+`/:publicId`), `/students/import` (+`/:publicId`),
`/students` (+`/:publicId`), `/academic/*` (9 sous-routes), `/alternation/*`
(9 sous-routes dont 2 gardes d'écriture), `/sessions` (+`/new`, `/:publicId`),
`/attendance`, `/my-attendance` (+`/:id`), `/attendance-management/*`
(5 sous-routes) · `/forbidden` · `**`→404.

---

## 4. État des tests

### 4.1 Résultats exécutés le 31 août 2026 (OpenJDK 21, MySQL 8.4, Redis 7, Node 24)

| Commande | Résultat |
|---|---|
| `cd backend && ./mvnw clean test` | **`BUILD SUCCESS` — 682 tests, 0 échec, 0 erreur, 0 ignoré** — 80 classes — ≈ 2 min 50 s |
| `cd frontend && npm test -- --watch=false` | **53 fichiers, 471 tests, 0 échec** (Vitest + jsdom) |
| `cd frontend && npm run lint` | **« All files pass linting »** |
| `cd frontend && npm run build` | **OK** — initial 483,26 kB brut / 122,84 kB transféré — **0 alerte de budget** — **2 avertissements** `NG8107` + `NG8102` (`session-detail.html` L194, L252), non bloquants |
| `cd frontend && npm audit` | **0 vulnérabilité** (dev + prod) |

Ces chiffres **remplacent** ceux disséminés dans `CURRENT-STATE.md`
(548 / 567 / 454…) et `09-matrice-rncp.md` (499 / 416 / 502…).

### 4.2 Tests back-end par type (80 classes)

| Type | Compte (classes) | Exemples |
|---|---|---|
| `@DataJpaTest` (contraintes SQL, mapping, `CHECK`, `UNIQUE`, FK) | 16 | `*ConstraintsTests`, `StudentImportSchemaConstraintsTests`, `AcademicConstraintsTests` |
| `@SpringBootTest` (intégration + sécurité + API + concurrence) | 31 | `*IntegrationTests`, `*SecurityTests`, `StudentImportApiIntegrationTests` |
| Unitaires purs (Mockito / composants purs) | ~31 | `*ServiceTests`, `CsvParserTests`, `AlternationConfigParserTests`, `CidrValidatorTests`, `InvitationTokenServiceTests` |
| Modularité | 1 | `ModularityTests` (frontières Spring Modulith — **vert**) |
| Contexte | 1 | `EsicConnectApplicationTests` |

Couvertures notables : **concurrence** (inscriptions, affectations
d'alternance, émargement, corrections, confirmations d'import → une
écriture / un `409` / aucun `500`) ; **transactionnel** (rollback total
de l'import, e-mail `AFTER_COMMIT`, audit sans trace si rollback —
invariants T1–T6) ; **sécurité** (matrices 401/403/200 par rôle sur
chaque module) ; **migration** (`Flyway` V1–V11 appliquées, Hibernate
`validate`).

### 4.3 Tests front-end (53 fichiers, 471 tests)

Services API (une méthode = un endpoint réel), gardes, intercepteurs,
navigation, et un `*.spec` par écran. Assertions récurrentes : aucun
accès `localStorage`/`sessionStorage`, aucun jeton en URL, `5xx`/code
inconnu → message générique, contexte de rôle restreint sans élargir le
JWT, réponses tardives ignorées après changement d'état.

### 4.4 Lacunes de tests (par rapport à `docs/08`)

| Catégorie | État |
|---|---|
| Tests e2e Angular → Spring Boot | **absents** |
| Tests de performance / latence (`TP-001..006`) | **absents** (seule une affirmation « < 1 s » non reproductible pour l'import) |
| Tests d'accessibilité outillés (`docs/08` §16) | **absents** |
| Tests IoT / MQTT (`TO-001..008`) | **absents** (fonctionnalité absente) |
| Tests IA (`TIA-001..006`) | **absents** (fonctionnalité absente) |
| Tests de résilience `TR-003` (SMTP) / `TR-007` (restauration MySQL) | **absents** ; `TR-001` (Redis) couvert |
| Démonstrations manuelles | émargement + V10 relevés en **statuts HTTP** (`docs/11` §7 et §10) ; **UI de bout en bout jamais exécutée automatiquement** ; import CSV : **aucune démonstration** (ni manuelle ni scénarisée) |

### 4.5 Suite de tests — dette d'infrastructure

Chaque `@SpringBootTest` porte sa propre `@TestConfiguration` imbriquée →
un contexte + un pool HikariCP mis en cache **par classe**.
`application-test.yml` plafonne le pool à 4 pour rester sous
`max_connections` de MySQL. Testcontainers ou une `@TestConfiguration`
partagée seraient préférables. Le profil `test` **pointe sur les mêmes
conteneurs que `local`** (pas de base isolée).

---

## 5. Sécurité & dépendances

### 5.1 Dépendances

| Écosystème | État |
|---|---|
| npm (`frontend/package.json`) | Angular 21.2.x cohérent, Material/CDK 21.2.14, `angularx-qrcode` 21.0.5, ESLint 10, Vitest 4. `npm audit` → **0 vulnérabilité**. `packageManager` épinglé (`npm@11.6.2`). |
| Maven (`backend/pom.xml`) | Spring Boot 3.5.16 (parent), Spring Modulith 1.4.12, springdoc 2.9.0, `flyway-core` + `flyway-mysql`, `mysql-connector-j`. **Aucun plugin de scan de vulnérabilité** (pas d'`org.owasp:dependency-check-maven`, pas de `versions-maven-plugin`). Pas de commande d'audit Maven disponible dans le projet. |

### 5.2 CI / GitHub Actions

| Point | État |
|---|---|
| `backend-ci.yml` / `frontend-ci.yml` | `permissions: contents: read` (**minimal** ✔), `concurrency` avec annulation ✔, `timeout-minutes` ✔, identifiants CI **dédiés et non sensibles**, `.env` jamais utilisé ✔ |
| Dependabot | **ABSENT** (`.github/dependabot.yml` inexistant) |
| `actions/dependency-review-action` | **ABSENT** — recommandation **F4** (détecte les dépendances vulnérables ajoutées/modifiées dans une PR ; non ajouté à F1) |
| CodeQL / SAST | **ABSENT** |
| Signature / SBOM | **ABSENT** |

### 5.3 Secrets & configuration

- `.env` : **non suivi**, **jamais** dans l'historique Git
  (`git log --all -- .env` → vide). `.env.example` ne contient que des
  placeholders (`change-me`, `JWT_SECRET=` vide).
- `.gitignore` couvre `.env`, `.env.*` (sauf `.env.example`), `*.pem`,
  `*.key`, `*.p12`, `*.jks`.
- `application.yml` / `-local` / `-demo` : **aucun secret en dur**, tout
  par `${VAR}`. `application-test.yml` : secret JWT **de test explicite**
  (`test-only-…`, jamais de production).
- Recherche de secrets dans les fichiers suivis (hors docs) : **aucun
  résultat** (les seules occurrences `token=` sont des URLs de test).
- `JWT_SECRET` : obligatoire, ≥ 32 octets, **le démarrage échoue** sinon
  (`SecurityConfig.jwtSigningKey`). `ESIC_DEMO_PASSWORD` : obligatoire ≥
  12 caractères sous le profil `demo`.

### 5.4 Contrôles applicatifs

| Contrôle | État |
|---|---|
| CORS | **absent** (voir §2.6) — OK local (proxy), à ajouter avant tout déploiement cross-origin |
| JWT | HS256, signature + `exp` + `iss` vérifiés, `401` nu (pas de fuite du motif) ✔ |
| Logs | `GlobalExceptionHandler` log le détail **côté serveur uniquement** ; aucune trace renvoyée ; jetons/codes courts jamais journalisés (asserté) ✔ ; **pas de logs structurés JSON, pas de filtre de corrélation** (UUID généré par exception) |
| Erreurs `5xx` | message générique `INTERNAL_ERROR` + `correlationId`, aucune stack ✔ |
| Uploads | seul l'import CSV ; `2 MiB`, extension + magic bytes + `resolve-lazily` → `413 IMP_FILE_TOO_LARGE`, **fichier jamais persisté** (empreinte SHA-256 seule) ✔ |
| Exports CSV | neutralisation d'injection de formule (`'` devant `= + - @`), BOM UTF-8, pas d'e-mail ni d'id SQL ✔ |
| Données personnelles | DTO sans `id` SQL / `password_hash` / jeton ; audit sans PII / IP ; QR = jeton opaque ✔ |
| En-têtes de sécurité HTTP | **partiels** — `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, en-têtes anti-cache et `Strict-Transport-Security` fournis **par défaut** par Spring Security (HSTS émis **uniquement sur les réponses HTTPS**, `SecurityConfig` n'appelant ni `headers(...)` ni leur désactivation). Écarts réels : **`Content-Security-Policy` et `Referrer-Policy` absents** (non configurés explicitement). À vérifier par un test d'intégration sur les en-têtes de réponse. |
| Rate-limiting | **absent** (Redis présent, non utilisé) |

---

## 6. Audit documentaire

### 6.1 Contradictions `CURRENT-STATE.md` ↔ code (le plus grave)

| Affirmation dans `CURRENT-STATE.md` | Réalité (`git log`, `git merge-base`) |
|---|---|
| « Dernier commit stable : `5874f5a` … PR #20 » | `HEAD` = `main` = `9c5affa` (PR #25) |
| « Tranche en cours — Import CSV … Branche `feature/student-csv-import-implementation` … **NON poussée, aucune PR, aucune fusion** » | **Fusionné** : `e8fd16d` (#23), `31acb09` (#24), `9c5affa` (#25) |
| « Tranche précédente — V10 … Branche `feature/attendance-management-and-reporting` … **NON fusionnée** » | **Fusionné** : `35bd04b` (#22) |
| Sections « CP1 : 7 échecs pré-existants `AttendanceIntegrationTests` » | ne se reproduisent plus (`AttendanceIntegrationTests` → 25/0 dans le run F1) — le doc le dit plus bas mais la contradiction subsiste en tête |
| Table « Documents » : tous « CONÇU », Journal IA « INITIALISÉ » | Journal IA contient 14 entrées réelles ; docs livrés mais partiellement périmés |
| « Prochaine priorité » = parcours d'émargement | l'émargement est livré et fusionné ; la vraie priorité est l'**import planning** |

Le document mélange aussi de longues sections historiques répétées
(PR #7 à #20 recopiées intégralement) → difficilement exploitable par un
jury.

### 6.2 `docs/09-matrice-rncp.md`

- §6, ligne **TR-023** : « branche `feature/attendance-management-and-reporting`, **non fusionnée** » — faux.
- **Aucune ligne `TR-024`** pour l'import CSV (fusionné, 3 PR).
- §6, **TR-003** : « Import apprenants → module `enrollment` » — c'est le
  module **`studentimport`**.
- §6, **TR-004 / TR-005** : renvoient au module `planning` — **inexistant**.
- En-tête « Avancement vérifié — **28 août 2026** » ; chiffres de tests
  cités (499 / 416 / 502 / 548 / 454) tous périmés.

### 6.3 `docs/03-architecture.md` §7

- Décrit `planning` (§7.5), `room` (§7.6), `justification` (§7.9),
  `claim` (§7.10), `reporting` (§7.12), `ai` (§7.14), `iot` (§7.15) —
  **aucun n'existe** dans `backend/src/main/java/com/esic/connect/`.
- N'inclut pas `studentimport`, `bootstrap` ; §7.6 devrait être
  `organization` (pas `room`).
- §7.4 : « **Différé** : … les modules `planning`, `coursesession` et
  `attendance` n'existent pas » — `coursesession` et `attendance`
  **existent** (V9/V10, fusionnés).

### 6.4 Cohérence de code / commentaires

- `SecurityConfig` javadoc : « Aucun contrôle d'accès par rôle métier
  n'est câblé ici : il n'existe encore aucune route métier. » — **périmé**
  (26 contrôleurs REST, 136 mappings de méthode, toutes les routes non
  publiques portant `@PreAuthorize`).
- `application-test.yml` : « faute de Testcontainers à ce stade (non
  demandé pour ce socle) » — cadrage périmé.

### 6.5 Installation / reproductibilité

- **Pas de `README.md` racine** : un jury ne sait pas par où commencer
  (prérequis, `cp .env.example .env`, ordre de démarrage, comptes démo).
  L'information existe, éclatée entre `frontend/README.md`,
  `docs/11-guide-demonstration.md` et `CURRENT-STATE.md`.
- Commandes reproductibles vérifiées : `docker compose up -d`,
  `./mvnw clean test` (nécessite `set -a && source ../.env`),
  `npm ci && npm test` — **toutes fonctionnent**.
- `docs/11` : guide de démonstration **fiable** pour le parcours
  émargement + V10 ; **ne couvre pas** la démonstration de l'import CSV
  (pas de jeu de données CSV d'exemple fourni, pas de scénario jury).
- Pas d'`openapi.json` versionné ni de collection Postman/`.http`.
- `scripts/seed-demo.sh` + `scripts/test/test-seed-demo.sh` :
  fonctionnels, testés (faux `curl`).

### 6.6 Éléments nécessaires à une démonstration jury et manquants

1. `README.md` racine (point d'entrée + « quickstart » 10 lignes).
2. Fichier CSV d'apprenants fictifs d'exemple + scénario de démo import.
3. Section de démo « bout en bout » : login RP → import CSV → (⚠ pas de
   planning) → séance → émargement → rapport → export.
4. Jeu de comptes démo **multi-rôles** (les 4 comptes sont mono-rôle →
   EF-AUTH-003 non démontrable manuellement).
5. `CURRENT-STATE.md` remis au niveau de `main`.

---

## 7. Backlog de finalisation `FINAL-*`

Chaque élément : preuve · risque · correction minimale · checkpoint
recommandé (F2 = doc/synchro ; F3 = qualité/sécurité outillage ; F4 = CI ;
F5 = combler une lacune fonctionnelle ; F6 = démo/soutenance).

### 7.1 `BLOQUANT_AVANT_LIVRAISON`

| ID | Titre | Preuve | Risque | Correction minimale | CP |
|---|---|---|---|---|---|
| **FINAL-001** | `CURRENT-STATE.md` décrit comme « non fusionné » du code présent sur `main` | §6.1 ; `git merge-base HEAD main` = `HEAD` | Le document de référence de l'état réel **ment** ; un jury ou un relecteur conclut que V10 + import CSV ne sont pas livrés | Réécrire les sections « Dernier commit stable », « Tranche en cours », « Tranche précédente », « Prochaine priorité » pour refléter `main` à `9c5affa` ; élaguer les blocs historiques dupliqués | F2 |
| **FINAL-002** | Absence de `README.md` racine | §6.5 ; `ls` racine | Dépôt non auto-portant ; installation et démo non découvrables | Créer `README.md` : pitch, stack, prérequis, `cp .env.example .env`, `docker compose up -d`, lancement back/front, comptes démo, liens vers `docs/` | F2 |
| **FINAL-003** | Parcours prioritaire incomplet : **import planning + publication + création des séances** absent | §1.11 ; aucun module `planning` | Le « objectif prioritaire » de `CLAUDE.md` et 3 exigences `MUST` (EF-PLAN, EF-SES-001) ne sont pas satisfaits ; RG-016 / AC-007 / AC-008 non démontrables | **Décision de périmètre requise** : soit implémenter un module `planning` minimal (import CSV → simulation → publication → `CourseSession` réelles), soit **assumer et documenter explicitement** l'exclusion (séances exceptionnelles uniquement) dans cadrage + CDC + soutenance | F5 (impl) ou F2 (exclusion assumée) |
| **FINAL-004** | `09-matrice-rncp.md` §6 : TR-023 « non fusionnée », pas de TR-024 (import CSV), TR-003/004/005 pointent des modules faux/absents | §6.2 | Traçabilité RNCP incohérente — bloc 2 mal étayé | Corriger TR-023 (fusionné) ; ajouter TR-024 (import CSV, module `studentimport`, PR #23-25, tests `StudentImport*`) ; corriger TR-003 (`studentimport`) ; annoter TR-004/005 « planning : non implémenté » | F2 |

### 7.2 `IMPORTANT_AVANT_JURY`

| ID | Titre | Preuve | Risque | Correction minimale | CP |
|---|---|---|---|---|---|
| **FINAL-005** | `03-architecture.md` §7 décrit des modules inexistants et en omet 3 | §6.3 | Le jury lit une architecture qui ne correspond pas au code | Réécrire §7 : lister les 12 modules réels (`identity`, `academic`, `enrollment`, `alternation`, `organization`, `coursesession`, `attendance`, `studentimport`, `notification`, `audit`, `bootstrap`, `shared`) ; marquer `planning`/`claim`/`ai`/`iot` « architecture cible non implémentée » | F2 |
| **FINAL-006** | Aucun scan de dépendances vulnérables en CI ni localement | §5.1, §5.2 | Une CVE dans une dépendance passe inaperçue | Ajouter `actions/dependency-review-action` (PR) + un job `npm audit --audit-level=high` + `org.owasp:dependency-check-maven` (ou au minimum documenter la commande) | F4 |
| **FINAL-007** | CORS non configuré | §2.6 ; aucun bean CORS ; `APP_ALLOWED_ORIGINS` non lu | Tout déploiement où le front n'est pas servi par le même origin est cassé ; contrôle attendu par `docs/07` §8 et §20 | Ajouter une `CorsConfigurationSource` pilotée par `APP_ALLOWED_ORIGINS` (origins/méthodes/headers restreints) + test | F5 |
| **FINAL-008** | Pas de `Content-Security-Policy` ni de `Referrer-Policy` explicites | §5.4 | Spring Security fournit déjà par défaut `nosniff`, `X-Frame-Options: DENY`, l'anti-cache et HSTS (HSTS sur réponses HTTPS uniquement) ; mais sans CSP explicite, `docs/07` §8 n'est pas respecté et le durcissement anti-XSS reste incomplet | Ajouter via `HttpSecurity.headers(...)` une `Content-Security-Policy` restrictive (`frame-ancestors 'none'`) et une `Referrer-Policy` ; ajouter un test d'intégration vérifiant l'ensemble des en-têtes de sécurité (ceux par défaut + ceux ajoutés) | F5 |
| **FINAL-009** | Pas de mesure de performance reproductible (TP-001..006) | §2.7, §4.4 | L'objectif « < 100 ms » et « import 100 apprenants » du cadrage est **affirmé sans preuve** | Ajouter un test chronométré (`@Timed` maison) pour la simulation d'import 100 lignes et la génération de jeton ; consigner p50 dans un court `docs/reports/PERF_NOTES.md` | F3 |
| **FINAL-010** | Jeu de données de démo import CSV + scénario jury absents | §6.6 | L'import CSV (grosse tranche fusionnée) n'est **pas démontrable** en séance | Fournir `docs/demo-data/apprenants-demo.csv` (fictif) + une section « §11 » dans `docs/11` (login RP → simulation → revue → confirmation → apprenants créés) | F6 |
| **FINAL-011** | Comptes démo mono-rôle → EF-AUTH-003 non démontrable manuellement | `DemoDataInitializer` ; `docs/11` §7 | Le sélecteur de contexte de rôle (exigence `MUST`) ne peut être montré | Ajouter au profil `demo` un 5ᵉ compte `PEDAGOGICAL_MANAGER + TEACHER` ; documenter la démo du sélecteur | F6 |
| **FINAL-012** | Chiffres de tests périmés dans `CURRENT-STATE.md` et `09-matrice-rncp.md` | §4.1 vs docs | Perte de crédibilité (chiffres qui ne retombent pas sur le run) | Remplacer par : back 682 / front 471 / lint OK / build 483,26 kB ; renvoyer vers ce rapport pour les détails | F2 |
| **FINAL-013** | Guide d'installation et guide d'utilisation absents (`docs/02` §28) | §2.9 | Livrables attendus manquants | `README.md` (FINAL-002) couvre l'installation ; ajouter `docs/12-guide-utilisateur.md` (parcours par rôle, captures) | F2/F6 |
| **FINAL-014** | Commentaire `SecurityConfig` périmé (« aucune route métier ») | §6.4 | Induit en erreur à la revue de code | Mettre à jour la javadoc de classe (JWT stateless + `@PreAuthorize` par module) | F2 |
| **FINAL-015** | Purge / rétention non implémentée hors import CSV (audit, invitations `PENDING`, présences) | §2.6 ; seul `StudentImportPurgeService` existe | `docs/07` §14, §39 (limitation de conservation) non respectés | À défaut d'implémentation : documenter la politique cible et l'écart dans `docs/07` ; sinon ajouter un `@Scheduled` de purge des invitations `PENDING` échues | F3 |

### 7.3 `AMÉLIORATION_OPTIONNELLE`

| ID | Titre | Preuve | Risque | Correction | CP |
|---|---|---|---|---|---|
| **FINAL-016** | 2 avertissements de template `NG8107`/`NG8102` (`session-detail.html`) | `npm run build` | Cosmétique ; bruit dans la sortie CI | Retirer les `?.`/`??` superflus L194 & L252 | F3 |
| **FINAL-017** | Rate-limiting absent (Redis présent) | §5.4 ; `docs/07` §5, §16.5 | Brute-force login / réémission non freinés | Filtre de rate-limit Redis sur `/auth/login` et endpoints sensibles | F5 |
| **FINAL-018** | Pas d'`openapi.json` versionné ni de collection `.http` | §2.5, §6.5 | Revue d'API moins commode | Générer `docs/openapi.json` au build (`springdoc-openapi-maven-plugin`) ou committer un export | F3 |
| **FINAL-019** | Écrans manquants pour des endpoints livrés (`organization`, `pedagogical-assignments`, écritures `academic`/`enrollment`) | §3.2 | Fonctionnalités back non exploitables par l'utilisateur final | Prioriser au moins : gestion des salles, affectation d'un RP, création d'une classe depuis l'UI | F5 |
| **FINAL-020** | Pas de tests d'accessibilité outillés | §2.8, §4.4 | `docs/08` §16 non couvert | Ajouter `axe-core` dans quelques `*.spec` clés (login, émargement, rapport) | F3 |
| **FINAL-021** | Profil `test` non isolé (mêmes conteneurs que `local`) + dette pool HikariCP | §4.5 ; `application-test.yml` | Un run local peut polluer la base de dev ; fragilité « Too many connections » | Introduire Testcontainers (MySQL + Redis) pour la suite d'intégration | F3 |
| **FINAL-022** | Journal IA : dernière entrée = CP10 ; F1 non tracé | `docs/10-journal-ia.md` | Traçabilité IA incomplète | Ajouter la ligne F1 (fait dans ce commit) | F2 |
| **FINAL-023** | `docs/CURRENT-STATE.md` ≈ 260 ko, très répétitif | `wc -c` | Illisible ; entretien coûteux | Archiver l'historique détaillé dans `docs/reports/HISTORY.md`, garder un état courant court | F2 |

### 7.4 `HORS_PÉRIMÈTRE_ASSUMÉ` (prototype 3 jours — `docs/01` §23.4, `docs/02` §4.5)

| ID | Élément | Justification |
|---|---|---|
| **FINAL-024** | Service IA / FastAPI / Isolation Forest | Expérimental (`docs/02` §4.4) ; à présenter comme conçu non réalisé |
| **FINAL-025** | IoT / MQTT / Raspberry Pi | Souhaité (`docs/01` §23.2) ; broker démarré, intégration non réalisée |
| **FINAL-026** | WebAuthn / MFA TOTP / Turnstile | Expérimental (`docs/02` §4.4) |
| **FINAL-027** | PWA installable / offline / push | Souhaité, non prioritaire |
| **FINAL-028** | Réclamations, départ anticipé, justificatif avec pièce jointe, Excel, groupes temporaires | `SHOULD`/`COULD` ; à lister explicitement comme non traités |
| **FINAL-029** | Déploiement cloud AWS / staging / HTTPS / HA | Cible documentée (`docs/03` §37), différée (`docs/01` §31) |
| **FINAL-030** | `/auth/logout` + révocation de session, mot de passe oublié | Reportés ; JWT stateless assumé pour le prototype |

---

## 8. Séquence proposée F2 → F6

| CP | Objet | Contenu (issu du backlog) | Contrainte |
|---|---|---|---|
| **F2 — Vérité documentaire** | Aligner toute la doc sur `main` | FINAL-001, 002, 004, 005, 012, 013, 014, 022, 023 ; décider et écrire l'exclusion « planning » si retenue (FINAL-003 volet doc) | Documentaire uniquement ; 1 commit `docs(finalization): …` |
| **F3 — Qualité & outillage (sans risque fonctionnel)** | Mesure et hygiène | FINAL-009 (perf), FINAL-015 (rétention — au moins doc), FINAL-016 (warnings), FINAL-018 (openapi), FINAL-020 (a11y), FINAL-021 (Testcontainers) | Pas de changement de règle métier ; tests seulement + config non fonctionnelle |
| **F4 — CI / chaîne d'approvisionnement** | Défenses en profondeur CI | FINAL-006 (`dependency-review-action` + `npm audit` + dependency-check), Dependabot, éventuel CodeQL | Workflows uniquement |
| **F5 — Combler les lacunes de sécurité et fonctionnelles ciblées** | Rendre déployable et compléter le parcours | FINAL-007 (CORS), FINAL-008 (CSP + `Referrer-Policy`), FINAL-017 (rate-limit), FINAL-019 (écrans manquants), **FINAL-003 (module `planning` minimal si retenu)** | Chaque item = sa PR, ses tests, sa MAJ doc |
| **F6 — Démo & soutenance** | Prêt pour le jury | FINAL-010 (données + scénario import), FINAL-011 (compte multi-rôles), vidéo de secours, rapport de soutenance, captures | Après F2–F5 |

---

## 9. Critères précis de fin de projet

Le projet est **livrable** (candidat à la soutenance) lorsque **tout** ce
qui suit est vrai et **prouvé par une commande ou un artefact** :

### 9.1 Cohérence

1. `git merge-base HEAD main == HEAD` sur la branche de livraison, ou PR
   fusionnée.
2. `docs/CURRENT-STATE.md` : « dernier commit stable » = tête de `main` ;
   aucune tranche fusionnée décrite comme « non fusionnée » ; les
   chiffres de tests égalent ceux du §9.2 (renvoi vers ce rapport
   autorisé).
3. `docs/09-matrice-rncp.md` : une ligne `TR-*` par tranche fusionnée,
   chacune reliée à des tests **existants** ; aucune référence à un
   module inexistant sans mention « architecture cible ».
4. `docs/03-architecture.md` §7 : liste des modules = modules réels.
5. `README.md` racine présent et suffisant pour cloner → lancer → se
   connecter sans autre document.

### 9.2 Tests (exécutés, non recopiés)

6. `cd backend && ./mvnw clean test` → `BUILD SUCCESS`, **0 échec, 0
   erreur** ; `ModularityTests` vert ; total consigné (référence F1 :
   682).
7. `cd frontend && npm test -- --watch=false` → **0 échec** (référence
   F1 : 471) ; `npm run lint` → pass ; `npm run build` → **0 alerte de
   budget** ; `npm audit` → 0 vulnérabilité haute/critique.
8. Aucune anomalie **bloquante** ouverte (fiche `docs/08` §18).

### 9.3 Sécurité

9. `git log --all -- .env` vide ; aucun secret dans les fichiers suivis ;
   `.env.example` sans valeur réelle.
10. Toutes les routes non publiques portent `@PreAuthorize` ; les
    matrices `*SecurityTests` (401/403/200) passent.
11. CORS restrictif configuré et testé **ou** absence explicitement
    justifiée pour le mode de déploiement retenu.
12. En-têtes de sécurité HTTP vérifiés par un test d'intégration :
    `X-Content-Type-Options: nosniff`, `X-Frame-Options`, en-têtes
    anti-cache et HSTS (sur HTTPS) sont fournis par défaut par Spring
    Security ; `Content-Security-Policy` et `Referrer-Policy` sont ajoutés
    explicitement **ou** l'écart est documenté et accepté.
13. CI : au moins un contrôle automatique des dépendances vulnérables
    (`dependency-review` ou `npm audit`/`dependency-check`).

### 9.4 Parcours & démonstration

14. Le parcours **`CLAUDE.md`** est démontrable localement de bout en
    bout, **ou** chaque maillon non réalisé (notamment *import planning →
    publication → création des séances*) est **listé explicitement**
    comme hors périmètre dans cadrage + CDC + rapport de soutenance.
15. `docs/11` couvre : connexion, import CSV apprenants (avec fichier
    d'exemple fictif), séance + émargement + correction, rapport +
    export CSV — chaque étape avec résultat attendu.
16. Le sélecteur de contexte de rôle (EF-AUTH-003) est démontrable
    (compte démo multi-rôles).
17. Données strictement fictives (`example.test`), vérifiées.

### 9.5 RNCP

18. Chaque bloc (BC01–BC04) a au moins une preuve **exécutable ou
    versionnée** listée dans `docs/09` ; les fonctions *simulées* ou
    *conçues non réalisées* sont marquées comme telles (jamais
    « `DEMONSTRATED` » sans preuve).

---

## Annexe A — Commandes de vérification (reproductibles)

```bash
# État Git
git branch --show-current            # chore/project-finalization-v2
git rev-parse HEAD                    # 9c5affae757c2a51385df373b50bad50042bfb2c
git merge-base HEAD main              # == HEAD

# Infrastructure
docker compose config && docker compose up -d && docker compose ps

# Back-end (nécessite les conteneurs + variables .env)
cd backend
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
set -a && source ../.env && set +a
./mvnw clean test                    # 682 tests, 0 échec, BUILD SUCCESS

# Front-end
cd ../frontend
npm ci
npm test -- --watch=false            # 53 fichiers, 471 tests, 0 échec
npm run lint                         # All files pass linting
npm run build                        # initial 483,26 kB (< 500 kB), 2 warnings NG8107/NG8102
npm audit                            # 0 vulnérabilité
```

## Annexe B — Inventaire des modules back-end (réel)

`com.esic.connect.` + : `identity`, `academic`, `enrollment`,
`alternation`, `organization`, `coursesession`, `attendance`,
`studentimport`, `notification`, `audit`, `bootstrap`, `shared`.

Migrations : `V1` identité/audit · `V2` seed rôles · `V3` invitations ·
`V4` organisation · `V5` académique · `V6` affectations pédagogiques ·
`V7` profils/inscriptions · `V8` alternance · `V9` séances/émargement ·
`V10` gestion d'assiduité · `V11` import CSV. **Schéma en version 11.**
