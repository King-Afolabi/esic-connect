# État courant — ESIC Connect

## Dernière mise à jour

```text
31 août 2026
```

## Dernier commit stable

```text
5874f5a — Merge pull request #20 from King-Afolabi/feature/attendance-qr-demonstration, sur main
```

## Tranche en cours — Import CSV contrôlé des apprenants (checkpoints CP2+)

```text
Branche `feature/student-csv-import-implementation` (créée depuis `main`,
HEAD = commit de fusion CP1 `31acb09` / PR #24). NON poussée, aucune PR
ouverte. CP1 (schéma V11) est fusionné sur `main` via la PR #24.
Implémentation CP2 → CP10 en cours sur cette branche, un commit par
checkpoint, aucun push, aucune fusion.
```

Conception figée au **Checkpoint 0** (rapport `docs/reports/STUDENT_CSV_IMPORT_DESIGN.md`,
révision **R2**, fusionné via PR #23 = `e8fd16d`) : couvre EF-IMP-001 /
EF-IMP-002, US-050 / US-051, RG-020 à RG-024, AC-004 / AC-005 / AC-006,
TI-001 à TI-012, docs/07 §10 ; nouveau module Spring Modulith
`studentimport` ; découpage en 11 checkpoints (§15). Le reste de la
conception (parsing CSV, simulation, confirmation transactionnelle, ports
inter-modules, endpoints REST, écrans Angular) est **inchangé et non
implémenté** — voir le rapport.

### CP1 réalisé — schéma V11 (migration + entités + repositories + tests)

**Périmètre strict CP1** : uniquement le schéma. Aucun endpoint,
contrôleur, parsing CSV, simulation, confirmation, service métier,
listener, événement, génération fonctionnelle de `student_number`, ni
frontend.

- **Migration `V11__create_student_import_tables.sql`** (additive, MySQL 8,
  `InnoDB` / `utf8mb4_0900_ai_ci` ; V1–V10 inchangées ; schéma **en
  version 11**). Aucune donnée métier insérée. Cinq tables propres au
  module `studentimport` (rapport §7) :
  - `student_import_job` — en-tête d'import (`public_id` unique,
    `status` `CHECK IN ('SIMULATED','APPLIED','CANCELLED','EXPIRED')` —
    **pas de `FAILED`**, §3.4 —, `original_file_name` assaini,
    `file_sha256` `CHAR(64)` (contenu **non conservé**),
    `file_size_bytes` `INT UNSIGNED` `CHECK > 0`, `csv_separator`
    `CHAR(1)`, compteurs de simulation / bilan `APPLIED`, `confirmable`,
    `simulated_at` / `expires_at` / `confirmed_at`, `version`) ; FK
    `RESTRICT` `requested_by_id` / `confirmed_by_id` → `user_account` ;
    index `(status, expires_at)` et `(requested_by_id, created_at)`.
  - `student_import_job_issue` — anomalies globales ; `severity`
    `CHECK IN ('INFO','WARNING','ERROR','BLOCKING')` ; FK
    **`ON DELETE CASCADE`** → `student_import_job`.
  - `student_import_row` — ligne CSV **normalisée** (11 colonnes métier
    typées explicites, **pas de JSON brut** — minimisation §7.3) ;
    `row_status` `CHECK IN ('VALID','WARNING','ERROR')` ;
    `planned_action` `CHECK IN ('CREATE_ACCOUNT_AND_ENROLL',
    'ENROLL_EXISTING','UPDATE_PROFILE','TRANSFER_CLASS','NONE')` ;
    `resolved_*_public_id` `BINARY(16)` de traçabilité ;
    `student_number_generated` ; `applied_outcome` (sans `CHECK`, §7.3) ;
    `UNIQUE (student_import_job_id, `row_number`)` (colonne citée car
    `row_number` est un mot réservé MySQL 8) ; index
    `(student_import_job_id, row_status)` ; FK **`CASCADE`**.
  - `student_import_row_issue` — anomalies de ligne ; `severity`
    `CHECK IN (...)` ; `received_value` `VARCHAR(200)` (valeur reçue
    tronquée, **jamais dans l'audit**) ; FK **`CASCADE`** →
    `student_import_row`. Chaîne complète
    `job → job_issue / row → row_issue` supprimée en cascade (avant
    confirmation / à la purge — docs/04 §16.4) ; les données métier d'une
    future confirmation ne dépendent d'aucune de ces FK.
  - `student_number_sequence` — `start_year` `INT UNSIGNED` **PK
    fonctionnelle** (ni `id`, ni `public_id`, ni `version`),
    `next_value` `INT UNSIGNED` `CHECK > 0`, `updated_at`. Alimentée
    **uniquement** pendant une future confirmation (allocation atomique
    `INSERT ... ON DUPLICATE KEY UPDATE`, verrou de ligne) — non
    implémentée à CP1.
- **Module `com.esic.connect.studentimport`** : `package-info.java`
  (`@ApplicationModule(displayName = "Student import")`, javadoc bornée à
  l'état CP1). Types d'implémentation dans `studentimport.internal` :
  entités JPA `StudentImportJob` / `StudentImportJobIssue` /
  `StudentImportRow` / `StudentImportRowIssue` (héritant
  `shared.BaseEntity` — d'où la colonne `version` sur les quatre tables,
  même convention que `audit_event` / `attendance_correction`) et
  `StudentNumberSequence` (`@Id start_year`, hors `BaseEntity`) ; enums
  `StudentImportJobStatus` (avec `FAILED` conservé mais **hors CHECK**,
  inutilisé), `StudentImportIssueSeverity`, `StudentImportRowStatus`,
  `StudentImportPlannedAction`, `StudentImportRowOutcome` ; repositories
  `JpaRepository` en lecture simple (`findByPublicId`, `countByJobId`,
  `countByRowId`, `findByJobIdOrderByRowNumberAsc`). Aucune dépendance
  inter-modules ; `ModularityTests` reste **vert**.
- **`StudentImportSchemaConstraintsTests`** (`@DataJpaTest`, base MySQL de
  test réelle, `replace = NONE` — pas de H2) : **19 tests, 0 échec**.
  Couvre les `CHECK` `status` / `file_size_bytes` / `severity` (job_issue
  et row_issue) / `row_status` / `planned_action` / `next_value` ;
  `UNIQUE (student_import_job_id, row_number)` (même n° accepté sur un
  autre job) ; `ON DELETE CASCADE` `job → job_issue` et
  `job → row → row_issue` ; FK `RESTRICT` `requested_by_id` /
  `confirmed_by_id` ; unicité `public_id` des quatre tables ; PK
  `student_number_sequence.start_year` et
  `INSERT ... ON DUPLICATE KEY UPDATE` incrémentant `next_value` ;
  aller-retour de persistance (mapping des colonnes typées, dont
  `BINARY(16)`, `CHAR`, énumérations, `created_at` audité).

**Vérifications (31 août 2026, OpenJDK 21, MySQL 8.4, Redis 7)** :
- `./mvnw test -Dtest=StudentImportSchemaConstraintsTests` → **19 tests,
  0 échec** ; Flyway « now at version v11 ».
- `./mvnw clean test` (suite complète, exécutée aussi sur un schéma
  jetable `esic_cp1_verify` créé puis supprimé) → **567 tests** (548 →
  567, +19), `ModularityTests` vert, `EsicConnectApplicationTests` vert
  (Hibernate `validate` OK contre V11), **7 échecs pré-existants** dans
  `AttendanceIntegrationTests` (`ATT_NOT_ENROLLED` sur
  `POST /api/v1/attendance/validate`). Ces 7 échecs sont **indépendants
  de CP1** : ils se reproduisent à l'identique avec l'arbre ramené
  exactement à `e8fd16d` (migration V11 et module `studentimport`
  retirés, ligne d'historique Flyway `11` supprimée). Aucun fichier de
  `coursesession` / `attendance` / `enrollment` ni migration V1–V10 n'a
  été touché par CP1. Non corrigés (hors périmètre CP1).

**Écart avec la conception (CP1)** : le rapport range `package-info`
(`@ApplicationModule`) et les entités JPA au **CP2** ; CP1 les crée déjà
car le `@DataJpaTest` de mapping du CP1 en a besoin (les repositories et
entités sont « strictement nécessaires » à ce checkpoint). Ajout des
colonnes `version` sur `student_import_job_issue` / `student_import_row` /
`student_import_row_issue` (non listées §7.2–7.4) pour respecter la
réutilisation de `shared.BaseEntity` annoncée §2.3 et la convention de
tout le dépôt (même les tables append-only portent `version`). Nom de
colonne `row_number` conservé (conception §7.3) mais **cité** en SQL et
dans `@Column` car réservé en MySQL 8. `csv_separator` / `file_sha256`
gardent `CHAR(1)` / `CHAR(64)` via `@JdbcTypeCode(SqlTypes.CHAR)`.

### CP2 réalisé — socle interne du module `studentimport`

**Périmètre strict CP2** : uniquement les briques transverses du module,
aucun parsing CSV, aucune API, aucune logique de simulation ou de
confirmation, aucun port inter-module, aucun événement, aucun frontend.
Aucune migration (V11 inchangée, schéma en version 11).

- **`StudentImportWeb`** — constante `MANAGE_ROLES`
  (`ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION`/`PEDAGOGICAL_MANAGER`,
  rapport §8/§9 ; `TEACHER`/`STUDENT` → 403 ; décision fine de périmètre
  reportée au service) + helpers `parseUuid` (UUID mal formé → ressource
  introuvable, jamais 500) et `subject(Jwt)`.
- **`StudentImportException` + `Kind`** — 21 valeurs couvrant le
  téléversement / la lecture du fichier
  (`UNSUPPORTED_MEDIA_TYPE`, `FILE_TOO_LARGE`, `ENCODING_INVALID`,
  `MISSING_COLUMN`, `TOO_MANY_ROWS`, `NO_DATA_ROWS`, `HEADER_UNREADABLE`),
  la consultation (`JOB_NOT_FOUND`, `JOB_FORBIDDEN`, `INVALID_SORT`,
  `INVALID_FILTER`, `SCOPE_FORBIDDEN`), le cycle de vie
  (`NOT_CONFIRMABLE`, `STALE_SIMULATION`, `SIMULATION_EXPIRED`,
  `JOB_CANCELLED`, `CONFIRM_FORBIDDEN`, `JOB_NOT_CANCELLABLE`) et la
  génération de numéro (`STUDENT_NUMBER_ALLOC_FAILED`,
  `STUDENT_NUMBER_EXHAUSTED`) ; porte un `detail` non sensible optionnel
  (jamais une valeur de cellule).
- **`StudentImportExceptionHandler`** —
  `@RestControllerAdvice(basePackageClasses = StudentImportWeb.class)`
  (portée limitée aux futurs contrôleurs du module, comme
  `EnrollmentExceptionHandler`). Mappe chaque `Kind` → `ApiError` (code
  HTTP + code `IMP_*` + `details[]` non sensibles) et retraduit
  `MaxUploadSizeExceededException` → `413 IMP_FILE_TOO_LARGE`.
- **`StudentImportIssueCodes`** — constantes `String` des `error_code`
  d'anomalie persistés (`student_import_job_issue` /
  `student_import_row_issue`) : structure, champs, résolution classe /
  année, compte existant. Distinctes des `Kind` (qui déclenchent un code
  HTTP).
- **`StudentImportProperties`** (`@ConfigurationProperties(prefix =
  "app.import.student")` + `@Validated`, record à valeurs par défaut) :
  `maxRows` (≥ 100, défaut 500), `maxFileBytes` (défaut 2 MiB),
  `simulationTtl` (P7D), `appliedRowsTtl` (P30D), `numberSequenceWidth`
  (1..9, défaut 5), `numberAllocMaxRetries` (défaut 5) ; durée nulle ou
  négative → échec de démarrage (posture du TTL d'invitation) ;
  `numberSequenceUpperBound()` dérive `10^largeur`. Activé par
  `StudentImportConfig` (`@EnableConfigurationProperties`, sans
  `@ConfigurationPropertiesScan` global). Bloc `app.import.student` ajouté
  à `application.yml` (variables d'environnement `STUDENT_IMPORT_*`,
  valeurs par défaut = décisions de prototype).

**Tests CP2** : `StudentImportPropertiesTests` (4, purs — défauts, borne
de séquence, refus de durée non positive), `StudentImportExceptionHandlerTests`
(3, purs — mapping des 20 `Kind` HTTP-mappés, `details[]`, retraduction
multipart).

**Vérifications (31 août 2026, OpenJDK 21, MySQL 8.4)** :
`./mvnw test -Dtest='StudentImportPropertiesTests,StudentImportExceptionHandlerTests,StudentImportSchemaConstraintsTests,ModularityTests'`
→ **30 tests, 0 échec** ; `EsicConnectApplicationTests` vert (contexte
complet avec le nouveau `@ConfigurationProperties`). `ModularityTests`
vert (aucune dépendance inter-module ajoutée).

**Écart avec la conception (CP2)** : `StudentImportProperties` /
`StudentImportConfig` (typage config) sont créés ici alors que le rapport
§8 les évoquait au fil des checkpoints API ; le bloc `app.import.student`
d'`application.yml` est ajouté dès maintenant (la config multipart
`spring.servlet.multipart.*` reste pour le checkpoint de l'API).

### CP3 réalisé — lecture sécurisée du CSV (composants purs)

**Périmètre strict CP3** : lecture et normalisation *technique* du
fichier. Aucune écriture, aucun accès base, aucune règle métier (syntaxe
d'e-mail, existence de classe, doublons, `planned_action` → CP4), aucun
port, aucune API, aucun stockage du fichier, aucune migration. Composants
purs testables sans contexte Spring.

- **`RecognizedColumn`** — 11 colonnes du modèle réduit (rapport §12.A) :
  6 obligatoires (`last_name`, `first_name`, `email`, `formation_code`,
  `class_code`, `academic_year`) + 5 optionnelles (`phone`,
  `student_number`, `birth_date`, `work_study`, `company_name`) ;
  correspondance d'en-tête insensible à la casse et à l'ordre ;
  `IGNORED_HEADERS` = `level_code` / `promotion_code` /
  `work_study_pattern`.
- **`CsvFileGuard`** — contrôles binaires *avant* parsing : extension
  `.csv`, `Content-Type` restreint à une liste tolérante (vide / absent
  toléré), rejet des contenus binaires (octet nul, magie `PK\x03\x04`
  ZIP/XLSX, OLE2 `D0 CF 11 E0`, `%PDF`), taille bornée, décodage
  **UTF-8 strict** (`CharsetDecoder` `REPORT`) → `IMP_ENCODING_INVALID`,
  BOM toléré et retiré. Renvoie le contenu texte ; le fichier n'est
  jamais écrit sur disque.
- **`CsvParser`** — lecteur RFC 4180 maison : guillemets, guillemet
  doublé, cellule multi-lignes entre guillemets, fins de ligne
  `CRLF`/`LF`, séparateur `,`/`;` auto-détecté sur l'en-tête (celui qui
  reconnaît le plus de colonnes ; égalité → `,`). Lignes entièrement
  vides ignorées et non comptées ; `row_number` = ligne physique du
  fichier (en-tête = 1). Produit `ParsedCsv` : séparateur, en-tête
  classé (`RECOGNIZED`/`IGNORED`/`UNKNOWN`, en-tête reconnu en double →
  `UNKNOWN`), colonnes obligatoires absentes, lignes de données
  (`columnCountMismatch` si nb de cellules ≠ en-tête), indicateurs
  `tooManyRows` / `noDataRows`. Aucune évaluation de cellule.
- **`CsvValueNormalizer`** — normalisation technique (rapport §5.2) :
  `trimToNull`, `collapseSpaces` (noms), `lowerCase` (e-mail),
  `upperCase` (codes), `normalizePhone` (retire espaces/points/tirets/
  parenthèses), `parseBirthDate` (`yyyy-MM-dd` **ou** `dd/MM/yyyy` →
  `BirthDateResult{value, present, malformed}`), `parseWorkStudy`
  (`true/false/oui/non/1/0/yes/no` → `WorkStudyResult{...}`),
  `sanitizeFileName` (basename, `[^A-Za-z0-9._ -]`→`_`, point initial
  retiré), `sha256Hex` (empreinte du contenu — contenu non conservé),
  `truncateReceivedValue` (200 car., sauts de ligne aplatis, jamais dans
  l'audit).
- **`CsvRowNormalizer` → `NormalizedRow`** — mappe une ligne brute selon
  l'en-tête et applique la normalisation champ par champ ; conserve les
  valeurs brutes des cellules reconnues (`rawValues`) pour un futur
  `received_value` d'anomalie ; expose les indicateurs de forme
  (`birthDateMalformed`, `workStudyMalformed`, `phonePresent`,
  `columnCountMismatch`). Aucune décision de gravité ici.

**Tests CP3** (purs, 36) : `CsvFileGuardTests` (11 — extension, type,
ZIP/OLE/PDF, octet nul, UTF-8 invalide, BOM, taille, fichier vide),
`CsvParserTests` (14 — `,`/`;`, `CRLF`, guillemets doublés, cellule
multi-lignes + n° de ligne physique, lignes vides ignorées,
classification d'en-tête, en-tête reconnu en double → `UNKNOWN`,
obligatoires absentes, écart de colonnes, `tooManyRows`, `noDataRows`,
fichier blanc), `CsvValueNormalizerTests` (7),
`CsvRowNormalizerTests` (4).

**Vérifications (31 août 2026)** :
`./mvnw test -Dtest='CsvFileGuardTests,CsvParserTests,CsvValueNormalizerTests,CsvRowNormalizerTests'`
→ **36 tests, 0 échec**. Aucune frontière de module modifiée
(`ModularityTests` non ré-exécuté — inchangé depuis CP2).

**Écart avec la conception (CP3)** : le rapport §15 rangeait aussi « la
dé-duplication fichier » et « la génération de numéro (pure) » au CP3 ; le
découpage retenu place la dé-duplication et les règles de champ au CP4
(« validation et simulation ») et le formatage de numéro au CP6 (seul
checkpoint qui en a réellement besoin), pour éviter du code mort et deux
implémentations concurrentes.

### CP4 réalisé — validation, ports d'import et simulation

**Périmètre strict CP4** : règles de validation métier, ports publics
inter-modules et service de simulation persistant **uniquement**
`student_import_*` (invariant T1). Aucune écriture métier, aucun e-mail,
aucun événement d'audit, aucun contrôleur HTTP, aucune migration (V11
inchangée). Frontières Spring Modulith respectées (`studentimport` ne
dépend que des types **publics** de `academic` / `identity` /
`enrollment`).

- **Port `academic.ClassGroupDirectory.resolveForImport(programCode,
  classCode, academicYearCode)`** + type scellé `ClassGroupResolution`
  (`Found(ref, academicYearStartYear)` / `Miss{PROGRAM_UNKNOWN,
  ACADEMIC_YEAR_UNKNOWN, CLASS_UNKNOWN, CLASS_NOT_IN_PROGRAM,
  CLASS_NOT_IN_YEAR, CHAIN_ARCHIVED}`). Impl `DefaultClassGroupDirectory`
  (repos `findByCodeIgnoreCase` sur `Program` / `AcademicYear` /
  `ClassGroup`, désambiguïsation formation → année, `openForEnrollment`
  déjà existant pour `CHAIN_ARCHIVED`). Aucune décision de sécurité ici.
- **Port `identity.StudentAccountProvisioner`** (+ `DefaultStudentAccountProvisioner`
  confiné) : `findByEmail` (lecture, simulation),
  `prepareStudentAccountAndInvitation` (**`@Transactional` `REQUIRED`**,
  jamais `REQUIRES_NEW` ; crée `user_account PENDING_ACTIVATION` + rôle
  `STUDENT` + `account_invitation` via `InvitationTokenService`, publie
  `AccountInvitationIssuedEvent`, **jamais `AccountLifecycleEvent`** —
  invariant T5), `updateStudentPhone` (action `UPDATE_PROFILE`). Compte
  existant non `PENDING_ACTIVATION` → `StudentAccountProvisioningException`
  (type public), aucune écriture. `AccountInvitationService.issue`
  **inchangé** (parcours HTTP mono-compte).
- **Port `enrollment.StudentEnrollmentProvisioner`** (+ `DefaultStudentEnrollmentProvisioner`
  confiné) : lectures `findProfileByUser` / `findProfileByStudentNumber` /
  `studentNumberTaken` / `describeSituation` (→ `Situation{NONE,
  SAME_CLASS, OTHER_CLASS_SAME_YEAR}` + `currentEnrollmentPublicId`) ;
  applications `provisionProfile` / `provisionEnrollment` /
  `provisionTransfer` / `updateProfileAlternation` — **`@Transactional`
  `REQUIRED`**, écriture directe `saveAndFlush`, **sans**
  `EnrollmentPersister` (`REQUIRES_NEW`) et **sans**
  `EnrollmentChangePublisher` (invariants T2 / T5). Le numéro étudiant
  est **fourni par l'appelant** (jamais généré ici : la table
  `student_number_sequence` appartient à `studentimport` — écart assumé
  vs rapport §4.2, frontières Modulith ; l'allocation atomique est du
  ressort du CP6).
- **`studentimport`** : `StudentImportFieldValidator` (valeurs
  obligatoires → `ERROR IMP_REQUIRED_VALUE_MISSING`, `IMP_COLUMN_COUNT`,
  syntaxe e-mail → `ERROR IMP_EMAIL_INVALID`, téléphone / date de
  naissance / booléen d'alternance → `WARNING`, entreprise recommandée
  si `work_study=true`) ; `FileDuplicateDetector` (doublons e-mail /
  numéro dans le fichier : charge identique → `WARNING` ×N, divergente →
  `ERROR` ×N) ; `PlannedActionResolver` (`@Component`, ports publics
  seulement — résout classe + périmètre + compte + situation → les
  situations §3.3 : `CREATE_ACCOUNT_AND_ENROLL` / `ENROLL_EXISTING` /
  `TRANSFER_CLASS` / `UPDATE_PROFILE` / `NONE`, `IMP_STUDENT_NUMBER_WILL_BE_GENERATED`
  (INFO), `IMP_STUDENT_NUMBER_TAKEN` / `IMP_ACCOUNT_NOT_USABLE` /
  `IMP_CLASS_OUT_OF_SCOPE` / `IMP_PROGRAM_UNKNOWN` … (ERROR)) ;
  `StudentImportSimulationService` (`@Transactional` — garde de filtre de
  périmètre `SCOPE_FORBIDDEN`, `CsvFileGuard` → `CsvParser`, garde
  structurelle (`MISSING_COLUMN` + détail / `TOO_MANY_ROWS` /
  `NO_DATA_ROWS` / `HEADER_UNREADABLE` → exception, **aucun job créé**),
  persistance du job `SIMULATED` + lignes normalisées + anomalies +
  `job_issue` `WARNING` pour colonnes ignorées / inconnues, bilan
  `recordSimulation` avec `confirmable = (blocking == 0 && errorRows ==
  0)`). Mutateurs ajoutés à `StudentImportJob` / `StudentImportRow`
  (laissés minimaux au CP1).

**Décisions / écarts documentés (CP4)** :
- structural BLOCKING (colonne obligatoire absente, trop de lignes,
  aucune donnée) → **exception 4xx, aucun job persisté** (aligné sur la
  table d'erreurs §8 ; la ligne « confirmation → 409 » d'IMP-STU-03 est
  alors sans objet) ;
- filtre de job (`programCode` / `classCode`) : accepté et persisté pour
  référence, mais **refusé (`IMP_SCOPE_FORBIDDEN`) pour un appelant non
  global** ; le contrôle de périmètre **par ligne**
  (`IMP_CLASS_OUT_OF_SCOPE`) reste l'autorité (rapport §9) ;
- `SUSPENDED` ajouté à `LOCKED` / `ARCHIVED` comme compte « non
  exploitable » (le rapport §3.3 ne nomme que les deux derniers) ;
- `UPDATE_PROFILE` = action primaire uniquement pour `SAME_CLASS` +
  divergence contact/alternance ; pour `ENROLL_EXISTING` /
  `TRANSFER_CLASS` la mise à jour de contact est appliquée en plus par la
  confirmation (CP6), l'action stockée restant la primaire ; bilan
  `updated = UPDATE_PROFILE + ENROLL_EXISTING`.

**Tests CP4** (37) : `StudentImportFieldValidatorTests` (8),
`FileDuplicateDetectorTests` (4), `PlannedActionResolverTests` (10,
Mockito), `StudentImportProvisionerContractTests` (3, réflexif —
`@Transactional` présent, jamais `REQUIRES_NEW` sur les écritures des
deux impls), `ClassGroupResolveForImportTests` (7, `@DataJpaTest` MySQL
réel — chaque `Miss.*` + `Found` avec année de début),
`StudentImportSimulationIntegrationTests` (3, `@SpringBootTest` — 100
lignes valides → job `SIMULATED` confirmable, `planned_create_rows=100`,
**`user_account` / `student_profile` / `enrollment` / `account_invitation`
inchangés** (T1) ; colonne obligatoire absente → `IMP_MISSING_COLUMN`,
aucun job ; filtre de périmètre par un `PEDAGOGICAL_MANAGER` →
`IMP_SCOPE_FORBIDDEN`). `EsicConnectApplicationTests` vert,
`ModularityTests` vert.

**Vérifications (31 août 2026)** :
`./mvnw test -Dtest='StudentImportFieldValidatorTests,FileDuplicateDetectorTests,PlannedActionResolverTests,StudentImportProvisionerContractTests,ClassGroupResolveForImportTests,StudentImportSimulationIntegrationTests,ModularityTests,EsicConnectApplicationTests'`
→ **37 tests, 0 échec**.

### CP5 réalisé — API de simulation et de consultation

**Périmètre strict CP5** : couche HTTP de la **phase de simulation** et
la **consultation**. Aucun endpoint de confirmation ni d'annulation
(cycle de vie → CP8). Aucune migration.

- **`application.yml`** : bloc `spring.servlet.multipart` (`max-file-size`
  2 MB, `max-request-size` 3 MB ; variables `MULTIPART_*`). Le seul
  téléversement du prototype.
- **`StudentImportController`** (`/api/v1/student-imports`,
  `@PreAuthorize(MANAGE_ROLES)`) :
  - `POST` `multipart/form-data` (`file` + `programCode?` / `classCode?`)
    → `201 JobResponse` — résout l'auteur via `CurrentUserResolver`,
    appelle `StudentImportSimulationService.simulate`, renvoie le job
    rechargé ;
  - `GET` (query `status?`, `sort∈{createdAt}`, `page`, `size≤50`) →
    `200 PageResponse<JobResponse>` ;
  - `GET /{publicId}` → `200 JobResponse` (+ `summary` + `issues[]` +
    `confirmable`) ;
  - `GET /{publicId}/rows` (query `rowStatus?`, `severity?`, `action?`,
    `sort∈{rowNumber}`, `page`, `size≤100`) →
    `200 PageResponse<RowResponse>` (anomalies de ligne incluses, chargées
    en une requête `IN`).
- **`StudentImportQueryService`** : décision fine de périmètre côté
  serveur — un appelant **sans accès global** (`PEDAGOGICAL_MANAGER`) ne
  liste et ne consulte que **ses** jobs (`requested_by_id` = appelant) ;
  job d'un autre → `403 IMP_JOB_FORBIDDEN` ; job inconnu / `public_id`
  mal formé → `404 IMP_JOB_NOT_FOUND`. `StudentImportSpecifications` (+
  `JpaSpecificationExecutor` sur job / row repos ; filtre `severity` =
  sous-requête `exists` sur `student_import_row_issue`) ; valeur de filtre
  hors énumération → `400 IMP_INVALID_FILTER` ; tri hors liste blanche →
  `400 IMP_INVALID_SORT` (`StudentImportQuerySupport`).
- **DTO** (`StudentImportResponses`) : `JobResponse` / `Summary` /
  `AppliedSummary` (null tant que non appliqué) / `JobIssueResponse` /
  `RowResponse` / `RowIssueResponse` — **aucun `id` SQL**, aucun jeton,
  aucun hachage ; `receivedValue` tronqué rendu à la revue mais jamais à
  l'audit. `PageResponse` local au module.
- **`StudentImportExceptionHandler`** : `@Order(HIGHEST_PRECEDENCE)`
  ajouté — sans lui, `shared.web.GlobalExceptionHandler` (advice global
  non ordonné) était consulté avant l'advice du module (tri des
  `@RestControllerAdvice` non ordonnés ⇒ « shared » avant
  « studentimport ») et un `StudentImportException` retombait en 500. Les
  autres modules y échappent par ordre alphabétique ; `@Order` rend le
  comportement explicite.

**Tests CP5** : `StudentImportApiIntegrationTests` (7, `@SpringBootTest`
RANDOM_PORT + `TestRestTemplate` multipart) — téléversement → `201` sans
`id` SQL, colonne obligatoire absente → `400 IMP_MISSING_COLUMN` (+
`details` contenant `email`), contenu ZIP/XLSX → `415`, endpoint `rows`
pagination + filtre `rowStatus`, `PEDAGOGICAL_MANAGER` ne voit que ses
jobs (`403 IMP_JOB_FORBIDDEN` sur celui d'un autre, `200` pour
l'administration globale), job inconnu → `404`, tri / filtre invalides →
`400`, matrice `401` anonyme / `403` `STUDENT` / `403` `TEACHER`.
`ModularityTests` + `EsicConnectApplicationTests` verts.

**Vérifications (31 août 2026)** :
`./mvnw test -Dtest='com.esic.connect.studentimport.**,ClassGroupResolveForImportTests'`
→ **107 tests, 0 échec**.

**Écart avec la conception (CP5)** : `cancel` (rangé au CP5 par le
rapport §15) est déplacé au CP8 (« API de confirmation et cycle de
vie »), avec `confirm` — les deux sont des mutations d'état du job.

### CP6 réalisé — confirmation transactionnelle

**Périmètre strict CP6** : service de confirmation. Aucun contrôleur HTTP
(→ CP8), **aucun `StudentImportChangeEvent` ni listener d'audit** (→ CP7),
aucune migration.

- **`StudentNumberAllocator`** (rapport §3.2) : `allocate(startYear)` →
  `student_number_sequence.bump` (native `INSERT ... ON DUPLICATE KEY
  UPDATE` — verrou de ligne sur `start_year`, sérialise les
  confirmations d'une même année, annulé au rollback) + relecture
  **native** de `next_value` (contourne le cache L1 sans détacher les
  autres entités) ; `format` = `ESIC-%04d-%0Nd` (largeur `numberSequenceWidth`) ;
  `allocated ≥ 10^largeur` → `IMP_STUDENT_NUMBER_EXHAUSTED`.
- **`StudentImportConfirmationService`** :
  - `confirm(...)` — **entrée publique non transactionnelle** — délègue à
    `runConfirmation` (via `ObjectProvider` self-proxy pour que
    `@Transactional` s'applique).
  - `runConfirmation` — **une seule transaction `@Transactional`
    (`REQUIRED`)** : `findWithLockByPublicId` (`SELECT … FOR UPDATE`) ;
    idempotence `APPLIED` → bilan mémorisé + `alreadyApplied = true`
    (T6) ; `CANCELLED` → `IMP_JOB_CANCELLED` ; `expires_at` dépassé →
    `IMP_SIMULATION_EXPIRED` ; non `SIMULATED` ou non `confirmable` →
    `IMP_NOT_CONFIRMABLE` ; périmètre → `IMP_CONFIRM_FORBIDDEN` ;
    **re-validation complète** (reconstruit `NormalizedRow` depuis les
    colonnes persistées, re-`PlannedActionResolver.resolve` live) — une
    ligne devenue `ERROR` ⇒ la transaction **commite sans rien
    appliquer** (job intact) et retourne un « stale outcome » ;
    sinon **application** ligne par ligne (ordre `rowNumber`) via
    `identity.StudentAccountProvisioner.prepareStudentAccountAndInvitation`
    (compte `PENDING_ACTIVATION` + rôle `STUDENT` + invitation, publie
    `AccountInvitationIssuedEvent` — e-mail `AFTER_COMMIT` seulement, T4)
    et `enrollment.StudentEnrollmentProvisioner` (profil / inscription /
    transfert / patch contact) — **numéro pré-alloué puis testé libre
    avant l'INSERT** (une collision au flush poisonnerait la transaction
    unique ; nouvelle tentative bornée `numberAllocMaxRetries` faite
    hors persistance ; épuisement → `IMP_STUDENT_NUMBER_ALLOC_FAILED`) ;
    `job.markApplied(...)` + bilan `applied_*` dérivé des `applied_outcome`.
    Toute exception d'application (`StudentAccountProvisioningException`,
    collision d'unicité `DataIntegrityViolationException`) →
    `IMP_STALE_SIMULATION` + **rollback total** (T3) : 0 compte / profil /
    inscription / rôle / invitation, séquence non consommée, job
    `SIMULATED`. Robustesse aux e-mails en double dans le fichier :
    l'enrôlement passe systématiquement par `describeSituation` (la 2ᵉ
    ligne voit le profil créé par la 1ʳᵉ → `SAME_CLASS` → NOOP).
  - `confirm` retourne `ConfirmationResult(jobPublicId, alreadyApplied,
    created, updated, transferred, invited, ignored)`.
- **`StaleRevalidationPersister`** (`@Transactional(REQUIRES_NEW)`) —
  persiste les anomalies **rafraîchies** (statuts de ligne + `row_issue`
  + compteurs job + `confirmable=false`) ; appelé **après** que la
  transaction de confirmation a commité sans rien appliquer (verrou
  relâché — pas de deadlock avec le `SELECT … FOR UPDATE`). Ce
  `REQUIRES_NEW` ne touche **que** `student_import_*` (jamais de donnée
  métier) et est strictement postérieur à une re-validation en lecture
  seule ⇒ ne viole pas l'esprit de l'invariant T2.

**Tests CP6** (13) : `StudentNumberAllocatorTests` (3, `@DataJpaTest` —
format, incrément consécutif par année, borne de largeur),
`StudentImportConfirmationIntegrationTests` (7, `@SpringBootTest` — 100
lignes → `APPLIED`, +100 `user_account` `PENDING_ACTIVATION` + rôle
`STUDENT` + 100 `student_profile` (`ESIC-2026-00001..`) + 100
`enrollment` + 100 `account_invitation` + 100 e-mails capturés +
`next_value=101` ; reconfirmation → `alreadyApplied=true`, 0 nouvelle
écriture, 0 e-mail (TI-012) ; `expires_at` passé → `SIMULATION_EXPIRED`,
job `SIMULATED` ; statut `CANCELLED` → `JOB_CANCELLED` ; ligne e-mail
invalide → `NOT_CONFIRMABLE` ; classe archivée entre simulation et
confirmation → `STALE_SIMULATION`, 0 compte, job `SIMULATED`, 1 ligne
`ERROR` persistée ; transfert conservant l'historique — ancienne
inscription `TRANSFERRED`, nouvelle `ACTIVE`, **aucun doublon de
compte** — TI-005/006, AC-005/006),
`StudentImportConfirmationRollbackTests` (2, `@SpringBootTest` — échec
sur la **dernière ligne** (son numéro devient pris juste avant la
confirmation) → `STALE_SIMULATION`, **0 compte / profil / inscription /
invitation créés par l'import** (rollback total de la ligne 1),
`next_value` inchangé, 0 e-mail, job `SIMULATED` (§14.4) ; deux
confirmations concurrentes (pool 2 threads) → exactement **un** jeu de
20 comptes créés, l'autre `alreadyApplied` ou `StudentImportException`,
jamais 40, jamais 500 (§14.6)).

**Correctif collatéral** : `StudentImportSchemaConstraintsTests` (CP1)
utilisait `start_year` `2026` / `2027` — les tests fonctionnels CP6
commitent désormais une ligne `student_number_sequence` `2026` (base de
test partagée). Le test de schéma bascule sur des années sentinelles
`2901`–`2904` (la valeur n'a aucune importance pour un test de
contrainte) ; comportement inchangé.

**Vérifications (31 août 2026)** :
`./mvnw test -Dtest='com.esic.connect.studentimport.**,ClassGroupResolveForImportTests,EsicConnectApplicationTests'`
→ **120 tests, 0 échec** ; `ModularityTests` vert.

### CP7 réalisé — audit et purge des imports

**Périmètre strict CP7** : événement d'audit + listener + purge
planifiée. Aucun contrôleur HTTP (→ CP8), aucune migration.

- **`com.esic.connect.studentimport.StudentImportChangeEvent`** (record
  public) + `StudentImportChangeAction` (`SIMULATED` / `CONFIRMED` /
  `CANCELLED` / `EXPIRED`). Ne transporte **aucune donnée personnelle** :
  `jobPublicId`, `actorUserId` (interne), action, `detail` non sensible
  (`job=…;rows=…;confirmable=…` pour SIMULATED,
  `job=…;created=…;updated=…;moved=…;invited=…;ignored=…` pour CONFIRMED).
- Publication **dans la transaction** : `StudentImportSimulationService`
  publie `SIMULATED` en fin de `simulate` ;
  `StudentImportConfirmationService` publie `CONFIRMED` juste avant de
  retourner (dans `runConfirmation`). Reconfirmation idempotente ⇒ pas de
  ré-audit.
- **`audit.internal.StudentImportAuditListener`** — **déviation volontaire**
  du motif legacy : `@TransactionalEventListener(phase = AFTER_COMMIT)`
  **+** `@Transactional(propagation = REQUIRES_NEW)`. Le listener n'est
  invoqué qu'après le commit de la transaction émettrice ; si elle
  rollback, la phase `AFTER_COMMIT` n'est jamais atteinte → **aucune
  ligne d'audit** (invariant T5). La ligne `audit_event` (catégorie
  `STUDENT_IMPORT`, action `STUDENT_IMPORT_SIMULATED` /
  `STUDENT_IMPORT_CONFIRMED`, `resource_public_id` = job) est écrite dans
  la transaction dédiée. Aucune dépendance vers `studentimport.internal`
  (`ModularityTests` vert).
- **`StudentImportPurgeService`** (`@Scheduled(cron =
  "${app.import.student.purge-cron:0 30 3 * * *}")` — `@EnableScheduling`
  ajouté à `StudentImportConfig` ; méthode `purge()` publique et
  transactionnelle, testable directement). Décisions de prototype
  (rapport §12.C) : jobs `SIMULATED` / `EXPIRED` dont `expires_at` est
  dépassé + jobs `CANCELLED` plus vieux que `simulation-ttl` →
  **supprimés** (chaîne `job_issue` / `row` / `row_issue` en
  `ON DELETE CASCADE`) ; jobs `APPLIED` confirmés depuis plus de
  `applied-rows-ttl` → **lignes filles supprimées, en-tête et agrégats
  `applied_*` conservés** ; `student_number_sequence` jamais purgée.
  Aucune donnée métier touchée.

**Tests CP7** (5) : `StudentImportAuditIntegrationTests` (3,
`@SpringBootTest` — simulation → 1 ligne `STUDENT_IMPORT_SIMULATED` sans
PII ; confirmation committée → 1 ligne `STUDENT_IMPORT_CONFIRMED` visible
après retour de l'appel, `detail` sans PII ; confirmation en rollback
(collision dernière ligne) → **0 ligne `_CONFIRMED`** — §14.4 (a) /
§14.3),
`StudentImportPurgeTests` (2, `@SpringBootTest` — simulation expirée
supprimée en cascade + job récent préservé ; job `APPLIED` ancien
conserve `status`/`applied_created` mais perd `student_import_row` et
`student_import_job_issue`).

**Vérifications (31 août 2026)** :
`./mvnw test -Dtest='com.esic.connect.studentimport.**,ClassGroupResolveForImportTests,ModularityTests,EsicConnectApplicationTests,com.esic.connect.audit.**'`
→ **127 tests, 0 échec** ; `ModularityTests` vert.

### CP8 réalisé — API de confirmation et cycle de vie

**Périmètre strict CP8** : couche HTTP de la confirmation et de
l'annulation. Aucune migration.

- **`StudentImportController`** — deux endpoints ajoutés
  (`@PreAuthorize(MANAGE_ROLES)`) :
  - `POST /api/v1/student-imports/{publicId}/confirm` → **`200`**
    `ConfirmationResultResponse` (jamais `201` : la ressource existe
    déjà). Reconfirmation d'un job `APPLIED` → `200` +
    `alreadyApplied = true` + bilan mémorisé (invariant T6). Erreurs :
    `409 IMP_NOT_CONFIRMABLE` / `IMP_STALE_SIMULATION` (anomalies
    rafraîchies persistées — le client recharge `/rows`) /
    `IMP_SIMULATION_EXPIRED` / `IMP_JOB_CANCELLED`, `403 IMP_CONFIRM_FORBIDDEN`,
    `404 IMP_JOB_NOT_FOUND`.
  - `POST /api/v1/student-imports/{publicId}/cancel` → **`204`**. Erreurs :
    `409 IMP_JOB_NOT_CANCELLABLE` (job non `SIMULATED`),
    `403 IMP_JOB_FORBIDDEN`, `404 IMP_JOB_NOT_FOUND`.
- **`StudentImportConfirmationService.cancel(...)`** (`@Transactional`) :
  charge le job (404), contrôle fin de périmètre (`PEDAGOGICAL_MANAGER`
  = son propre job → 403 sinon), `status != SIMULATED` →
  `IMP_JOB_NOT_CANCELLABLE`, `job.markCancelled(now, actor)` + publication
  `StudentImportChangeEvent(CANCELLED)` (audité `STUDENT_IMPORT_CANCELLED`
  par le listener CP7).
- `StudentImportJob.markCancelled(...)` (mutateur) +
  `StudentImportResponses.ConfirmationResultResponse`
  (`{ jobPublicId, alreadyApplied, created, updated, transferred,
  invited, ignored }`).

**Tests CP8** : `StudentImportLifecycleApiIntegrationTests` (5,
`@SpringBootTest` + `TestRestTemplate`) — `confirm` → `200`
`alreadyApplied=false`, `created=2`, job `APPLIED` ; reconfirmation →
`200` `alreadyApplied=true`, même bilan (TI-012) ; `confirm` sur job non
confirmable / expiré / annulé → `409` `IMP_NOT_CONFIRMABLE` /
`IMP_SIMULATION_EXPIRED` / `IMP_JOB_CANCELLED` ; `cancel` d'un job
`SIMULATED` → `204` puis statut `CANCELLED` ; `cancel` d'un job `APPLIED`
→ `409 IMP_JOB_NOT_CANCELLABLE` ; `cancel` d'un job inconnu → `404` ;
`401` anonyme, `403` `STUDENT`, `403` pour un `PEDAGOGICAL_MANAGER` sur
le `confirm`/`cancel` du job d'un autre RP ; deux `confirm` HTTP
concurrents → exactement **un** `200` non idempotent (10 comptes créés,
jamais 20), l'autre `200` idempotent ou `409`, jamais `500`.

**Vérifications (31 août 2026)** :
`./mvnw test -Dtest='com.esic.connect.studentimport.**'` → **122 tests,
0 échec** ; `ModularityTests` vert.

**Écart avec la conception (CP8)** : `cancel` (rapport §15 → CP5) a été
regroupé ici avec `confirm` (mutations d'état du job).

### CP9 réalisé — interface Angular d'import des apprenants

**Périmètre strict CP9** : écrans front-end. Aucun fichier back-end,
migration ou `docs/01`–`docs/04` modifié ; aucune dépendance npm ajoutée.

- **Routes** `frontend/src/app/features/students/import/` :
  `/students/import` → `StudentImportHome`, `/students/import/:publicId`
  → `StudentImportReview`. Déclarées **avant** `/students` et **hors de
  son sous-arbre** :
  `roleGuard(['ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER'])`
  (le parent `/students` restreint ses enfants à trois rôles seulement ;
  l'import est aussi ouvert au `PEDAGOGICAL_MANAGER`, limité à son
  périmètre côté serveur). Entrée de navigation « Import apprenants »
  (`upload_file`, 4 rôles).
- **`StudentImportApiService`** — une méthode par endpoint réel
  (`simulate` en `FormData` multipart — fichier transmis brut, jamais lu
  côté navigateur ; `listJobs` / `getJob` / `listRows` / `confirm` /
  `cancel`) ; jamais de jeton en URL ; `HttpParams` sans clé vide.
- **`student-import.models.ts`** — miroir exact des DTO back-end + libellés
  FR (`plannedAction`, `rowStatus`, statut de job, gravité).
- **`student-import-errors.ts`** — `toStudentImportError` : **liste
  blanche explicite** `KNOWN_IMP_CODES` (jamais `startsWith('IMP_')`) ;
  code hors liste ou `5xx` → `code` `null` + message générique, jamais le
  corps brut ; drapeaux `forbidden` / `notFound` / `stale` / `expired`.
- **`StudentImportHome`** — `input[type=file]` `accept=".csv"` + refus
  client (extension, taille > 2 Mo) ; codes de périmètre facultatifs ;
  « Lancer la simulation » désactivé tant qu'aucun fichier valide ; barre
  de progression ; `201` → navigation vers la revue ; anomalie globale →
  message contrôlé (jamais le corps brut) ; `403` → « périmètre » ;
  `mat-table` des imports récents (`GET /student-imports`). Formulaire
  neutralisé si le contexte de rôle actif ne permet pas d'importer ;
  réponse tardive ignorée dans ce cas.
- **`StudentImportReview`** — cartes de synthèse (total, à créer / mettre
  à jour / transférer / sans changement, avertissements, erreurs),
  bandeau des anomalies globales, table des lignes (`GET .../rows`,
  pagination serveur ≤ 100, filtres `rowStatus` / `severity` / `action`
  remettant à la page 0, tri `rowNumber,asc`, anomalies dépliables avec
  valeur reçue) ; confirmation **en ligne** avec récapitulatif chiffré,
  bouton désactivé si `!confirmable`, double soumission bloquée, capacité
  revérifiée au clic ; `200` → bandeau succès + bilan (`alreadyApplied`
  → « déjà appliqué ») ; `409 IMP_STALE_SIMULATION` → rechargement
  synthèse + lignes, bandeau « plus à jour », confirmation bloquée ;
  `IMP_SIMULATION_EXPIRED` / `IMP_NOT_CONFIRMABLE` / `403` → message
  contrôlé, aucun faux succès ; bouton « Annuler l'import » →
  `POST .../cancel` → `204`. Perte du contexte de rôle d'écriture →
  panneau fermé, actions masquées, réponse en vol ignorée.

**Tests CP9** (17, Vitest) : `student-import-api.service.spec.ts` (4 —
`FormData` + parts de périmètre conditionnels, params conditionnels,
`encodeURIComponent`, `confirm`/`cancel` corps `{}` / `204`),
`student-import-home.spec.ts` (5 — refus client `.csv` / taille, `submit`
gaté puis navigation sur `201`, mapping d'anomalie globale sans corps
brut, formulaire neutralisé hors contexte d'import, rien en storage),
`student-import-review.spec.ts` (8 — chargement synthèse + lignes,
filtres → page 0, confirmation → bilan mémorisé, `IMP_STALE_SIMULATION`
→ rechargement + blocage, `IMP_SIMULATION_EXPIRED` → message sans faux
succès, `cancel` → rechargement, perte du contexte → réponse tardive
ignorée, rien en storage). Specs mis à jour : `app-shell.spec.ts`
(entrée « Import apprenants » pour `ADMIN` et `PEDAGOGICAL_MANAGER`).

**Vérifications (31 août 2026, Node 24)** : `npm test` → **471 tests, 0
échec** (454 → 471) ; `npm run lint` → « All files pass linting » ;
`npm run build` → bundle initial **483,26 kB** brut / 122,84 kB
transféré (seuil 500 kB, aucune alerte). `./mvnw` non ré-exécuté (aucun
fichier back-end modifié).

## Tranche précédente — Gestion de l'assiduité et reporting (V10, fusionnée PR #22)

```text
Branche `feature/attendance-management-and-reporting` (créée depuis `main`
= `dc6da1a`). PR à ouvrir après validation locale. NON fusionnée.
```

Très grande tranche verticale full-stack qui complète le module
d'assiduité. **Migration additive `V10`** (V1–V9 inchangées) ; frontières
Spring Modulith respectées. Rapport de conception :
`docs/reports/ATTENDANCE_MANAGEMENT_DESIGN.md` (décisions figées + 6
divergences documentées vs docs/02 & docs/04, + §10 « livraison réelle »).

> **Passe corrective PR #22 — 1ʳᵉ passe (30 août 2026, état antérieur)** —
> 11 points de revue traités en 3 commits sur la branche existante :
> - **§1** `AttendanceReportService` ne remplace plus silencieusement un
>   `timeZoneId` persistant invalide par UTC : `persistedZone()` lève une
>   erreur interne contrôlée (→ 500 générique, valeur jamais exposée),
>   même convention que le module `alternation` ;
> - **§2** nouveau contrat borné à la séance
>   `GET /api/v1/sessions/{id}/attendance/candidates` (inscriptions
>   actives des classes, dédupliquées, sans e-mail ni id SQL, contrôle
>   fin = lecture des présences) ; le front `SessionDetail` remplace le
>   champ libre par un `mat-select` alimenté par cet endpoint ;
> - **§3** tests de concurrence déterministes ajoutés (QR/code vs présence
>   manuelle, deux corrections, deux examens de justificatif, ouverture /
>   fermeture d'un point de contrôle, deux créations manuelles) → une
>   écriture, un conflit contrôlé, aucun 500 ;
> - **§6** tri serveur borné `AttendanceReportSort` (liste blanche par
>   rapport, `field,asc|desc`, tri en mémoire avant pagination + tri
>   secondaire stable) → `400 ATT_REPORT_INVALID_SORT` sinon ; contrôle
>   front `mat-select` aligné ;
> - **§7** code de classe lisible dans les rapports (port
>   `academic.ClassGroupDirectory`), plus jamais `UUID.toString()` ;
> - **§8** export CSV borné à la séance
>   `GET /api/v1/sessions/{id}/attendance/export` (formateur affecté
>   autorisé, protections CSV, nom de fichier contrôlé) + bouton
>   `SessionDetail` ;
> - **§4/§5** front : formulaires d'annulation de point de contrôle et de
>   présence séparés (`checkpointCancelForm` / `attendanceCancelForm`) ;
>   revérification du droit effectif dans chaque callback de succès et
>   `effect()` explicites (`SessionDetail`, `JustificationQueue`,
>   `MyAttendanceList`, `MyAttendanceDetail`) ;
> - **§9** démonstration locale réelle exécutée contre le back-end
>   (profil `demo`, schéma jetable `esic_pr22_verify` créé au compte root
>   puis supprimé) — 15 étapes vertes (voir `docs/11-guide-demonstration.md`
>   §10) ;
> - vérifs de cette passe (état antérieur) : back-end `./mvnw clean test`
>   → **545 tests, 0 échec** ; front `npm test` → **451 tests, 0 échec**.
>
> **Passe corrective PR #22 — 2ᵉ passe (30 août 2026, état courant)** —
> 8 points de revue en ≤ 2 commits, aucune nouvelle fonctionnalité,
> aucune migration :
> - **§1 (sécurité, hors commit)** : le contenu de `.env` a été affiché
>   lors d'une exécution antérieure — l'affirmation « aucun secret
>   affiché » est corrigée. Les mots de passe **MySQL (`root` + `esic_app`)
>   et Redis** locaux ont été **rotés** le 2026-08-30T18:35:39Z en
>   préservant les volumes (schéma en version 10 intact) ; `.env` reste
>   ignoré par Git, jamais versionné ; aucun artefact de démonstration
>   sensible ne subsiste. Aucun commit produit par cette étape.
> - **§2** : l'éligibilité des candidats et la validation d'une saisie
>   manuelle dépendent désormais de la **date locale de la séance**
>   (`startsAt` + fuseau persisté ; aucun repli UTC silencieux) — nouveau
>   `EnrollmentDirectory.findRosterForClassesOn` /
>   `isEnrollmentValidOn` : une inscription débutant après la séance ou
>   terminée avant est exclue et refusée, même si active aujourd'hui.
> - **§3** : matrice de sécurité du endpoint des candidats exercée avec
>   des fixtures réelles (ADMIN 200, `SCHOOL_ADMINISTRATION` 200,
>   `PEDAGOGICAL_MANAGER` dans / hors périmètre 200 / 403, `TEACHER`
>   affecté / non affecté 200 / 403, `STUDENT` 403, anonyme 401).
> - **§4** : `SessionDetail` sépare `canManageCheckpoint()` (points de
>   contrôle + QR ; `SCHOOL_ADMINISTRATION` exclu),
>   `canManageAttendance()` (saisie manuelle / correction / annulation +
>   candidats ; `SCHOOL_ADMINISTRATION` inclus) et `canReadAttendance()` ;
>   chaque callback sensible revérifie sa capacité au clic.
> - **§5** : `AttendanceSummary` (et `AttendanceReport`,
>   `JustificationQueue`) injectent `RoleContextService` : perte du droit
>   actif → requête en cours invalidée (jeton monotone), synthèse
>   effacée, réponse tardive ignorée, plus aucune requête ; contexte
>   retrouvé → rechargement propre.
> - **§6** : l'export CSV de séance ne contient plus la colonne libre
>   `commentaire` (minimisation) ; neutralisation d'injection de formule
>   conservée sur toutes les cellules.
> - **§7** : totaux de tests et rapport de sécurité assainis
>   (ci-dessous et dans la PR).
> - **§8** : suite back-end complète sur base propre + suite front
>   complète + lint + build + démo API minimale sur base temporaire
>   distincte, puis nettoyage.
> - vérifs de cette passe (état courant) : back-end `./mvnw clean test`
>   → **548 tests, 0 échec**, `ModularityTests` vert, `V10` inchangée ;
>   front `npm run lint` OK, `npm test` → **454 tests, 0 échec**,
>   `npm run build` **482,24 kB** (< 500 kB).

**Back-end** (`./mvnw clean test` sur MySQL 8.4 / Redis 7 → **548 tests,
0 échec** (état courant ; 532 puis 545 aux passes antérieures),
`ModularityTests` vert, `V10` appliquée — schéma en version 10) :

- **V10** : `attendance_checkpoint` — retrait de l'unicité « un point de
  contrôle par séance », ajout `label` / `checkpoint_type`
  (`START`|`END`|`CUSTOM`) / `display_order` (unique par séance) /
  `status` (`PLANNED`|`OPEN`|`CLOSED`|`CANCELLED`) / `required` /
  `cancel_reason` / colonnes auteur ; reprise déterministe des lignes V9
  (index simple créé d'abord pour libérer la FK). `attendance_record` —
  `status` (`PRESENT`|`LATE`|`ABSENT`|`EXCUSED_ABSENCE`|`CANCELLED`),
  `source` étendue (+ `MANUAL`, `CORRECTION`), `late_minutes`, `comment`,
  `recorded_by_id`, `last_corrected_at`, `corrected_by_id`,
  `cancelled_at` ; unicité `(checkpoint, enrollment)` de V9 **conservée**.
  `attendance_correction` (append-only). `attendance_justification`
  (métadonnée métier **sans fichier** ; colonne générée
  `active_justification_key` → un seul justificatif actif par absence,
  re-dépôt après refus).
- **`coursesession`** : `AttendanceCheckpointService` +
  `AttendanceCheckpointController`
  (`GET/POST /api/v1/sessions/{id}/checkpoints`, `.../{cpId}/open|close|cancel`) ;
  ouverture de séance → ouvre le `START`, fermeture → ferme tous les
  points de contrôle ouverts. Événement public
  `AttendanceCheckpointChangeEvent` → `audit` (catégorie
  `COURSE_SESSION`) + `attendance` (purge du jeton Redis du point de
  contrôle). `CourseSessionResponse` + `checkpoints[]`.
  `CourseSessionDirectory` : `SessionRef` porte désormais une liste de
  `CheckpointRef` (+ `teacherUserId`, `timeZoneId`) ; nouvelles méthodes
  `findCheckpointForAttendance`, `findSessionsForClasses`,
  `findSessionsInRange`, `findSessionByCheckpointPublicId`.
- **`attendance`** : `AttendanceTokenService` — jeton **par point de
  contrôle** (payload `sessionPublicId\ncheckpointPublicId` ; pointeur
  courant `session -> token\ncode\ncheckpointId` ; invariant conservé ;
  `invalidateCheckpoint` sélective). `validate` classe la présence
  `PRESENT` / `LATE` selon `app.attendance.late-threshold` (défaut
  `PT10M`, ≥ 0, fail-fast ; référence = `starts_at` de la séance).
  `listForSession` détaille par point de contrôle.
  `AttendanceManagementService` (présence manuelle, correction,
  annulation logique — motif obligatoire, historique append-only, verrou
  optimiste → 409). `AttendanceJustificationService` (dépôt / modif
  `PENDING` / examen ; `ACCEPTED` → `ABSENT → EXCUSED_ABSENCE` ;
  `REJECTED` motivé → reste `ABSENT` ; TEACHER exclu de l'examen ;
  périmètre via `AcademicScopeDirectory`). `StudentAttendanceService`
  (`GET /api/v1/me/attendance*` — présences réelles + absences
  **dérivées** d'un point de contrôle fermé, jamais persistées ;
  apprenant résolu du seul JWT). `AttendanceReportService` +
  `AttendanceReportController` (`/api/v1/attendance/reports/{sessions,classes,students,summary}`
  + `.../export` CSV ; paramètre `sort` borné par `AttendanceReportSort`,
  hors liste blanche → `400 ATT_REPORT_INVALID_SORT`). Codes de classe
  des rapports résolus via `academic.ClassGroupDirectory` (jamais l'UUID).
  `AttendanceManagementController` porte aussi
  `GET /api/v1/sessions/{id}/attendance/candidates` (candidats à la
  saisie manuelle) et `GET .../attendance/export` (CSV borné à la
  séance). `AttendanceCsvWriter` (UTF-8 + BOM, `;`,
  RFC 4180, **neutralisation d'injection de formule**). Audit
  `ATTENDANCE_MANUAL_RECORDED` / `_CORRECTED` / `_CANCELLED` /
  `JUSTIFICATION_SUBMITTED` / `_UPDATED` / `_REVIEWED` / `REPORT_EXPORTED`.
- **Port `alternation.AlternationDirectory`** (nouveau) : contexte
  `SCHOOL` / `COMPANY` / `UNKNOWN` d'une inscription à une date — les
  demi-journées `COMPANY` sont exclues du dénominateur, les `UNKNOWN`
  non satisfaites sont comptées à part (jamais en absence).
- **`EnrollmentDirectory`** : + `findEnrollmentsForUser`,
  `findActiveRosterForClasses` (record `RosterEntry`) ; `EnrollmentRef`
  porte `studentUserPublicId`.
- Rôles : checkpoints / présence manuelle / correction =
  `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION`(global)/`PEDAGOGICAL_MANAGER`(périmètre)/`TEACHER`(ses séances)
  (`SCHOOL_ADMINISTRATION` exclu de la **gestion des points de
  contrôle**) ; examen des justificatifs = idem sauf `TEACHER` ;
  rapports = 4 rôles sans `TEACHER` ; `/me/attendance*` et dépôt de
  justificatif = `STUDENT`. Décision fine côté serveur (contexte Spring
  Security), jamais d'après un paramètre client.
- `application.yml` : + `app.attendance.late-threshold`.

**Front-end** (`npm test` → **454 tests, 0 échec** (état courant ; 444
puis 451 aux passes antérieures) ; `npm run lint` OK ; `npm run build`
**482,24 kB** brut / 122,57 kB transféré, seuil 500 kB) :

- `sessions.models.ts` / `SessionsApiService` étendus (points de
  contrôle, jeton par point de contrôle, présence manuelle, correction,
  annulation, historique). `session-errors.ts` : liste blanche étendue
  aux nouveaux codes `ATT_*`.
- `features/attendance/` : `attendance.models.ts`, `AttendanceApiService`
  (`/me/*`, examen, rapports, export CSV en `blob` +
  `triggerCsvDownload` programmatique), `attendance-errors.ts`.
- `SessionDetail` enrichi : gestion des points de contrôle (création,
  ouverture / fermeture / annulation en confirmation en ligne — motif
  d'annulation d'un point de contrôle et motif d'annulation d'une
  présence dans **deux `FormGroup` distincts**, §4), QR ciblant le point
  de contrôle ouvert sélectionné, présences par point de contrôle (chips
  statut + texte, retard, absents dérivés), présence manuelle avec
  **`mat-select` de candidats** alimenté par
  `GET .../attendance/candidates` (§2 ; états chargement / vide / erreur /
  403 ; identifiant seulement dans la valeur du contrôle), correction,
  annulation logique, historique dépliable, **bouton d'export CSV des
  présences de la séance** (§8) ; un contexte de rôle sans droit de
  gestion ferme tous les formulaires, efface les candidats, arrête le
  polling et le renouvellement du jeton ; chaque callback de succès
  revérifie le droit effectif (§5 : aucune fausse confirmation si le
  droit a été perdu pendant l'appel).
- **`/my-attendance`** + `/my-attendance/:id` (garde `STUDENT`) :
  historique filtrable, dépôt et suivi d'un justificatif métier sans
  fichier, modification tant que `PENDING`.
- **`/attendance-management`** (garde
  `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION`/`PEDAGOGICAL_MANAGER`),
  sous-routes `summary` (défaut) / `sessions` / `classes` / `students` /
  `justifications` : cartes de synthèse, rapports tabulaires (filtres +
  **contrôle de tri `mat-select` borné à la liste blanche serveur**,
  §6 ; pagination serveur), export CSV (blob), file d'examen des
  justificatifs (accepter / refuser motivé en confirmation en ligne ;
  `effect()` fermant le panneau à la perte du droit, §5).
- Navigation : entrées « Mes présences » (`STUDENT`) et « Suivi
  d'assiduité » (4 rôles). Aucune bibliothèque graphique ajoutée. JWT /
  contexte de rôle en mémoire seule ; aucun accès `localStorage` /
  `sessionStorage` (asserté) ; aucun jeton ni filtre sensible en URL.

**Limites** : contexte d'alternance `UNKNOWN` non compté comme absence
(rapport « utile » ⇒ rythme d'alternance affecté à la classe) ;
`TEACHER` hors rapports agrégés ; présence manuelle : sélecteur de
candidats et validation bornés à l'inscription **valable le jour de la
séance** (statut `ACTIVE` + période couvrant la date locale de la
séance ; §2, 2ᵉ passe) ;
justificatif = métadonnée (aucune pièce jointe) ; un `timeZoneId`
persistant invalide fait échouer le rapport en 500 contrôlé plutôt que de
produire des chiffres trompeurs (§1) ; pas de scan caméra, QR fixe,
contrôle réseau, WebAuthn ; pas de test e2e Angular → Spring Boot ; dette
transactionnelle des listeners d'audit (`@EventListener` +
`REQUIRES_NEW`) **non** résolue (migration globale à planifier).

Le **parcours d'émargement démontrable** (modules `coursesession` +
`attendance`, migration `V9`, espace front-end `/sessions` + `/attendance`,
amorçage `demo` + `scripts/seed-demo.sh` + `docs/11-guide-demonstration.md`)
est désormais **fusionné sur `main`** via la PR #20 (commit de fusion
`5874f5a` ; dernier commit fonctionnel de la branche `2036277`). Sa
validation manuelle locale du parcours fonctionnel frontend et API, sous
le profil `demo`, est **réussie** (connexion formateur et apprenants,
ouverture de séance, QR / code court, enregistrement des deux présences,
anti-double présence, consultation du tableau, fermeture et refus de
l'ancien code) ; les changements de contexte de rôle étaient non
applicables manuellement, les comptes de démonstration utilisés étant
mono-rôle — ces cas restent couverts par les tests automatisés.
La branche `feature/attendance-qr-demonstration` n'est donc **plus une PR
ouverte**. Le texte détaillé du « Dernier lot fonctionnel livré »
ci-dessous est conservé comme description du lot ; il ne représente plus
un travail en cours.

Le **parcours d'écriture de l'administration des comptes** (suspension /
réactivation / archivage / attribution / retrait de rôle sur la fiche
`/administration/:publicId`) reste **fusionné sur `main`** via la
PR #19 (commit `317753a`). L'administration front-end n'est donc **plus
en lecture seule**.

## Dernier lot fonctionnel livré

```text
PARCOURS D'ÉMARGEMENT DÉMONTRABLE (BACK-END + FRONT-END) — branche
`feature/attendance-qr-demonstration` (créée depuis `main` synchronisé
avec `origin/main`, HEAD `317753a`), **fusionnée sur `main` via la PR #20**
(commit de fusion `5874f5a`, dernier commit fonctionnel `2036277`) ;
validation manuelle locale du parcours fonctionnel frontend et API, sous
le profil `demo`, **réussie** (connexion formateur et apprenants,
ouverture, QR / code court, enregistrement des deux présences,
anti-double présence, consultation du tableau, fermeture et refus de
l'ancien code) ; les changements de contexte de rôle étaient non
applicables manuellement, les comptes de démonstration utilisés étant
mono-rôle — ces cas restent couverts par les tests automatisés.
Grande tranche verticale : deux nouveaux
modules Spring Modulith (`coursesession`, `attendance`), un module
d'amorçage `bootstrap`, la migration Flyway `V9` (schéma en version 9),
l'espace front-end `/sessions` + `/attendance`, un amorçage de
démonstration au profil `demo`, un script `scripts/seed-demo.sh` (+ son
test de non-régression `scripts/test/test-seed-demo.sh`) et le
guide `docs/11-guide-demonstration.md`.

Passe corrective (revue PR #20, 30 août 2026) : (1) invariant du pointeur
courant dans `AttendanceTokenService.resolveSession` — une clé
`token -> session` résiduelle ne valide plus un jeton qui n'est plus le
jeton courant de la séance ; (2) `scripts/seed-demo.sh` durci (helper
`http_post` : une requête HTTP par appel logique, fichier temporaire +
`trap`, refus `>= 400` par défaut, `409` toléré seulement dans `ensure_*`
avec vérification de la ressource exacte, query params encodés) + test
faux-`curl` ; (3) prise en compte du contexte de rôle actif dans les 4
écrans front (`SessionList`, `SessionForm`, `SessionDetail`,
`AttendanceCheckIn`) ; (4) totaux de tests et affirmations de commandes
corrigés. Détails ci-dessous, marqués « (revue PR #20) ».

Passe corrective (validation manuelle, 30 août 2026) : idempotence du
provisionnement des comptes de démonstration sur **base MySQL
persistante**. `DefaultDemoAccountProvisioner.ensureActiveAccount`
n'écrivait le mot de passe que pour un compte **absent** ; un compte
fictif déjà présent (volume MySQL réutilisé) restait sur son ancien
hachage et `scripts/seed-demo.sh` échouait alors en `401`. Désormais,
**sous le profil `demo` uniquement**, un compte existant est
resynchronisé à chaque amorçage : le hachage est réécrit **seulement si**
`passwordEncoder.matches(rawPassword, hash)` est faux (idempotent avec le
même mot de passe — le sel BCrypt diffère sinon à chaque démarrage), et
le compte est ramené à un état permettant la connexion (statut `ACTIVE`,
suspension levée) via `UserAccount.ensureUsableForDemo`. Rôles, profil
apprenant, inscriptions et audit sont conservés ; le mot de passe brut et
le hachage ne sont jamais journalisés. Le message de démarrage de
`DemoDataInitializer` annonce désormais « 4 comptes fictifs synchronisés
— statut ACTIVE et mot de passe aligné sur la valeur courante de
ESIC_DEMO_PASSWORD » (il n'affirme plus « prêts » sans garantie).
Fichiers : `DefaultDemoAccountProvisioner`, `UserAccount`
(+ `ensureUsableForDemo`), `DemoAccountProvisioner` (javadoc),
`DemoDataInitializer` (log) ; `docs/10-journal-ia.md`,
`docs/11-guide-demonstration.md` (encadré § 4.2, § 6, dépannage) mis à
jour.

Migrations historiques V1–V8 inchangées. `SecurityConfig` inchangé
(`/api/v1/auth/login` et les routes publiques d'activation restent les
seules ouvertes ; le reste exige un JWT). `docs/01`–`docs/04` non
modifiés ; `docs/09-matrice-rncp.md` (TR-006, TR-022, ligne « Utiliser
Redis »), `docs/11-guide-demonstration.md` (nouveau) et
`docs/CURRENT-STATE.md` mis à jour. `.env.example` : ajout de
`ATTENDANCE_TOKEN_TTL` et `ESIC_DEMO_PASSWORD` (documentés, sans valeur).
`application.yml` : ajout de `app.attendance.token-ttl` (défaut `PT30S`).
Nouveau `application-demo.yml`. Dépendance front ajoutée :
`angularx-qrcode@21.0.5` (MIT ; `package.json` + `package-lock.json`
ensemble ; `qrcode` déclaré `allowedCommonJsDependencies`). Aucune
dépendance de scan caméra. Aucun secret commité.

PÉRIMÈTRE DÉCIDÉ (tranche) : séance **exceptionnelle** créée manuellement
(sans planning), motif obligatoire ; formateur = compte `user_account`
avec rôle actif `TEACHER` ; ≥ 1 classe rattachée ; cycle
`PLANNED → OPEN → CLOSED` sans réouverture ; **un seul** point de
contrôle d'émargement par séance ; jeton dynamique **opaque** + **code
court** émis et validés par le serveur, stockés **uniquement dans Redis**
(TTL court, rotation, purge à la fermeture) ; QR encodant uniquement le
jeton opaque ; validation par un `STUDENT` inscrit dans une classe de la
séance ; **anti-double présence par contrainte SQL** ; consultation des
présences. HORS PÉRIMÈTRE (non livré, non simulé) : scan caméra
physique, présence manuelle, correction, justificatif, calcul de
demi-journée, export CSV, QR fixe de salle, contrôle réseau CIDR,
WebAuthn, import CSV apprenants, planning.

--- MIGRATION V9 (`V9__create_course_sessions_and_attendance.sql`) ---
- `course_session` : `public_id`, `teacher_user_id` FK `RESTRICT`,
  `status` (`PLANNED`|`OPEN`|`CLOSED`), `title` nullable, `starts_at` /
  `ends_at`, `time_zone_id`, `exception_reason` NOT NULL, `opened_at` /
  `opened_by_id` / `closed_at` / `closed_by_id`, colonnes auteur,
  `version` ; `CHECK (ends_at > starts_at)` ;
  `CHECK` de cohérence PLANNED/OPEN/CLOSED sur `opened_at` / `closed_at` ;
  index formateur / statut / période.
- `session_class` : jointure `public_id`, `course_session_id` FK,
  `class_group_id` FK, `UNIQUE (course_session_id, class_group_id)`.
- `attendance_checkpoint` : `public_id`, `course_session_id` **UNIQUE**
  (un point de contrôle par séance), `opened_at` / `closed_at`,
  timestamps, `version`. Créé avec la séance ; ouvert / fermé avec elle.
- `attendance_record` : `public_id`, `attendance_checkpoint_id` FK,
  `enrollment_id` FK, `student_user_id` FK, `recorded_at`, `source`
  (`DYNAMIC_QR`|`SHORT_CODE`), timestamps, `version` ;
  **`UNIQUE (attendance_checkpoint_id, enrollment_id)`** — autorité
  anti-double émargement. Aucun jeton en base.
`course_session` / `session_class` / `attendance_checkpoint`
appartiennent au module `coursesession` ; `attendance_record` au module
`attendance` ; les FK inter-modules sont de simples valeurs techniques
résolues par des ports publics. Aucune donnée métier insérée.

--- MODULE `coursesession` ---
Entités `CourseSession` (+ `SessionClass`, `AttendanceCheckpoint`), enum
public `SessionLifecycle`. API `/api/v1/sessions` : `GET` liste (filtre
`status`, `teacher`, `classGroup`, `from`, `to` ; tri liste blanche
`startsAt|createdAt` ; pagination ≤ 100), `GET /teachers` (formateurs
éligibles), `GET /{publicId}`, `POST` (création `PLANNED` + point de
contrôle), `POST /{publicId}/open` (`204`), `POST /{publicId}/close`
(`204`). Aucun `PATCH` / `archive` / `cancel` / `substitute`.
Cycle de vie strict : création directe `OPEN`/`CLOSED` impossible ;
ouverture d'une séance non `PLANNED` → `409 SESSION_INVALID_STATE` ;
fermeture d'une séance non `OPEN` → `409` ; pas de réouverture.
Création réservée à `ADMIN` / `SUPER_ADMIN` / `PEDAGOGICAL_MANAGER`
(`SCHOOL_ADMINISTRATION` exclu). Lecture ouverte à ces rôles +
`SCHOOL_ADMINISTRATION` + `TEACHER`. Contrôle fin
(`CourseSessionAccessGuard`, contexte Spring Security, jamais un
paramètre client) : `ADMIN`/`SUPER_ADMIN` global ;
`SCHOOL_ADMINISTRATION` lecture seule ; `PEDAGOGICAL_MANAGER` limité à
son périmètre (`AcademicScopeDirectory`) ; `TEACHER` uniquement ses
séances ; `STUDENT` aucun accès (`GET /sessions` → `403`).
Formateur vérifié via **nouveau port** `identity.TeacherDirectory`
(compte `ACTIVE` + rôle `TEACHER` actif) : compte inconnu →
`400 SESSION_TEACHER_NOT_FOUND`, non éligible →
`409 SESSION_TEACHER_NOT_ELIGIBLE`. Classes vérifiées via
`academic.ClassGroupDirectory` (existence + `openForEnrollment`) :
inconnue → `400 SESSION_CLASS_NOT_FOUND`, chaîne archivée →
`409 SESSION_CLASS_INACTIVE`, hors périmètre `PEDAGOGICAL_MANAGER` →
`403 SESSION_SCOPE_FORBIDDEN`. Motif obligatoire (`@NotBlank`),
`ends_at > starts_at` sinon `400 SESSION_INVALID_PERIOD`, `timeZoneId`
IANA sinon `400 SESSION_INVALID_TIME_ZONE`. Horloge `java.time.Clock`
injectée. DTO sans identifiant SQL ni jeton. Audit `SESSION_CREATED` /
`_OPENED` / `_CLOSED` (catégorie `COURSE_SESSION`) via
`CourseSessionChangeEvent` → `audit.internal.CourseSessionAuditListener`.
Port public `coursesession.CourseSessionDirectory` : `resolve(publicId,
READ|MANAGE)` → `GRANTED` / `NOT_FOUND` / `FORBIDDEN` (contrôle d'accès
de l'appelant fait dans `coursesession`), et `findForAttendance(publicId)`
**sans** contrôle d'accès (réservé à `attendance` après validation d'un
jeton — c'est le jeton qui est la capacité).

--- MODULE `attendance` (Redis) ---
`AttendanceTokenService` : jeton opaque (`SecureRandom` 32 octets,
Base64 URL-safe sans padding, 43 caractères) + code court (8 caractères,
alphabet `ABCDEFGHJKMNPQRSTUVWXYZ23456789`), stockés **uniquement dans
Redis** — clés `esic:attendance:token:{token}`,
`esic:attendance:code:{code}`, `esic:attendance:session:{sessionPublicId}`
(couple courant) — avec TTL `app.attendance.token-ttl` (défaut `PT30S`,
strictement positif, refus de démarrage sinon). **Rotation** :
`issue()` écrit d'abord le nouveau couple, bascule ensuite le pointeur
`session -> token\ncode`, puis seulement supprime les clés du couple
précédent. **Invariant du pointeur courant** (corrigé, revue PR #20) :
`resolveSession` ne se fie **jamais** à la seule clé
`token -> session`. Après avoir résolu la séance, il relit le pointeur
courant et n'accepte le jeton (et, si présenté, le code court) que s'il
est **exactement** celui désigné par le pointeur ; pointeur absent,
illisible, incohérent (≠ 2 segments, segment vide) ou divergent ⇒
`Optional.empty()`. Une clé `token -> session` résiduelle (rotation
concurrente ou partiellement échouée) est donc refusée, et après
`invalidateSession` (pointeur supprimé) toute clé résiduelle est
inutilisable même si Redis ne l'a pas encore expirée. Aucun jeton ni
code n'est journalisé.
**Fermeture de séance** → `CourseSessionCloseListener` (écoute
`CourseSessionChangeEvent` action `CLOSED`) → `invalidateSession` (purge
les 3 clés ; un échec Redis y est avalé, journalisé, le TTL fait foi).
Aucun jeton en base MySQL, dans une URL ou dans les logs. **Redis
indisponible** (toute `DataAccessException`) → `AttendanceException`
`TOKEN_BACKEND_UNAVAILABLE` → **`503 ATT_TOKEN_BACKEND_UNAVAILABLE`**,
jamais de validation dégradée.
`POST /api/v1/sessions/{publicId}/attendance-token` (rôles
`ADMIN`/`SUPER_ADMIN`/`PEDAGOGICAL_MANAGER`/`TEACHER` + contrôle fin
délégué à `coursesession` ; séance doit être `OPEN` sinon
`409 ATT_SESSION_CLOSED`) → `{ token, shortCode, expiresAt,
sessionPublicId, ttlSeconds }`.
`POST /api/v1/attendance/validate` (**`STUDENT` uniquement**) : corps
`{ token? , shortCode? }`, exactement l'un des deux sinon
`400 ATT_INVALID_SUBMISSION` ; code court normalisé (majuscules, sans
séparateurs) ; la valeur soumise n'est jamais renvoyée. Le serveur
résout l'apprenant à partir du **seul JWT** (`sub`) — jamais
d'identifiant d'apprenant / d'inscription transmis : jeton/code présent
dans Redis (sinon `409 ATT_TOKEN_INVALID` — un seul code, Redis ne
distingue pas expiré / inconnu), séance `OPEN` + point de contrôle
ouvert (sinon `409 ATT_SESSION_CLOSED`), inscription `ACTIVE` dont la
classe est rattachée à la séance et dont la période couvre le jour
(via **`enrollment.EnrollmentDirectory.findActiveEnrollmentsForUserOn`**) —
zéro correspondance → `409 ATT_NOT_ENROLLED`, plusieurs →
`409 ATT_ENROLLMENT_AMBIGUOUS` (refus, jamais de choix silencieux),
compte non archivé (`identity.UserDirectory`) sinon
`403 ATT_OPERATION_FORBIDDEN`. Écriture isolée dans
`AttendanceRecordPersister` (`@Transactional(REQUIRES_NEW)`) : une
violation de `uq_attendance_record_checkpoint_enrollment` en concurrence
→ **`409 ATT_ALREADY_RECORDED`**, jamais un 500. `recorded_at` via
`Clock`. `source` = `DYNAMIC_QR` (jeton) ou `SHORT_CODE`. Réponse
`{ attendancePublicId, sessionPublicId, sessionTitle, recordedAt,
source }`. Audit `ATTENDANCE_RECORDED` (catégorie `ATTENDANCE`,
détail `session=…;source=…`, jamais de numéro / nom / jeton).
`GET /api/v1/sessions/{publicId}/attendance` (rôles de lecture des
séances) : `{ sessionPublicId, checkpointPublicId, expectedCount,
presentCount, records[] }` — chaque ligne = profil / inscription
publics, numéro étudiant, prénom / nom, `recordedAt`, `source` (pas
d'email, pas d'id SQL). `expectedCount` =
`EnrollmentDirectory.countActiveEnrollmentsInClasses`.

--- PORTS PUBLICS AJOUTÉS / ÉTENDUS ---
- `identity.TeacherDirectory` (nouveau) + impl `DefaultTeacherDirectory` ;
- `identity.UserDirectory` : + `findName(internalId)` (+ record
  `PersonName`) ;
- `identity.DemoAccountProvisioner` (nouveau, impl `@Profile("demo")`) ;
- `enrollment.EnrollmentDirectory` : + `findActiveEnrollmentsForUserOn`,
  `describeAttendee` (+ record `AttendeeRef`),
  `countActiveEnrollmentsInClasses` ; `DefaultEnrollmentDirectory`
  injecte désormais `identity.UserDirectory` ;
- `coursesession.CourseSessionDirectory` (nouveau).
`ModularityTests` reste vert (2 modules + `bootstrap` ; aucune dépendance
vers un package `.internal` d'un autre module ; `audit` ne consomme que
les événements publics `CourseSessionChangeEvent` /
`AttendanceChangeEvent`).

--- AMORÇAGE `demo` ---
Module `bootstrap`, `DemoDataInitializer` (`@Profile("demo")`,
`ApplicationRunner`, idempotent) : crée 4 comptes fictifs
(`admin@example.test`, `formateur@example.test`, `apprenant1@example.test`,
`apprenant2@example.test`) via `identity.DemoAccountProvisioner`
(implémentation elle aussi `@Profile("demo")`, jamais active en `local` /
`test` / production). Mot de passe via `ESIC_DEMO_PASSWORD` (obligatoire,
≥ 12 caractères, refus de démarrage sinon ; jamais journalisé, jamais
commité). Comme le volume MySQL est persistant, chaque amorçage
**resynchronise** un compte fictif déjà présent (profil `demo`
uniquement) : hachage réécrit seulement si le mot de passe courant ne
correspond plus (`passwordEncoder.matches`), statut ramené à `ACTIVE`
(suspension levée) via `UserAccount.ensureUsableForDemo` ; rôles, profil
apprenant, inscriptions et audit conservés. Fonctionnellement idempotent
avec le même mot de passe (aucun champ réécrit). `application-demo.yml` s'appuie sur l'infrastructure locale, ne
désactive pas la sécurité, n'utilise pas `ddl-auto=create`, ne contient
aucun secret. Aucune donnée de démonstration dans une migration Flyway.
`scripts/seed-demo.sh` (bash + curl + jq) crée ensuite via
les API REST réelles : site `SITE-DEMO`, formation `PRG-DEMO`, niveau
`N1-DEMO`, année `AY-DEMO`, promotion `P-DEMO`, classe `C-DEMO`, deux
profils (`ESIC-DEMO-001/002`), deux inscriptions et **une séance
`PLANNED`** (`Atelier émargement (démo)`).
Helper `http_post` **durci** (revue PR #20) : **une seule** requête HTTP
par appel logique (corps → fichier temporaire nettoyé par `trap`, statut
capturé séparément) ; refus de tout `HTTP >= 400` par défaut ; `409`
toléré **uniquement** dans les fonctions `ensure_*`, qui retrouvent alors
la ressource exacte par son `code` / `studentNumber` et **échouent** si
elle reste introuvable. Query params via `curl -G --data-urlencode`. La
séance (sans contrainte d'unicité) n'est POSTée qu'après un `GET`
confirmant son absence ; les inscriptions de même. Le JWT n'est jamais
affiché. Point d'injection `CURL` pour les tests.
Non-régression : `scripts/test/test-seed-demo.sh` (faux `curl`
déterministe) — scénario base vierge (1 séance POSTée, aucun double POST
d'un même appel logique) + scénario ré-exécution (créations en `409`,
`GET` renvoyant l'existant → **aucune** séance ni inscription POSTée).

--- FRONT-END (`/sessions`, `/attendance`) ---
Routes enfants de la coquille authentifiée : `/sessions` (`roleGuard`
READ = `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION`/`PEDAGOGICAL_MANAGER`/`TEACHER`)
→ `SessionList` ; `/sessions/new` (`roleGuard` CREATE =
`ADMIN`/`SUPER_ADMIN`/`PEDAGOGICAL_MANAGER`) → `SessionForm` ;
`/sessions/:publicId` → `SessionDetail` ; `/attendance` (`roleGuard`
`STUDENT`) → `AttendanceCheckIn`. Entrées `NAV_ITEMS` « Séances » (5
rôles) et « Émargement » (`STUDENT`).
`SessionsApiService` : une méthode par endpoint réel ; le jeton
d'émargement ne transite que dans le corps HTTPS des réponses, jamais
dans une URL ; aucun paramètre client n'élargit un périmètre.
Contexte de rôle actif (revue PR #20) — les quatre écrans dérivent leur
affichage de `RoleContextService.effectiveRoles()` (restreint, jamais
n'élargit le JWT) ; Spring Security reste l'autorité.
`SessionList` : bouton « Nouvelle séance » calculé sur le contexte actif
(masqué immédiatement si l'on bascule vers un contexte sans droit de
création).
`SessionForm` : perte du droit de création → formulaire **neutralisé**
(désactivé, panneau « permission perdue »), `submit()` bloqué, réponse
de création arrivée tardivement ignorée (pas de navigation).
`SessionDetail` : faits, ouverture / fermeture avec confirmation en
ligne ; panneau QR (`QrDisplay` encode la seule chaîne opaque, jamais
affichée en texte ; code court affiché ; jeton renouvelé ~3 s avant
expiration) ; présences (rafraîchissement manuel + polling modéré 15 s).
Renouvellement **arrêté** et QR **effacé** à la destruction, à la
fermeture de la séance et à la perte du droit de gestion dans le
contexte actif ; une émission de jeton déjà en vol est ignorée si l'état
a changé (aucun renouvellement programmé sur une réponse obsolète). Le
**polling** ne part plus dès que le contexte actif ne permet plus la
lecture de la page (`canRead`). Redis `503` → message contrôlé, rotation
stoppée.
`AttendanceCheckIn` : saisie du code court (normalisée comme le serveur),
succès accessible, erreurs `ATT_*` contrôlées, code inconnu / `5xx` →
message générique (jamais le corps brut), formulaire réutilisable, rien
en URL ni en storage ; note « scan caméra ajouté ultérieurement » (pas
présentée comme livrée). Saisie et soumission uniquement en **contexte
`STUDENT` effectif** : en sortir efface code, récépissé et erreurs
métier et bloque toute requête (réponse tardive ignorée) ; y revenir
rend le formulaire utilisable **sans rechargement**.
`toSessionError` : liste blanche **explicite** de codes `SESSION_*` /
`ATT_*` (pas de `startsWith`) ; `503` → message client dédié ; code
inconnu → vue générique.

--- SÉCURITÉ / DONNÉES PERSONNELLES ---
JWT en mémoire seule côté front (docs/07 §6, RG-085) ; aucun accès
`localStorage` / `sessionStorage` (asserté). Le QR n'encode que le jeton
opaque serveur ; aucun jeton en base, en URL ni en logs. L'apprenant ne
choisit jamais son inscription ; le contrôle de périmètre est côté
serveur (`roleGuard` = ergonomie). Les DTO n'exposent ni `id` SQL, ni
`password_hash`, ni jeton ; l'audit ne contient ni jeton, ni numéro
étudiant, ni nom, ni IP.

--- TESTS ---
Back-end `./mvnw clean test` (réellement exécuté après corrections) :
origine `main` **449 → 502**, 0 échec, `ModularityTests` vert, V9
appliquée. Nouveaux / étendus :
`CourseSessionConstraintsTests` (7, `@DataJpaTest`),
`CourseSessionIntegrationTests` (6, `@SpringBootTest` — cycle de vie +
audit + transitions interdites + motif / période / formateur / classe +
`TEACHER` ne voit que ses séances + `/teachers` exclut un formateur
suspendu + `STUDENT` → 403),
`AttendanceRecordConstraintsTests` (4, `@DataJpaTest`),
`AttendanceTokenServiceTests` (**11 → 18**, `StringRedisTemplate` mocké —
jeton opaque / code court, rotation, résolution par jeton et par code,
Redis KO → `TOKEN_BACKEND_UNAVAILABLE` (message sans jeton ni code), TTL
non positif refusé, collision de code court régénérée, **+ invariant du
pointeur courant** : ancien jeton résiduel refusé après rotation, ancien
code court refusé, clé jeton sans pointeur refusée, pointeur incohérent /
divergent refusé, rotation normale ne laisse que le nouveau couple
utilisable, après invalidation une clé résiduelle est inutilisable),
`AttendanceIntegrationTests` (7 — parcours code court complet + audit +
anti-double, jeton opaque `DYNAMIC_QR`, non-inscrit refusé, soumission
malformée, rotation invalide l'ancien code, séance `PLANNED` sans jeton,
**deux validations concurrentes → 1×200 / 1×409 / 0×5xx / une seule
ligne**),
`AttendanceSecurityTests` (4 — anonyme 401, `validate` réservé à
`STUDENT`, matrice `@PreAuthorize` de `attendance-token` et de la liste
sur les 6 rôles),
`DefaultDemoAccountProvisionerTests` (**2 → 5**, `@DataJpaTest` — création
`ACTIVE` + rôle puis idempotence **fonctionnelle** (même mot de passe →
hachage inchangé octet pour octet), normalisation d'email + ajout d'un
rôle manquant, **+ resynchronisation** d'un compte existant sur un
nouveau mot de passe (hachage remplacé, `matches` neuf OK / ancien KO,
même `publicId` et même id interne, rôles / statut conservés), **+**
compte `SUSPENDED` ramené à `ACTIVE` (suspension levée) avec le mot de
passe courant, **+** garde réflexive : le provisioner porte bien
`@Profile("demo")` — le comportement de synchronisation ne peut pas
exister hors du profil `demo`),
`DemoDataInitializerTests` (2 — mot de passe obligatoire ≥ 12, 4 comptes
`@example.test`).
`./mvnw spotless:check` : **non applicable** — aucun plugin Spotless (ni
autre plugin de format) n'est configuré dans `backend/pom.xml` ; la
commande échoue avec « No plugin found for prefix 'spotless' ». Aucun
plugin ajouté (hors périmètre de la revue).
Front `npm test -- --watch=false` (réellement exécuté après corrections) :
origine `main` **336 → 416**, 0 échec ; `npm run lint`
(« All files pass linting ») / `npm run build` verts. Nouveaux /
étendus : `sessions-api.service.spec` (11), `session-errors.spec` (7),
`qr-display.spec` (2), `session-list.spec` (**10 → 11** : + bouton de
création masqué au changement de contexte de rôle),
`session-form.spec` (**7 → 9** : + formulaire neutralisé sur perte de
permission, + réponse de création tardive ignorée),
`session-detail.spec` (**10 → 12**, fake timers : rotation, arrêt sur
fermeture / contexte / `503`, polling nettoyé à la destruction, +
polling stoppé quand le contexte perd la lecture, + émission de jeton
tardive ignorée),
`attendance-check-in.spec` (**13 → 18** : + refus hors contexte
`STUDENT`, + code / récépissé / erreurs effacés à la perte du contexte,
+ réponse tardive ignorée, + retour au contexte `STUDENT` sans
rechargement). Specs mis à jour : `navigation`, `app-shell`,
`dashboard`, `app.routes`.
Preuve seed (hors total `npm test`) : `bash scripts/test/test-seed-demo.sh`
— 2 scénarios passent.
Baselines mesurées sur `origin/main` (`317753a`) dans un worktree
dédié : back-end 449, front 336.

--- DÉMONSTRATION LOCALE (30 août 2026, profil `demo`) ---
`docker compose up -d` (mysql / redis `healthy`) ; back-end
`SPRING_PROFILES_ACTIVE=demo` avec `JWT_SECRET` généré et
`ESIC_DEMO_PASSWORD` (≥ 12).
**Idempotence du seed (revue PR #20)** — vérifiée sur une base MySQL
**vierge dédiée** `esic_demo_verify` (créée puis supprimée ; `esic_connect`
non touchée) : `SELECT COUNT(*) FROM course_session` = **0** avant,
**1** après le 1ᵉʳ `scripts/seed-demo.sh`, **1** après le 2ᵈ (même
`public_id` de séance, mêmes profils / inscriptions). Séances de
démonstration présentes dans `esic_connect` : **0** (`title = 'Atelier
émargement (démo)'` → 0) ; les 75 lignes `course_session` de
`esic_connect` sont des **artefacts des tests d'intégration** (la suite
partage cette base et ne tronque pas), pas des données de seed.
Invariant de rotation Redis vérifié en direct : après deux émissions,
`validate` avec l'ancien code court → `409`, avec le code courant →
`200`.
Scénario **API** exécuté, statuts HTTP relevés (aucun jeton / mot de
passe / donnée personnelle affiché) :
ADMIN `GET` séance `200` → TEACHER `open` `204` → TEACHER
`attendance-token` `200` (code 8 car., TTL 30 s) → apprenant 1 `validate`
`{shortCode}` `200` (`SHORT_CODE`) → apprenant 1 revalidation
`409 ATT_ALREADY_RECORDED` → apprenant 2 `validate` `{token}` `200`
(`DYNAMIC_QR`) → `GET .../attendance` `200` (2/2) → apprenant 1
`GET /sessions` `403` → TEACHER `close` `204` → `validate` ultérieur
`409 ATT_TOKEN_INVALID` → `attendance-token` `409 ATT_SESSION_CLOSED` ;
conteneur Redis mis en pause → `attendance-token`
`503 ATT_TOKEN_BACKEND_UNAVAILABLE` → Redis relancé → `200`. Processus
back-end arrêté proprement à la fin ; infrastructure Docker laissée en
l'état. **Démonstration UI de bout en bout non exécutée
automatiquement** (parcours API vérifié ; guide manuel fourni).

ÉLÉMENTS NON RÉALISÉS : scan caméra (NON LIVRÉ) ; planning (NON LIVRÉ) ;
présence manuelle, correction, justificatif, demi-journée, export,
QR fixe de salle, contrôle réseau, WebAuthn. LIMITES : séance
exceptionnelle sans planning ; un seul point de contrôle par séance ;
les 4 comptes de démonstration sont **mono-rôle** (le sélecteur de
contexte de rôle ne peut donc pas être exercé manuellement depuis le jeu
`demo`) ; pas de test e2e Angular → Spring Boot ; démonstration téléphone
non effectuée.

--- CONTEXTE ANTÉRIEUR (Administration des comptes — parcours d'écriture,
PR #19 fusionnée sur `main`, commit `317753a`) ---
```

Contrat back-end consommé **tel quel** (rien d'inventé ; toutes en
`public_id`, corps JSON, réponse `204` sans corps) :
- `POST /api/v1/users/{publicId}/suspend` — `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION`
  (`LIFECYCLE_ROLES`), corps `{ reason }` (`AccountActionRequest`,
  `@NotBlank @Size(max = 500)`) ;
- `POST .../restore` — mêmes rôles, corps `{ reason }` ;
- `POST .../archive` — `ADMIN`/`SUPER_ADMIN` (`ADMIN_ROLES`), corps
  `{ reason }` ;
- `POST .../roles` — `ADMIN`/`SUPER_ADMIN`, corps `{ role, reason }`
  (`AssignRoleRequest`, tous deux `@NotBlank`) ;
- `POST .../roles/{roleCode}/revoke` — `ADMIN`/`SUPER_ADMIN`, corps
  `{ reason }` ;
- `GET /api/v1/users/{publicId}` est ré-appelé après chaque mutation
  réussie pour recharger la fiche + l'historique des rôles.
Aucun endpoint de recherche ou de catalogue de rôles n'est ajouté : le
choix des rôles proposés est dérivé de l'enum contractuelle `RoleCode`
(source `ROLES` du cœur applicatif), pas d'une liste inventée.

Fichiers touchés sous `frontend/src/app/features/administration/` :
- `administration.models.ts` : +`AccountActionRequest`,
  +`AssignRoleRequest`, +`ACTION_REASON_MAX_LENGTH` (500 — **motif des
  `AccountActionRequest` seulement** : suspension / réactivation /
  archivage / retrait de rôle. `AssignRoleRequest.reason` ne porte que
  `@NotBlank`, sans borne de longueur contractuelle) ; en-tête de
  fichier remis à jour (n'est plus « lecture seule ») ;
- `administration-api.service.ts` : +`suspendUser`, `restoreUser`,
  `archiveUser`, `assignRole`, `revokeRole` (une méthode par endpoint
  réel ; `encodeURIComponent` sur `publicId` **et** `roleCode` ; corps
  exact sans propriété superflue ; un motif d'attribution de plus de 500
  caractères est transmis intégralement, sans troncature) ;
- `administration-errors.ts` (nouveau) : `toAdministrationError` —
  s'appuie sur `normalizeHttpError` (messages `USER_*` du back-end déjà
  sûrs), expose `{ status, code (USER_* connu ou null), message, field }`.
  Les codes connus sont énumérés par une **liste blanche explicite**
  (`USER_NOT_FOUND`, `USER_INVALID_STATE`, `USER_ROLE_ALREADY_ASSIGNED`,
  `USER_ROLE_NOT_ASSIGNED`, `USER_LAST_ACTIVE_ROLE`,
  `USER_SELF_ACTION_FORBIDDEN`, `USER_SUPER_ADMIN_PROTECTED`,
  `USER_OPERATION_FORBIDDEN`, `USER_ROLE_UNKNOWN`, `USER_INVALID_SORT`,
  `USER_INVALID_FILTER`) — **et non** un `startsWith('USER_')`, qui
  laisserait passer un code futur ou inattendu. Seul `USER_ROLE_UNKNOWN`
  cible le champ `role`, tout le reste est un message global. Tout code
  hors liste (y compris un `USER_*` non listé) **et** tout `5xx` →
  `code` / `field` à `null` et message générique sûr, sans jamais
  afficher le message arbitraire de la réponse ;
- `user-detail/` (`.ts` / `.html` / `.scss`) : la fiche gagne une
  section « Actions sur le compte » (boutons Suspendre / Réactiver /
  Archiver / Attribuer un rôle) et une colonne « actions » dans le
  tableau d'historique (bouton « Retirer » sur chaque affectation
  **active**). Confirmations **en ligne** (pas de dialogue) : motif
  obligatoire ; pour suspension / réactivation / archivage / retrait de
  rôle le `textarea` porte `maxlength=500` + compteur (contrat
  `AccountActionRequest`) ; pour **l'attribution de rôle**, le motif est
  seulement requis, **sans `maxlength` ni compteur** (contrat
  `AssignRoleRequest` = `@NotBlank` seul) — un motif > 500 caractères
  part intégralement. Description de l'effet, avertissement explicite
  « l'archivage clôture tous les rôles actifs », contrôles `disabled`
  pendant l'appel, double soumission empêchée (`submitting()` + garde
  dans `confirm()`), bandeau succès (`NotificationService.info`) puis
  rechargement de la fiche ; en cas d'échec le message métier contrôlé
  s'affiche en ligne sans faux succès ni rechargement.
  `USER_ROLE_UNKNOWN` est rattaché **au champ rôle** via l'état d'erreur
  du `FormControl` (`serverUnknown`) : le `mat-error` du
  `mat-form-field` est relié au `mat-select` par `aria-describedby`
  (association réelle) ; choisir un autre rôle lève l'erreur serveur et
  réautorise la soumission ; une erreur globale n'est jamais transformée
  en erreur de champ.

Visibilité des actions (ergonomie seule — le back-end reste l'autorité) :
- pilotée par `RoleContextService.effectiveRoles()` (contexte de rôle
  actif, toujours un sous-ensemble des rôles du JWT : il peut
  **restreindre** l'affichage, jamais l'**élargir**) ;
- Suspendre : `ACTIVE` + contexte ∈ `LIFECYCLE_ROLES` ; Réactiver :
  `SUSPENDED` + mêmes rôles ; Archiver et gestion de rôle : contexte ∈
  `{ADMIN, SUPER_ADMIN}` ;
- cible `ARCHIVED` : aucune action, seulement une note « état terminal
  dans ce lot » ;
- auto-action : si `AuthService.session()?.subject` correspond de façon
  fiable au `publicId` de la cible, Suspendre / Réactiver / Archiver /
  Retirer sont masqués (le back-end renvoie `USER_SELF_ACTION_FORBIDDEN`)
  — l'**attribution** d'un rôle à soi-même **n'est pas** masquée car le
  back-end ne l'interdit pas ;
- cible portant un rôle `SUPER_ADMIN` **actif** (calculé uniquement à
  partir des `roleAssignments` de `UserDetailResponse`) : hors contexte
  `SUPER_ADMIN`, **aucune** mutation n'est proposée (suspension,
  réactivation, archivage, attribution de n'importe quel rôle, retrait
  de n'importe quel rôle) — le back-end
  (`UserManagementService.guardSuperAdminTarget`) les refuserait toutes.
  Une note concise indique que la gestion de ce compte requiert le rôle
  super administrateur, sans présenter le masquage comme une garantie de
  sécurité ; la lecture de la fiche et de l'historique reste disponible.
  Un contexte `SUPER_ADMIN` conserve les actions normalement permises
  (sous réserve de l'auto-action, du statut et du dernier rôle actif).
  Le passage du contexte `SUPER_ADMIN` à `ADMIN` ferme un formulaire
  déjà ouvert sur une telle cible (via l'`effect()` de fermeture) ;
- attribution : `SUPER_ADMIN` proposé seulement à un contexte
  `SUPER_ADMIN` ; les rôles déjà actifs sur la cible sont retirés de la
  liste (aide ergonomique, `USER_ROLE_ALREADY_ASSIGNED` reste géré) ;
- retrait de `SUPER_ADMIN` masqué hors contexte `SUPER_ADMIN` ;
- un `effect()` ferme un panneau de confirmation ouvert dès que l'action
  n'est plus proposée (changement de contexte de rôle, passage self) —
  jamais de formulaire sensible orphelin ;
- même masquée, chaque action reste traitée côté erreur : un `403`
  (`USER_SUPER_ADMIN_PROTECTED`, `USER_OPERATION_FORBIDDEN`) est rendu
  comme un message d'erreur en ligne, jamais comme un succès.

Codes `USER_*` traités (via `toAdministrationError` + message serveur
contrôlé) : `USER_NOT_FOUND` (404, état « introuvable »),
`USER_INVALID_STATE`, `USER_ROLE_ALREADY_ASSIGNED`,
`USER_ROLE_NOT_ASSIGNED`, `USER_LAST_ACTIVE_ROLE`,
`USER_SELF_ACTION_FORBIDDEN` (409), `USER_SUPER_ADMIN_PROTECTED`,
`USER_OPERATION_FORBIDDEN` (403), `USER_ROLE_UNKNOWN` (400, rattaché au
champ rôle), `USER_INVALID_SORT` / `USER_INVALID_FILTER` (400, hérités de
la liste). Un `409` n'est jamais transformé en succès visuel ;
`correlationId`, trace, requête SQL et identifiant interne ne sont jamais
affichés ; `5xx` neutralisé par `normalizeHttpError`.

Confidentialité : JWT et contexte de rôle **en mémoire seule** (docs/07
§6, RG-085) ; aucun accès `localStorage` / `sessionStorage` (asserté).

Accessibilité : chaque confirmation a un `<h3>` + `role="group"` +
`aria-label`, labels Material associés, `mat-error` reliée au champ
motif, boutons « Confirmer » / « Annuler » distincts et nommés,
`disabled` réel pendant l'appel, `role="alert"` sur les erreurs en
ligne, `role="note"` sur l'avertissement d'archivage et la note d'état
terminal ; aucune information portée par la seule couleur ; tableau
d'historique défilable (`overflow-x: auto`) ; formulaires utilisables sur
petit écran (`flex-wrap`).

Tests front : **291 → 336** (0 échec ; +45). Nouveaux / étendus :
`administration-errors.spec.ts` (8 : message serveur sûr conservé + code
`USER_*` ; auto-action / `SUPER_ADMIN` = message global ;
`USER_ROLE_UNKNOWN` → champ `role` ; `5xx` chaîne brute masqué ; `5xx`
`ApiError` structuré portant une pseudo-trace masqué, aucun code
métier ; code inconnu non `USER_*` → vue générique, message arbitraire
non affiché ; code inconnu commençant par `USER_` → vue générique,
message arbitraire non affiché). `administration-api.service.spec.ts`
(+9 : `suspend` / `restore` / `archive` / `roles` / `roles/{code}/revoke`
— URL exacte, `POST`, corps exact sans clé superflue, encodage de
`publicId` **et** `roleCode`, complétion sur `204`, propagation d'une
erreur ; `assignRole` transmet un motif > 500 caractères verbatim ; une
lecture pure n'émet aucun `POST`/`PATCH`/`PUT`/`DELETE`).
`user-detail.spec.ts` (7 → 42 : lecture non-régression conservée ;
Suspendre visible pour `ACTIVE` / Réactiver pour `SUSPENDED` ; archivage
+ attribution réservés à `ADMIN`/`SUPER_ADMIN`, `SCHOOL_ADMINISTRATION`
suspend/réactive mais n'archive pas ; `ARCHIVED` → aucune `form`, note
terminale ; motif requis + `maxlength=500` + corps exact + rechargement ;
`restore` d'une cible `SUSPENDED` → `POST /restore { reason }` → `204` →
rechargement → succès ; `USER_SELF_ACTION_FORBIDDEN` (409) quand le
`subject` est indisponible et le bouton visible → message, aucun faux
succès, aucun rechargement ; avertissement de clôture des rôles à
l'archivage ; double soumission empêchée ; `USER_INVALID_STATE` /
`USER_SUPER_ADMIN_PROTECTED` / `USER_OPERATION_FORBIDDEN` affichés sans
faux succès ni rechargement ; liste exacte des rôles d'attribution,
rôles actifs exclus, `SUPER_ADMIN` selon le contexte ; motif
d'attribution > 500 caractères accepté et posté en entier ;
`USER_ROLE_ALREADY_ASSIGNED` global vs `USER_ROLE_UNKNOWN` sur le champ
(erreur `FormControl` `serverUnknown`, `hasError`, effacée au changement
de rôle, resoumission possible, jamais transformée en erreur de champ) ;
`USER_ROLE_UNKNOWN` réellement relié au `mat-select` par
`aria-describedby` (id du `mat-error` présent dans l'attribut, pas un
simple texte adjacent) ; attribution à soi-même non masquée ;
« Retirer » seulement sur les affectations actives et pour
`ADMIN`/`SUPER_ADMIN` ; `USER_ROLE_NOT_ASSIGNED` (409) sans faux succès
ni rechargement ; `USER_LAST_ACTIVE_ROLE` sans faux succès ; cible
portant `SUPER_ADMIN` actif — contexte `ADMIN` : aucune mutation, aucune
`form`, note « requiert le rôle super administrateur », aucun bouton
« Retirer » (même pour le rôle `ADMIN` de la cible) ; contexte
`SUPER_ADMIN` : mutations proposées selon le statut, deux boutons
« Retirer » ; `SUPER_ADMIN` → `ADMIN` ferme un formulaire ouvert sur une
telle cible ; une affectation `SUPER_ADMIN` **clôturée** ne protège pas
la cible ; auto-actions masquées quand le `subject` correspond ; un
contexte non admin n'ouvre aucune action ; un panneau ouvert se ferme au
changement de contexte ; `403` traité même bouton initialement visible ;
rien en storage).

Vérifs locales le 30 août 2026 (Node 24.13.0), depuis `frontend/` :
`npm ci` réussi, 0 vulnérabilité ;
`npm test -- --watch=false` → **40 fichiers, 336 tests, 0 échec** ;
`npm run lint` → « All files pass linting » ;
`npm run build` → bundle initial **479,36 kB brut / 122,12 kB
transféré** (quasi inchangé — `user-detail` est un chunk paresseux :
8,48 kB → 23,35 kB brut, hors budget « initial » ; 0 alerte, seuil
500 kB) ;
`git diff --check` → propre. Aucun script `format` réel dans
`frontend/package.json` (bloc `prettier` présent mais aucune dépendance
ni commande). `cd backend && ./mvnw test` non ré-exécuté : aucun fichier
back-end modifié.

Éléments PARTIELS / limites : le front n'est pas une autorité de sécurité
(gardes de route, boutons masqués et `effect()` de fermeture = ergonomie ;
Spring Security décide, un `403` est rendu « accès refusé ») ; la
détection d'auto-action dépend d'un `subject` JWT fiable — si absent,
l'action est proposée et le back-end renvoie
`USER_SELF_ACTION_FORBIDDEN` ; écrans non démontrés de bout en bout avec
le back-end en marche ; pas de tests e2e Angular → Spring Boot
(TestBed / Vitest uniquement).
```

## Contexte antérieur — Rythmes d'alternance (backend, PR #17 fusionnée sur `main`)

```text
Rythmes d'alternance (BACKEND) — fusionné sur `main` via PR #17
(commit `60b3cf6`). Module Spring Modulith
`alternation` + migration Flyway `V8` (schéma en version 8). Couvre
docs/02 §8, docs/04 §14, backlog EP-07 / US-060 à US-063, sprint
T-J1-033. Aucun fichier front-end. `docs/01`, `docs/02` non modifiés ;
`docs/03` (§7.4), `docs/09` (TR-021) et `docs/CURRENT-STATE.md` mis à
jour ; migrations V1–V8 **inchangées** (aucune nouvelle migration dans la
passe corrective), `SecurityConfig`, `.env`, `compose.yaml`, `pom.xml` et
le workflow CI inchangés ; `src/test/resources/application-test.yml`
ajusté (pool HikariCP de test). Aucune donnée métier de référence seedée
par `V8`.

Trois tables (V8) :
- `work_study_pattern` : modèle réutilisable de rythme. `id`, `public_id`
  `BINARY(16)` unique, `code` `VARCHAR(80)` unique **immuable**, `name`,
  `description` nullable, `pattern_type`
  (`THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY` | `ONE_WEEK_SCHOOL_OUT_OF_FOUR` |
  `TWO_WEEKS_SCHOOL_OUT_OF_FOUR` | `CUSTOM`), `cycle_length_weeks` `INT`
  nullable (`INT` et non `SMALLINT` esquissé §14.1 : alignement sur
  l'entité JPA `Integer` + validation de schéma Hibernate),
  `configuration_json` `JSON` non null, `status` (`ACTIVE` | `ARCHIVED`),
  colonnes d'archivage + auteur + `version` + horodatage.
  `CHECK (cycle_length_weeks IS NULL OR > 0)`. FK `RESTRICT` vers
  `user_account`. Index `status`, `pattern_type`.
- `class_work_study_pattern` : affectation historisée. `class_group_id`
  (valeur technique, FK `RESTRICT` vers `class_group`),
  `work_study_pattern_id` (FK `RESTRICT`), `cycle_start_date` `DATE` non
  null (**l'ancre du cycle est ici, pas dans le modèle** — consigne du
  lot), `valid_from` `DATE` non null, `valid_until` `DATE` nullable
  (inclusif), `status` (`ACTIVE` | `CLOSED`), `close_reason`, colonne
  générée `active_open_key = IF(status='ACTIVE' AND valid_until IS NULL,
  class_group_id, NULL)` + `UNIQUE` (une seule affectation ACTIVE
  « ouverte » par classe), `CHECK (valid_until IS NULL OR >= valid_from)`.
  Index `class_group_id`, `work_study_pattern_id`, `status`,
  `(class_group_id, valid_from, valid_until)`.
- `student_schedule_exception` : exception individuelle. `enrollment_id`
  (valeur technique, FK `RESTRICT` vers `enrollment`), `exception_type`
  (`REMOTE_ALLOWED` | `ON_SITE_REQUIRED` | `COMPANY_PERIOD` |
  `VALIDATED_UNAVAILABILITY` — valeurs minimales déduites de docs/04
  §14.3 / docs/02 §8.3, aucune valeur arbitraire ajoutée),
  `start_at` / `end_at` `TIMESTAMP(6)`, `time_zone_id` `VARCHAR(64)`,
  `reason` `VARCHAR(500)` non null, `status` (`ACTIVE` | `CANCELLED`),
  `cancel_reason`. `CHECK (end_at > start_at)`. Index `enrollment_id`,
  `status`, `(enrollment_id, start_at, end_at)`.

Schéma JSON de `configuration_json` (contrat retenu — noms alignés sur
docs/04 §14.1, `cycleStartDate` **exclu** car propre à l'affectation de
classe). Toute propriété inconnue, tout jour inconnu, toute incohérence
→ `400 ALT_INVALID_CONFIGURATION` (jamais d'acceptation silencieuse) :
- `THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY` :
  `{"schoolDays":[...],"companyDays":[...]}` — jours MON..FRI classifiés
  exactement une fois, disjoints, aucun nom inconnu, aucun week-end ;
  `cycleLengthWeeks` absent ou `1` (normalisé à `1`).
- `ONE_WEEK_SCHOOL_OUT_OF_FOUR` :
  `{"schoolWeeks":[1],"companyWeeks":[2,3,4]}` — `cycleLengthWeeks` absent
  ou `4` ; exactement 1 semaine école, les 3 autres entreprise ;
  `schoolDays` facultatif (défaut MON..FRI).
- `TWO_WEEKS_SCHOOL_OUT_OF_FOUR` :
  `{"schoolWeeks":[1,2],"companyWeeks":[3,4]}` — exactement 2 semaines
  école.
- `CUSTOM` :
  `{"cycleLengthWeeks":N,"schoolWeeks":[...],"companyWeeks":[...],
  "schoolDays":[...],"companyDays":[...]}` — `cycleLengthWeeks`
  obligatoire (> 0, doit correspondre à la colonne si fournie aux deux
  endroits) ; `schoolWeeks` ∩ `companyWeeks` = ∅ ; chaque index dans
  1..N ; au moins une des deux listes non vide ; les semaines non
  classifiées produisent `UNKNOWN`. `schoolDays` défaut MON..FRI,
  `companyDays` défaut vide.
La configuration est **canonicalisée** (5 clés triées :
`cycleLengthWeeks`, `schoolWeeks`, `companyWeeks`, `schoolDays`,
`companyDays`) et stockée sous cette forme ; la réponse HTTP renvoie
cette forme normalisée. La résolution relit la forme canonique
(`AlternationConfigParser.parseCanonical`).

**Round-trip canonique — corrigé et testé pour les quatre types.**
`parseCanonical(canonicalize(parse(...)))` relit désormais correctement
toute configuration produite à l'écriture. Avant correction,
`parseCanonical` rejetait les tableaux `schoolDays` / `companyDays`
**vides** — que `canonicalize` produit pourtant systématiquement (ex.
`companyDays:[]` pour un rythme semaine/4 ou un `CUSTOM` sans jour
entreprise) —, ce qui cassait la résolution du contexte pour
`ONE_WEEK_SCHOOL_OUT_OF_FOUR`, `TWO_WEEKS_SCHOOL_OUT_OF_FOUR` et `CUSTOM`
sans `companyDays`. `parseCanonical` reste strict — il n'a **pas** été
relâché pour les requêtes clientes (`parse(...)` et `requireDays(...)`
inchangés, toujours stricts) : il exige les cinq clés canoniques, refuse
toute autre propriété, contrôle les index de semaine contre
`cycleLengthWeeks`, refuse les intersections `schoolWeeks`/`companyWeeks`
et `schoolDays`/`companyDays`, mais tolère un tableau de jours vide via
une méthode dédiée `requireCanonicalDays` (tableau vide accepté ; valeur
non-tableau, élément non textuel, jour inconnu ou doublon refusés). Ne
retourne jamais de configuration incohérente.

Résolution du contexte (`AlternationResolver`, service pur, déterministe,
bornes inclusives) : la semaine du cycle est le bloc de 7 jours depuis
`cycle_start_date` (jours 0..6 = semaine 1) ; position =
`((joursDepuisAncre / 7) mod cycleLengthWeeks) + 1`. Date < ancre →
`UNKNOWN` ; samedi/dimanche → `UNKNOWN` ; semaine non classifiée →
`UNKNOWN` ; aucune affectation couvrante → `UNKNOWN` / source `NONE`.
Résolution **effective** d'une inscription : priorité **structurelle**
d'une exception ACTIVE recouvrant la date — `ON_SITE_REQUIRED` → `SCHOOL`,
`COMPANY_PERIOD` → `COMPANY` (source `INDIVIDUAL_EXCEPTION`) ; les deux
types simultanés → `UNKNOWN` (aucune règle inventée) ; `REMOTE_ALLOWED` /
`VALIDATED_UNAVAILABILITY` sont renvoyés dans `coveringExceptionTypes`
mais ne modifient pas l'axe SCHOOL/COMPANY. **Aucun calcul d'assiduité**
(modules `planning` / `coursesession` / `attendance` inexistants).

**Sémantique temporelle des exceptions — `[startAt, endAt)` (demi-ouvert),
adoptée et appliquée explicitement.** La couverture d'une date civile
n'est **plus** déduite d'un `startDay`/`endDay` arrondi : la date est
projetée dans le fuseau propre à l'exception en son propre intervalle
demi-ouvert `[date 00:00 dans le fuseau, lendemain 00:00 dans le fuseau)`
(`date.plusDays(1).atStartOfDay(zone)`), et l'exception couvre la date
si et seulement si `exception.startAt < dayEnd && exception.endAt >
dayStart`. Cas correctement gérés : exception se terminant exactement à
minuit (non couverte le jour suivant), exception commençant exactement à
la fin du jour interrogé (non couverte), fuseaux à changement d'heure
(le 25 octobre 2026 Europe/Paris dure 25 h), exception de quelques
heures, exception couvrant plusieurs jours. `safeZone` est supprimé :
une valeur de fuseau **persistée** invalide lève désormais une erreur
interne explicite (`IllegalStateException`) au lieu d'un repli
silencieux sur UTC qui fausserait la projection.

Endpoints (préfixe `/api/v1/alternation/...` — les URI du plan étaient en
`/api/alternation/...` ; ajustées au préfixe `/api/v1` réel du dépôt,
seul écart au plan) :
- `POST /api/v1/alternation/patterns` · `GET .../patterns`
  (`status`, `type`, `q` code|nom, `sort` liste blanche
  `code|name|createdAt|updatedAt`, `page`, `size` ≤ 100) ·
  `GET .../patterns/{publicId}` · `PATCH .../patterns/{publicId}` ·
  `POST .../patterns/{publicId}/archive` ·
  `POST .../patterns/{publicId}/restore` ;
- `POST /api/v1/alternation/class-assignments` ·
  `GET .../class-assignments` (`class`, `status`, `sort` liste blanche
  `validFrom|validUntil|createdAt`) ·
  `GET .../class-assignments/{publicId}` ·
  `POST .../class-assignments/{publicId}/close` ·
  `GET /api/v1/alternation/classes/{classPublicId}/assignments` ·
  `GET /api/v1/alternation/classes/{classPublicId}/context?date=YYYY-MM-DD` ;
- `POST /api/v1/alternation/student-exceptions` ·
  `GET .../student-exceptions/{publicId}` ·
  `GET /api/v1/alternation/enrollments/{enrollmentPublicId}/exceptions`
  (`sort` liste blanche `startAt|endAt|createdAt`) ·
  `POST .../student-exceptions/{publicId}/cancel` ·
  `GET /api/v1/alternation/enrollments/{enrollmentPublicId}/context?date=YYYY-MM-DD`.
Toutes réponses en `public_id` (jamais de PK SQL). Tri inconnu →
`400 ALT_INVALID_SORT`. Erreurs au format `ApiError` (codes `ALT_*`), le
détail non sensible d'une configuration invalide est ajouté à
`details[]`.

Rôles (`@PreAuthorize` + contrôle de périmètre côté service) :
- modèles : lecture
  `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION`/`PEDAGOGICAL_MANAGER` ;
  écriture `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION` ;
- affectations de classe **et** exceptions individuelles : lecture et
  écriture `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION` +
  `PEDAGOGICAL_MANAGER` **limité à son périmètre** (classe de la formation
  qu'il gère — décision prise dans `academic` via
  `AcademicScopeDirectory`, jamais d'après un paramètre client ; hors
  périmètre → `403 ALT_FORBIDDEN`) ;
- `TEACHER` et `STUDENT` : toujours `403`.

Ports publics ajoutés (frontières `ModularityTests` vertes ;
`alternation` n'importe jamais `academic.internal` ni
`enrollment.internal` ; `audit` ne dépend que de l'événement public) :
- `com.esic.connect.enrollment.EnrollmentDirectory` (+ impl
  `enrollment.internal.DefaultEnrollmentDirectory`) : `findByPublicId` /
  `findByInternalId` → `EnrollmentRef(internalId, publicId,
  studentProfilePublicId, classGroupPublicId, classGroupCode,
  academicYearPublicId, academicYearCode, usable)` ; `usable` = statut
  `ACTIVE`. N'expose ni `Enrollment`, ni repository, ni DTO interne ;
- `com.esic.connect.academic.AcademicScopeDirectory` (+ impl
  `academic.internal.DefaultAcademicScopeDirectory`, qui **délègue** à
  `AcademicScopeGuard` interne) : `hasGlobalScope()`,
  `isClassInScope(UUID classGroupPublicId)`,
  `visibleClassGroupIds()` (`Optional.empty()` = accès global).
  `ClassGroupRepository` reçoit `findIdsByProgramIdIn`.

Clôture d'une affectation (`ClassWorkStudyPatternService.close`) — bornée
pour ne jamais produire un historique incohérent :
- `effectiveDate` doit être `>= valid_from` (sinon `400
  ALT_INVALID_PERIOD`) ;
- si l'affectation possède déjà `valid_until` (affectation ACTIVE mais
  bornée), `effectiveDate` ne doit pas lui être postérieure (`400
  ALT_INVALID_PERIOD`) ;
- `effectiveDate` ne doit pas atteindre ni dépasser le `valid_from` de la
  prochaine affectation historisée de la même classe — dernière date
  acceptable `next.validFrom - 1 jour` (sinon `409
  ALT_ASSIGNMENT_CLOSE_CONFLICT`). La prochaine affectation est retrouvée
  par une requête repository déterministe
  `findFirstByClassGroupIdAndValidFromGreaterThanOrderByValidFromAscIdAsc`.

Concurrence : `ClassWorkStudyPatternService.assign` n'est pas
`@Transactional` ; l'INSERT est isolé dans `ClassAssignmentPersister`
(`@Transactional(REQUIRES_NEW)`, même approche que
`academic.internal.AssignmentPersister` /
`enrollment.internal.EnrollmentPersister`). Une collision sur
`uq_class_work_study_pattern_active_open` est reçue hors transaction en
échec et retraduite en `409 ALT_OPEN_ASSIGNMENT_EXISTS` uniquement si
c'est bien cette contrainte (nom Hibernate **ou** message SQL, sémantique
de doublon) ; toute autre violation d'intégrité est relancée telle
quelle. **Garantie concurrente réelle vérifiée** (`AlternationIntegrationTests`) :
deux créations HTTP simultanées d'affectations ouvertes sur la même
classe → exactement un `201`, un `409`, jamais un `500`, une seule ligne
ACTIVE ouverte persistée. Le non-chevauchement complet des périodes
**bornées** (adjacence stricte autorisée) reste un pré-contrôle
applicatif seul (`findActiveOverlapping`) : aucune contrainte de plage
SQL fiable en MySQL → **course résiduelle documentée, non résolue par
SQL**. Pour les exceptions, la règle de chevauchement (deux exceptions
ACTIVE de **même type** ne se recoupent pas) est un pré-contrôle
applicatif `@Transactional` sans contrainte SQL : un test concurrent
vérifie l'absence de `500` et une issue déterministe par requête
(`201` ou `409`), mais **en concurrence les deux insertions peuvent
encore réussir** — l'unicité n'est **pas** garantie, limite explicitement
documentée.

Audit : `alternation.AlternationChangeEvent` (record public, enums
`AlternationResourceType` WORK_STUDY_PATTERN / CLASS_WORK_STUDY_PATTERN /
STUDENT_SCHEDULE_EXCEPTION, `AlternationChangeAction` CREATED / UPDATED /
ARCHIVED / RESTORED / ASSIGNED / CLOSED / CANCELLED) →
`audit.internal.AlternationAuditListener` (catégorie `ALTERNATION`,
transaction `REQUIRES_NEW`, motif non sensible `code=…;type=…` ou
`class=…;pattern=…` ou `class=…;type=…` — jamais de numéro étudiant, de
nom ni d'adresse). Actions écrites : `WORK_STUDY_PATTERN_CREATED` /
`_UPDATED` / `_ARCHIVED` / `_RESTORED`,
`CLASS_WORK_STUDY_PATTERN_ASSIGNED` / `_CLOSED`,
`STUDENT_SCHEDULE_EXCEPTION_CREATED` / `_CANCELLED`.

**Dette transactionnelle de l'audit — connue, NON résolue par ce lot.**
`AlternationAuditListener` (comme **tous** les listeners d'audit du
projet) est un `@EventListener` synchrone en `REQUIRES_NEW` : il peut
écrire la ligne d'audit **avant** le commit définitif de la transaction
métier ayant publié l'événement (et cette ligne subsiste si la
transaction métier échoue ensuite). Spring Modulith recommande une
intégration événementielle transactionnelle
(`@TransactionalEventListener(phase = AFTER_COMMIT)` ou
`@ApplicationModuleListener`) pour découpler le traitement de l'événement
de la transaction métier. La migration doit être faite **globalement** et
de façon cohérente pour tous les modules — ne toucher que
`AlternationAuditListener` rendrait la stratégie d'audit incohérente.
Documentée dans la javadoc du listener ; à planifier ; **pas** marquée
résolue.

Horloge : bean `java.time.Clock` (`shared.config.ClockConfig`) injecté
dans `ClassWorkStudyPatternService` (`effectiveDate` de clôture par
défaut).

Décisions / ambiguïtés (aucune règle métier importante inventée) :
- `cycle_start_date` placé sur `class_work_study_pattern` (consigne
  explicite « Ne place pas cycleStartDate dans le pattern ») ;
- `pattern_type` `THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY` : `schoolDays` **et**
  `companyDays` explicites (consigne « définir explicitement les jours
  école et entreprise ») ;
- énumération des exceptions limitée aux 4 valeurs strictement adossées
  aux cas décrits (docs/04 §14.3) ; statut `ACTIVE`/`CANCELLED` ;
- résolution effective : uniquement la priorité structurelle d'une
  exception `ON_SITE_REQUIRED` / `COMPANY_PERIOD` ; sinon `UNKNOWN` —
  aucun calcul d'assiduité inventé (section 10 du lot) ;
- URI préfixées `/api/v1/alternation/...` (convention réelle du dépôt) au
  lieu de `/api/alternation/...` du plan ;
- `cycle_length_weeks` en `INT` (schéma Hibernate) et non `SMALLINT` de
  l'esquisse docs/04 §14.1.

Passe corrective de revue (PR #17) — round-trip canonique des 4 types,
sémantique `[startAt, endAt)` exacte (couverture civile par intersection
d'intervalles), clôture bornée par l'affectation suivante, garanties
concurrentes réelles + limites résiduelles, dette transactionnelle de
l'audit documentée. Fichiers touchés : `AlternationConfigParser`
(+`parseCanonical` strict tolérant les tableaux de jours vides,
`requireCanonicalDays`), `AlternationContextService` (projection
demi-ouverte, `safeZone` → erreur interne explicite),
`ClassWorkStudyPatternService` + `ClassWorkStudyPatternRepository`
(borne de clôture + requête `findFirstBy...`), `AlternationException`
(+`ASSIGNMENT_CLOSE_CONFLICT`) + `AlternationExceptionHandler`
(`ALT_ASSIGNMENT_CLOSE_CONFLICT`), `AlternationAuditListener` (javadoc de
dette), `application-test.yml` (pool HikariCP réduit :
`maximum-pool-size: 4`, `minimum-idle: 0`, `idle-timeout` court — les
contextes `@SpringBootTest` mis en cache relâchent leurs connexions au
fil d'une suite qui grandit).

Vérifs locales le 30 août 2026 (OpenJDK 21, Maven 3.9.x, MySQL 8.4,
Redis 7) : `./mvnw clean test` → `BUILD SUCCESS`, **449 tests** (419 →
449, +30), 0 échec, 0 erreur, **exécuté trois fois de suite** (résultats
identiques), dont `ModularityTests` vert. Migration `V8` inchangée,
appliquée et vérifiée (schéma en version 8). Nouveaux tests : round-trip
canonique des 4 types + tolérances/refus `parseCanonical`
(`AlternationConfigParserTests`, 21 → 35) ; sémantique `[startAt, endAt)`,
minuit exact, changement d'heure Europe/Paris, fuseau persisté invalide
(`AlternationContextServiceTests`, 6 → 11) ; clôture après `valid_until`
initial → 400, veille/jour de la suivante → accepté/409
(`ClassWorkStudyPatternServiceTests`, 12 → 15) ; les 4 rythmes en HTTP
(`SCHOOL` puis `COMPANY`), clôture bornée, deux créations concurrentes
d'affectations ouvertes → 1×201 / 1×409 / 0×500 / une seule ligne ACTIVE
ouverte, deux exceptions concurrentes de même type → aucun 500
(`AlternationIntegrationTests`, 10 → 17) ; `PEDAGOGICAL_MANAGER` limité au
périmètre pour exceptions + contexte d'inscription + liste plate
(`AlternationSecurityTests`, 5 → 6).

Éléments différés : modules `planning`, `coursesession`, `attendance`,
calcul réel d'assiduité, frontend Angular ; exceptions **collectives**
(docs/03 §7.4) non couvertes (seules les exceptions individuelles le
sont) ; pas de seed métier des 3 rythmes MVP (créés via API / fixtures).

Risques / limites résiduels documentés :
- course résiduelle sur des **périodes bornées** d'affectation à une
  classe : pré-contrôle applicatif seul, aucune contrainte de plage SQL
  fiable en MySQL ;
- **exceptions concurrentes de même type et même période** : les deux
  peuvent encore être persistées (pas de contrainte SQL) — unicité non
  garantie en concurrence ;
- **dette transactionnelle de l'audit** : `@EventListener` +
  `REQUIRES_NEW` peut écrire l'audit avant le commit métier ; migration
  globale vers `@TransactionalEventListener(AFTER_COMMIT)` /
  `@ApplicationModuleListener` recommandée, non faite.
```

## Contexte antérieur — administration des comptes utilisateurs (front-end)

```text
Administration des comptes utilisateurs (front-end) — branche
`feature/frontend-user-administration`, PR ouverte contre `main`, NON
fusionnée. Sixième tranche verticale front-end, EN LECTURE SEULE :
consultation des comptes utilisateurs et de leurs rôles
(liste → fiche → historique des rôles). Le socle front-end (PR #11,
`6fa341f`), l'activation publique (PR #12, `2ff7aa8`), le sélecteur de
contexte de rôle (PR #13, `810c8a2`), l'espace Apprenants (PR #14,
`1678399`) et la consultation des référentiels académiques (PR #15,
`b47cfa3`) sont fusionnés sur `main`. Aucun fichier back-end, migration
V1–V7 ou docs/01–04 modifié ; `SecurityConfig`, autorisations, CORS et
endpoints back-end inchangés ; aucune dépendance npm ajoutée
(`package.json` / `package-lock.json` inchangés).

Contrat back-end consommé **tel quel** (module `identity`,
`UserAccountController` ; rien d'inventé ; **lecture seule**). Toutes ces
routes sont réservées côté serveur à
`ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION`
(`UserAccountController.READ_ROLES` — le même périmètre que `LIFECYCLE_ROLES`
pour suspend/restore ; `ADMIN`/`SUPER_ADMIN` seuls pour archive/roles) :
- `GET /api/v1/users` → `PageResponse<UserSummaryResponse>` : `q`
  (sous-chaîne insensible à la casse sur **email OU prénom OU nom**,
  `LIKE` échappé, bornée à 100 caractères), `status`
  (`PENDING_ACTIVATION`|`ACTIVE`|`SUSPENDED`|`LOCKED`|`ARCHIVED`, sinon
  400 `USER_INVALID_FILTER`), `role` (code `RoleCode` ; filtre sur une
  affectation **active** ; valeur inconnue → 400 `USER_INVALID_FILTER`),
  `sort` (liste blanche stricte `createdAt|lastLoginAt|email|lastName` ;
  champ ou direction hors liste → 400 `USER_INVALID_SORT` ; défaut
  `createdAt,desc`), `page` (défaut 0), `size` (≤ 0 → défaut 20 ; borné à
  100). `UserSummaryResponse` = `{ publicId, email, firstName, lastName,
  status, roles (codes des rôles actifs), createdAt, lastLoginAt|null }`.
- `GET /api/v1/users/{publicId}` → `UserDetailResponse` ; identifiant
  inconnu **ou mal formé** → 404 `USER_NOT_FOUND`. `UserDetailResponse` =
  `{ publicId, email, firstName, lastName, phone|null, status,
  emailVerifiedAt|null, lastLoginAt|null, suspendedAt|null,
  suspensionReason|null, archivedAt|null, createdAt, updatedAt,
  roleAssignments: [{ role, active, validFrom, validUntil|null }] }` —
  historique **complet** des rôles (actifs et clôturés), trié du plus
  récent au plus ancien côté serveur.
Aucune écriture consommée : `POST …/{id}/suspend` · `/restore` ·
`/archive` · `/roles` · `/roles/{roleCode}/revoke` **ne sont pas
appelés** (tranche lecture seule — voir « Décisions »).
Tous les `PageResponse<T>` = `{ content, page, size, totalElements,
totalPages }`.

- Route `/administration` : l'ancien **placeholder est remplacé** par un
  écran réel. Devient un parent gardé
  (`roleGuard(['ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION'])` +
  `canActivateChild` identique — repris **à l'identique** de
  `UserAccountController.READ_ROLES`). Enfants : `''` → `UserList`,
  `:publicId` → `UserDetail`. `/login`, `/activation`, `/dashboard`,
  `/students`, `/academic` et le sélecteur de contexte : inchangés. Le
  périmètre du guard passe de `['ADMIN','SUPER_ADMIN']` (placeholder) à
  `+ 'SCHOOL_ADMINISTRATION'` pour coller au `@PreAuthorize` réel — c'est
  un alignement du guard front sur le back, aucun droit élargi.
- `NAV_ITEMS` : l'entrée « Administration » perd son drapeau
  `placeholder` ; ses `roles` deviennent
  `['ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION']`. Elle apparaît donc
  dans la navigation de `AppShell` et dans les accès rapides du tableau
  de bord pour ces trois rôles, filtrée en plus par le contexte de rôle
  actif (`effectiveRoles`) — sans jamais élargir un droit. Le mécanisme
  `NavItem.placeholder` + `visibleNavItems` est conservé (plus aucune
  route ne l'utilise ; testé via des entrées fabriquées). Le composant
  `shared/components/module-placeholder` n'est plus référencé mais est
  laissé en place (infrastructure réutilisable pour une future route
  gardée sans écran).
- `AdministrationApiService` (nouveau, `providedIn: 'root'`, lecture
  seule) : 2 méthodes GET (`listUsers`, `getUser`). `HttpParams`
  construits en omettant toute clé absente (aucun filtre vide envoyé).
  Appels via les intercepteurs existants (jeton porteur en mémoire ;
  `401` → purge + `/login` par `apiErrorInterceptor` — **traitement
  transversal de session inchangé** ; `5xx` → bandeau générique) — aucun
  intercepteur modifié.
- `UserList` (`app-user-list`) : `mat-table` + `mat-sort` (en-têtes
  triables = liste blanche `createdAt`/`lastLoginAt`/`email`/`lastName` ;
  un tri hors liste retombe sur `createdAt,desc` **avant** l'appel,
  jamais un 400) + `mat-paginator` francisé (`MatPaginatorIntl` fourni au
  composant ; options 10/20/50/100). Filtres : recherche « Nom ou
  adresse électronique » (`q`, trim), sélecteur de statut, sélecteur de
  rôle actif (les 6 `RoleCode`, libellés FR via `roleLabel`) ;
  « Filtrer » remet à la page 0 ; « Réinitialiser » vide les filtres.
  Colonnes : email, nom (prénom + nom), rôles actifs (libellés joints),
  statut, créé le, dernière connexion, action. Action = lien `Consulter`
  (focusable, `aria-label` explicite) vers `/administration/{publicId}` ;
  aucune ligne cliquable sans équivalent clavier. États : `loading`,
  `ready` vide, `ready` peuplé, `error` (+ « Réessayer »), `forbidden`
  (403 API → panneau + retour tableau de bord).
- `UserDetail` (`app-user-detail`) : lit `:publicId` depuis
  `ActivatedRoute.snapshot`. Charge le compte, puis rend une carte de
  faits (`<dl>` : email, téléphone, statut, adresse vérifiée le,
  dernière connexion, suspendu le + motif si présent, archivé le si
  présent, créé le, modifié le) + section « Historique des rôles »
  (`mat-table` : rôle, état actif/clôturé, attribué le, clôturé le|`—`).
  États : `loading`, `ready`, `not-found` (404), `forbidden` (403),
  `error` (+ « Réessayer »). Lien de retour « ← Retour à la liste des
  comptes ».
- Sécurité / confidentialité : JWT et contexte de rôle restent **en
  mémoire seule** (docs/07 §6, RG-085) ; aucun accès `localStorage` /
  `sessionStorage` (asserté en test). Les DTO back-end n'exposent ni
  `id` SQL, ni `password_hash`, ni jeton ; aucun message d'exception,
  trace, requête SQL ou `correlationId` affiché (les `5xx` sont
  neutralisés par `normalizeHttpError`). Les gardes de route ne
  remplacent pas Spring Security : un `403` de l'API est rendu comme un
  état « accès refusé » explicite. Un `401` continue de passer par le
  traitement transversal de session existant (`apiErrorInterceptor` →
  `AuthService.handleUnauthorized`).
- Tests front : **167 → 190** (0 échec). Nouveaux :
  `administration-api.service.spec.ts` (4 : URL / méthode / params inclus
  seulement si renseignés pour `listUsers` (`q`/`status`/`role`/`sort`/
  `page`/`size`) et `getUser` ; aucune requête `POST`/`PATCH`/`DELETE`).
  `user-list.spec.ts` (10 : 1re page + tri par défaut `createdAt,desc` +
  état de chargement ; une ligne par compte + libellés de rôles joints +
  lien clavier ; état vide ; panneau 403 ; erreur générique +
  « Réessayer » ; filtres `q` (trim) + `status` + `role` remettant à la
  page 0 ; tri toujours dans la liste blanche, repli sur le défaut ;
  pagination transmet `page`/`size` ; rien en storage).
  `user-detail.spec.ts` (7 : chargement → faits ; historique complet
  actifs + clôturés ; historique vide → message dédié ; 404 → panneau
  introuvable sans autre appel ; 403 → panneau accès refusé ;
  « Réessayer » ; aucune requête d'écriture + rien en storage). Specs
  mis à jour : `navigation.spec.ts`, `app-shell.spec.ts`,
  `dashboard.spec.ts` (l'entrée « Administration » est un écran livré,
  visible pour `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION`),
  `app.routes.spec.ts` (`/administration` = parent gardé avec enfants
  `''` et `:publicId` ; `SCHOOL_ADMINISTRATION` ouvre liste + détail ;
  `TEACHER` et `PEDAGOGICAL_MANAGER` → `/forbidden` via
  `canActivateChild`).
- Vérifs locales le 29 août 2026 (Node 24.13.0), depuis `frontend/` :
  `npm test -- --watch=false` → 27 fichiers, 190 tests, 0 échec ;
  `npm run build` → bundle initial 477,81 kB brut / 121,63 kB transféré
  (−0,90 kB vs 478,71 kB ; `user-list` 11,05 kB brut et `user-detail`
  8,48 kB brut sont des chunks paresseux, hors budget « initial »),
  0 alerte de budget (seuil d'avertissement 500 kB) ; `npm run lint` →
  « All files pass linting ». `cd backend && ./mvnw test` non
  ré-exécuté : aucun fichier back-end modifié.
- Décisions / ambiguïtés (aucune règle inventée) :
  * tranche **strictement en lecture seule** — les endpoints d'écriture
    du module `identity` existent et sont pleinement spécifiés, mais leur
    parcours complet (gardes fines côté serveur : protection
    `SUPER_ADMIN`, auto-action interdite, dernier rôle actif protégé,
    rôle `SUPER_ADMIN` réservé à un appelant `SUPER_ADMIN` ; plus un
    formulaire d'attribution de rôle et des confirmations) représente une
    surface qui sera livrée sans approximation dans un lot dédié. Les
    trois tranches d'administration front précédentes (Apprenants,
    référentiels académiques) sont elles aussi en lecture seule.
  * le placeholder `/administration` **est remplacé** : « administration
    des comptes, des rôles et des référentiels » décrivait exactement cet
    écran. Le guard de route est aligné sur le `@PreAuthorize` réel
    (`+ SCHOOL_ADMINISTRATION`), ce qui n'élargit aucun droit (Spring
    Security reste l'autorité).
  * recherche `q` = email **ou** prénom **ou** nom (spécification
    back-end `UserAdminSpecifications.matchesText`) — le libellé du champ
    le précise.
  * filtre `role` = les 6 `RoleCode` ; le back-end filtre sur une
    affectation **active** — aucun sous-ensemble arbitraire.
  * historique des rôles non paginé (le détail renvoie la liste complète
    dans `roleAssignments`).
```

CONTEXTE ANTÉRIEUR (référentiels académiques, PR #15 fusionnée sur `main`,
commit `b47cfa3`) : consultation **en lecture seule** à `/academic`
(années scolaires → formations → niveaux → promotions → classes) ;
`AcademicApiService` (10 GET) ; composants génériques
`AcademicReferenceList` + `AcademicReferenceDetail` pilotés par
`data.resource` ; recherche `q` (code ou nom), filtre `status`, tri liste
blanche par ressource, pagination ≤ 100 — strictement l'API ; sous-listes
d'enfants via filtres réels `program`/`academicYear`/`promotion`/
`programLevel` ; aucune écriture consommée ; 403 (dont `ACAD_FORBIDDEN`)
rendu « accès refusé », 404 « introuvable ». 131 → 167 tests.


CONTEXTE ANTÉRIEUR (espace Apprenants, PR #14 fusionnée) :

```text
Espace Apprenants (front-end) — fusionné sur `main` via PR #14
(commit `1678399`). Aucun fichier
back-end, migration V1–V7 ou docs/01–04 modifié ; autorisation, CORS et
endpoints back-end inchangés ; aucune dépendance npm ajoutée
(`package.json` / `package-lock.json` inchangés).

Contrat back-end consommé **tel quel** (module `enrollment`, rien
d'inventé ; toutes réservées côté serveur à
`ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION` — `EnrollmentWeb.MANAGE_ROLES`) :
- `GET /api/v1/student-profiles` → `PageResponse<StudentProfileResponse>` :
  params réellement exposés uniquement — `q` (sous-chaîne du **numéro
  étudiant** seul ; le nom n'est pas interrogeable par l'API), `status`
  (`ACTIVE`|`ARCHIVED`, sinon 400 `ENR_INVALID_FILTER`), `sort` (liste
  blanche `studentNumber`|`createdAt`, sinon 400 `ENR_INVALID_SORT`),
  `page`, `size` (borné à 100). Le filtre `user` existe mais n'est pas
  utilisé ici.
- `GET /api/v1/student-profiles/{publicId}` → `StudentProfileResponse` ;
  identifiant inconnu / non-UUID → 404 `ENR_STUDENT_PROFILE_NOT_FOUND`.
- `GET /api/v1/enrollments?student={profilePublicId}&sort=startDate,desc`
  → `PageResponse<EnrollmentResponse>` (historique complet d'un
  apprenant ; `size=100`, aucune pagination — un cursus tient largement
  sous 100). `sort` liste blanche `startDate`|`endDate`|`createdAt`.
- `GET /api/v1/users/{userPublicId}` → sous-ensemble de
  `UserDetailResponse` (`firstName`, `lastName`, `email`), **facultatif** :
  le profil apprenant n'expose que `userPublicId` ; cet appel enrichit la
  fiche avec l'identité civile et son échec est **ignoré** (la fiche
  reste affichée, titrée « Apprenant <numéro> »). Même périmètre de rôles
  (`UserAccountController` READ_ROLES).
Les POST du module `enrollment` (`create`, `{id}/transfer`, `{id}/close`)
ne sont **pas** consommés : cette tranche est en lecture seule.

- Route `/students` : le placeholder est **remplacé** par un écran réel.
  Devient un parent gardé (`roleGuard(['ADMIN','SUPER_ADMIN',
  'SCHOOL_ADMINISTRATION'])` + `canActivateChild` identique — aligné sur
  `EnrollmentWeb.MANAGE_ROLES`) avec deux enfants : `''` → `StudentList`,
  `:publicId` → `StudentProfile`. `/administration` reste un placeholder
  gardé et masqué de la navigation. `/login`, `/activation`,
  `/dashboard`, le sélecteur de contexte : inchangés.
- `NAV_ITEMS` : l'entrée « Apprenants » perd son drapeau `placeholder`
  (elle conserve son filtre de rôles). Elle apparaît donc dans la
  navigation de `AppShell` et dans les accès rapides du tableau de bord
  pour `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION`, filtrée en plus par
  le contexte de rôle actif (`effectiveRoles`) — sans jamais élargir un
  droit. `/administration` reste absent de la navigation.
- `StudentsApiService` (nouveau, `providedIn: 'root'`) : lecture seule ;
  `HttpParams` construits en omettant toute clé absente (aucun filtre
  vide envoyé). Les appels passent par les intercepteurs existants
  (jeton porteur en mémoire ; `401` → purge + `/login` ; `5xx` → bandeau
  générique) — aucun intercepteur modifié.
- `StudentList` (`app-student-list`) : `mat-table` + `mat-sort` (en-têtes
  triables limités à `studentNumber` et `createdAt` — un clic sur une
  colonne hors liste blanche retombe sur le tri par défaut
  `createdAt,desc`) + `mat-paginator` (options 10/20/50/100 ; libellés
  français via `MatPaginatorIntl` fourni au composant). Formulaire de
  filtres : recherche « Numéro étudiant » (`q`, trim) + sélecteur de
  statut ; « Filtrer » remet à la page 0 ; « Réinitialiser » vide les
  filtres. Colonne d'action = lien `Consulter` (accessible clavier,
  `aria-label` explicite) vers `/students/{publicId}` ; **aucune ligne
  cliquable sans équivalent clavier**. États : `loading` (barre de
  progression + `role="status"`), `ready` vide (« Aucun profil
  apprenant… »), `ready` peuplé, `error` (message générique +
  « Réessayer »), `forbidden` (403 API → panneau « Vous n'êtes pas
  autorisé… » + retour tableau de bord).
- `StudentProfile` (`app-student-profile`) : lit `:publicId` depuis
  `ActivatedRoute.snapshot`. Charge le profil, puis (en parallèle)
  l'identité civile facultative et l'historique. Carte « Profil »
  (numéro étudiant, e-mail si dispo, statut, alternance, entreprise,
  naissance, date de création) + section « Historique des inscriptions »
  (`mat-table` : année scolaire, classe, formation, période
  `début – fin|en cours`, statut, origine `MANUAL`/`CLASS_TRANSFER` +
  motif de changement). États profil : `loading`, `ready`, `not-found`
  (404 → « Aucun profil apprenant ne correspond… » + retour liste),
  `forbidden` (403), `error` (+ « Réessayer »). États historique
  indépendants : `loading`, `ready` (vide → « Aucune inscription… »),
  `error` (+ « Réessayer » ne recharge que l'historique). Lien de retour
  « ← Retour à la liste des apprenants ».
- Sécurité / confidentialité : JWT et contexte de rôle restent **en
  mémoire seule** (docs/07 §6, RG-085) ; aucun accès `localStorage` /
  `sessionStorage` (asserté en test). Aucun message d'exception, trace,
  requête SQL, `correlationId` ou identifiant SQL interne n'est affiché
  (les DTO back-end n'en exposent pas ; les `5xx` sont neutralisés par
  `normalizeHttpError`). Les gardes de route ne remplacent pas Spring
  Security : un `403` de l'API est rendu comme un état « accès refusé »
  explicite.
- Tests front : **102 → 131** (0 échec). Nouveaux :
  `students-api.service.spec.ts` (7 : URL / méthode / params inclus
  seulement si renseignés pour `listProfiles`, `getProfile`,
  `listEnrollments` avec filtre `student` + `sort`, `getUserIdentity`).
  `student-list.spec.ts` (9 : 1re page + tri par défaut + état de
  chargement ; une ligne par profil + lien clavier vers le détail ; état
  vide ; panneau 403 ; erreur générique + « Réessayer » qui relance ;
  filtres `q` (trim) + `status` remettant à la page 0 ; tri toujours
  dans la liste blanche, repli sur le défaut ; pagination transmet
  `page`/`size` ; rien en storage). `student-profile.spec.ts` (8 :
  chargement → faits du profil ; profil rendu même si l'identité
  facultative échoue ; historique demandé avec `student` + `sort=startDate,desc`
  et rendu ; historique vide ; 404 → panneau introuvable sans autre
  appel ; 403 → panneau accès refusé ; « Réessayer » sur l'historique ;
  rien en storage). Specs mis à jour : `navigation.spec.ts`,
  `app-shell.spec.ts`, `dashboard.spec.ts` (l'entrée « Apprenants » est
  désormais un écran livré, visible pour les rôles de
  `EnrollmentWeb.MANAGE_ROLES` ; `/administration` reste masqué),
  `app.routes.spec.ts` (`/students` = parent gardé avec enfants `''` et
  `:publicId` ; `SCHOOL_ADMINISTRATION` ouvre un détail ; `TEACHER` →
  `/forbidden` via `canActivateChild`).
- Vérifs locales le 29 août 2026 (Node 24.13.0), depuis `frontend/` :
  `npm test -- --watch=false` → 21 fichiers, 131 tests, 0 échec ;
  `npm run build` → bundle initial 477,42 kB brut / 123,31 kB transféré,
  0 alerte de budget (seuil d'avertissement 500 kB) ; `npm run lint` →
  « All files pass linting ». `cd backend && ./mvnw test` non ré-exécuté :
  aucun fichier back-end modifié.
- Ambiguïtés documentaires (aucune règle inventée) : docs/02 §48.5 /
  §7 n'imposent pas d'écran normatif ; la fiche apprenant complète le
  profil (qui n'a pas de nom) par `GET /api/v1/users/{id}`, endpoint
  réel de même périmètre — décision documentée, appel rendu facultatif
  et non bloquant. La recherche est limitée au numéro étudiant car
  `GET /api/v1/student-profiles` n'expose pas d'autre critère textuel
  (le libellé du champ le précise). L'historique n'est pas paginé
  (`size=100`, cursus < 100).
```

### Activation de compte (front-end) — fusionnée sur `main` via PR #12 (commit `2ff7aa8`)

```text
Parcours public `/activation?token=<jeton>`, fusionné sur `main`
(commit `2ff7aa8`). Le socle front-end (PR #11) est fusionné sur `main`
(commit `6fa341f`). Aucun fichier back-end, migration V1–V7 ou docs/01–04
modifié ; autorisation et CORS back-end inchangés.
- Route `/activation` — **publique, sans aucune garde** (ni `authGuard`,
  ni `roleGuard`, ni `guestGuard`) : le jeton d'invitation fait foi,
  indépendamment d'une éventuelle session en mémoire (docs silencieux sur
  le cas d'un utilisateur déjà connecté activant un autre compte).
  N'apparaît pas dans la navigation authentifiée. Les routes `/login`,
  `/dashboard`, placeholders, gardes et navigation existantes sont
  inchangées.
- Endpoints consommés **exactement** (contrat existant, rien d'inventé) :
  * `GET /api/v1/account-invitations/validate?token=<jeton>` (jeton en
    paramètre de requête) → toujours `200` avec `{ "valid": boolean }`
    (aucune donnée personnelle, aucun motif) ;
  * `POST /api/v1/account-invitations/activate`, corps
    `{ "token": string, "password": string }` → succès `204 No Content`,
    corps vide, **aucun identifiant de session renvoyé**.
- Traitement du jeton : lu une seule fois depuis `?token=` via
  `ActivatedRoute.snapshot`, puis retiré de la barre d'adresse dès
  `NavigationEnd` par `Location.replaceState` (pas de rechargement, pas
  d'entrée d'historique) ; conservé uniquement dans un champ privé du
  composant (effacé à la destruction) ; jamais journalisé, affiché, mis
  en `localStorage` / `sessionStorage` / IndexedDB, ajouté à une autre
  URL, ni envoyé comme jeton porteur.
- Formulaire de mot de passe (Angular reactive form) : champ `password`
  seul — le contrat back-end est `{ token, password }` avec
  `@Size(min = 12, max = 200)`, sans règle de complexité ni champ de
  confirmation ; aucune de ces contraintes n'est renforcée côté client
  au-delà de `required` + `minLength(12)` + `maxLength(200)`. Bascule
  afficher/masquer accessible (bouton, `aria-label` explicite,
  `aria-pressed`), `autocomplete="new-password"`. Envoi désactivé tant
  que le formulaire est invalide ou en cours ; double envoi empêché ;
  champs marqués « touchés » à une soumission invalide ; mot de passe
  effacé du formulaire après succès, après échec terminal, et à la
  destruction du composant.
- États de l'interface (dérivés des codes back-end réels) :
  `validating` → `form` (invitation valide) → `success` (lien vers
  `/login`, **aucune connexion automatique**, aucun JWT fabriqué) ;
  `invalid-link` (jeton absent / illisible, `{ valid: false }`, ou
  `400 INVITATION_INVALID` à l'envoi) — **état terminal unique** : le
  back-end renvoie un seul code pour un lien inconnu / expiré / révoqué /
  déjà utilisé, aucune distinction n'est inventée ; `validation-error`
  (réseau ou `5xx` pendant la validation, bouton « Réessayer ») ;
  `submitting` ; message d'erreur en ligne pour `400 VALIDATION_ERROR`
  (« 12 à 200 caractères », formulaire conservé), pour le réseau
  (statut 0) et pour un `5xx` (message générique sûr, renvoi possible).
  Aucune trace serveur, message d'exception, requête SQL, valeur de jeton
  ni détail de compte n'est affiché.
- Intercepteurs ajustés (plus petit changement sûr) : `authTokenInterceptor`
  et `apiErrorInterceptor` excluent tous deux
  `/account-invitations/validate` et `/account-invitations/activate`
  (`isPublicInvitationRequest`) → aucun en-tête `Authorization` sur ces
  appels publics, et un `401` / `5xx` venant d'eux ne purge jamais la
  session en mémoire ni ne déclenche le bandeau global. Le `POST
  /account-invitations` protégé (émission) reçoit toujours le jeton
  porteur.
- Stratégie de session inchangée : jeton d'accès en mémoire uniquement,
  ni `localStorage` ni `sessionStorage` ni cookie JS (docs/07 §6,
  RG-085) ; un rechargement de page perd la session et renvoie vers
  `/login` ; une vraie session persistante exige le futur cookie
  `HttpOnly` + refresh token côté back-end. Le jeton d'invitation est
  distinct du jeton d'accès.
- Accessibilité : `<main>` sémantique, labels associés, `role="status"`
  pour la validation asynchrone, `role="alert"` pour les échecs, focus
  visible, navigation clavier, aucun indicateur d'état par la seule
  couleur, libellés de boutons explicites, page responsive cohérente avec
  l'écran de connexion.
- Tests front : **69 → 85** (16 nouveaux, 0 échec). `AccountActivationApiService`
  (méthode / chemin / placement du jeton en paramètre ; corps
  `{ token, password }` ; `204` géré ; aucun `Authorization`),
  `authTokenInterceptor` (aucun bearer sur validate/activate ; bearer
  conservé sur l'émission protégée), `apiErrorInterceptor` (un `401` /
  `5xx` public d'activation ne purge pas la session et ne notifie pas ;
  l'erreur est toujours relayée), `app.routes` (`/activation` déclarée
  sans garde ; joignable anonyme et authentifié), `AccountActivation`
  via `RouterTestingHarness` (navigation réelle) : jeton absent → état
  terminal sans requête ; jeton lu et retiré de l'URL visible ;
  formulaire pour invitation valide avec `autocomplete="new-password"` ;
  `{ valid: false }` → terminal ; « Réessayer » après échec de
  validation ; règles 12/200 ; bouton désactivé si invalide ; charge
  utile exacte + double envoi empêché ; succès + lien `/login` + aucune
  connexion + mot de passe effacé ; `INVITATION_INVALID` terminal ;
  `VALIDATION_ERROR` en ligne ; échec réseau récupérable ; jeton absent
  du DOM rendu et de tout stockage navigateur. `makeJwt` déplacé de
  `jwt.spec.ts` vers `jwt.testing.ts` (plus aucun spec n'en importe un
  autre ; `*.testing.ts` exclu du build).
- Aucune dépendance ajoutée (`@angular/material` fournit déjà le
  `progress-spinner`) : `package.json` et `package-lock.json` inchangés.
  Vérifs locales le 29 août 2026 (Node 24.13.0), depuis `frontend/` :
  `rm -rf node_modules && npm ci` → 0 vulnérabilité ;
  `npm test -- --watch=false` → 16 fichiers, 85 tests, 0 échec ;
  `npm run build` → bundle initial 410,57 kB brut / 106,54 kB transféré,
  0 alerte de budget ; `npm run lint` → « All files pass linting ».
- Ambiguïtés documentaires signalées (aucune règle inventée) : aucun
  document n'impose de champ de **confirmation** du mot de passe pour
  l'activation (docs/02 §8.3 étape 6 = simple « définition du mot de
  passe ») → champ omis ; l'accès invité à `/activation` est laissé sans
  garde car le jeton d'invitation est l'autorité de cet endpoint public
  (docs silencieux) ; le back-end renvoyant un unique `INVITATION_INVALID`,
  l'interface ne distingue pas expiré / consommé / révoqué.

Socle front-end Angular — **fusionné sur `main` via PR #11**
(commit `6fa341f`). Première tranche verticale authentifiée : connexion →
tableau de bord (rapport d'un **état de session local** établi après
connexion réussie).
- Application créée avec `ng new` sous `frontend/` (docs/03 §9.1) :
  Angular **21.2** (paquets framework / CLI / build résolus en 21.2.22 ;
  Material + CDK en 21.2.14 — même ligne mineure 21.2, versionnement
  propre à Angular Components ; runner de tests `@angular/build:unit-test`
  = Vitest + jsdom ; application **zoneless** par défaut), Node.js 24.13,
  npm 11. `package.json` déclare une politique de version cohérente
  (`^21.2.x` pour tous les paquets `@angular/*`). Composants standalone,
  TypeScript strict, formulaires réactifs, signaux, routes de
  fonctionnalités en lazy loading, control flow natif (`@if` / `@for`).
- Dépendances ajoutées, toutes first-party : `@angular/material` +
  `@angular/cdk` (Angular Material explicitement requis par docs/02 §48.1,
  docs/01 §5.3, US-023, T-J1-040) ; `angular-eslint` (+ `eslint`,
  `typescript-eslint`) en dev pour `npm run lint`. Pas de NgRx, pas de
  Tailwind/Bootstrap, pas de SSR ni service worker.
- Routes implémentées :
  * `/login` — écran de connexion (`guestGuard`), formulaire réactif
    email + mot de passe, validations alignées sur `LoginRequest`
    (`@NotBlank @Email` / `@NotBlank`), message d'échec unique et
    générique (aucune énumération de comptes), bouton désactivé +
    barre de progression pendant l'appel ;
  * `/dashboard` — premier écran authentifié (`authGuard`) : rapporte un
    **état de session local** (établi après connexion réussie ; le
    tableau de bord ne prétend PAS avoir revérifié le jeton porteur via
    un second appel d'API authentifié), affiche le compte (email saisi),
    l'identifiant public (claim `sub`), l'échéance du jeton et les rôles ;
    carte « accès rapides » n'exposant que des écrans livrés (donc vide
    tant qu'il n'y en a pas d'autre que le tableau de bord) ; état vide
    si aucun rôle ;
  * `/administration` — `roleGuard(['ADMIN','SUPER_ADMIN'])`, écran
    d'attente (placeholder) — périmètre aligné sur `UserAccountController` ;
  * `/students` — `roleGuard(['ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION'])`,
    placeholder — périmètre aligné sur `EnrollmentWeb.MANAGE_ROLES` ;
  * `/forbidden` (403) et `**` (404).
  Les deux routes placeholder sont **masquées de la navigation principale
  et des accès rapides** (`NavItem.placeholder`), mais restent
  directement adressables et gardées par rôle — un rôle non autorisé est
  toujours redirigé vers `/forbidden`. La navigation principale ne
  présente que les écrans réellement utilisables (aujourd'hui : le seul
  tableau de bord). Les routes authentifiées sont enfants d'une coquille
  `AppShell` (barre Material + navigation latérale responsive, repères
  `<nav>` / `<main>`, lien d'évitement, `aria-current`, email + rôles +
  déconnexion).
- Authentification / session :
  * `POST /api/v1/auth/login` consommé tel quel (réponse
    `{ accessToken, tokenType, expiresInSeconds }`) ; aucun endpoint
    `/auth/me` ni `/auth/logout` n'existe côté back-end — la déconnexion
    est purement locale, l'identité affichée vient de l'email saisi et des
    claims du JWT ;
  * **stockage du jeton en mémoire uniquement** (signal `AuthService`),
    ni `localStorage` ni `sessionStorage` ni cookie JS — conforme à
    docs/07 §6 et RG-085. Conséquence assumée : un rechargement de page
    perd la session et renvoie vers `/login`. `AuthService.restoreSession()`
    est le point d'ancrage d'un futur cookie `HttpOnly` + refresh token
    (stratégie cible docs/03 §15.2, docs/07 §6), non exposé par le
    back-end à ce jour ;
  * le décodage du JWT (rôles, `sub`, `exp`) est **non vérifié** et ne
    sert qu'à l'affichage et au filtrage de la navigation ; toute
    autorisation réelle reste décidée par Spring Security (consigne du
    lot ; docs/07 §7) ;
  * intercepteur de jeton porteur (en-tête `Authorization` sur les appels
    `/api`, jamais journalisé) ; intercepteur d'erreurs : `401` non-login
    → purge de session + redirection `/login?reason=expired` ; `0` / `5xx`
    → bandeau générique (aucune trace serveur exposée) ; `4xx` laissé au
    composant ; `normalizeHttpError` conserve le `code` métier
    (`ApiError`, docs/03 §10.3).
- Infra HTTP : URL de base d'API **relative** (`/api`) via
  `src/environments/environment*.ts` ; `ng serve` proxifie `/api` vers
  `http://localhost:8080` (`proxy.conf.json`) → aucune requête
  cross-origin, **aucune modification de la configuration CORS du
  back-end** (inexistante à ce jour) nécessaire en local. Un déploiement
  cross-origin devra définir l'URL absolue ici ET activer une
  configuration CORS Spring (documenté dans `environment.ts`).
- Accessibilité : labels de formulaire associés (Material), navigation
  clavier, focus visible, repères sémantiques, messages de validation
  `aria-live` / `role="alert"`, état de soumission communiqué
  (`aria-busy`), CSS responsive sans framework additionnel, aucun secret
  en paramètre d'URL.
- Tests front (69, Vitest) : `AuthService` (login succès/échec, session
  en mémoire, `restoreSession` sans persistance, `logout`,
  `handleUnauthorized`, `hasAnyRole`), décodage JWT, `normalizeHttpError`
  (préservation du code métier, masquage des 5xx, réseau, non-HTTP),
  intercepteur jeton porteur, intercepteur d'erreurs (401 / 5xx / 4xx),
  `authGuard` / `guestGuard` / `roleGuard`, matrice de navigation
  (placeholders jamais rendus quel que soit le rôle ; mapping
  rôle → route conservé pour la traçabilité), câblage réel des gardes sur
  `app.routes` (routes placeholder toujours déclarées et gardées ;
  TEACHER → `/forbidden` sur route ADMIN, SCHOOL_ADMINISTRATION →
  `/students` mais pas `/administration`, invité → `/login`), `Login`
  (rendu, état de soumission, message générique), `Dashboard` (rapport
  d'état de session **local**, aucune allégation d'appel d'API
  revérifié, rôles, état vide, absence des placeholders dans les accès
  rapides), `AppShell` (navigation limitée aux écrans livrés, placeholders
  absents même pour un ADMIN, déconnexion), `App`.
- CI : nouveau workflow `.github/workflows/frontend-ci.yml` (lint + tests
  + build de production, déclenché sur `frontend/**`). `backend-ci.yml`
  inchangé.
- Commandes de vérification front (voir plus bas) : `npm ci`,
  `npm test -- --watch=false`, `npm run build`, `npm run lint` — tous
  exécutés avec succès en local le 29 août 2026.
- Limites connues : pas de restauration de session au rechargement — un
  rechargement de page perd la session et renvoie vers `/login` ; une
  vraie session persistante exige le futur cookie `HttpOnly` + refresh
  token côté back-end ; l'écran `/activation` exige un `?token=` valide
  dans le lien (aucun renvoi d'invitation en libre-service dans la SPA) ;
  pas de contexte de rôle sélectionnable (docs/02 §6.1) ;
  `/administration` et `/students` sont des routes gardées sans contenu
  métier, volontairement masquées de la navigation ; PWA, notifications,
  SSE non abordés.
- Correction de revue (2ᵉ commit sur la PR) : formulation du tableau de
  bord rendue exacte (« session locale » au lieu de « appel d'API
  authentifié fonctionne ») ; routes placeholder retirées de la
  navigation visible tout en restant gardées et adressables ; alignement
  des versions `@angular/*` sur la ligne 21.2 ; `package-lock.json`
  régénéré ; 64 → 69 tests.

Inscriptions historiques — fusionné sur `main` via PR #10 (commit
`495c2bf`) — module `enrollment` + migration V7 `student_profile` /
`enrollment`
(schéma en version 7, appliquée et vérifiée). Couvre le profil apprenant
et l'inscription d'un apprenant
dans une classe pour une année scolaire, avec conservation de
l'historique lors d'un changement de classe (docs/02 §7.6, §13 ;
docs/04 §11.1, §13 ; RG-006, RG-012, RG-022, RG-023 ; AC-006 ;
T-J1-032 / US-053). N'aborde ni l'import CSV des apprenants, ni les
rythmes d'alternance, ni les apprenants provisoires, ni Angular.
- Entités `enrollment.internal.StudentProfile` (`user_id` = valeur
  technique via port `identity.UserDirectory`, unique ; `student_number`
  unique ; `work_study`, `birth_date`, `company_name` ; statut
  ACTIVE/ARCHIVED — seul ACTIVE produit dans ce lot) et
  `enrollment.internal.Enrollment` (`student_profile` = relation
  intra-module ; `class_group_id` / `academic_year_id` = valeurs
  techniques via nouveau port `academic.ClassGroupDirectory` ;
  `previous_enrollment_id` auto-référence ; `start_date` / `end_date`
  en `LocalDate` bornes inclusives ; `enrollment_source`
  MANUAL/CLASS_TRANSFER ; statut
  PENDING/ACTIVE/COMPLETED/TRANSFERRED/WITHDRAWN/SUSPENDED/ARCHIVED —
  ACTIVE/TRANSFERRED/COMPLETED/WITHDRAWN pilotés). Aucun DELETE
  physique ; rattachements, `start_date`, `enrollment_source` et
  `previous_enrollment_id` immuables.
- Règle RG-012 / docs/04 §13.3 : au plus une inscription ACTIVE par
  apprenant et par année scolaire — pré-contrôle applicatif
  (`ENR_ACTIVE_ENROLLMENT_EXISTS`, 409) doublé par la contrainte SQL
  `uq_enrollment_active_per_year` (deux colonnes générées VIRTUAL
  portant `student_profile_id` / `academic_year_id` uniquement pour une
  ligne ACTIVE ; une clôture libère immédiatement le créneau). Course
  concurrente :
  * création de profil (`StudentProfileService.create`) et inscription
    (`EnrollmentService.enroll`) — non transactionnelles ; l'INSERT est
    isolé dans le bean proxifié `EnrollmentPersister`
    (`@Transactional(REQUIRES_NEW)`, même approche que
    `academic.internal.AssignmentPersister`). La
    `DataIntegrityViolationException` est reçue **hors** de toute
    transaction en échec et retraduite en 409 sur place, uniquement pour
    `uq_student_profile_user` / `uq_student_profile_student_number`
    (profil) ou `uq_enrollment_active_per_year` (inscription) ; toute
    autre violation est relancée telle quelle (500 via le gestionnaire
    global). Jamais de `catch (Exception)`.
  * changement de classe (`EnrollmentService.transfer`) — reste
    `@Transactional` : l'UPDATE de clôture et l'INSERT de la nouvelle
    inscription sont atomiques, et l'INSERT doit voir dans la même
    transaction le créneau libéré. La course résiduelle ne peut donc pas
    être captée dans le service (transaction déjà rollback-only) : elle
    est retraduite après l'annulation faite par le proxy, par
    `EnrollmentExceptionHandler`, en 409 ciblé sur la seule contrainte
    `uq_enrollment_active_per_year`.
- Changement de classe (`POST /api/v1/enrollments/{id}/transfer`,
  docs/04 §13.2) : l'inscription courante ACTIVE est clôturée en
  TRANSFERRED (`end_date` = date effective, borne **inclusive**, ≥ sa
  `start_date`), l'UPDATE est flushé d'abord (colonnes générées → NULL,
  créneau libéré), puis une nouvelle inscription ACTIVE est créée
  (`start_date` = date effective **+ 1 jour** — bornes inclusives, aucun
  chevauchement de période ; docs/04 §13.2 ne fixe pas de valeur de
  `start_date`, la non-superposition découle des bornes inclusives et de
  l'unicité d'une inscription active §13.3 —, `enrollment_source` =
  CLASS_TRANSFER, `previous_enrollment_id` = ancienne). Vers une autre
  année : contrôle explicite d'absence d'inscription ACTIVE avant
  écriture. Deux événements d'audit (`ENROLLMENT_TRANSFERRED` sur
  l'ancienne, `ENROLLMENT_CREATED` sur la nouvelle). L'ancienne reste
  consultable (AC-006).
- Clôture (`POST /api/v1/enrollments/{id}/close`) : `status`
  (`COMPLETED` | `WITHDRAWN`, `@Pattern` + garde service), `reason`
  obligatoire, `effectiveDate` par défaut aujourd'hui (horloge
  injectée), ≥ `start_date`. Audit `ENROLLMENT_CLOSED`.
- Nouveau port public `academic.ClassGroupDirectory` (impl
  `academic.internal.DefaultClassGroupDirectory`, confinée) :
  `ClassGroupRef(internalId, publicId, code, programPublicId,
  programCode, academicYearInternalId, academicYearPublicId,
  academicYearCode, openForEnrollment)` — `openForEnrollment` faux dès
  qu'un maillon de la chaîne (classe, promotion, formation, année
  scolaire) est archivé ; l'inscription sous une chaîne archivée est
  refusée (409 `ENR_ARCHIVED_PARENT`). N'expose ni `ClassGroup`, ni
  repository. `ModularityTests` reste vert (module `enrollment` →
  `identity`, `academic`, `shared` ; publie vers `audit`).
- Routes (toutes réservées `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION`
  — cahier §6.4, §10.1 ; `PEDAGOGICAL_MANAGER` exclu tant qu'un port de
  périmètre pédagogique public n'existe pas ; `TEACHER`/`STUDENT` sans
  accès — la consultation de son propre historique par l'apprenant
  relève d'un lot ultérieur) :
  * `GET/POST /api/v1/student-profiles`, `GET …/{publicId}` — filtres
    `q` (numéro étudiant), `status`, `user` ; tri liste blanche
    `studentNumber`/`createdAt` (sinon 400 `ENR_INVALID_SORT`) ;
    pagination max 100 ;
  * `GET/POST /api/v1/enrollments`, `GET …/{publicId}`,
    `POST …/{id}/transfer`, `POST …/{id}/close` — filtres `student`
    (profil), `classGroup`, `status` ; tri liste blanche
    `startDate`/`endDate`/`createdAt` ; pagination max 100.
  Aucun `PATCH`, aucun `DELETE`, aucune route nichée. DTO sans
  identifiant SQL interne (`id`, `userId`, `studentProfileId`,
  `classGroupId`, `academicYearId`) ni colonne auteur.
- Éligibilité de la cible d'un profil : compte existant, non archivé,
  porteur d'un rôle actif `STUDENT` (via `identity.UserDirectory`) —
  sinon 422 `ENR_USER_NOT_ELIGIBLE`. Un seul profil par compte
  (`ENR_PROFILE_EXISTS`), numéro étudiant unique
  (`ENR_DUPLICATE_STUDENT_NUMBER`).
- Audit : `enrollment.EnrollmentChangeEvent` (module racine, enums
  `EnrollmentResourceType` STUDENT_PROFILE/ENROLLMENT,
  `EnrollmentChangeAction` CREATED/TRANSFERRED/CLOSED) → nouveau
  `audit.internal.EnrollmentAuditListener` (catégorie `ENROLLMENT`,
  transaction REQUIRES_NEW), actions `STUDENT_PROFILE_CREATED` /
  `ENROLLMENT_CREATED` / `_TRANSFERRED` / `_CLOSED`, motif non sensible
  (`class=<code>;year=<code>` ou `class=<code>;status=<statut>`), jamais
  de numéro étudiant, de nom ni d'adresse.
- Horloge : bean `java.time.Clock` (`shared.config.ClockConfig`) injecté
  dans `EnrollmentService` (`start_date` / `effectiveDate` par défaut).
- Config test : `application-test.yml` plafonne désormais le pool
  HikariCP (`maximum-pool-size: 6`, `minimum-idle: 1`). Chaque classe
  `@SpringBootTest` déclarant sa propre `@TestConfiguration` imbriquée,
  Spring met en cache un contexte (et un pool) par classe ; avec le pool
  par défaut (10) et 16 classes `@SpringBootTest`, MySQL 8
  (`max_connections` = 151) saturait (« Too many connections »). Aucun
  test métier modifié.
- docs/03 et docs/04 non modifiés. Aucune donnée fictive en V7.
- Tests ajoutés (57) — voir la section de vérification.

Périmètre pédagogique — fusionné sur main via PR #9 (commit `52d38ce`) —
table `pedagogical_assignment` via migration V6 ; schéma en version 6,
appliquée et vérifiée. Affecte un responsable
pédagogique à une formation (RG-004, RG-010, RG-011) et branche le
contrôle d'accès par périmètre sur TOUT le référentiel académique
(formation, niveau, promotion, classe). Ne traite ni les inscriptions,
ni les matières, ni Angular.
- Entité `academic.internal.PedagogicalAssignment` : relation
  intra-module vers `Program` ; `manager_user_id` et `delegated_by_id`
  = valeurs techniques (FK SQL vers `user_account`, aucune relation JPA
  inter-module, résolues via le nouveau port `identity.UserDirectory`).
  Enums `PedagogicalAssignmentRole` (PRIMARY_MANAGER | DELEGATE) et
  `PedagogicalAssignmentStatus` (ACTIVE | CLOSED). Validité en
  **`LocalDate` / `DATE`** (jour civil, bornes inclusives), colonnes
  `reason` (motif d'affectation) et `close_reason` (motif de clôture).
  Conventions techniques complètes ; aucun DELETE physique ;
  rattachements, rôle et `valid_from` immuables, seule la clôture fait
  évoluer l'entité.
- Modèle : un seul PRIMARY_MANAGER ACTIF par formation via colonne
  générée `active_primary_key` (UNIQUE) + pré-contrôle applicatif
  (409 `ACAD_PRIMARY_MANAGER_EXISTS`). Gestion de la course entre deux
  créations : `saveAndFlush` isolé dans un bean dédié `AssignmentPersister`
  (`@Transactional(propagation = REQUIRES_NEW)`) — la transaction
  d'insertion échoue et est annulée sans contaminer l'appelant, qui n'est
  **pas** transactionnel (`PedagogicalAssignmentService.create` : lectures
  en transactions implicites, insertion déléguée, audit dans sa propre
  transaction). La `DataIntegrityViolationException` est donc capturée
  **hors** de toute transaction en échec et n'est retraduite en 409 que
  si la contrainte violée est `uq_pedagogical_assignment_active_primary`
  (recherche du nom de contrainte Hibernate **et** du message SQL, avec
  sémantique de doublon) — **toute autre** violation (FK, `CHECK`,
  `NOT NULL`, longueur, unicité de `public_id`...) est relancée
  telle quelle, jamais mappée sur ce code. DELEGATE multiples et
  chevauchements autorisés,
  toujours sur toute la formation. La période détermine l'accès
  effectif ; le créneau du PRIMARY_MANAGER n'est libéré que par une
  clôture explicite (status=CLOSED), même période expirée. `CHECK
  (valid_until IS NULL OR valid_until >= valid_from)`. Cible : doit
  exister, ne pas être archivée, porter un rôle actif PEDAGOGICAL_MANAGER
  — sinon **422 `ACAD_TARGET_NOT_ELIGIBLE`**. Création refusée sous une
  formation archivée (409 `ACAD_ARCHIVED_PARENT`).
- Routes `/api/v1/pedagogical-assignments` (réservées ADMIN/SUPER_ADMIN) :
  GET liste — filtres **`program`, `user`, `type`, `status`, `activeOn`**
  (`activeOn` en `LocalDate`, validité inclusive `validFrom <= activeOn
  <= validUntil`), tri liste blanche stricte `validFrom`/`validUntil`/
  `createdAt` (sinon 400 `ACAD_INVALID_SORT`), pagination max 100 ; GET
  détail ; POST création (`type` validé `@Pattern`) ; POST `{id}/close`
  — corps `{reason (obligatoire), effectiveDate? (LocalDate, défaut
  aujourd'hui)}`, exige `effectiveDate >= validFrom` sinon 400
  `ACAD_ASSIGNMENT_DATE_INVALID`, persiste `validUntil = effectiveDate`.
  Aucune route nichée, aucun PATCH, aucun DELETE. DTO sans id SQL ni
  `programId`/`managerUserId`/`delegatedById`.
- Contrôle de périmètre centralisé — nouveau `AcademicScopeGuard`
  (unique point de décision, lit le contexte Spring Security, jamais un
  paramètre client) :
  * accès **global** (aucun filtrage) = autorité `ROLE_ADMIN`,
    `ROLE_SUPER_ADMIN` **ou `ROLE_SCHOOL_ADMINISTRATION`** ; un
    PEDAGOGICAL_MANAGER cumulé avec l'un de ces rôles est donc global,
    cumulé seulement avec TEACHER il reste limité ;
  * sinon, lecture des listes `programs`/`programs/{id}/levels`/
    `promotions`/`class-groups` filtrée aux formations du périmètre
    effectif (sous-requête `IN` sur l'ensemble des `program_id` visibles,
    affectation ACTIVE dont la période couvre le jour courant) ;
  * détail et toute opération create/update/archive/restore hors
    périmètre → **403 `ACAD_FORBIDDEN`** (formation, niveau, promotion,
    classe).
  Écriture ouverte au PEDAGOGICAL_MANAGER via `SCOPED_WRITE_ROLES`
  (update/archive/restore de la formation + toutes les écritures niveau/
  promotion/classe), puis restreinte au périmètre par le service. La
  **création d'une formation** reste réservée à ADMIN/SUPER_ADMIN
  (`WRITE_ROLES`). AcademicYear reste global et inchangé.
- Codes d'erreur alignés : `ACAD_FORBIDDEN`, `ACAD_ASSIGNMENT_NOT_FOUND`,
  `ACAD_TARGET_NOT_ELIGIBLE`, `ACAD_PRIMARY_MANAGER_EXISTS`,
  `ACAD_ASSIGNMENT_ALREADY_CLOSED`, `ACAD_ASSIGNMENT_DATE_INVALID`
  (+ `ACAD_INVALID_ASSIGNMENT_ROLE` défensif).
- Horloge : `java.time.Clock` injectable (bean `shared.config.ClockConfig`,
  `@ConditionalOnMissingBean` pour permettre une horloge figée en test).
  `AcademicScopeGuard` et `PedagogicalAssignmentService` l'utilisent
  (`LocalDate.now(clock)`) au lieu de `LocalDate.now()` — dates de
  validité par défaut et décision de périmètre testables avec
  `Clock.fixed(...)`.
- Port `identity.UserDirectory` (impl `identity.internal.
  DefaultUserDirectory`, confinée) : `UserRef(internalId, publicId,
  archived, activeRoles)` — types standard uniquement, n'expose ni
  `UserAccount`, ni repository. Complète `CurrentUserResolver`.
  `ModularityTests` reste vert.
- Audit : `AcademicChangeEvent` étendu (type `PEDAGOGICAL_ASSIGNMENT`,
  action `CLOSED`) → `audit.internal.AcademicAuditListener` inchangé,
  produit `PEDAGOGICAL_ASSIGNMENT_CREATED` / `_CLOSED` (catégorie
  ACADEMIC, motif non sensible `program=<code>;type=<type>`, jamais de
  donnée personnelle).
- docs/03 et docs/04 non modifiés. Aucune affectation fictive en V6.
- Tests ajoutés / mis à jour (49) :
  `PedagogicalAssignmentServiceTests` (16, Mockito — persister mocké,
  horloge figée ; dont **traduction d'une collision `active_primary` en
  409** avec message SQL réaliste, **relance inchangée d'une violation
  FK sans objet**, `validFrom` par défaut lu sur l'horloge injectée,
  clôture avant `validFrom`, clôture par défaut sur l'horloge injectée) ;
  `AcademicScopeGuardTests` (7, Mockito + `SecurityContextHolder` +
  `Clock.fixed` — global admin/super-admin/school-admin, manager+teacher
  limité et **requêtes de périmètre datées par l'horloge injectée**,
  appelant non résolu → rien de visible, `requireProgramInScope` OK /
  403, contexte anonyme non global) ;
  `PedagogicalAssignmentConstraintsTests` (11, @DataJpaTest — unicité
  `active_primary` (autre formation acceptée), DELEGATE non limités,
  créneau libéré par clôture, `CHECK` période + validité un seul jour
  acceptée, `public_id` unique, FK `RESTRICT` `program`/`manager`/
  `delegated_by` via `org.hibernate.exception.ConstraintViolationException`
  précise, **`isActivePrimaryUniqueViolation` reconnaît une vraie
  exception de collision et rejette une violation `public_id`**) ;
  `PedagogicalScopeIntegrationTests` (4, @SpringBootTest — scope sur
  formation/niveau/promotion/classe, manager+teacher limité,
  manager+administrator global, school-admin lecture globale sans gestion
  d'affectations) ;
  `PedagogicalAssignmentIntegrationTests` (11, @SpringBootTest — cycle
  create/list/close + audit, filtres `activeOn` (dates inclusives) et
  `type`, clôture par défaut à aujourd'hui, clôture avant `validFrom`
  refusée, éligibilité de la cible → 422, doublon PRIMARY_MANAGER → 409,
  **deux créations concurrentes (pool 2 threads) → exactement un 201 et
  un 409**, `type` invalide → 400, tri hors liste blanche, matrice
  401/403/200). `AcademicServiceTests` : constructeurs des 4 services
  académiques (+`AcademicScopeGuard` mock).

Référentiel académique minimal — fusionné sur main via PR #8 (commit
`a27b761`) — nouveau module `academic` + migration V5
`academic_year` / `program` / `program_level` / `promotion` / `class_group`
(schéma en version 5, appliqué et vérifié). Couvre la hiérarchie
formation → promotion → classe/groupe (docs/04 §12) ; n'aborde ni les
inscriptions, ni les matières, ni les responsabilités pédagogiques, ni
Angular.
- `academic_year` et `program_level` inclus uniquement comme référentiels
  support des FK de `promotion` (academic_year_id) et `class_group`
  (program_level_id). Conventions techniques complètes (public_id,
  created_at/by, updated_at/by, version, status, archived_at/by,
  archive_reason). Écarts documentés vs docs/04 §12 : `program_level`
  reçoit public_id/horodatage/version/archivage (absents du tableau
  §12.3) ; `promotion` reçoit start_date/end_date optionnelles pour la
  validation de période ; les colonnes external_source/external_id de
  §12.2/§12.5 ne sont pas reprises (pas de synchronisation externe).
- CRUD + archivage logique + restauration pour les cinq entités. Aucun
  DELETE physique. `code` immuable après création ; tous les
  rattachements parents (program, academic_year, program_level,
  promotion, site) immuables.
- Consultation paginée (max 100, défaut 20) + filtres : status, q
  (code+name, LIKE échappé) ; promotions filtrables par program /
  academicYear ; classes par promotion / programLevel / site ; niveaux
  listés sous /programs/{id}/levels. Tri liste blanche (sinon 400
  ACAD_INVALID_SORT). Consultation par public_id. Routes exclusivement
  en public_id : `/api/v1/academic-years`, `/api/v1/programs`,
  `/api/v1/programs/{id}/levels` + `/api/v1/program-levels/{id}`,
  `/api/v1/promotions`, `/api/v1/class-groups`.
- Règles métier vérifiées : end_date > start_date (année, + CHECK SQL) ;
  période de promotion, si renseignée, strictement incluse dans celle de
  l'année (ACAD_PROMOTION_PERIOD_OUT_OF_YEAR) ; modification de la période
  d'une année refusée si elle exclurait une promotion existante à période
  renseignée (ACAD_ACADEMIC_YEAR_PERIOD_CONFLICT, deux `exists` ciblés,
  aucun chargement de liste) ; le program_level d'une classe doit
  appartenir à la même formation que sa promotion
  (ACAD_PROGRAM_LEVEL_MISMATCH), revérifié aussi à la restauration ;
  création refusée sous un parent archivé (ACAD_ARCHIVED_PARENT) ;
  archivage refusé tant qu'il reste des enfants actifs
  (ACAD_HAS_ACTIVE_CHILDREN : niveaux/promotions pour une formation,
  promotions pour une année, classes pour niveau/promotion) ;
  restauration d'une classe refusée si un maillon de la chaîne est
  archivé — promotion, sa formation, son année, le niveau, la formation
  du niveau — ou si le site est absent/archivé ; restauration d'une
  promotion refusée si sa formation ou son année est archivée.
  `capacity > 0` (class_group, + CHECK SQL).
- Unicités testées : academic_year.code (global), program.code (global),
  (program_id, code) pour program_level, (program_id, academic_year_id,
  code) pour promotion, (promotion_id, code) pour class_group, tous les
  public_id ; FK RESTRICT vérifiées (program→promotion,
  promotion→class_group).
- Rattachement au site : `class_group.site_id` est une valeur technique
  (FK SQL `fk_class_group_site` vers `site.id`), jamais une relation JPA
  inter-module. Résolu via un nouveau port public minimal
  `organization.SiteDirectory` (impl `organization.internal.
  DefaultSiteDirectory`) qui n'expose que `SiteRef(internalId, publicId,
  archived)` — ni `Site`, ni `SiteRepository`, ni `organization.internal`.
  Le module `academic` n'importe jamais `organization.internal`.
  `ModularityTests` reste vert.
- Autorisations @PreAuthorize : lecture =
  ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION/PEDAGOGICAL_MANAGER ; écriture
  = ADMIN/SUPER_ADMIN uniquement (écriture PEDAGOGICAL_MANAGER reportée
  au périmètre pédagogique T-J1-023). TEACHER et STUDENT exclus (401/403
  testés).
- DTO sans identifiant SQL interne ni colonne auteur ; erreurs via
  ApiError commun (AcademicExceptionHandler, codes ACAD_*).
- Audit : événement applicatif `academic.AcademicChangeEvent` (module
  racine) → `audit/internal.AcademicAuditListener` (catégorie ACADEMIC,
  transaction REQUIRES_NEW), actions
  ACADEMIC_YEAR_/PROGRAM_/PROGRAM_LEVEL_/PROMOTION_/CLASS_GROUP_ +
  CREATED/UPDATED/ARCHIVED/RESTORED, motif non sensible (code), jamais de
  donnée personnelle.
- Aucune formation, promotion ni classe fictive insérée en V5.

Référentiel organisationnel — fusionné sur main via PR #7 (commit
`085c2f9`) — nouveau module `organization` +
migration V4 `site` / `building` / `room` / `site_network_range` (schéma
en version 4, appliqué et vérifié). Ce module élargit et remplace le
module `room` prévu par l'architecture (docs/03 §7.6).
- Hiérarchie site → bâtiment → salle. Conventions techniques complètes
  (public_id, created_at/by, updated_at/by, version, status, archived_at/by,
  archive_reason) ; `site_network_range` reçoit en plus public_id,
  updated_at et version.
- CRUD + archivage logique + restauration (site/bâtiment/salle) ;
  plages réseau = création + activation/désactivation. Aucun DELETE
  physique. `code` immuable après création ; rattachement au site immuable.
- Consultation paginée (max 100, défaut 20) + filtres (status, q, site,
  building, active) + tri liste blanche. Consultation par public_id.
  Routes exclusivement en public_id.
- Règles : refus building/room sous parent archivé ; room.site =
  building.site imposé ; archivage d'un site/bâtiment refusé tant qu'il
  reste des enfants actifs ; unicité site.code (global), (site,code) pour
  building et room, (site,cidr) active pour les plages.
- Validations : fuseau IANA via ZoneId, code pays ISO 3166-1 alpha-2,
  CIDR IPv4 et IPv6 réellement validé (préfixes bornés 0..32 / 0..128,
  sans résolution DNS).
- Autorisations @PreAuthorize : lecture site/bâtiment/salle =
  ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION/PEDAGOGICAL_MANAGER ; écriture
  = ADMIN/SUPER_ADMIN ; site_network_range = SUPER_ADMIN pour TOUTES les
  opérations, consultation comprise.
- DTO sans identifiant SQL interne ni colonne auteur ; erreurs via
  ApiError commun (OrganizationExceptionHandler).
- Audit : événement applicatif `organization.OrganizationChangeEvent`
  (module racine) → `audit/internal.OrganizationAuditListener`
  (catégorie ORGANIZATION, transaction REQUIRES_NEW), actions
  SITE_/BUILDING_/ROOM_/SITE_NETWORK_RANGE_ + CREATED/UPDATED/ARCHIVED/
  RESTORED/ACTIVATED/DEACTIVATED, motif non sensible (code, cidr), jamais
  de donnée personnelle ni d'IP.
- Port public minimal ajouté au module `identity` :
  `identity.CurrentUserResolver` (résout l'id interne depuis le subject
  public du JWT) ; implémentation `DefaultCurrentUserResolver` confinée à
  `identity.internal`. N'expose ni UserAccount, ni repository, ni autre
  classe interne. `ModularityTests` reste vert.
- Aucun site fictif ni donnée métier insérés en V4.

Administration minimale des comptes et des rôles (fusionnée sur main via
PR #6) — n'avait ajouté aucune migration (colonnes suspended_*/archived_at/
user_role.* déjà présentes en V1) :
- GET /api/v1/users (ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION) : liste
  paginée (taille max 100, défaut 20), filtres status / role (affectation
  active) / q (email+prénom+nom, normalisé, borné à 100 car., LIKE
  échappé), tri restreint à createdAt/lastLoginAt/email/lastName ;
- GET /api/v1/users/{public_id} : détail + historique complet des rôles ;
- POST …/{public_id}/suspend | /restore (ACTIVE↔SUSPENDED, motif
  obligatoire) — SCHOOL_ADMINISTRATION autorisé ;
- POST …/{public_id}/archive (ADMIN/SUPER_ADMIN) : statut ARCHIVED,
  clôture de tous les user_role actifs dans la même transaction
  (historique conservé), irréversible dans ce lot ;
- POST …/{public_id}/roles et …/roles/{roleCode}/revoke
  (ADMIN/SUPER_ADMIN) : attribution = nouvelle ligne user_role ; retrait =
  clôture (active=false, valid_until), jamais de suppression ; retrait du
  dernier rôle actif refusé.
DTO exposant uniquement public_id (jamais id SQL, password_hash, jeton).
Contrôles sensibles doublés dans le service (au-delà de @PreAuthorize) :
un compte ou le rôle SUPER_ADMIN n'est administrable que par un
SUPER_ADMIN ; auto-suspension / auto-archivage / retrait de son propre
rôle interdits. Audit ACCOUNT_SUSPENDED / ACCOUNT_REACTIVATED /
ACCOUNT_ARCHIVED / ROLE_ASSIGNED / ROLE_REVOKED (module audit, motif
seul, sans donnée sensible). PEDAGOGICAL_MANAGER exclu tant que le
périmètre pédagogique n'existe pas. Aucune suppression physique. Toujours
aucun MFA, WebAuthn, refresh token ni réinitialisation de mot de passe.

Flux d'invitation et d'activation de compte (fusionné sur main via PR #4) :
- POST /api/v1/account-invitations (protégé ADMIN/SUPER_ADMIN/
  PEDAGOGICAL_MANAGER/SCHOOL_ADMINISTRATION) : émission réservée aux
  comptes PENDING_ACTIVATION, attribution du rôle demandé via user_role,
  jeton SecureRandom 32 octets Base64URL, empreinte SHA-256 seule stockée,
  TTL configurable (défaut P30D, strictement positif), révocation des
  invitations PENDING antérieures, email d'activation via Mailpit ;
- GET /api/v1/account-invitations/validate (public) : réponse générique
  {"valid": bool} — aucune donnée personnelle, réponse identique pour
  jeton inconnu/expiré/révoqué/accepté ;
- POST /api/v1/account-invitations/activate (public) : mot de passe encodé
  (BCrypt), statut ACTIVE, email_verified_at, invitation ACCEPTED à usage
  unique.
Audit ACCOUNT_INVITATION_ISSUED / ACCOUNT_ACTIVATED (module audit, sans
jeton).

Dette technique : l'email d'activation est envoyé de façon synchrone
après commit ; en cas d'échec l'invitation est conservée et seule une
erreur technique est journalisée (ni jeton, ni email, ni lien). Il
n'existe pas encore de file persistante ni de reprise garantie
(docs/03-architecture.md §18, cahier §23.3).
```

## Documents

| Document | Statut |
|---|---|
| Cadrage | CONÇU |
| Cahier des charges | CONÇU |
| Architecture | CONÇU |
| Modèle de données | CONÇU |
| Product Backlog | CONÇU |
| Roadmap | CONÇU |
| Sprint Backlog | CONÇU |
| Diagrammes | CONÇU |
| Risques | CONÇU |
| Sécurité/RGPD | CONÇU |
| Tests/recette | CONÇU |
| Matrice RNCP | CONÇU |
| Journal IA | INITIALISÉ |

## Implémentation

| Fonctionnalité | Statut |
|---|---|
| Dépôt Git | INITIALISÉ (`main`, remote `origin` GitHub) |
| Docker Compose | TESTED |
| Spring Boot | TESTED (socle : démarrage du contexte, `mvn test` exécuté avec succès — aucune route ni entité métier) |
| Angular | IMPLEMENTED (socle `frontend/` fusionné via PR #11 = `6fa341f` ; activation de compte via PR #12 = `2ff7aa8` ; sélecteur de contexte de rôle (docs/02 §6.1, EF-AUTH-003) via PR #13 = `810c8a2` ; espace Apprenants via PR #14 = `1678399` ; consultation des référentiels académiques (lecture seule) via PR #15 = `b47cfa3` ; administration des comptes utilisateurs (lecture seule) via PR #16 = `5d5e51d` ; gestion de l'alternance via PR #18 = `a79b5bf` ; **parcours d'écriture de l'administration des comptes (suspension / réactivation / archivage / attribution / retrait de rôle) sur branche `feature/frontend-user-administration-write`, PR ouverte non fusionnée** — Angular 21.2 (framework/CLI 21.2.22, Material/CDK 21.2.14) / Node 24, zoneless, standalone, Angular Material ; routes `/login`, `/activation` (publique, sans garde), `/dashboard`, **`/administration` (placeholder REMPLACÉ par un écran réel : parent gardé `roleGuard`+`canActivateChild` sur `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION` — `UserAccountController.READ_ROLES` ; `''` → `UserList`, `:publicId` → `UserDetail`)**, `/students` (parent gardé `EnrollmentWeb.MANAGE_ROLES` → `StudentList`, `StudentProfile`), `/academic` (parent gardé `AcademicWeb.READ_ROLES` → `AcademicReferenceList`/`AcademicReferenceDetail`, `data.resource`), `/forbidden`, `**` ; `authGuard` / `guestGuard` / `roleGuard` ; intercepteurs jeton porteur + erreurs (endpoints publics d'activation exclus) ; jeton d'accès et contexte de rôle **en mémoire uniquement** (docs/07 §6, RG-085), aucun `localStorage` / `sessionStorage` ; jeton d'invitation lu depuis `?token=` puis retiré de l'URL ; activation `POST …/activate` → `204`, aucune connexion automatique ; tableau de bord = état de session **local** ; `RoleContextService` + `app-role-context-menu` visible seulement si ≥ 2 rôles ; espace Apprenants : `StudentsApiService` (lecture seule) consommant `GET /api/v1/student-profiles`·`/{id}`, `GET /api/v1/enrollments?student={id}`, `GET /api/v1/users/{id}` ; référentiels académiques : `AcademicApiService` (lecture seule, 10 GET) ; **administration des comptes : `AdministrationApiService` (lecture seule, 2 GET) consommant `GET /api/v1/users` (recherche `q` = email ou prénom ou nom, filtres `status` (`AccountStatus`) + `role` (affectation active, `RoleCode`), tri liste blanche `createdAt`/`lastLoginAt`/`email`/`lastName` — repli silencieux sur le défaut —, pagination ≤ 100, strictement l'API) et `GET /api/v1/users/{publicId}` (fiche + historique complet des rôles actifs et clôturés) ; `UserList` + `UserDetail` ; états chargement / vide / erreur+Réessayer / accès refusé (403 API) / introuvable (404) ; `mat-table` + `mat-sort` (liste blanche) + `mat-paginator` francisé ; aucun endpoint ni champ inventé ; aucun `id` SQL / hash / jeton / trace affiché, `5xx` masqués par `normalizeHttpError`. **Parcours d'écriture (branche `feature/frontend-user-administration-write`, non fusionnée)** : `AdministrationApiService` gagne `suspendUser` / `restoreUser` / `archiveUser` / `assignRole` / `revokeRole` (une méthode par `POST` réel, corps exact `{ reason }` ou `{ role, reason }`, `encodeURIComponent` sur `publicId` et `roleCode`, `204`) ; `UserDetail` gagne une section « Actions sur le compte » (Suspendre `ACTIVE` / Réactiver `SUSPENDED` / Archiver / Attribuer un rôle) et un bouton « Retirer » sur chaque affectation active — confirmations **en ligne**, motif obligatoire (`maxlength=500` + compteur pour suspension / réactivation / archivage / retrait ; **sans borne** pour l'attribution — `AssignRoleRequest.reason` = `@NotBlank` seul, un motif > 500 caractères part intégralement), avertissement de clôture des rôles à l'archivage, `disabled` pendant l'appel, double soumission bloquée, `NotificationService.info` puis rechargement `GET /api/v1/users/{publicId}`, échec métier affiché en ligne sans faux succès ; visibilité pilotée par `RoleContextService.effectiveRoles()` (restreint, jamais n'élargit le JWT) + masquage des auto-actions si `subject` JWT = cible (sauf attribution, non interdite côté back-end) ; **cible portant `SUPER_ADMIN` actif : hors contexte `SUPER_ADMIN`, toutes les mutations sont masquées** (note « requiert le rôle super administrateur », non présentée comme une garantie ; lecture inchangée ; `SUPER_ADMIN` → `ADMIN` ferme un formulaire ouvert) ; `ARCHIVED` = état terminal (note, aucune action) ; `SUPER_ADMIN` proposé/révocable seulement en contexte `SUPER_ADMIN` ; `effect()` fermant un panneau devenu indisponible ; `administration-errors.ts` (`toAdministrationError`) — **liste blanche explicite** de codes (pas de `startsWith('USER_')`) : `USER_NOT_FOUND` / `USER_INVALID_STATE` / `USER_ROLE_ALREADY_ASSIGNED` / `USER_ROLE_NOT_ASSIGNED` / `USER_LAST_ACTIVE_ROLE` / `USER_SELF_ACTION_FORBIDDEN` / `USER_SUPER_ADMIN_PROTECTED` / `USER_OPERATION_FORBIDDEN` / `USER_ROLE_UNKNOWN` (→ champ rôle, erreur `FormControl` reliée au `mat-select` par `aria-describedby`) / `USER_INVALID_SORT` / `USER_INVALID_FILTER` ; tout autre code (y compris un `USER_*` non listé) et tout `5xx` → `code`/`field` `null`, message générique, message brut jamais affiché ; JWT et contexte en mémoire seule, rien en `localStorage` / `sessionStorage`** ; **gestion de l'alternance (`/alternation`) via PR #18 = `a79b5bf` — première tranche front-end avec écriture : parent gardé `roleGuard` sur `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION`/`PEDAGOGICAL_MANAGER` (`AlternationWeb` lecture), garde d'écriture supplémentaire `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION` sur `patterns/new` et `patterns/:publicId/edit` ; `AlternationApiService` (une méthode par endpoint réel des modèles de rythme, affectations de classe et exceptions individuelles) ; `PatternList`/`PatternForm` (création + édition, `code`/`type` figés en édition, `configuration` assemblée localement par type via `pattern-config.ts` — `companyDays` explicite même vide pour `CUSTOM` — validation finale serveur `ALT_INVALID_CONFIGURATION`)/`PatternDetail` (faits + `app-cycle-preview` accessible représentant la config, jamais une résolution de date + archiver/restaurer avec confirmation en ligne) ; `ClassPicker`/`ClassAlternation` (historique des affectations, affectation, clôture avec motif, sonde `GET .../classes/{id}/context` affichée telle quelle) ; `EnrollmentPicker`/`EnrollmentAlternation` (exceptions, création avec encodage heure locale + fuseau IANA → instant via `Intl` sans repli UTC ni conversion de fuseau, sémantique `[startAt, endAt)` affichée, annulation, sonde `GET .../enrollments/{id}/context`) ; limite back-end : `GET /api/v1/enrollments` fermé au `PEDAGOGICAL_MANAGER` → `EnrollmentPicker` propose une saisie directe d'identifiant en repli ; nav item « Alternance » (`sync_alt`) ; aucune écriture ni endpoint inventé ; 403 `ALT_FORBIDDEN` rendu « accès refusé »** ; **le parcours d'écriture de l'administration des comptes est désormais FUSIONNÉ sur `main` via la PR #19 (`317753a`) — l'administration front-end n'est plus en lecture seule** ; **séances & émargement (fusionné sur `main` via la PR #20 = `5874f5a`) : espace `/sessions` (parent gardé `roleGuard` READ `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION`/`PEDAGOGICAL_MANAGER`/`TEACHER` ; `/sessions/new` gardé CREATE `ADMIN`/`SUPER_ADMIN`/`PEDAGOGICAL_MANAGER`) → `SessionList` / `SessionForm` / `SessionDetail` (ouverture/fermeture en confirmation en ligne ; panneau QR — `QrDisplay` (`angularx-qrcode@21.0.5`) encode la seule chaîne opaque, jamais affichée en texte ; code court affiché ; jeton renouvelé ~3 s avant expiration ; présences avec rafraîchissement manuel + polling modéré 15 s ; renouvellement + QR arrêtés à la destruction / fermeture / perte du droit de gestion dans le contexte de rôle actif, polling arrêté dès que le contexte actif ne permet plus la lecture, émission de jeton tardive ignorée si l'état a changé ; `SessionList` masque « Nouvelle séance » sur le contexte actif ; `SessionForm` neutralisé sur perte de permission (réponse tardive ignorée) ; Redis 503 → message contrôlé) ; `/attendance` gardé `STUDENT` → `AttendanceCheckIn` (saisie du code court normalisée comme le serveur, erreurs `ATT_*` contrôlées, code inconnu / 5xx → message générique, rien en URL ni en storage, note « scan caméra ajouté ultérieurement » ; saisie/soumission uniquement en contexte `STUDENT` effectif, perte du contexte efface code/récépissé/erreurs et bloque les requêtes, retour au contexte sans rechargement) ; `SessionsApiService` (une méthode par endpoint réel, jeton jamais dans une URL) ; nav items « Séances » et « Émargement » ; origine `main` 336 → 416 tests Vitest** ; `npm test -- --watch=false` / `npm run build` (< seuil 500 kB) / `npm run lint` verts en local le 30 août 2026. Non démontré de bout en bout automatiquement avec le back-end en marche (parcours API vérifié en direct, cf. « Démonstration locale ») ; pas de restauration de session au rechargement) |
| MySQL | TESTED (healthy, auth root et `esic_app` vérifiée) |
| Redis | TESTED (healthy, auth vérifiée). **Avant cette PR : infrastructure présente, non consommée par le back-end. Après : consommé par le module `attendance` pour les jetons d'émargement uniquement** (jeton opaque + code court, TTL `app.attendance.token-ttl` défaut `PT30S`, rotation, purge à la fermeture ; `StringRedisTemplate` ; Redis indisponible → `503 ATT_TOKEN_BACKEND_UNAVAILABLE`, jamais de validation dégradée). `AttendanceTokenServiceTests` (Redis mocké), `AttendanceIntegrationTests`, démonstration locale (503 en pausant le conteneur) |
| Flyway | TESTED (V1 tables identité/audit, V2 seed des 6 rôles, V3 table `account_invitation`, V4 tables `site`/`building`/`room`/`site_network_range`, V5 tables `academic_year`/`program`/`program_level`/`promotion`/`class_group`, V6 table `pedagogical_assignment`, V7 tables `student_profile`/`enrollment`, V8 tables `work_study_pattern`/`class_work_study_pattern`/`student_schedule_exception`, V9 tables `course_session`/`session_class`/`attendance_checkpoint`/`attendance_record`, **V10 (branche `feature/attendance-management-and-reporting`, non fusionnée) : enrichissement `attendance_checkpoint` (N points de contrôle par séance) et `attendance_record` (statut métier, retard, commentaire, acteurs), nouvelles tables append-only `attendance_correction` et `attendance_justification`**, **V11 (branche `feature/student-csv-import-cp1`, non poussée) : tables techniques d'import `student_import_job` / `student_import_job_issue` / `student_import_row` / `student_import_row_issue` (chaîne `ON DELETE CASCADE`, FK `RESTRICT` vers `user_account`, `CHECK` de statut / gravité / action) + séquence `student_number_sequence` (`start_year` PK, `CHECK next_value > 0`) ; additive, aucune donnée métier** — migrations appliquées et vérifiées, schéma en version 11) |
| Authentification | TESTED (`POST /api/v1/auth/login` : email/mot de passe, JWT HS256 stateless, `last_login_at`, audit succès/échec ; réponse publique uniforme vérifiée pour email inconnu/mauvais mot de passe/compte non actif ; routes protégées refusent sans jeton ; MFA/WebAuthn/refresh token non implémentés) |
| Rôles | TESTED (persistance `role`/`user_role` : 6 rôles système, unicité d'affectation active, réattribution après clôture ; attribués via `user_role` à l'émission d'une invitation ; API d'attribution / retrait dédiée — voir « Gestion des comptes / rôles ») |
| Gestion des comptes / rôles | TESTED (`GET /api/v1/users` paginé/filtré/trié, `GET /api/v1/users/{public_id}`, `POST …/{public_id}/suspend`·`/restore`·`/archive`·`/roles`·`/roles/{roleCode}/revoke` ; `@PreAuthorize` + contrôles sensibles dans `UserManagementService` (protection SUPER_ADMIN, auto-action interdite, dernier rôle actif protégé) ; archivage = clôture transactionnelle des rôles actifs, ARCHIVED irréversible ; DTO sans id SQL / `password_hash` / jeton ; audit `ACCOUNT_SUSPENDED`/`ACCOUNT_REACTIVATED`/`ACCOUNT_ARCHIVED`/`ROLE_ASSIGNED`/`ROLE_REVOKED` ; aucune migration V4 ; `PEDAGOGICAL_MANAGER` exclu jusqu'au périmètre pédagogique) |
| Invitation / activation | TESTED (`POST /api/v1/account-invitations` protégé par rôle, `GET …/validate` et `POST …/activate` publics ; migration V3 `account_invitation` ; jeton SecureRandom 32 o Base64URL, empreinte SHA-256 unique stockée, TTL configurable strictement positif, révocation des invitations PENDING antérieures, jeton à usage unique ; validation publique strictement générique ; email d'activation via Mailpit ; audit `ACCOUNT_INVITATION_ISSUED`/`ACCOUNT_ACTIVATED` sans jeton) |
| Notification (email) | TESTED (module `notification` : écouteur `AFTER_COMMIT` sur `AccountInvitationIssuedEvent`, envoi SMTP `SimpleMailMessage` via Mailpit ; échec d'envoi avalé, invitation conservée, log sans jeton/email/lien ; pas de file persistante — dette technique) |
| Périmètre pédagogique (pedagogical_assignment) | TESTED (module `academic`, migration V6 réécrite ; entité `PedagogicalAssignment` reliant un responsable (`manager_user_id`) + `delegated_by_id` — valeurs techniques via port `identity.UserDirectory` — à une formation ; rôles PRIMARY_MANAGER/DELEGATE, statut ACTIVE/CLOSED ; validité en `LocalDate`/`DATE` bornes inclusives, colonnes `reason`/`close_reason` ; un seul PRIMARY_MANAGER actif par formation (colonne générée `active_primary_key` + pré-contrôle 409 + collision de contrainte retraduite en 409, jamais 500), DELEGATE multiples ; `CHECK (valid_until IS NULL OR valid_until >= valid_from)`, créneau libéré uniquement par clôture explicite ; cible = compte existant, non archivé, rôle actif PEDAGOGICAL_MANAGER sinon 422 `ACAD_TARGET_NOT_ELIGIBLE` ; routes `/api/v1/pedagogical-assignments` GET liste (filtres `program`/`user`/`type`/`status`/`activeOn` inclusif, tri liste blanche stricte `validFrom`/`validUntil`/`createdAt`) + GET détail + POST création + POST `{id}/close` (`reason` obligatoire, `effectiveDate` défaut aujourd'hui, `>= validFrom` sinon 400 `ACAD_ASSIGNMENT_DATE_INVALID`, persiste `validUntil`), réservées ADMIN/SUPER_ADMIN, aucun PATCH/DELETE/route nichée ; contrôle de périmètre centralisé (`AcademicScopeGuard`) sur formation + niveau + promotion + classe : listes filtrées, détail/écriture hors périmètre → 403 `ACAD_FORBIDDEN` ; accès global = `ROLE_ADMIN`/`ROLE_SUPER_ADMIN`/`ROLE_SCHOOL_ADMINISTRATION` (déduit des autorités Spring Security), écriture ouverte au PEDAGOGICAL_MANAGER dans son périmètre via `SCOPED_WRITE_ROLES`, création de formation toujours ADMIN/SUPER_ADMIN, AcademicYear inchangé ; DTO sans id SQL ; audit `PEDAGOGICAL_ASSIGNMENT_CREATED`/`_CLOSED` catégorie ACADEMIC). |
| Référentiels pédagogiques (formation/niveau/année/promotion/classe) | TESTED (module `academic`, migration V5 ; CRUD + archivage/restauration des 5 entités, aucun DELETE physique ; hiérarchie formation → promotion → classe/groupe ; routes en public_id sous `/api/v1/academic-years`, `/api/v1/programs`, `/api/v1/programs/{id}/levels` + `/api/v1/program-levels/{id}`, `/api/v1/promotions`, `/api/v1/class-groups` ; pagination max 100 + tri liste blanche ; unicités academic_year.code / program.code / (program,code) / (program,academicYear,code) / (promotion,code) ; période année (end>start), période promotion incluse dans l'année, program_level d'une classe = même formation que sa promotion, refus parent archivé, archivage bloqué si enfants actifs, code + rattachements immuables ; `class_group.site_id` = valeur technique via port public `organization.SiteDirectory` (aucun import de `organization.internal`, aucune relation JPA inter-module) ; `@PreAuthorize` lecture ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION/PEDAGOGICAL_MANAGER, écriture ADMIN/SUPER_ADMIN ; DTO sans id SQL ; audit `ACADEMIC_YEAR_*`/`PROGRAM_*`/`PROGRAM_LEVEL_*`/`PROMOTION_*`/`CLASS_GROUP_*` catégorie ACADEMIC). Inscriptions, apprenants, formateurs, matières : hors périmètre de ce lot. |
| Référentiel organisationnel (site/bâtiment/salle/plage réseau) | TESTED (module `organization`, migration V4 ; CRUD + archivage/restauration site·bâtiment·salle, création + activation/désactivation plages réseau, aucun DELETE physique ; routes en public_id sous `/api/v1/sites`, `/api/v1/buildings/{id}`, `/api/v1/rooms/{id}`, `/api/v1/network-ranges/{id}` ; pagination max 100 + tri liste blanche ; unicités site.code / (site,code) / (site,cidr) active ; refus parent archivé, room.site=building.site, archivage bloqué si enfants actifs, code immuable ; ZoneId + ISO 3166-1 + CIDR IPv4/IPv6 validés ; `@PreAuthorize` lecture ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION/PEDAGOGICAL_MANAGER, écriture ADMIN/SUPER_ADMIN, plages réseau SUPER_ADMIN pour toute opération ; DTO sans id SQL ; audit `SITE_*`/`BUILDING_*`/`ROOM_*`/`SITE_NETWORK_RANGE_*` catégorie ORGANIZATION ; port public `identity.CurrentUserResolver` pour l'auteur des écritures) |
| Inscriptions historiques (student_profile / enrollment) | TESTED (module `enrollment`, migration V7 ; `StudentProfile` (`user_id` valeur technique via `identity.UserDirectory`, unique ; `student_number` unique ; statut ACTIVE/ARCHIVED) et `Enrollment` (rattachements `class_group_id`/`academic_year_id` = valeurs techniques via nouveau port `academic.ClassGroupDirectory` ; `previous_enrollment_id` auto-référence ; `enrollment_source` MANUAL/CLASS_TRANSFER ; statuts docs/04 §13.1) ; **au plus une inscription ACTIVE par apprenant et par année scolaire** (RG-012 / docs/04 §13.3) : pré-contrôle applicatif + contrainte SQL `uq_enrollment_active_per_year` (colonnes générées) + isolation de la collision concurrente (bean `EnrollmentPersister` `@Transactional(REQUIRES_NEW)` pour `create`/`enroll` — retraduction hors transaction en échec ; `EnrollmentExceptionHandler` pour `transfer` — dont l'INSERT doit voir la clôture dans la même transaction), retraduite en 409 ciblé, jamais 500 ; changement de classe `POST …/{id}/transfer` clôturant l'ancienne inscription en TRANSFERRED (`end_date` inclusif, historique conservé — AC-006) et créant la nouvelle liée débutant `end_date` + 1 jour (aucun chevauchement de période) ; clôture `POST …/{id}/close` (COMPLETED/WITHDRAWN, motif obligatoire) ; `CHECK (end_date IS NULL OR end_date >= start_date)` ; routes en public_id sous `/api/v1/student-profiles` et `/api/v1/enrollments` (GET liste filtres + tri liste blanche + pagination max 100, GET détail, POST) réservées ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION (PEDAGOGICAL_MANAGER exclu tant qu'un port de périmètre pédagogique public n'existe pas ; TEACHER/STUDENT sans accès), aucun PATCH/DELETE/route nichée ; horloge `java.time.Clock` injectée ; DTO sans id SQL ; audit `STUDENT_PROFILE_CREATED`/`ENROLLMENT_CREATED`/`_TRANSFERRED`/`_CLOSED` catégorie `ENROLLMENT`. Import CSV, rythmes d'alternance, apprenants provisoires : hors périmètre de ce lot. |
| Rythmes d'alternance (work_study_pattern / class_work_study_pattern / student_schedule_exception) | TESTED (module `alternation`, migration V8 ; modèles réutilisables de rythme — 4 `pattern_type`, `configuration_json` validé + canonicalisé par `AlternationConfigParser` (composant pur ; propriété inconnue / jour inconnu / intersection école-entreprise / nombre de semaines incohérent / index hors cycle → 400 `ALT_INVALID_CONFIGURATION`), round-trip canonique `parseCanonical(canonicalize(parse(...)))` corrigé et testé pour les 4 types (tolère les tableaux de jours vides que `canonicalize` produit, reste strict : 5 clés obligatoires, aucune propriété inconnue, index de semaine et intersections contrôlés) ; CRUD + archivage/restauration, `code` et `pattern_type` immuables ; affectation historisée à une classe (`class_group_id` valeur technique via `academic.ClassGroupDirectory`, `cycle_start_date` porté par l'affectation), `CHECK (valid_until IS NULL OR >= valid_from)`, non-chevauchement des périodes ACTIVE — adjacence stricte autorisée, pré-contrôle applicatif (course résiduelle sur périodes bornées documentée) —, unicité SQL de l'affectation ACTIVE « ouverte » par classe (`active_open_key` généré) + collision concurrente retraduite en 409 `ALT_OPEN_ASSIGNMENT_EXISTS` par `ClassAssignmentPersister` (`REQUIRES_NEW`), jamais 500 (deux créations HTTP simultanées → 1×201 / 1×409 / 0×500 / une seule ligne ACTIVE ouverte, vérifié) ; clôture explicite bornée (`effectiveDate >= valid_from`, `<= valid_until` s'il est fixé sinon 400 `ALT_INVALID_PERIOD` ; `< next.validFrom` de l'affectation suivante sinon 409 `ALT_ASSIGNMENT_CLOSE_CONFLICT` via requête repository déterministe), historique conservé ; exceptions individuelles (`enrollment_id` valeur technique via **nouveau port** `enrollment.EnrollmentDirectory`) — 4 `exception_type`, `ACTIVE`/`CANCELLED`, `CHECK (end_at > start_at)`, `time_zone_id` IANA validé, `reason` obligatoire ; chevauchement de **même type** refusé (pré-contrôle applicatif seul — deux exceptions concurrentes de même type peuvent encore être persistées, limite documentée) ; projection d'une exception sur un jour civil en sémantique demi-ouverte `[startAt, endAt)` par intersection d'intervalles dans le fuseau de l'exception (minuit exact et changement d'heure Europe/Paris gérés ; fuseau persisté invalide → erreur interne explicite, plus de repli UTC) ; résolution `SCHOOL`/`COMPANY`/`UNKNOWN` par classe et par inscription (`AlternationResolver`, service pur, déterministe — date < ancre / week-end / semaine non classifiée / absence d'affectation → `UNKNOWN`), résolution effective = priorité **structurelle** d'une exception `ON_SITE_REQUIRED`→`SCHOOL` / `COMPANY_PERIOD`→`COMPANY`, **aucun calcul d'assiduité** ; routes `/api/v1/alternation/...` (patterns, class-assignments, classes/{id}/assignments+context, student-exceptions, enrollments/{id}/exceptions+context), tri liste blanche → 400 `ALT_INVALID_SORT`, pagination ≤ 100, DTO sans id SQL, `ApiError` codes `ALT_*` ; `@PreAuthorize` — modèles : lecture ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION/PEDAGOGICAL_MANAGER, écriture ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION ; affectations + exceptions : + PEDAGOGICAL_MANAGER limité à son périmètre via **nouveau port** `academic.AcademicScopeDirectory` (hors périmètre → 403 `ALT_FORBIDDEN`) ; TEACHER/STUDENT → 403 ; audit `WORK_STUDY_PATTERN_*` / `CLASS_WORK_STUDY_PATTERN_ASSIGNED`/`_CLOSED` / `STUDENT_SCHEDULE_EXCEPTION_CREATED`/`_CANCELLED` catégorie `ALTERNATION` via `alternation.AlternationChangeEvent` → `audit.internal.AlternationAuditListener`. Exceptions collectives, `planning`/`coursesession`/`attendance`, calcul d'assiduité, frontend : hors périmètre de ce lot. Aucun seed métier en V8.) |
| Import apprenants | IN_PROGRESS — CP1 sur `feature/student-csv-import-cp1` (non poussée). Conception CP0 figée (`docs/reports/STUDENT_CSV_IMPORT_DESIGN.md` R2, fusionnée PR #23 = `e8fd16d`). **CP1 = schéma V11 uniquement** : migration `V11__create_student_import_tables.sql` (tables `student_import_job` / `_job_issue` / `_row` / `_row_issue` + `student_number_sequence` ; chaîne `ON DELETE CASCADE` ; FK `RESTRICT` vers `user_account` ; `CHECK` statut / gravité / action / `file_size_bytes > 0` / `next_value > 0` ; `UNIQUE (student_import_job_id, `row_number`)` ; index purge / requêteur), module Spring Modulith `studentimport` (`package-info` `@ApplicationModule`, entités JPA `studentimport.internal` héritant `shared.BaseEntity`, enums, repositories `JpaRepository` en lecture simple), `StudentImportSchemaConstraintsTests` (`@DataJpaTest` MySQL réel, **19 tests, 0 échec**). `ModularityTests` vert ; suite `./mvnw clean test` → **567 tests** (+19), schéma en version 11 ; 7 échecs `AttendanceIntegrationTests` **pré-existants** (reproduits arbre ramené à `e8fd16d`), hors périmètre CP1. **Non implémenté** (CP2+) : parsing CSV, simulation, confirmation transactionnelle, ports `identity.StudentAccountProvisioner` / `enrollment.StudentEnrollmentProvisioner` / `academic.ClassGroupDirectory.resolveForImport`, génération fonctionnelle de `student_number`, endpoints REST, écrans Angular. |
| Import planning | TODO |
| Séances | IMPLEMENTED et TESTED — fusionné sur `main` via la PR #20 (`5874f5a`) (module `coursesession`, V9 ; séance **exceptionnelle** créée manuellement, motif obligatoire, formateur = compte `TEACHER` actif via port `identity.TeacherDirectory`, ≥ 1 classe ; cycle strict `PLANNED → OPEN → CLOSED` sans réouverture ; API `/api/v1/sessions` liste filtrée par périmètre + `/teachers` + détail + création + `/open` + `/close` ; contrôle fin `CourseSessionAccessGuard` (contexte Spring Security) : `ADMIN`/`SUPER_ADMIN` global, `SCHOOL_ADMINISTRATION` lecture seule, `PEDAGOGICAL_MANAGER` limité au périmètre, `TEACHER` seulement ses séances, `STUDENT` aucun accès ; audit `SESSION_CREATED`/`_OPENED`/`_CLOSED` ; port public `coursesession.CourseSessionDirectory`. `CourseSessionConstraintsTests` (7), `CourseSessionIntegrationTests` (6). Un seul point de contrôle par séance ; planning non livré) **V10 (branche non fusionnée)** : plusieurs points de contrôle par séance (`AttendanceCheckpointService` + `AttendanceCheckpointController`, cycle `PLANNED → OPEN → CLOSED`/`CANCELLED`, ordre d'affichage unique, ≤ 1 `START` / ≤ 1 `END` actif, motif obligatoire à l'annulation ; `SCHOOL_ADMINISTRATION` exclu de la gestion) ; ouverture de séance ouvre le `START`, fermeture ferme tous les points de contrôle ouverts ; événement `AttendanceCheckpointChangeEvent` audité (`CHECKPOINT_CREATED`/`_OPENED`/`_CLOSED`/`_CANCELLED`) ; `CourseSessionConstraintsTests` 7→10, `CourseSessionIntegrationTests` 6→9→10 (**PR #22** : `flushCheckpoint()` retraduit une transition concurrente perdante en `409 ATT_CHECKPOINT_INVALID_STATE`, jamais 500 ; test ouverture/fermeture concurrentes d'un point de contrôle). |
| Émargement | IMPLEMENTED, TESTED et DÉMONTRÉ localement (API) — fusionné sur `main` via la PR #20 (`5874f5a`), validation manuelle locale du parcours fonctionnel frontend et API, sous le profil `demo`, réussie (connexion formateur et apprenants, ouverture, QR / code court, enregistrement des deux présences, anti-double présence, consultation du tableau, fermeture et refus de l'ancien code ; changements de contexte de rôle non applicables manuellement — comptes de démonstration mono-rôle —, couverts par les tests automatisés) (module `attendance`, V9 ; jeton dynamique **opaque** `SecureRandom` + **code court** dans **Redis** avec TTL court, rotation, purge à la fermeture ; QR encodant uniquement le jeton opaque ; `POST /api/v1/sessions/{id}/attendance-token` (formateur/gestionnaire, séance `OPEN`) ; `POST /api/v1/attendance/validate` (**`STUDENT` uniquement** ; apprenant résolu depuis le seul JWT ; inscription `ACTIVE` dans une classe de la séance, 0 ou >1 → refus) ; **anti-double présence par contrainte SQL `uq_attendance_record_checkpoint_enrollment`** (violation concurrente → `409 ATT_ALREADY_RECORDED`, jamais 500) ; `GET /api/v1/sessions/{id}/attendance` (effectif attendu + présents + lignes sans email ni id SQL) ; Redis KO → `503 ATT_TOKEN_BACKEND_UNAVAILABLE` ; audit `ATTENDANCE_RECORDED` sans jeton/numéro/nom. **Revue PR #20** : `resolveSession` applique l'invariant du pointeur courant (`session -> token\ncode`) — une clé `token -> session` résiduelle n'est plus acceptée après rotation ou invalidation. `AttendanceRecordConstraintsTests` (4), `AttendanceTokenServiceTests` (18), `AttendanceIntegrationTests` (7 dont concurrence), `AttendanceSecurityTests` (4). **Scan caméra NON RÉALISÉ** ; parcours fiable = code court ; pas de présence manuelle, correction, justificatif, demi-journée, export) **V10 (branche non fusionnée)** : jeton émis **par point de contrôle** ; `validate` classe la présence `PRESENT`/`LATE` selon `app.attendance.late-threshold` (défaut `PT10M`) ; présence manuelle / correction / annulation logique avec historique append-only (`attendance_correction`, motif obligatoire, verrou optimiste → 409) ; justificatif métier SANS fichier (`attendance_justification`, dépôt / modif `PENDING` / examen ; `ACCEPTED` → `ABSENT → EXCUSED_ABSENCE`) ; espace apprenant `GET /api/v1/me/attendance*` (absences dérivées non persistées) ; `AttendanceIntegrationTests` (25), `AttendanceSecurityTests` (8), `AttendanceTokenServiceTests` (19), `AttendanceRecordConstraintsTests` (7), `CourseSessionIntegrationTests` (10), `AttendanceManagementConstraintsTests` (9), `AttendanceReportSortTests` (4). **Correctifs PR #22** : `GET /api/v1/sessions/{id}/attendance/candidates` (candidats à la saisie manuelle : inscriptions actives des classes de la séance, dédupliquées, sans e-mail ni id SQL, contrôle fin = lecture des présences ; §2) ; `GET /api/v1/sessions/{id}/attendance/export` (CSV borné à la séance, formateur affecté autorisé, protections CSV, nom de fichier contrôlé ; §8) ; tests de concurrence déterministes (QR/code vs présence manuelle, deux corrections, deux examens de justificatif, deux créations manuelles → une écriture / un conflit contrôlé / aucun 500 ; §3). **2ᵉ passe PR #22** : éligibilité des candidats et validation manuelle bornées à l'inscription valable **le jour de la séance** (`EnrollmentDirectory.findRosterForClassesOn` / `isEnrollmentValidOn`, date locale = `startsAt` + fuseau persisté, aucun repli UTC) ; matrice de sécurité du endpoint des candidats sur fixtures réelles (ADMIN/`SCHOOL_ADMINISTRATION` 200, `PEDAGOGICAL_MANAGER` in/out périmètre 200/403, `TEACHER` affecté/non 200/403, `STUDENT` 403, anonyme 401) ; export CSV de séance **sans** colonne libre `commentaire`. |
| Rapports | IMPLEMENTED et TESTED — branche `feature/attendance-management-and-reporting` (V10), NON fusionnée (module `attendance` : `AttendanceReportService` + `AttendanceReportController` — `GET /api/v1/attendance/reports/{sessions,classes,students,summary}` JSON paginé + `.../export` `text/csv` ; unité de calcul = demi-journée (docs/02 §24.2), point de contrôle classé matin / après-midi (< 13:00 local), demi-journée présente ⇔ tous ses points de contrôle obligatoires satisfaits, contexte d'alternance `COMPANY` exclu du dénominateur (port `alternation.AlternationDirectory`), `UNKNOWN` non satisfait compté à part jamais en absence, retards talliés à part ; `AttendanceCsvWriter` UTF-8+BOM séparateur `;` RFC 4180 + **neutralisation d'injection de formule** ; rôles `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION` global + `PEDAGOGICAL_MANAGER` périmètre, `TEACHER` exclu ; audit `REPORT_EXPORTED` ; front `/attendance-management`). **Correctifs PR #22** : `safeZone()` → `persistedZone()` (fuseau persisté invalide → erreur interne contrôlée, jamais UTC silencieux ni chiffres trompeurs, §1) ; codes de classe lisibles via `academic.ClassGroupDirectory` (§7) ; paramètre `sort` borné par `AttendanceReportSort` (liste blanche par rapport, tri en mémoire avant pagination + tri secondaire stable, `400 ATT_REPORT_INVALID_SORT` sinon, §6) ; `AttendanceReportSortTests` (4), assertions de rapport / tri / fuseau ajoutées à `AttendanceIntegrationTests`. Rapport journalier / mensuel / annuel structurés du cahier : approche demi-journées livrée, mise en page officielle non implémentée) |
| Audit | TESTED (persistance `audit_event` + écriture depuis flux métier réels : connexion réussie/refusée, émission d'invitation, activation de compte, suspension/réactivation/archivage d'un compte, attribution/retrait d'un rôle, changements du référentiel organisationnel — catégorie `ORGANIZATION` — et changements du référentiel académique — année/formation/niveau/promotion/classe **et affectations de responsable pédagogique (`PEDAGOGICAL_ASSIGNMENT_CREATED`/`_CLOSED`)**, catégorie `ACADEMIC` — **et changements du module inscriptions — `STUDENT_PROFILE_CREATED` / `ENROLLMENT_CREATED` / `_TRANSFERRED` / `_CLOSED`, catégorie `ENROLLMENT`** — **et changements du module alternance — `WORK_STUDY_PATTERN_CREATED` / `_UPDATED` / `_ARCHIVED` / `_RESTORED`, `CLASS_WORK_STUDY_PATTERN_ASSIGNED` / `_CLOSED`, `STUDENT_SCHEDULE_EXCEPTION_CREATED` / `_CANCELLED`, catégorie `ALTERNATION`** — **et changements des séances — `SESSION_CREATED` / `_OPENED` / `_CLOSED`, catégorie `COURSE_SESSION`** — **et émargements — `ATTENDANCE_RECORDED`, catégorie `ATTENDANCE`** — jamais de jeton, de code court, de numéro étudiant, de nom, de donnée sensible ni d'IP ; pour les actions d'administration, le compte/la ressource concernée est portée par `resource_public_id`, l'acteur par `actor_user_id`) |
| FastAPI | TODO |
| MQTT | TODO |
| Raspberry Pi | TODO |
| WebAuthn | TODO |
| CI (GitHub Actions) | IMPLEMENTED (`.github/workflows/backend-ci.yml` : déclenché sur PR vers `main` et push sur `main` ; job unique `ubuntu-latest`, `permissions: contents: read`, `timeout-minutes: 20`, concurrence avec annulation des exécutions obsolètes ; Java 21 Temurin + cache Maven ; services `mysql:8.4` et `redis:7.4-alpine` (mot de passe via `command: redis-server --requirepass`) avec identifiants dédiés CI non sensibles ; exécute `./mvnw --batch-mode test` depuis `backend/` ; aucun usage de `.env`, aucun SMTP réel. Non encore exécuté sur GitHub — statut à confirmer au premier run) |
| Staging | TODO |

## Prochaine priorité

```text
Le PARCOURS D'ÉMARGEMENT DÉMONTRABLE (modules `coursesession` +
`attendance`, V9 ; espace front `/sessions` + `/attendance` ; amorçage
`demo` + `scripts/seed-demo.sh` + `docs/11-guide-demonstration.md`) est
**fusionné sur `main` via la PR #20** (commit de fusion `5874f5a`,
dernier commit fonctionnel `2036277`) ; sa validation manuelle locale du
parcours fonctionnel frontend et API, sous le profil `demo`, est
**réussie** (connexion formateur et apprenants, ouverture, QR / code
court, enregistrement des deux présences, anti-double présence,
consultation du tableau, fermeture et refus de l'ancien code ; les
changements de contexte de rôle, non applicables manuellement avec des
comptes de démonstration mono-rôle, restent couverts par les tests
automatisés). Le parcours d'écriture de
l'administration des comptes est fusionné sur `main` (PR #19, `317753a`).
Prochaines étapes possibles : plusieurs points de contrôle par séance et
calcul de demi-journée ; QR fixe de salle + contrôle réseau ; import CSV
des apprenants ; scan caméra mobile ; module `planning` créant les
séances ; présence manuelle / correction / justificatif ; rapports et
export CSV ; WebAuthn ; migration globale des listeners d'audit vers
`@TransactionalEventListener(AFTER_COMMIT)`.

---
CONTEXTE ANTÉRIEUR :

Les référentiels organisationnel (module `organization`, V4), académique
minimal (module `academic`, V5), le périmètre pédagogique (module
`academic`, V6), les inscriptions historiques (module `enrollment`,
V7 : `student_profile` + `enrollment` + changement de classe conservant
l'historique) et les rythmes d'alternance (module `alternation`, V8 :
`work_study_pattern` + `class_work_study_pattern` +
`student_schedule_exception` + résolution du contexte
SCHOOL/COMPANY/UNKNOWN ; PR #17, commit `60b3cf6`, fusionnée sur `main`)
sont en place. Le socle front-end Angular est fusionné sur
`main` (PR #11, commit `6fa341f`) : connexion + tableau de bord (état de
session local) + gardes de route par rôle. Le parcours public
d'activation de compte (`/activation`,
`GET/POST /api/v1/account-invitations/validate|activate`) est fusionné
sur `main` (PR #12, commit `2ff7aa8`). Le sélecteur de contexte de rôle
(docs/02 §6.1, EF-AUTH-003) est fusionné sur `main` (PR #13, commit
`810c8a2`). L'espace Apprenants front-end (liste `/students` + fiche
`/students/:publicId` + historique d'inscriptions) est fusionné sur
`main` (PR #14, commit `1678399`). La consultation front-end des
référentiels académiques (lecture seule : `/academic` → années scolaires
→ formations → niveaux → promotions → classes) est fusionnée sur `main`
(PR #15, commit `b47cfa3`). L'administration front-end des comptes
utilisateurs en **lecture seule** (liste `/administration` + fiche
`/administration/:publicId` + historique des rôles, consommant
`GET /api/v1/users` et `GET /api/v1/users/{publicId}` ; remplace le
placeholder `/administration`) est fusionnée sur `main` (PR #16, commit
`5d5e51d`). La gestion front-end de l'alternance (`/alternation` :
modèles de rythme avec création / édition / archivage, affectations de
rythme aux classes avec clôture, exceptions individuelles de calendrier
avec annulation, sondes de contexte classe et inscription) est fusionnée
sur `main` (PR #18, commit `a79b5bf`) — elle consomme l'intégralité du
module back-end `alternation` (PR #17). Le **parcours d'écriture** de
l'administration des comptes (suspension / réactivation / archivage ;
attribution / retrait de rôle, confirmations en ligne avec motif
obligatoire) est implémenté sur
`feature/frontend-user-administration-write` (PR ouverte, non fusionnée) :
il consomme les cinq `POST` de `UserAccountController`, dérive la
visibilité des actions de `RoleContextService.effectiveRoles()` sans
jamais élargir le JWT, masque les auto-actions (hors attribution) et
laisse Spring Security appliquer les gardes fines (`SUPER_ADMIN`,
auto-action, dernier rôle actif), rendues en ligne à partir des codes
`USER_*`.
Prochaines étapes :
- front-end : formulaires de création / modification / archivage du
  référentiel académique, lorsque les dépendances manquantes seront
  disponibles (liste des sites pour rattacher une classe, etc.) ;
- import CSV des apprenants (T-J2-001 à 004 / US-050, US-051) : simulation
  puis confirmation, s'appuyant sur `student-profiles` et `enrollments`
  (la colonne `work_study_pattern` du modèle CSV pourra rattacher un
  `work_study_pattern` via son `code`) ;
- exceptions **collectives** d'alternance (docs/03 §7.4) — non couvertes
  par le lot `alternation` (seules les exceptions individuelles le sont) ;
- exposer la résolution du contexte d'alternance aux futurs modules
  `planning` / `attendance` (pour ne jamais compter une période en
  entreprise comme une absence — docs/02 §8.4) ;
- créer un port de périmètre pédagogique public afin d'ouvrir la gestion
  des profils / inscriptions au PEDAGOGICAL_MANAGER dans son périmètre
  (aujourd'hui ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION uniquement) — cela
  débloquera notamment le parcours de parcours des inscriptions depuis
  `EnrollmentPicker` de l'écran d'alternance (saisie directe d'identifiant
  en repli aujourd'hui) ;
- exposer éventuellement une route de consultation de ses propres
  affectations pour le PEDAGOGICAL_MANAGER ;
- affectation d'un responsable pédagogique principal à une formation au
  fil de l'eau depuis les écrans d'administration.
/auth/logout et la révocation de session restent à évaluer (jeton
stateless sans état serveur pour l'instant).

Dettes techniques à traiter ultérieurement :
- file persistante + reprise garantie pour les emails d'activation
  (actuellement envoi synchrone après commit, échec seulement journalisé) ;
- purge / expiration explicite des invitations `PENDING` périmées ;
- création de comptes `PENDING_ACTIVATION` par API (l'émission cible
  aujourd'hui un compte déjà existant, créé par fixture ou futur import ;
  vaut aussi pour la création d'un `student_profile`, qui exige un
  compte `STUDENT` préexistant) ;
- consultation par l'apprenant de son propre profil et de son historique
  d'inscriptions (routes réservées à l'administration dans ce lot) ;
- génération locale d'un numéro étudiant `ESIC-{ANNEE}-{SEQUENCE}` quand
  il est absent (docs/04 §3.5 ; aujourd'hui le numéro est obligatoire
  dans la requête) ;
- suite de tests : chaque classe `@SpringBootTest` porte sa propre
  `@TestConfiguration` imbriquée → un contexte Spring (et un pool
  HikariCP) mis en cache par classe ; le pool de test est plafonné
  (`application-test.yml`, `maximum-pool-size: 6`) pour rester sous
  `max_connections` de MySQL. Une `@TestConfiguration` partagée ou des
  Testcontainers dédiés seraient préférables à terme ;
- incohérences docs à corriger : docs/03 §6.4 (dépendances du module
  `academic` : ajouter `organization` et la publication vers `audit` ;
  ajouter le module `enrollment` → `identity`, `academic`, publication
  vers `audit`, et le port `academic.ClassGroupDirectory` ; ajouter le
  module `alternation` → `academic`, `enrollment`, `identity`, `shared`,
  publication vers `audit`, et les ports `enrollment.EnrollmentDirectory`
  et `academic.AcademicScopeDirectory` — docs/03 §7.4 déjà annoté) ;
  docs/04 §12.3 (colonnes techniques de `program_level`) ; docs/04 §14.1
  (`cycle_length_weeks` livré en `INT` et non `SMALLINT`, et
  `cycleStartDate` déplacé du modèle vers l'affectation de classe).
```

## Blocages

```text
Aucun blocage technique vérifié pour le moment.
```

## Commandes de démarrage

```text
docker compose config    # valider la syntaxe et les variables
docker compose up -d     # démarrer mysql, redis, mailpit, mosquitto
docker compose ps        # vérifier l'état des conteneurs
```

Infrastructure vérifiée le 28 août 2026 : MySQL et Redis en état
`healthy` (authentification testée), Mailpit `healthy`, Mosquitto
démarré (pas de healthcheck configuré). Nécessite un fichier `.env`
local non versionné (voir `.env.example`).

Front-end (dossier `frontend/`, Node.js 24) :

```text
cd frontend
npm ci
npm start                    # ng serve — http://localhost:4200, proxifie /api vers :8080
npm test -- --watch=false    # Vitest + jsdom (builder @angular/build:unit-test)
npm run build                # build de production dans dist/
npm run lint                 # angular-eslint
```

```text
cd backend
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
set -a && source ../.env && set +a   # variables MySQL/Redis requises
./mvnw test                          # exécute EsicConnectApplicationTests + ModularityTests
```

Socle back-end vérifié le 28 août 2026 (Java 21.0.12.1, Maven 3.9.16,
Spring Boot 3.5.16, Spring Modulith 1.4.12) : `./mvnw test` →
`BUILD SUCCESS`, 2 tests exécutés (chargement du contexte + vérification
de la structure modulaire), 0 échec. Aucune route métier, aucune entité
JPA, aucune authentification réelle. Sécurité : seules
`/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**` et
`/swagger-ui.html` sont ouvertes ; toutes les autres routes exigent une
authentification (non implémentée). Travaux réalisés sur la branche
`feature/spring-boot-foundation`, non fusionnée et non committée (fusionnée
depuis sur `main` via la PR #1).

Socle identité + audit vérifié le 28 août 2026, sur la branche
`feature/identity-foundation` (non fusionnée, non committée) : `./mvnw test`
(mêmes commandes ci-dessus) → `BUILD SUCCESS`, 9 tests exécutés (rôles
seedés, unicité email, unicité public_id, suppression d'un utilisateur
référencé refusée par `RESTRICT`, unicité d'une affectation active +
réattribution possible après clôture, acteur d'audit mis à `NULL` après
suppression du compte avec conservation du snapshot, chargement du
contexte, structure modulaire), 0 échec, exécuté deux fois pour
vérifier la stabilité. Migrations Flyway `V1` (tables `user_account`,
`role`, `user_role`, `audit_event`) et `V2` (6 rôles système) appliquées
sur la base locale. Aucune donnée de démonstration, aucun compte
administrateur, aucun JWT, aucun contrôleur d'authentification.

Authentification vérifiée le 28 août 2026, sur la branche
`feature/authentication-foundation` (non fusionnée, non committée) :
`./mvnw test` (mêmes commandes ci-dessus) → `BUILD SUCCESS`, 24 tests
exécutés (15 nouveaux : adaptateur `UserDetailsService`, service
d'authentification unitaire — y compris qu'un échec de journalisation
d'audit ne modifie ni ne masque jamais le résultat réel —, connexion
réussie de bout en bout avec jeton décodable et audit committé, réponse
publique strictement identique pour email inconnu/mauvais mot de
passe/compte non actif, aucune fuite de l'email brut d'un compte
inconnu dans l'audit, rejet sans jeton d'une route protégée, refus d'un
jeton correctement signé mais à l'émetteur (`iss`) incorrect (401 nu,
sans détail de validation exposé), routes `/actuator/health` et
`/v3/api-docs` toujours publiques), 0 échec, exécuté deux fois pour
vérifier la stabilité. Aucune migration `V3` : le schéma `V1`
suffisait. `JwtDecoder` vérifie désormais explicitement l'émetteur
(`JwtValidators.createDefaultWithIssuer`) et `AuthenticationService`
refuse de démarrer si `JWT_ACCESS_TOKEN_TTL_SECONDS` n'est pas
strictement positif. Un `AuthenticationEntryPoint` dédié a dû remplacer
celui par défaut de Resource Server, qui exposait le motif technique du
refus (ex. « the iss claim is not valid ») dans l'en-tête
`WWW-Authenticate` — désormais un 401 nu. **`JWT_SECRET` est requis**
(≥ 32 octets, aucune valeur par défaut) : à ajouter manuellement à
votre `.env` local
(voir `.env.example`) pour lancer l'application hors des tests — les
tests utilisent un secret dédié dans `application-test.yml`, jamais
`.env`.

Invitation / activation vérifiée le 28 août 2026, sur la branche
`feature/account-invitation` (non fusionnée, non committée) : `./mvnw test`
(mêmes commandes ci-dessus) → `BUILD SUCCESS`, **50 tests** exécutés
(26 nouveaux), 0 échec, exécuté deux fois pour vérifier la stabilité.
Nouveaux tests : `InvitationTokenServiceTests` (SecureRandom ≥ 32 o,
Base64URL sans padding, SHA-256 hex déterministe + vecteur
`SHA-256("abc")`), `AccountInvitationServiceTests` (TTL non positif
refusé au démarrage, émission limitée à `PENDING_ACTIVATION`, rôle
inconnu/inactif refusé, révocation des invitations PENDING antérieures
avec `flush` avant insert, empreinte stockée jamais égale au jeton,
activation encodant le mot de passe, jetons inconnu/expiré/accepté →
même erreur générique, `validate` = booléen seul),
`InvitationEmailListenerTests` (transmission au mailer, échec avalé sans
propagation), `AccountInvitationIntegrationTests` (émission protégée →
email capturé par un mailer enregistreur → `validate` public générique →
activation → connexion avec le nouveau mot de passe → jeton à usage
unique refusé en 400 `INVITATION_INVALID` → audit
`ACCOUNT_INVITATION_ISSUED`/`ACCOUNT_ACTIVATED` ; réémission révoquant le
jeton précédent), `AccountInvitationSecurityTests` (émission : 401 sans
jeton, 403 rôle `STUDENT` ; `validate` public : uniquement `{"valid":
bool}`, réponse identique pour tout jeton invalide ; `activate` jeton
inconnu → 400 générique sans fuite).

Migration Flyway `V3` (`account_invitation` : `public_id`, `version`,
`token_hash` UNIQUE, `active_invitation_key` générée → une seule
invitation `PENDING` par compte, FK `RESTRICT`) appliquée sur la base
locale (schéma en version 3). `pom.xml` : ajout de
`spring-boot-starter-mail`. `SecurityConfig` : `@EnableMethodSecurity` +
`/api/v1/account-invitations/validate` et `/activate` publics.
`GlobalExceptionHandler` : `AccessDeniedException` → 403 neutre (sinon
masqué en 500 par le catch-all). Module `shared` déclaré `OPEN`
(noyau technique : `ApiError` consommé hors module). Nouveau module
`notification`. `management.health.mail.enabled=false` **uniquement**
dans les profils `local` et `test` (pas globalement). Nouvelles
variables dans `.env.example` (`MAIL_HOST`, `MAIL_PORT`, `APP_MAIL_FROM`,
`APP_ACTIVATION_BASE_URL`, `INVITATION_TOKEN_TTL`) ; `.env`, `compose.yaml`,
`V1` et `V2` inchangés. TTL par défaut `P30D`, configurable via
`INVITATION_TOKEN_TTL`, refus de démarrage si ≤ 0.

Gestion des comptes / rôles vérifiée le 28 août 2026, sur la branche
`feature/user-management` (non fusionnée, non committée) : `./mvnw test`
(mêmes commandes ci-dessus, `JAVA_HOME` OpenJDK 21) → `BUILD SUCCESS`,
**98 tests** exécutés (48 nouveaux), 0 échec, exécuté deux fois pour
vérifier la stabilité (dont `ModularityTests` : frontières de modules
respectées). Nouveaux tests : `UserManagementServiceTests` (35 —
transitions ACTIVE↔SUSPENDED, un compte SUSPENDED ne peut pas se
réactiver lui-même, archivage clôturant les rôles actifs sans
suppression, protection d'un compte `SUPER_ADMIN` (y compris pour une
réactivation par `SCHOOL_ADMINISTRATION` et pour l'attribution/retrait
de *n'importe quel* rôle par un `ADMIN`), `SUPER_ADMIN` interdit de
s'auto-suspendre / s'auto-archiver / retirer son propre rôle, dernier
rôle actif protégé, rôle inconnu, tri hors liste blanche, direction de
tri invalide (`email,wrong`) refusée au lieu d'un ASC silencieux, filtre
invalide, taille de page bornée à 100 / défaut 20),
`UserManagementIntegrationTests` (5 — liste paginée/filtrée/triée sans
`id` ni `password_hash`, détail par `public_id` + 404, suspension →
connexion refusée → réactivation → connexion rétablie, archivage
bloquant la connexion et refusant la réactivation (409), attribution
puis retrait de rôle conservant l'historique, dernier rôle protégé,
audit écrit), `UserManagementSecurityTests` (8 — 401 anonyme, 403
`STUDENT`/`TEACHER`, `SCHOOL_ADMINISTRATION` peut suspendre mais pas
archiver ni gérer les rôles, `ADMIN` ne peut pas archiver un
`SUPER_ADMIN` ni attribuer le rôle `SUPER_ADMIN`, auto-suspension
refusée (409), `public_id` inconnu → 404).

Aucune migration `V4` : `user_account` (`suspended_at`/`suspended_by_id`/
`suspension_reason`/`archived_at`/`updated_by_id`) et `user_role`
(`valid_until`/`active`/`assigned_by_id`/`assignment_reason`) portaient
déjà les colonnes nécessaires depuis `V1`. Fichiers back-end ajoutés
dans `identity.internal` (`UserAccountController`, `UserManagementService`,
`UserAdminSpecifications`, `UserManagementException(+Handler)`, DTO
`UserSummaryResponse`/`UserDetailResponse`/`RoleAssignmentResponse`/
`PageResponse`, requêtes `AccountActionRequest`/`AssignRoleRequest`) ;
modifiés : `UserAccount` (méthodes `suspend`/`reactivate`/`archive` +
getters), `UserRole` (`close` + getters), `UserAccountRepository`
(`JpaSpecificationExecutor`), `UserRoleRepository` (requêtes de rôles
actifs), `identity.AccountLifecycleAction` (+5 actions),
`audit.internal.AuditEvent` (`setResourcePublicId`),
`AccountLifecycleAuditListener`. `.env`, `compose.yaml`, `V1`–`V3`,
`SecurityConfig` et le workflow CI inchangés. Aucun commit, aucun push.

Référentiel organisationnel vérifié le 28 août 2026, sur la branche
`feature/organization-foundation` (depuis fusionnée sur main via PR #7) :
`./mvnw test` (mêmes commandes ci-dessus, `JAVA_HOME` OpenJDK 21) →
`BUILD SUCCESS`, **164 tests** exécutés (66 nouveaux), 0 échec, exécuté
deux fois pour vérifier la stabilité (dont `ModularityTests` : nouveau
module `organization`, frontières respectées, aucun cycle). Nouveaux
tests : `CidrValidatorTests` (32 — littéraux IPv4/IPv6 valides, préfixes
hors bornes, octets > 255, noms d'hôte refusés, absence de résolution
DNS), `OrganizationServiceTests` (11, Mockito — fuseau inconnu, code
pays non ISO, code dupliqué, archivage bloqué par enfants actifs, tri
hors liste blanche / direction invalide, bâtiment d'un autre site,
création sous site archivé, CIDR invalide, doublon de plage active,
publication d'événement), `OrganizationConstraintsTests` (8, `@DataJpaTest`
— unicité `site.code`, `(site,code)` bâtiment libre entre sites, unicité
`(site,code)` salle, `public_id` unique, FK `RESTRICT` site→bâtiment et
bâtiment→salle, unicité de la plage réseau active, créneau libéré après
désactivation), `OrganizationIntegrationTests` (8, `@SpringBootTest`
`RANDOM_PORT` — cycle complet site/bâtiment/salle + archivage en cascade
contrôlée + restauration + audit `SITE_*`/`BUILDING_*`/`ROOM_*`, DTO sans
`id`/`siteId`/`createdById`, archivage refusé avec enfants actifs,
création sous parent archivé refusée, `room.site` ≠ `building.site`
refusé, unicité/fuseau/pays validés, pagination bornée à 100, tri
inconnu 400, CRUD plage réseau IPv4 + IPv6 par SUPER_ADMIN avec audit),
`OrganizationSecurityTests` (7 — 401 anonyme, 403 `STUDENT`/`TEACHER`,
`PEDAGOGICAL_MANAGER` lit mais n'écrit pas, `SCHOOL_ADMINISTRATION` lit
les sites, plages réseau réservées à `SUPER_ADMIN` y compris en lecture,
`ADMIN` et `SCHOOL_ADMINISTRATION` reçoivent 403).

Migration Flyway `V4` (`site`, `building`, `room`, `site_network_range` :
`public_id` unique, FK `RESTRICT` vers `user_account` pour les colonnes
auteur et vers le parent hiérarchique, `version`, colonnes générées
`active_range_key`, `CHECK (capacity > 0)`) appliquée sur la base locale
(schéma en version 4). Fichiers back-end ajoutés : nouveau module
`com.esic.connect.organization` (package racine : `OrganizationChangeEvent`
+ enums `OrganizationResourceType`/`OrganizationChangeAction` ;
`organization.internal` : entités `Site`/`Building`/`Room`/
`SiteNetworkRange` + `OrganizationStatus`, 4 repositories, 4 services,
4 contrôleurs, DTO de réponse et records de requête, `CidrValidator`,
`SiteFieldValidator`, `OrganizationQuerySupport`, `OrganizationSpecifications`,
`OrganizationChangePublisher`, `OrganizationException(+Handler)`,
`PageResponse` local) ; `identity.CurrentUserResolver` (port public) +
`identity.internal.DefaultCurrentUserResolver` (implémentation) ;
`audit.internal.OrganizationAuditListener`. `.env`, `compose.yaml`,
`V1`–`V3`, `SecurityConfig`, `pom.xml` et le workflow CI inchangés.
Aucun site fictif ni donnée métier insérés. Aucun commit, aucun push.

Référentiel académique minimal vérifié le 29 août 2026, sur la branche
`feature/academic-foundation` (depuis fusionnée sur main via PR #8),
après la passe corrective : `./mvnw clean test` (`JAVA_HOME` OpenJDK 21,
`set -a && source ../.env`) → `BUILD SUCCESS`, **214 tests** exécutés
(50 nouveaux : 32 du premier lot + 18 correctifs), 0 échec, exécuté trois
fois pour vérifier la stabilité (dont `ModularityTests` : nouveau module
`academic`, frontières respectées, aucun cycle). Tests académiques :
`AcademicServiceTests` (22, Mockito — période inversée, code dupliqué, tri
hors liste blanche / direction invalide, archivage bloqué par enfants
actifs (formation via niveau **et** via promotion seule, niveau via
classe, promotion via classe), type de formation inconnu, période de
promotion hors année, promotion sous formation archivée, niveau d'une
autre formation, site inconnu, modification d'année excluant une
promotion existante, restauration de promotion refusée si formation ou
année archivée / réussie et auditée si parents actifs, restauration de
classe refusée si année ou formation-du-programme archivée, si site
archivé, si site absent), `AcademicConstraintsTests` (13, `@DataJpaTest`
— unicités `academic_year.code` / `program.code` / `public_id` /
`(program,code)` / `(program,academicYear,code)` / `(promotion,code)`,
FK `RESTRICT` formation→promotion, promotion→classe, année→promotion,
niveau→classe et site→classe (DELETE natif du site refusé), `CHECK`
période `academic_year` et `CHECK` capacité `class_group` ; le site
requis par les FK est inséré en SQL natif),
`AcademicIntegrationTests` (9, `@SpringBootTest` `RANDOM_PORT` — cycle
complet année→formation→niveau→promotion→classe rattachée à un site,
DTO sans `id`/`siteId`/`programId`, archivage en cascade contrôlée +
restauration complète (année, formation, niveau, promotion, classe) +
audit `…_RESTORED` inclus, archivage refusé avec enfants actifs (409),
niveau d'une autre formation refusé (400), période de promotion hors
année refusée (400), période d'année inversée refusée (400), code de
formation dupliqué (409), création de classe sous promotion archivée
refusée (409), restauration de classe refusée sous année archivée (409),
modification d'année excluant une promotion existante refusée (409) puis
acceptée si la période l'englobe, pagination bornée à 100, tri inconnu
400), `AcademicSecurityTests` (6 — 401 anonyme, 403 `STUDENT`/`TEACHER`,
`SCHOOL_ADMINISTRATION` et `PEDAGOGICAL_MANAGER` lisent mais n'écrivent
pas (403), `ADMIN` crée (201)).

Passe corrective (module `academic` + tests uniquement) :
`ClassGroupService.restore` vérifie désormais toute la chaîne de
rattachement (promotion, sa formation, son année, le niveau, la formation
du niveau, le site présent et actif) et revérifie l'invariant
niveau↔formation ; `AcademicYearService.update` refuse une période qui
exclurait une promotion existante à période renseignée (nouveau code
`ACAD_ACADEMIC_YEAR_PERIOD_CONFLICT`, requêtes `exists` ciblées) ;
`ClassGroupService.resolveSitePublicId` lève une erreur métier contrôlée
au lieu de renvoyer `null` silencieusement, sans exposer d'identifiant
SQL.

Migration Flyway `V5` (`academic_year`, `program`, `program_level`,
`promotion`, `class_group` : `public_id` unique, FK `RESTRICT` vers
`user_account` pour les colonnes auteur et vers le parent hiérarchique,
FK `RESTRICT` `class_group.site_id` → `site.id`, `version`,
`CHECK (end_date > start_date)` année, `CHECK (end_date IS NULL OR
start_date IS NULL OR end_date > start_date)` promotion,
`CHECK (capacity IS NULL OR capacity > 0)`) appliquée sur la base locale
(schéma en version 5). Fichiers back-end ajoutés : nouveau module
`com.esic.connect.academic` (package racine : `AcademicChangeEvent` +
enums `AcademicResourceType`/`AcademicChangeAction` ; `academic.internal` :
entités `AcademicYear`/`Program`/`ProgramLevel`/`Promotion`/`ClassGroup`
+ `AcademicStatus`/`ProgramType`, 5 repositories, 5 services,
5 contrôleurs, DTO de réponse et records de requête, `AcademicWeb`,
`AcademicQuerySupport`, `AcademicSpecifications`, `AcademicChangePublisher`,
`AcademicException(+Handler)`, `PageResponse` local) ;
`organization.SiteDirectory` (port public) +
`organization.internal.DefaultSiteDirectory` (implémentation) ;
`audit.internal.AcademicAuditListener`. `.env`, `compose.yaml`, `V1`–`V4`,
`SecurityConfig`, `pom.xml` et le workflow CI inchangés. Aucune formation,
promotion ni classe fictive insérée. Aucun commit, aucun push.

Périmètre pédagogique vérifié le 29 août 2026, sur la branche
`feature/pedagogical-scope` (depuis fusionnée sur main via PR #9), après
**deux** passes correctives (revues « NOT READY TO COMMIT ») — la seconde portant
sur l'isolation transactionnelle de la collision `PRIMARY_MANAGER` et
l'injection de `Clock` :
`./mvnw clean test` (`JAVA_HOME` OpenJDK 21,
`set -a && source ../.env`) → `BUILD SUCCESS`, **263 tests** exécutés,
0 échec, 0 erreur, 0 ignoré, **exécuté deux fois** de suite après tous
les changements code + docs (résultats identiques), dont `ModularityTests`
vert (ports `identity.UserDirectory`, bean `shared.config.ClockConfig`,
table `pedagogical_assignment` et bean `AssignmentPersister` dans le
module `academic` — frontières respectées, aucun cycle).

État Flyway local : `V6` inchangée depuis la première passe corrective
(déjà réécrite + réappliquée alors ; table `pedagogical_assignment` +
ligne d'historique `version = "6"` supprimées puis recréées). `V1`–`V5`
jamais touchées ; aucun `flyway clean`. Cette passe-ci ne modifie pas de
migration.

Isolation de la collision concurrente : `PedagogicalAssignmentService`
n'est plus `@Transactional` au niveau classe ; `create` n'ouvre aucune
transaction et délègue l'`INSERT` à `AssignmentPersister.persist`
(`@Transactional(propagation = REQUIRES_NEW)`). Une violation de
`uq_pedagogical_assignment_active_primary` annule cette transaction
interne, puis `create` reçoit la `DataIntegrityViolationException` hors
de toute transaction en échec et ne la mappe sur
`ACAD_PRIMARY_MANAGER_EXISTS` (409) que si le nom de contrainte / le
message SQL désigne bien cette contrainte-là ; toute autre violation
(FK, `CHECK`, `NOT NULL`, longueur, `uq_pedagogical_assignment_public_id`)
est relancée intacte. `close` reste `@Transactional`.

Horloge : bean `java.time.Clock` (`shared.config.ClockConfig`,
`@ConditionalOnMissingBean`) injecté dans `AcademicScopeGuard` et
`PedagogicalAssignmentService` ; tous les `LocalDate.now()` deviennent
`LocalDate.now(clock)`.

Tests ajoutés / mis à jour :
`PedagogicalAssignmentServiceTests` (16, Mockito — persister mocké,
`Clock.fixed(2026-06-15)` : formation inconnue / archivée, `type`
invalide, cible non éligible → `ASSIGNMENT_TARGET_NOT_ELIGIBLE`,
`validUntil < validFrom`, deuxième PRIMARY_MANAGER refusé par
pré-contrôle, **collision `active_primary` (message SQL réaliste)
retraduite en `PRIMARY_MANAGER_ALREADY_ASSIGNED` sans publier
d'événement**, **violation FK sans objet relancée à l'identique**,
DELEGATE autorisé + `reason` trimé + `delegatedById`, `validFrom` par
défaut = date de l'horloge injectée, clôture déjà clôturée, clôture
avant `validFrom`, clôture par défaut = date de l'horloge injectée + événement,
tri hors liste blanche) ;
`AcademicScopeGuardTests` (7, Mockito + `SecurityContextHolder` +
`Clock.fixed` — accès global admin / super-admin / school-admin, cumul
manager+teacher limité avec **requêtes de périmètre datées par l'horloge
injectée**, appelant non résolu → rien de visible,
`requireProgramInScope` OK vs 403 `OUT_OF_SCOPE`, contexte anonyme non
global) ;
`PedagogicalAssignmentConstraintsTests` (11, `@DataJpaTest` — unicité
`active_primary` (autre formation acceptée), DELEGATE non limités,
créneau libéré après clôture, `CHECK` de période + validité d'un seul
jour acceptée, `public_id` unique, FK `RESTRICT` `program` /
`manager_user_id` / `delegated_by_id` via
`org.hibernate.exception.ConstraintViolationException` précise,
**`PedagogicalAssignmentService.isActivePrimaryUniqueViolation`
reconnaît une vraie exception de collision et rejette une violation
`public_id`**) ;
`PedagogicalScopeIntegrationTests` (4, `@SpringBootTest` `RANDOM_PORT` —
scope descendant sur formation / niveau / promotion / classe : listes
filtrées et 403 `ACAD_FORBIDDEN` en lecture détail comme en création /
modification / archivage-restauration hors périmètre ;
`PEDAGOGICAL_MANAGER + TEACHER` reste limité ; `PEDAGOGICAL_MANAGER +
ADMIN` est global ; `SCHOOL_ADMINISTRATION` lit globalement mais ne gère
pas les affectations (403)) ;
`PedagogicalAssignmentIntegrationTests` (11, `@SpringBootTest`
`RANDOM_PORT` — cycle create/list/close + audit
`PEDAGOGICAL_ASSIGNMENT_CREATED` / `_CLOSED` ; filtres `activeOn` (bornes
`LocalDate` inclusives : `2026-09-01` et `2026-09-30` → 1, `2026-08-31`
et `2026-10-01` → 0) et `type` ; clôture par défaut à aujourd'hui,
clôture `effectiveDate < validFrom` → 400 `ACAD_ASSIGNMENT_DATE_INVALID` ;
cible inconnue / non responsable → 422 `ACAD_TARGET_NOT_ELIGIBLE` ;
doublon PRIMARY_MANAGER → 409 `ACAD_PRIMARY_MANAGER_EXISTS` ; **deux
créations concurrentes (pool 2 threads) → exactement un 201 et un 409** ;
`type` invalide → 400 ; tri hors liste blanche → 400 `ACAD_INVALID_SORT` ;
matrice 401 anonyme / 403 STUDENT·TEACHER·SCHOOL_ADMINISTRATION·
PEDAGOGICAL_MANAGER / 200 ADMIN).
`AcademicServiceTests` : les quatre fabriques de services
(`Program`/`ProgramLevel`/`Promotion`/`ClassGroup`) reçoivent un mock
`AcademicScopeGuard` ; les appels `ProgramService.archive` reviennent à
la signature à trois arguments.

Migration Flyway `V6` réécrite (`pedagogical_assignment` : `public_id`
unique, `valid_from`/`valid_until` en `DATE`, `reason` /
`close_reason` / `delegated_by_id`, FK `RESTRICT` vers `program.id` et
vers `user_account.id` pour `manager_user_id`, `delegated_by_id` et les
colonnes auteur, `version`, colonne générée `active_primary_key`
(VIRTUAL) + `UNIQUE`, `CHECK (valid_until IS NULL OR valid_until >=
valid_from)`). Fichiers back-end ajoutés : `academic.internal`
(`PedagogicalAssignment`, `PedagogicalAssignmentRole`,
`PedagogicalAssignmentStatus`, `PedagogicalAssignmentRepository`,
`PedagogicalAssignmentService`, `PedagogicalAssignmentController`,
`PedagogicalAssignmentRequests`, `PedagogicalAssignmentResponse`,
`AcademicScopeGuard`, `AssignmentPersister`) ; `identity.UserDirectory`
(port public) + `identity.internal.DefaultUserDirectory` ;
`shared.config.ClockConfig` (bean `Clock`). Fichiers modifiés :
`academic.AcademicResourceType` (+`PEDAGOGICAL_ASSIGNMENT`),
`academic.AcademicChangeAction` (+`CLOSED`), `academic.package-info`,
`academic.internal` (`AcademicException`(+`Handler`) — codes alignés
`ACAD_FORBIDDEN` / `ACAD_ASSIGNMENT_NOT_FOUND` / `ACAD_TARGET_NOT_ELIGIBLE`
/ `ACAD_PRIMARY_MANAGER_EXISTS` / `ACAD_ASSIGNMENT_ALREADY_CLOSED` /
`ACAD_ASSIGNMENT_DATE_INVALID` ; `AcademicSpecifications` — specs `IN`
de périmètre + `assignmentHasType` / `assignmentActiveOn` ; `AcademicWeb`
— `SCOPED_WRITE_ROLES` / `ASSIGNMENT_ROLES` ; `AcademicScopeGuard` et
`PedagogicalAssignmentService` — `Clock` injectée + isolation de
l'`INSERT` via `AssignmentPersister` ; les cinq services académiques
(hors `AcademicYear`) et leurs contrôleurs — branchement du
`AcademicScopeGuard`). `.env`, `compose.yaml`, `V1`–`V6`,
`SecurityConfig`, `pom.xml`, `docs/03`, `docs/04` et le workflow CI
inchangés. Aucune affectation fictive insérée. Aucun commit, aucun push.

Inscriptions historiques vérifiées le 29 août 2026, sur la branche
`feature/enrollment-history` — depuis **fusionnée sur `main` via PR #10**
(commit `495c2bf`). Après la passe corrective de revue de PR #10 (sémantique
de date du changement de classe + isolation transactionnelle des
collisions concurrentes) : `./mvnw clean test` (`JAVA_HOME` OpenJDK 21,
`set -a && source ../.env && set +a`) → `BUILD SUCCESS`, **320 tests**
exécutés (57 nouveaux ; 263 → 320), 0 échec, 0 erreur, 0 ignoré,
**exécuté deux fois** de suite après tous les changements code + docs
(résultats identiques), dont `ModularityTests` vert (nouveau module
`enrollment` → `identity`, `academic`, `shared` ; publie
`EnrollmentChangeEvent` vers `audit` ; nouveau port
`academic.ClassGroupDirectory` ; frontières respectées, aucun cycle).

État Flyway local : nouvelle migration `V7`
(`student_profile` / `enrollment`), appliquée et vérifiée ; schéma en
version 7. `V1`–`V6` jamais touchées ; aucun `flyway clean`.

`enrollment` — deux entités : `StudentProfile` (`user_id` valeur
technique, unique ; `student_number` unique ; `birth_date`,
`work_study`, `company_name` ; statut ACTIVE/ARCHIVED) et `Enrollment`
(`student_profile` intra-module ; `class_group_id` / `academic_year_id`
valeurs techniques via `ClassGroupDirectory` ; `previous_enrollment_id`
auto-référence FK `RESTRICT` ; `start_date`/`end_date` en `LocalDate`,
`CHECK (end_date IS NULL OR end_date >= start_date)` ;
`enrollment_source` MANUAL/CLASS_TRANSFER ; statuts docs/04 §13.1).
Unicité d'une inscription ACTIVE par (apprenant, année) : deux colonnes
générées `VIRTUAL` (`active_student_key` / `active_year_key`, valorisées
seulement pour une ligne ACTIVE) + `UNIQUE (active_student_key,
active_year_key)` ; pré-contrôle applicatif `ENR_ACTIVE_ENROLLMENT_EXISTS`
(409). Isolation des collisions concurrentes :
- `StudentProfileService.create` et `EnrollmentService.enroll` ne sont
  **pas** `@Transactional` ; l'INSERT est isolé dans le bean proxifié
  `EnrollmentPersister` (`@Transactional(REQUIRES_NEW)`, même approche
  que `academic.internal.AssignmentPersister`). La
  `DataIntegrityViolationException` est reçue **hors** de toute
  transaction en échec et retraduite en 409 sur place, uniquement pour
  `uq_student_profile_user` / `uq_student_profile_student_number`
  (profil) ou `uq_enrollment_active_per_year` (inscription) ; toute
  autre violation d'intégrité est relancée telle quelle. Jamais de
  `catch (Exception)`.
- `EnrollmentService.transfer` reste `@Transactional` : l'INSERT de la
  nouvelle inscription doit voir, dans la même transaction, le créneau
  libéré par la clôture ; il ne peut donc pas capter la collision
  localement (transaction déjà rollback-only). La course résiduelle est
  retraduite après l'annulation faite par le proxy, par
  `EnrollmentExceptionHandler`, en 409 ciblé sur la seule contrainte
  `uq_enrollment_active_per_year` ; toute autre violation relancée
  (500 via le gestionnaire global).

Changement de classe (`transfer`) : `@Transactional` unique — clôture de
l'inscription courante en TRANSFERRED (`end_date` = date effective,
borne **inclusive**, ≥ `start_date`), `saveAndFlush` de l'UPDATE d'abord
(colonnes générées → NULL), puis création de la nouvelle inscription
ACTIVE avec `start_date` = date effective **+ 1 jour** (bornes
inclusives → aucun chevauchement de période ; docs/04 §13.2 ne fixe pas
de `start_date`, la non-superposition découle des bornes inclusives et
de l'unicité d'une inscription active §13.3) — `enrollment_source` =
CLASS_TRANSFER, `previous_enrollment_id`, `change_reason` ; vers une
autre année scolaire, contrôle explicite d'absence d'inscription ACTIVE
avant écriture. `close` : COMPLETED / WITHDRAWN (`@Pattern` + garde
service), motif obligatoire, `effectiveDate` par défaut =
`LocalDate.now(clock)` ≥ `start_date`.

Nouveau port public `academic.ClassGroupDirectory` (impl
`academic.internal.DefaultClassGroupDirectory`, `@Component` confiné) :
`ClassGroupRef(internalId, publicId, code, programPublicId, programCode,
academicYearInternalId, academicYearPublicId, academicYearCode,
openForEnrollment)` — `openForEnrollment` vrai seulement si la classe,
sa promotion, sa formation et son année scolaire sont toutes ACTIVE
(sinon inscription refusée en 409 `ENR_ARCHIVED_PARENT`). N'expose ni
`ClassGroup`, ni repository.

Horloge : `EnrollmentService` reçoit le bean `java.time.Clock`
(`shared.config.ClockConfig`) — `start_date` et `effectiveDate` par
défaut lus dessus, testables avec `Clock.fixed(...)`.

Config test : `application-test.yml` plafonne le pool HikariCP
(`spring.datasource.hikari.maximum-pool-size: 6`, `minimum-idle: 1`).
Motif : chaque classe `@SpringBootTest` déclare sa propre
`@TestConfiguration` imbriquée → Spring met en cache un contexte (et un
pool) par classe ; avec le pool par défaut (10) et 16 classes
`@SpringBootTest`, MySQL 8 (`max_connections` = 151 par défaut, y compris
en CI) était saturé (« Too many connections »). Aucun test métier
existant modifié.

Tests ajoutés (57) :
`EnrollmentServiceTests` (20, Mockito, `Clock.fixed(2026-06-15)` :
profil inconnu / archivé, classe inconnue / chaîne archivée, unicité
année (pré-contrôle), `start_date` par défaut = horloge et fournie,
**collision `uq_enrollment_active_per_year` du persister retraduite en
409** et **violation d'intégrité sans objet relancée à l'identique**,
`transfer` sur inscription non active / même classe / `effectiveDate` <
`start_date` / inscription ACTIVE dans l'année cible / cas nominal —
ancienne → TRANSFERRED + `end_date` inclusif, **nouvelle ACTIVE
débutant `end_date` + 1 jour, sans chevauchement** + deux événements —,
**`transfer` avec `effectiveDate` explicite : `end_date` = effectiveDate
et nouvelle `start_date` = effectiveDate + 1**, `close` non active /
statut invalide / date invalide / COMPLETED nominal + événement, tri
hors liste blanche) ;
`StudentProfileServiceTests` (10, Mockito — compte inconnu / archivé /
sans rôle `STUDENT`, numéro dupliqué, profil déjà existant, création +
`companyName` trimé + événement, **collisions concurrentes
`uq_student_profile_user` / `uq_student_profile_student_number` du
persister retraduites en 409** et **violation `public_id` relancée à
l'identique**, tri hors liste blanche) ;
`EnrollmentConstraintsTests` (12, `@DataJpaTest` — deuxième inscription
ACTIVE même année rejetée, collision reconnue par
`EnrollmentPersistence.isActiveEnrollmentUniqueViolation` et violation
`public_id` **non** reconnue, clôture qui libère le créneau, année
distincte acceptée, unicités `user_id` / `student_number` /
`public_id`, `CHECK` de période, FK `RESTRICT` `student_profile` /
`class_group` / `previous_enrollment_id` via
`org.hibernate.exception.ConstraintViolationException` ; chaîne
académique insérée en SQL natif) ;
`ClassGroupDirectoryTests` (3, `@SpringBootTest` — résolution
publicId → codes formation / année + `openForEnrollment`, faux après
archivage de la classe, identifiant inconnu / `null` → `Optional.empty`) ;
`EnrollmentIntegrationTests` (9, `@SpringBootTest` `RANDOM_PORT` — cycle
profil → inscription → changement de classe (ancienne consultable en
TRANSFERRED + **nouvelle `start_date` = `end_date` de l'ancienne + 1
jour, aucun chevauchement**) → clôture, audit `STUDENT_PROFILE_CREATED`
/ `ENROLLMENT_CREATED` / `_TRANSFERRED` / `_CLOSED` ; doublon
d'inscription ACTIVE → 409 ; **deux créations concurrentes (pool 2
threads) → exactement un 201 et un 409** ; transfert vers la même
classe → 400 `ENR_SAME_CLASS` ; profil sur compte non `STUDENT` → 422 ;
numéro étudiant dupliqué → 409 ; inscription sous classe archivée → 409
`ENR_ARCHIVED_PARENT` ; profil inconnu → 404 ; tri hors liste blanche →
400 `ENR_INVALID_SORT` ; DTO sans identifiant SQL) ;
`EnrollmentSecurityTests` (3, `@SpringBootTest` — 401 anonyme ;
`STUDENT` / `TEACHER` / `PEDAGOGICAL_MANAGER` → 403 en lecture comme en
écriture ; `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION` → 200 en
lecture).

Fichiers back-end ajoutés : migration `V7` ; nouveau module
`com.esic.connect.enrollment` (package racine : `EnrollmentChangeEvent`
+ enums `EnrollmentResourceType` / `EnrollmentChangeAction`,
`package-info` ; `enrollment.internal` : `StudentProfile`(+`Status`),
`Enrollment`(+`EnrollmentStatus`, `EnrollmentSource`), leurs
repositories, `StudentProfileService` / `EnrollmentService`,
`StudentProfileController` / `EnrollmentController`, `*Requests` /
`*Response`, `EnrollmentChangePublisher`, `EnrollmentException`(+
`Handler`), `EnrollmentPersistence`, `EnrollmentPersister`
(`@Transactional(REQUIRES_NEW)`), `EnrollmentWeb`,
`EnrollmentQuerySupport`, `EnrollmentSpecifications`, `PageResponse`
local) ; `academic.ClassGroupDirectory` (port public) +
`academic.internal.DefaultClassGroupDirectory` (implémentation) ;
`audit.internal.EnrollmentAuditListener`. Fichiers modifiés :
`academic/package-info.java` (mention du port `ClassGroupDirectory`) ;
`src/test/resources/application-test.yml` (pool HikariCP plafonné).
`.env`, `compose.yaml`, `V1`–`V6`, `SecurityConfig`, `pom.xml`,
`docs/01`–`docs/04` et le workflow CI inchangés. Aucun profil, aucune
inscription fictive insérés. **PR #10 fusionnée sur `main`** (commit
`495c2bf`).

---

## Socle front-end Angular — 29 août 2026 (corrigé après revue de PR #11)

Branche `feature/frontend-foundation`, **fusionnée sur `main` via PR #11**
(commit `6fa341f`). Aucun fichier back-end modifié : `docs/01`–`docs/04`,
migrations `V1`–`V7`, `SecurityConfig`, `backend/**` et `backend-ci.yml`
inchangés. Autorisation et CORS back-end inchangés.

Application créée avec `ng new` sous `frontend/`. **Angular 21.2**,
politique de version cohérente dans `package.json` (`^21.2.x` pour tous
les paquets `@angular/*`). Versions résolues : `@angular/{core, common,
compiler, compiler-cli, forms, platform-browser, router, cli, build}` =
**21.2.22** ; `@angular/material` + `@angular/cdk` = **21.2.14**. Le
décalage de patch entre le framework (21.2.22) et Material/CDK (21.2.14)
est normal : Angular Components suit sa propre cadence de patch et
21.2.14 est son dernier patch de la ligne **21.2**, compatible avec le
framework 21.2.22. **Node.js 24.13.0**, npm 11.6.2. Application
*zoneless* par défaut, composants standalone, TypeScript strict,
formulaires réactifs, signaux, routes de fonctionnalités en lazy
loading, control flow natif. Dépendances first-party ajoutées :
`@angular/material` + `@angular/cdk` (Material explicitement requis —
docs/02 §48.1, docs/01 §5.3, US-023, T-J1-040) ; `angular-eslint` (dev)
pour `npm run lint`. `package-lock.json` régénéré via `npm install`,
`npm ci` vérifié depuis un `node_modules` vide.

Routes : `/login` (guestGuard), `/dashboard` (authGuard, 1re tranche
verticale authentifiée), `/administration`
(`roleGuard(['ADMIN','SUPER_ADMIN'])`), `/students`
(`roleGuard(['ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION'])`),
`/forbidden` (403), `**` (404). `/administration` et `/students` sont des
écrans d'attente (placeholder) : **masqués de la navigation principale
et des accès rapides** (`NavItem.placeholder`, `visibleNavItems` les
exclut), mais toujours déclarés, directement adressables et gardés par
rôle — un rôle non autorisé est redirigé vers `/forbidden`, un rôle
autorisé atteint l'écran « À venir ». La navigation principale ne
présente que les écrans livrés (aujourd'hui : le seul `/dashboard`).
Routes authentifiées enfants d'une coquille `AppShell` (barre Material +
navigation latérale responsive, `<nav>`/`<main>`, lien d'évitement,
`aria-current`).

Tableau de bord : rapporte un **état de session local** établi après une
connexion réussie. Il n'effectue **pas** de second appel d'API
authentifié et ne prétend pas avoir revérifié le jeton porteur via un
autre endpoint back-end (aucun `/auth/me` n'existe et aucun n'a été
ajouté). Il affiche l'email saisi, le claim `sub`, l'échéance du jeton,
les rôles, et une carte « accès rapides » limitée aux écrans livrés.

Authentification : `POST /api/v1/auth/login` consommé tel quel — c'est
cette requête qui prouve l'authentification. Pas d'endpoint `/auth/me`
ni `/auth/logout` côté back-end → déconnexion locale, identité affichée
= email saisi + claims JWT. **Stockage du jeton en mémoire uniquement**
(signal `AuthService`), ni `localStorage` ni `sessionStorage` ni
IndexedDB ni cookie JS (docs/07 §6, RG-085). Rechargement de page =
perte de session et retour à `/login` ; une vraie session persistante
exige le futur cookie `HttpOnly` + refresh token côté back-end.
`AuthService.restoreSession()` reste le point d'ancrage de ce futur flux
(aucun faux endpoint de refresh ni de current-user ajouté). Décodage JWT
non vérifié, affichage et navigation uniquement — autorisation réelle =
Spring Security. Intercepteurs : jeton porteur (`Authorization` sur
`/api`, jamais journalisé) ; erreurs (`401` non-login → purge +
`/login?reason=expired`, `0`/`5xx` → bandeau générique, `4xx` →
composant ; `normalizeHttpError` conserve le `code` métier `ApiError`
docs/03 §10.3).

Infra HTTP : URL d'API **relative** (`/api`) via
`src/environments/environment*.ts` ; `ng serve` proxifie `/api` vers
`http://localhost:8080` (`proxy.conf.json`) → aucune requête
cross-origin, **CORS back-end non modifié** (inexistant, non requis en
local). Déploiement cross-origin ultérieur : URL absolue + CORS Spring
(documenté dans `environment.ts`).

Vérifications exécutées avec succès en local le 29 août 2026 (Node
24.13.0), depuis `frontend/` :

```text
rm -rf node_modules && npm ci   # 582 paquets, 0 vulnérabilité
npm test -- --watch=false        # 14 fichiers, 69 tests, 0 échec (Vitest + jsdom)
npm run build                    # bundle initial 409 kB brut / 106 kB transféré, 0 alerte de budget
npm run lint                     # angular-eslint, « All files pass linting »
```

`cd backend && ./mvnw clean test` non ré-exécuté : aucun fichier
back-end modifié.

CI : `.github/workflows/frontend-ci.yml` (lint + tests + build sur
`frontend/**`).

Limites connues :
- pas de restauration de session au rechargement — un rechargement de
  page perd la session et renvoie vers `/login` ; une vraie session
  persistante exige le futur cookie `HttpOnly` + refresh token côté
  back-end ;
- sélecteur de contexte de rôle (docs/02 §6.1) livré à part sur
  `feature/frontend-role-context` (voir « Phase actuelle ») ;
- `/administration` et `/students` sont des routes gardées sans contenu
  métier, volontairement masquées de la navigation ;
- PWA, notifications, SSE non abordés ;
- tests front en TestBed/Vitest uniquement, pas de tests e2e Angular →
  Spring Boot.

---

## Activation de compte (front-end) — 29 août 2026

Branche `feature/frontend-account-activation` (créée depuis `main` à
`6fa341f`), **fusionnée sur `main` via PR #12** (commit `2ff7aa8`). Aucun
fichier back-end modifié : `docs/01`–`docs/04`, migrations `V1`–`V7`,
`SecurityConfig`, `backend/**`, `backend-ci.yml`, autorisation et CORS
back-end inchangés. Aucune dépendance ajoutée → `package.json` /
`package-lock.json` inchangés.

Parcours public `/activation` atteint via le lien d'invitation généré
par le back-end (`JavaMailSenderInvitationMailer` :
`${app.activation.base-url}?token=<jeton URL-encodé>`).

Contrat back-end consommé **tel quel** (`AccountInvitationController`,
`ActivateAccountRequest`, `InvitationValidationResponse`,
`InvitationExceptionHandler`) — rien inventé :
- `GET /api/v1/account-invitations/validate` — **public** (SecurityConfig
  `PUBLIC_PATHS`), jeton en **paramètre de requête** `token` ; toujours
  `200` avec `{ "valid": boolean }` (aucun code d'erreur, aucune donnée
  personnelle) ;
- `POST /api/v1/account-invitations/activate` — **public** ; corps
  `{ "token": string, "password": string }` ; succès `204 No Content`,
  corps vide, **aucun identifiant de session** ; erreurs :
  `400 VALIDATION_ERROR` (`@NotBlank` / `@Size(min = 12, max = 200)` sur
  `token` / `password`, via `GlobalExceptionHandler`) et
  `400 INVITATION_INVALID` (message « Lien d'activation invalide ou
  expire. ») — **code unique** pour jeton inconnu / expiré / révoqué /
  déjà consommé / cible non `PENDING_ACTIVATION`. Les autres `Kind`
  (`TARGET_NOT_FOUND` 404, `TARGET_NOT_PENDING` 409, `ROLE_INVALID` 422)
  ne sont atteignables que depuis l'émission protégée, jamais `/activate`.

Contraintes de mot de passe côté client, alignées exactement :
`required` + `minLength(12)` + `maxLength(200)`. Pas de règle de
complexité (absente du DTO), **pas de champ de confirmation** (absent du
contrat et des docs — docs/02 §8.3 étape 6 = simple « définition du mot
de passe »). Bascule afficher/masquer accessible ; `autocomplete="new-password"`.

Fichiers ajoutés : `frontend/src/app/features/account-activation/`
(`account-activation.models.ts`, `account-activation-api.service.ts`
(+ `.spec`), `account-activation.ts` / `.html` / `.scss` (+ `.spec`)),
`frontend/src/app/core/auth/jwt.testing.ts` (extraction de `makeJwt`
hors de `jwt.spec.ts`).
Fichiers modifiés : `app.routes.ts` (route `/activation` publique sans
garde), `core/http/auth-token.interceptor.ts` +
`core/http/api-error.interceptor.ts` (helper `isPublicInvitationRequest`
excluant `/account-invitations/validate|activate` — pas de bearer, pas
de purge de session sur `401`, pas de bandeau sur `5xx`),
`jwt.spec.ts` + `auth.service.spec.ts` (import depuis `jwt.testing`),
`app.routes.spec.ts` / `auth-token.interceptor.spec.ts` /
`api-error.interceptor.spec.ts` (tests ajoutés),
`tsconfig.spec.json` / `tsconfig.app.json` (`*.testing.ts`).

Vérifications exécutées avec succès en local le 29 août 2026 (Node
24.13.0), depuis `frontend/` :

```text
rm -rf node_modules && npm ci   # 0 vulnérabilité
npm test -- --watch=false        # 16 fichiers, 85 tests, 0 échec (Vitest + jsdom)
npm run build                    # bundle initial 410,57 kB brut / 106,54 kB transféré, 0 alerte de budget
npm run lint                     # angular-eslint, « All files pass linting »
```

`cd backend && ./mvnw clean test` non ré-exécuté : aucun fichier
back-end modifié (CI back-end `backend-ci.yml` inchangée).

Limites connues (activation) :
- l'écran exige un `?token=` valide dans le lien : pas de renvoi
  d'invitation en libre-service dans la SPA ;
- le back-end renvoyant un unique `INVITATION_INVALID`, l'interface
  affiche un seul état terminal « lien invalide ou expiré » (pas d'écran
  distinct expiré / consommé / révoqué — choix délibéré) ;
- pas de champ de confirmation du mot de passe (hors contrat back-end) ;
- l'activation ne connecte pas automatiquement (le `204` ne renvoie
  aucun identifiant) : écran de succès + lien explicite vers `/login`.

---

## Espace Apprenants (front-end) — 29 août 2026

Branche `feature/frontend-student-list` (créée depuis `main` à `810c8a2`),
PR ouverte contre `main`, **non fusionnée**. Aucun fichier back-end
modifié : `docs/01`–`docs/04`, migrations `V1`–`V7`, `SecurityConfig`,
`backend/**`, `backend-ci.yml`, autorisation et CORS back-end inchangés.
Aucune dépendance ajoutée → `package.json` / `package-lock.json`
inchangés.

Liste des apprenants → fiche d'un apprenant → historique de ses
inscriptions. Lecture seule : aucun `POST` du module `enrollment`
(`create` / `transfer` / `close`) n'est consommé.

Contrat back-end consommé **tel quel** (rien inventé) :

| Endpoint | Usage front | Points du contrat respectés |
|---|---|---|
| `GET /api/v1/student-profiles` | liste `/students` | params **réellement exposés uniquement** : `q` (sous-chaîne du **numéro étudiant** — `StudentProfileSpecifications.profileMatchesStudentNumber` ; le nom n'est pas un critère), `status` (`ACTIVE`/`ARCHIVED`, sinon 400 `ENR_INVALID_FILTER`), `sort` (liste blanche `studentNumber`/`createdAt`, sinon 400 `ENR_INVALID_SORT`), `page`, `size` (≤ 100). `PageResponse<StudentProfileResponse>`. Filtre `user` non utilisé. |
| `GET /api/v1/student-profiles/{publicId}` | fiche | 404 `ENR_STUDENT_PROFILE_NOT_FOUND` (inconnu / non-UUID) → état « introuvable ». |
| `GET /api/v1/enrollments?student={publicId}&sort=startDate,desc` | historique | filtre `student` = `public_id` de profil ; `sort` liste blanche `startDate`/`endDate`/`createdAt` ; `size=100`, pas de pagination (cursus < 100). `EnrollmentResponse` : `academicYearCode`, `classGroupCode`, `programCode`, `startDate`, `endDate`, `status`, `enrollmentSource`, `changeReason`, `previousEnrollmentPublicId`. |
| `GET /api/v1/users/{userPublicId}` | fiche (identité civile) | **facultatif** — le profil n'expose que `userPublicId` ; `firstName` / `lastName` / `email` seuls sont lus ; échec **ignoré** (fiche titrée « Apprenant <numéro> »). Même périmètre de rôles que le module `enrollment`. |

Toutes ces routes sont réservées côté serveur à
`ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION`
(`EnrollmentWeb.MANAGE_ROLES`, `UserAccountController` READ_ROLES).

Fichiers ajoutés : `frontend/src/app/features/students/`
(`students.models.ts`, `students-api.service.ts` (+ `.spec`),
`student-list/student-list.ts` / `.html` / `.scss` (+ `.spec`),
`student-profile/student-profile.ts` / `.html` / `.scss` (+ `.spec`)).
Fichiers modifiés : `app.routes.ts` (`/students` : placeholder →
parent gardé `roleGuard(['ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION'])`
+ `canActivateChild` identique, enfants `''` → `StudentList` et
`:publicId` → `StudentProfile`) ; `core/navigation/navigation.ts`
(l'entrée « Apprenants » perd `placeholder`, garde son filtre de rôles) ;
specs `navigation.spec.ts`, `app-shell.spec.ts`, `dashboard.spec.ts`,
`app.routes.spec.ts` mis à jour. `/login`, `/activation`, `/dashboard`,
`/administration` (placeholder), le sélecteur de contexte, les
intercepteurs, `AuthService`, `jwt.ts`, les gardes : inchangés.

Décisions :
- **Recherche limitée au numéro étudiant** : `GET /api/v1/student-profiles`
  n'expose pas d'autre critère textuel. Le libellé du champ l'indique
  (« Numéro étudiant »). Aucune recherche par nom n'est simulée.
- **Fiche enrichie par `GET /api/v1/users/{id}`** : le profil apprenant
  n'ayant pas de nom, cet endpoint réel (même périmètre de rôles) fournit
  l'identité civile. Appel **facultatif et non bloquant** — un échec
  n'empêche jamais l'affichage du profil ni de l'historique.
- **Tri** : en-têtes triables réduits à `studentNumber` / `createdAt`
  (liste blanche back-end) ; toute autre colonne retombe sur le défaut
  `createdAt,desc` avant l'appel, jamais un 400.
- **403 rendu explicitement** : bien que `roleGuard` filtre déjà la
  route, un `403` de l'API donne un état « accès refusé » — Spring
  Security reste l'autorité.
- **Historique non paginé** (`size=100`).
- Libellés de statut d'inscription / d'origine : table de correspondance
  FR modifiable, aucune valeur d'enum inventée.

Accessibilité : `<h1>` par écran, libellés de formulaire associés
(Material), `role="status"` sur les chargements asynchrones,
`role="alert"` sur les panneaux d'erreur / accès refusé, action
« Consulter » = lien focalisable avec `aria-label` explicite (aucune
ligne cliquable sans équivalent clavier), `mat-paginator` francisé
(`MatPaginatorIntl`), tables défilables horizontalement
(`overflow-x: auto`).

Confidentialité : JWT et contexte de rôle **en mémoire seule** (docs/07
§6, RG-085) ; aucun accès `localStorage` / `sessionStorage` (asserté).
Aucun identifiant SQL interne, `correlationId`, message d'exception,
trace ou requête SQL affiché ; les `5xx` sont neutralisés en message
générique par `normalizeHttpError` (le bandeau global de
`apiErrorInterceptor` reste la voie transverse pour `0` / `5xx`).

Vérifications exécutées avec succès en local le 29 août 2026 (Node
24.13.0), depuis `frontend/` :

```text
npm test -- --watch=false   # 21 fichiers, 131 tests, 0 échec (102 → 131)
npm run build               # bundle initial 477,42 kB brut / 123,31 kB transféré, 0 alerte (seuil 500 kB)
npm run lint                # angular-eslint, « All files pass linting »
```

`cd backend && ./mvnw test` non ré-exécuté : aucun fichier back-end
modifié (CI back-end inchangée).

Limites connues (Apprenants) :
- recherche par numéro étudiant uniquement (limite de l'API) ;
- pas de tri interactif sur l'historique (fixé `startDate,desc`) ;
- l'identité civile dépend d'un second appel facultatif ; sans lui, la
  fiche n'affiche que le numéro étudiant et le statut ;
- écran non démontré de bout en bout avec le back-end en marche ;
- pas de tests e2e Angular → Spring Boot (TestBed / Vitest uniquement).

---

## Administration des comptes utilisateurs (front-end) — 29 août 2026

Branche `feature/frontend-user-administration`, **fusionnée sur `main`
via PR #16** (commit `5d5e51d`). Aucun fichier back-end modifié :
`docs/01`–`docs/04`, migrations `V1`–`V7`, `SecurityConfig`,
`backend/**`, `backend-ci.yml`, autorisations et CORS back-end
inchangés. Aucune dépendance ajoutée → `package.json` /
`package-lock.json` inchangés.

> Le **parcours d'écriture** (suspension, réactivation, archivage,
> attribution et retrait de rôle) est livré séparément sur la branche
> `feature/frontend-user-administration-write` (PR ouverte, non
> fusionnée) — détail complet dans « Phase actuelle » ci-dessus.

Liste des comptes → fiche d'un compte → historique de ses rôles. Lecture
seule : aucun `POST` du module `identity` (`suspend` / `restore` /
`archive` / `roles` / `roles/{code}/revoke`) n'est consommé.

Contrat back-end consommé **tel quel** (`UserAccountController`,
`UserManagementService`, `UserAdminSpecifications`, `UserManagementExceptionHandler` ;
rien d'inventé) :

| Endpoint | Usage front | Points du contrat respectés |
|---|---|---|
| `GET /api/v1/users` | liste `/administration` | `q` (sous-chaîne insensible à la casse sur **email OU prénom OU nom** — `UserAdminSpecifications.matchesText` ; `LIKE` échappé, bornée à 100 car.), `status` (`PENDING_ACTIVATION`/`ACTIVE`/`SUSPENDED`/`LOCKED`/`ARCHIVED`, sinon 400 `USER_INVALID_FILTER`), `role` (code `RoleCode`, filtre sur affectation **active**, sinon 400 `USER_INVALID_FILTER`), `sort` (liste blanche `createdAt`/`lastLoginAt`/`email`/`lastName` ; champ **ou** direction hors liste → 400 `USER_INVALID_SORT` ; défaut `createdAt,desc`), `page`, `size` (≤ 0 → 20 ; borné à 100). `PageResponse<UserSummaryResponse>` = `{ publicId, email, firstName, lastName, status, roles (codes actifs), createdAt, lastLoginAt\|null }`. |
| `GET /api/v1/users/{publicId}` | fiche | identifiant inconnu **ou mal formé** → 404 `USER_NOT_FOUND` → état « introuvable ». `UserDetailResponse` = `{ publicId, email, firstName, lastName, phone\|null, status, emailVerifiedAt\|null, lastLoginAt\|null, suspendedAt\|null, suspensionReason\|null, archivedAt\|null, createdAt, updatedAt, roleAssignments: [{ role, active, validFrom, validUntil\|null }] }` (historique complet, du plus récent au plus ancien côté serveur). |

Toutes deux réservées côté serveur à
`ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION`
(`UserAccountController.READ_ROLES`). Distinction lecture / écriture du
back-end : la **lecture** (les deux `GET` ci-dessus) est ouverte à ces
trois rôles ; l'**écriture** ne l'est pas et n'est pas touchée —
suspend/restore = mêmes trois rôles, archive + roles + revoke =
`ADMIN`/`SUPER_ADMIN` seuls, avec en plus des gardes fines dans
`UserManagementService` (protection `SUPER_ADMIN`, auto-action interdite,
dernier rôle actif protégé, rôle `SUPER_ADMIN` réservé à un appelant
`SUPER_ADMIN`).

Fichiers ajoutés : `frontend/src/app/features/administration/`
(`administration.models.ts`, `administration-api.service.ts` (+ `.spec`),
`user-list/user-list.ts` / `.html` / `.scss` (+ `.spec`),
`user-detail/user-detail.ts` / `.html` / `.scss` (+ `.spec`)).
Fichiers modifiés : `app.routes.ts` (`/administration` : placeholder →
parent gardé `roleGuard(['ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION'])`
+ `canActivateChild` identique, enfants `''` → `UserList` et
`:publicId` → `UserDetail`) ; `core/navigation/navigation.ts` (l'entrée
« Administration » perd `placeholder` ; ses `roles` deviennent les trois
`READ_ROLES`) ; specs `navigation.spec.ts`, `app-shell.spec.ts`,
`dashboard.spec.ts`, `app.routes.spec.ts` mis à jour. `/login`,
`/activation`, `/dashboard`, `/students`, `/academic`, le sélecteur de
contexte, les intercepteurs, `AuthService`, `jwt.ts`, les gardes :
inchangés. Le composant `shared/components/module-placeholder` n'est plus
référencé par aucune route (plus aucun placeholder) ; il est laissé en
place, avec le mécanisme `NavItem.placeholder` + `visibleNavItems`
(toujours testé via des entrées fabriquées), pour une future route
gardée sans écran.

Décisions :
- **Lecture seule** : les endpoints d'écriture existent et sont
  spécifiés, mais leur parcours complet (gardes fines serveur ci-dessus,
  formulaire d'attribution de rôle, confirmations d'action
  irréversible) sera livré sans approximation dans un lot dédié —
  cohérent avec les tranches Apprenants et référentiels académiques,
  elles aussi en lecture seule.
- **Placeholder `/administration` remplacé** : « administration des
  comptes, des rôles et des référentiels » décrivait exactement cet
  écran. Le guard de route est **aligné** sur le `@PreAuthorize` réel
  (`+ SCHOOL_ADMINISTRATION`, la lecture y est ouverte) ; cela n'élargit
  aucun droit — Spring Security reste l'autorité, un `403` de l'API est
  rendu « accès refusé ».
- **Recherche `q`** = email / prénom / nom (le libellé du champ le
  précise). **Filtre `role`** = les 6 `RoleCode` (le back-end filtre sur
  une affectation active). **Tri** = en-têtes triables limités à la liste
  blanche back-end ; une colonne hors liste retombe sur `createdAt,desc`
  **avant** l'appel, jamais un 400. **Historique des rôles** non paginé
  (le détail renvoie la liste complète).

Accessibilité : `<h1>` par écran, libellés de formulaire associés
(Material), `role="status"` sur les chargements asynchrones,
`role="alert"` sur les panneaux d'erreur / accès refusé / introuvable,
action « Consulter » = lien focalisable avec `aria-label` explicite
(aucune ligne cliquable sans équivalent clavier), `mat-paginator`
francisé (`MatPaginatorIntl`), tables défilables horizontalement
(`overflow-x: auto`).

Confidentialité : JWT et contexte de rôle **en mémoire seule** (docs/07
§6, RG-085) ; aucun accès `localStorage` / `sessionStorage` (asserté).
Les DTO back-end n'exposent ni `id` SQL, ni `password_hash`, ni jeton ;
aucun identifiant SQL interne, `correlationId`, message d'exception,
trace ou requête SQL affiché ; les `5xx` sont neutralisés en message
générique par `normalizeHttpError` (le bandeau global de
`apiErrorInterceptor` reste la voie transverse pour `0` / `5xx`, et un
`401` continue de purger la session via `AuthService.handleUnauthorized`).

Vérifications exécutées avec succès en local le 29 août 2026 (Node
24.13.0), depuis `frontend/` :

```text
npm test -- --watch=false   # 27 fichiers, 190 tests, 0 échec (167 → 190)
npm run build               # bundle initial 477,81 kB brut / 121,63 kB transféré, 0 alerte (seuil 500 kB)
npm run lint                # angular-eslint, « All files pass linting »
```

`cd backend && ./mvnw test` non ré-exécuté : aucun fichier back-end
modifié (CI back-end inchangée).

Limites connues (Administration) :
- lecture seule : aucune action de suspension / réactivation / archivage
  / gestion de rôle (reportée) ;
- pas de tri interactif sur l'historique des rôles (ordre serveur, du
  plus récent au plus ancien) ;
- écran non démontré de bout en bout avec le back-end en marche ;
- pas de tests e2e Angular → Spring Boot (TestBed / Vitest uniquement).

---

## Gestion de l'alternance (front-end) — 30 août 2026

Branche `feature/alternation-management-ui`, **fusionnée sur `main` via
PR #18** (commit `a79b5bf`). Aucun fichier back-end, migration V1–V8 ou
docs/01–04 modifié ; `SecurityConfig`, `backend/**`, `backend-ci.yml`,
autorisations et CORS back-end inchangés. Aucune dépendance ajoutée →
`package.json` / `package-lock.json` inchangés.

Septième tranche verticale front-end, première **avec écriture**. Elle
consomme l'intégralité du module back-end `alternation`
(`com.esic.connect.alternation.internal`, PR #17) — aucun endpoint, DTO,
rôle, paramètre, statut HTTP, code `ALT_*` ni script npm inventé.

Matrice des endpoints réellement consommés :

| Fonctionnalité | Méthode / URL | Rôles serveur | Notes |
|---|---|---|---|
| Modèles — liste | `GET /api/v1/alternation/patterns` | READ (4 rôles) | `q`, `status`, `type`, `sort` (`code\|name\|createdAt\|updatedAt`), `page`, `size` ≤ 100 |
| Modèles — détail | `GET .../patterns/{publicId}` | READ | 404 `ALT_PATTERN_NOT_FOUND` |
| Modèles — création | `POST .../patterns` | WRITE (`ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION`) | corps `{code,name,description?,type,cycleLengthWeeks?,configuration}` ; 201 |
| Modèles — modification | `PATCH .../patterns/{publicId}` | WRITE | `{name,description?,cycleLengthWeeks?,configuration}` (`code`/`type` figés) |
| Modèles — archivage / restauration | `POST .../patterns/{publicId}/archive` `{reason}` · `/restore` | WRITE | 204 |
| Affectations — historique d'une classe | `GET .../classes/{classPublicId}/assignments` | SCOPED (4 rôles, PM borné serveur) | `status`, `sort` (`validFrom\|validUntil\|createdAt`) |
| Affectations — création | `POST .../class-assignments` | SCOPED | `{classGroupPublicId,workStudyPatternPublicId,cycleStartDate,validFrom,validUntil?}` ; 201 |
| Affectations — clôture | `POST .../class-assignments/{publicId}/close` | SCOPED | `{reason,effectiveDate?}` ; 204 ; `409 ALT_ASSIGNMENT_*` / `400 ALT_INVALID_PERIOD` |
| Contexte de classe | `GET .../classes/{classPublicId}/context?date=` | SCOPED | `AlternationContextResponse` affiché tel quel |
| Exceptions — liste d'une inscription | `GET .../enrollments/{enrollmentPublicId}/exceptions` | SCOPED | `sort` (`startAt\|endAt\|createdAt`) |
| Exceptions — création | `POST .../student-exceptions` | SCOPED | `{enrollmentPublicId,type,startAt,endAt,timeZoneId,reason}` ; 201 |
| Exceptions — annulation | `POST .../student-exceptions/{publicId}/cancel` | SCOPED | `{reason}` ; 204 |
| Contexte effectif d'inscription | `GET .../enrollments/{enrollmentPublicId}/context?date=` | SCOPED | `EnrollmentContextResponse` affiché tel quel |
| Support — classes | `GET /api/v1/class-groups` (module `academic`) | `AcademicWeb.READ_ROLES` | picker de classe |
| Support — inscriptions d'une classe | `GET /api/v1/enrollments?classGroup=` (module `enrollment`) | `EnrollmentWeb.MANAGE_ROLES` (**pas** PM) | picker d'inscription ; 403 pour un PM → panneau + saisie directe d'identifiant |

Couverture des fonctionnalités demandées :

| Fonctionnalité | État |
|---|---|
| Modèles de rythme (liste, détail, création, modification, archivage, restauration, rafraîchissement) | TERMINÉE |
| Type `CUSTOM` (semaines école/entreprise, jours école/entreprise, `companyDays` vide, affichage canonique sans perte, validations ergonomiques d'intersection/doublon, validation finale serveur) | TERMINÉE |
| Prévisualisation accessible du cycle (grille `<table>` + légende, libellé texte par cellule, pas de calcul de contexte de date) | TERMINÉE |
| Affectations aux classes (classe accessible, affectation courante + historique, création, clôture, rafraîchissement, doubles soumissions bloquées) | TERMINÉE |
| Historique présenté comme non librement modifiable (création + clôture uniquement) | TERMINÉE |
| Contexte de classe à une date (SCHOOL / COMPANY / UNKNOWN, source, métadonnées, jamais recalculé côté client, pas de dépendance à la seule couleur) | TERMINÉE |
| Exceptions individuelles (accès à l'inscription, liste, création, annulation, rafraîchissement) | TERMINÉE ; l'accès *par parcours* des inscriptions est PARTIEL pour le `PEDAGOGICAL_MANAGER` (voir limite back-end) |
| Sémantique `[startAt, endAt)` explicite, `timeZoneId` IANA transmis tel quel, aucun repli UTC, aucune projection civile locale, `endAt > startAt` | TERMINÉE |
| Contexte effectif d'inscription (patternContext, effectiveContext, source, `coveringExceptionTypes`, jamais recombiné côté client) | TERMINÉE |
| Rôles & périmètre (route parente + garde d'écriture supplémentaire ; visibilité des actions ; 403 rendu « accès refusé » ; aucun paramètre d'élargissement de périmètre ; données non mises en cache d'un utilisateur/périmètre précédent — état par composant, rien en storage) | TERMINÉE |
| Périmètre `PEDAGOGICAL_MANAGER` sur le parcours des inscriptions | PARTIELLE — `GET /api/v1/enrollments` réservé à `EnrollmentWeb.MANAGE_ROLES` ; repli par saisie directe d'identifiant + panneau explicite ; correction recommandée : port de périmètre pédagogique public côté back-end |
| Accessibilité (titres, labels, erreurs reliées, navigation clavier, `role="status"` / `role="alert"`, boutons nommés, `disabled` pendant les mutations, pas d'info par la seule couleur, tables défilables, confirmations en ligne) | TERMINÉE |
| Responsive (réutilise les tokens `--mat-sys-*` et conventions existantes, `overflow-x:auto` sur tables et grille de cycle) | TERMINÉE |
| Documentation (`docs/CURRENT-STATE.md`, `docs/09-matrice-rncp.md`) | TERMINÉE |

Fichiers ajoutés sous `frontend/src/app/features/alternation/` :
`alternation.models.ts`, `alternation-api.service.ts` (+ `.spec`),
`alternation-errors.ts` (+ `.spec`), `alternation-paginator.ts`,
`pattern-config.ts` (+ `.spec`), `zoned-time.ts` (+ `.spec`),
`_alt-common.scss`, `shared/cycle-preview/` (`.ts` / `.html` / `.scss` /
`.spec`), `patterns/pattern-list/`, `patterns/pattern-detail/`,
`patterns/pattern-form/`, `class-alternation/class-picker/`,
`class-alternation/class-alternation/`,
`enrollment-alternation/enrollment-picker/`,
`enrollment-alternation/enrollment-alternation/` (chacun `.ts` / `.html`
/ `.scss` / `.spec`). Fichiers modifiés : `app.routes.ts` (route parente
`/alternation` + 8 enfants ; gardes), `core/navigation/navigation.ts`
(entrée « Alternance »), specs `navigation.spec.ts`, `app-shell.spec.ts`,
`dashboard.spec.ts`, `app.routes.spec.ts`.

Vérifications exécutées avec succès en local le 30 août 2026 (Node
24.13.0), depuis `frontend/` :

```text
rm -rf node_modules && npm ci   # 582 paquets, 0 vulnérabilité
npm test -- --watch=false        # 39 fichiers, 291 tests, 0 échec (190 → 291)
npm run build                    # bundle initial 479,35 kB brut / 122,11 kB transféré, 0 alerte (seuil 500 kB)
npm run lint                     # angular-eslint, « All files pass linting »
git diff --check                 # propre
```

`cd backend && ./mvnw test` non ré-exécuté : aucun fichier back-end
modifié (CI back-end inchangée).

Limites résiduelles (alternance) :
- le front n'est pas une autorité de sécurité (gardes + boutons masqués =
  ergonomie ; Spring Security décide) ;
- pas de garantie SQL générale contre les chevauchements concurrents
  d'affectations bornées ni d'unicité des exceptions concurrentes de même
  type — limites back-end documentées, non résolues ici ;
- dette transactionnelle de l'audit back-end (`@EventListener` +
  `REQUIRES_NEW`) — non concernée par le front, rappelée pour mémoire ;
- l'encodage heure-de-mur → instant suit la règle standard « offset avant
  transition » aux minutes exactes d'un changement d'heure ;
- écrans non démontrés de bout en bout avec le back-end en marche ; pas
  de tests e2e Angular → Spring Boot.

## Règle de mise à jour

Ce document doit refléter le dépôt.

Ne jamais déclarer :

- TESTÉ sans commande exécutée ;
- DÉMONTRÉ sans vérification manuelle ;
- DÉPLOYÉ sans URL ou preuve ;
- FONCTIONNEL uniquement parce que le code existe.