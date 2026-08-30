# Conception — Gestion de l'assiduité et reporting (tranche V10)

| Élément | Valeur |
|---|---|
| Branche | `feature/attendance-management-and-reporting` |
| Date de référence | 30 août 2026 |
| HEAD de départ | `dc6da1a` (= `origin/main`, arbre propre) |
| Migration ajoutée | `V10__extend_attendance_management_and_reporting.sql` (additive) |
| Migrations V1–V9 | **inchangées** |
| Statut du document | Décisions arrêtées (Checkpoint 0) |

Ce document est le livrable du **Checkpoint 0**. Il fige les décisions de
conception ; les checkpoints suivants s'y conforment. Toute divergence
ultérieure est consignée ici.

---

## 1. Audit de l'existant

### 1.1 État Git et tests de référence

- Branche : `feature/attendance-management-and-reporting`, HEAD `dc6da1a`,
  `git status --short` vide, `git log origin/main..HEAD` vide.
- **Backend** : `backend/src/test` — 57 classes de test, 492 `@Test` +
  2 `@ParameterizedTest`. `docs/CURRENT-STATE.md` documente **502 tests**
  après la PR #20 (l'écart provient de l'expansion des tests
  paramétrés / `@Nested`). Baseline exacte à confirmer par
  `./mvnw clean test` au Checkpoint 12.
- **Frontend** : `frontend/src` — 47 fichiers `*.spec.ts`, 411
  occurrences `it(` / `test(`. `docs/CURRENT-STATE.md` documente
  **416 tests**. Baseline exacte à confirmer par
  `npm test -- --watch=false` au Checkpoint 12.
- Infrastructure Docker **non démarrée** au moment de l'audit.

### 1.2 Modèle V9 existant (contraintes à faire évoluer)

`V9__create_course_sessions_and_attendance.sql` :

| Table | Module | Points bloquants pour cette tranche |
|---|---|---|
| `course_session` | `coursesession` | `status` PLANNED/OPEN/CLOSED, `CHECK chk_course_session_open_state` lie `opened_at`/`closed_at` au statut. Inchangée par V10. |
| `session_class` | `coursesession` | Inchangée. |
| `attendance_checkpoint` | `coursesession` | **`uq_attendance_checkpoint_session UNIQUE (course_session_id)`** = **un seul** point de contrôle par séance. Pas de `label`, `type`, `status`, `display_order`, `required`, colonnes auteur. **À faire évoluer.** |
| `attendance_record` | `attendance` | `source` limité à `DYNAMIC_QR`/`SHORT_CODE`. Pas de `status`, `late_minutes`, `comment`, `recorded_by_id`, `corrected_*`, `cancelled_*`. **À faire évoluer.** `uq_attendance_record_checkpoint_enrollment` conservée. |

Entité JPA `AttendanceCheckpoint` : `@OneToOne` vers `CourseSession`,
`openedAt`/`closedAt` seulement, pas de statut propre (« l'état vient de
la séance »). Entité `AttendanceRecord` : `enrollmentId`,
`studentUserId`, `recordedAt`, `source` seulement.

### 1.3 Contrats et ports existants réutilisés

- `coursesession.CourseSessionDirectory` : `resolve(publicId, READ|MANAGE)`
  → `SessionAccess { GRANTED|NOT_FOUND|FORBIDDEN, SessionRef }`,
  `findForAttendance(publicId)` (sans contrôle d'accès, réservé au module
  `attendance` après validation d'un jeton). `SessionRef` porte
  `checkpointInternalId` / `checkpointPublicId` **unique** →
  **à faire évoluer** vers une liste de checkpoints.
- `coursesession.internal.CourseSessionAccessGuard` : point unique de
  décision fine (contexte Spring Security). Rôles : ADMIN/SUPER_ADMIN
  global ; SCHOOL_ADMINISTRATION lecture seule ; PEDAGOGICAL_MANAGER
  périmètre (`AcademicScopeDirectory`) ; TEACHER = ses séances ;
  STUDENT aucun accès.
- `enrollment.EnrollmentDirectory` : `findByPublicId`,
  `findActiveEnrollmentsForUserOn(userPublicId, date)`,
  `describeAttendee(enrollmentInternalId)` → `AttendeeRef`,
  `countActiveEnrollmentsInClasses(classPublicIds)`. **À étendre** :
  lister les inscriptions actives d'une (ou plusieurs) classe(s) pour
  bâtir l'effectif attendu nominatif d'un rapport.
- `academic.AcademicScopeDirectory` : `hasGlobalScope`, `isClassInScope`,
  `visibleClassGroupIds`.
- `academic.ClassGroupDirectory` : `findByPublicId` / `findByInternalId`
  → `ClassGroupRef`.
- `identity.UserDirectory` : `findByPublicId` / `findByInternalId` /
  `findName`. `identity.CurrentUserResolver` : `resolveInternalId(sub)`.
- `alternation` : **aucun port public** ; la résolution
  SCHOOL/COMPANY/UNKNOWN n'est exposée que par HTTP
  (`GET /api/v1/alternation/enrollments/{id}/context?date=`) via
  `AlternationContextService` (interne). **Nouveau port à créer.**
- `shared.web.ApiError` (format d'erreur commun),
  `shared.web.GlobalExceptionHandler` (VALIDATION_ERROR, ACCESS_DENIED
  403 neutre, RESOURCE_NOT_FOUND, INTERNAL_ERROR).
- `shared.BaseEntity` : `id` (auto), `publicId` (BINARY(16), généré en
  `@PrePersist`), `version` (`@Version`).
- `shared.config.ClockConfig` : bean `java.time.Clock` injectable
  (`@ConditionalOnMissingBean`, horloge figée en test).
- Audit : événements applicatifs publics par module
  (`CourseSessionChangeEvent`, `AttendanceChangeEvent`) →
  `audit.internal.*AuditListener` (`@EventListener` + `REQUIRES_NEW`).
  `AuditEvent(occurredAt, actorUserId, action, category, resourceType,
  result)` + `setResourcePublicId`, `setReason`. **Dette
  transactionnelle** (`@EventListener` synchrone en `REQUIRES_NEW`)
  connue et **non résolue** dans cette tranche (migration globale vers
  `@TransactionalEventListener(AFTER_COMMIT)` à planifier — cf. §13).
- `AttendanceTokenService` (Redis) : `issue(sessionPublicId)` → jeton
  opaque + code court, clés `esic:attendance:token:{t}`,
  `esic:attendance:code:{c}`, pointeur
  `esic:attendance:session:{sessionPublicId}` → `token\ncode` ; TTL
  `app.attendance.token-ttl` (défaut `PT30S`) ; rotation ; invariant du
  pointeur courant ; `invalidateSession(sessionPublicId)` à la fermeture.
  **À faire évoluer** : jeton **par point de contrôle**.

### 1.4 Frontend existant réutilisé

- `/sessions` (`SessionList`, `SessionForm`, `SessionDetail`),
  `/attendance` (`AttendanceCheckIn`) — routes enfants de la coquille
  authentifiée `AppShell`.
- `SessionsApiService` (une méthode par endpoint réel),
  `sessions.models.ts`, `session-errors.ts` (`toSessionError`, liste
  blanche explicite de codes).
- `RoleContextService.effectiveRoles()` (restreint l'affichage, jamais
  n'élargit le JWT), `roleGuard`, `core/models/api-error`
  (`normalizeHttpError`, `SAFE_FALLBACK_MESSAGE`), `NotificationService`.
- `angularx-qrcode@21.0.5` déjà présent (`QrDisplay`).
- JWT + contexte de rôle **en mémoire seule** (RG-085) ; aucun
  `localStorage` / `sessionStorage`.

### 1.5 Règles déjà fixées par le cahier des charges

- docs/02 §17.2/§17.3 : 4 points de contrôle types
  (`MORNING_ARRIVAL`, `MORNING_BREAK_RETURN`, `AFTERNOON_ARRIVAL`,
  `AFTERNOON_BREAK_RETURN`). **Cette tranche ne les impose pas** : elle
  généralise à N points de contrôle typés `START` / `END` / `CUSTOM`
  (décision §4.A ; les 4 types du cahier restent réalisables via des
  `CUSTOM` libellés et un ordre d'affichage).
- docs/02 §17.4/§17.5 : demi-journée = 2 contrôles cohérents ; journée =
  4 contrôles. docs/02 §24.2 : « Deux demi-journées validées = une
  journée ; une demi-journée validée = 0,5 journée ».
- docs/02 §17.6 : tolérance de retard 0–15 min `PRESENT`, 16–30 `LATE`,
  > 30 `LATE` + validation manuelle. **Divergence documentée** (§4.B) :
  seuil unique configurable `app.attendance.late-threshold` (défaut
  `PT10M` imposé par le plan de tranche), au-delà → `LATE` ; la
  gradation 15/30 min du cahier est reportée.
- docs/02 §21.9 / RG-075/076 : justificatif accepté →
  `ABSENT → EXCUSED` ; n'efface jamais l'historique de l'absence.
- docs/02 §21.3/§21.4 : formats JPEG/PNG/PDF, 5 Mo. **Hors périmètre**
  ici : le justificatif de cette tranche est **une métadonnée métier**
  (catégorie, référence externe, commentaire), **sans fichier** (règle
  explicite du plan de tranche : « ne pas inventer de stockage S3 »).
- docs/02 §8.4 / RG : une période en entreprise (`COMPANY`) n'est
  **jamais** comptée comme une absence. `UNKNOWN` signalé, jamais
  transformé en absence certaine.
- docs/04 §19.2 : statuts `PRESENT`/`LATE`/`PARTIAL`/`TO_CONFIRM`/
  `ABSENT`/`EXCUSED`. **Divergence documentée** (§4.B) : cette tranche
  implémente `PRESENT`/`LATE`/`ABSENT`/`EXCUSED_ABSENCE`/`CANCELLED`
  (liste imposée par le plan de tranche + annulation logique) ;
  `PARTIAL`/`TO_CONFIRM` reportés ; `EXCUSED_ABSENCE` ≡ `EXCUSED` du
  cahier.
- docs/04 §19.4 : `attendance_correction` append-only, une correction
  s'ajoute, ne remplace jamais.
- docs/04 §30.3 / cahier §30.3 : audit sans mot de passe, secret, jeton
  complet, donnée biométrique, **IP dans l'audit métier**, ni nom /
  numéro étudiant / commentaire libre complet.
- RG-015 : présence unique par (point de contrôle, inscription).

---

## 2. Périmètre livré / non livré

### 2.1 Livré (bout en bout backend + frontend)

1. **N points de contrôle par séance** (`START` / `END` / `CUSTOM`),
   cycle `PLANNED → OPEN → CLOSED` / `CANCELLED`, ordre d'affichage,
   caractère obligatoire / optionnel.
2. **Présence manuelle** (formateur sur sa séance ; gestionnaires).
3. **Correction** d'une présence (statut, retard, commentaire) avec
   motif obligatoire + **annulation logique** (`CANCELLED`, ligne
   conservée).
4. **Justificatif métier** (catégorie, référence externe, commentaire) —
   sans fichier — déposé par l'apprenant sur une absence, examiné
   (`ACCEPTED` / `REJECTED`) par un gestionnaire.
5. **Calcul d'assiduité** : taux de présence, retards, absences
   injustifiées, absences justifiées, contexte `COMPANY` exclu,
   `UNKNOWN` signalé ; **dérivation des demi-journées** (§4.C).
6. **Rapports** par séance / classe / apprenant + **synthèse**.
7. **Exports CSV** (UTF-8, en-tête, séparateur `;`, neutralisation
   d'injection de formule).
8. **Tableau de bord d'assiduité** (cartes de synthèse + file des
   justificatifs en attente).
9. **Écrans Angular** : fiche séance enrichie ; espace apprenant
   « Mes présences » ; section « Suivi d'assiduité » (rapports +
   justificatifs).
10. Tests backend + frontend, sécurité, audit, procédure de
    démonstration.

### 2.2 Non livré (ni simulé)

Planning général ; import CSV apprenants ; scan caméra ; QR fixe de
salle ; contrôle réseau CIDR ; WebAuthn ; refonte graphique globale ;
**stockage de fichiers justificatifs** ; notifications externes ;
microservices ; `PARTIAL` / `TO_CONFIRM` ; table
`daily_attendance_summary` matérialisée (calcul à la volée) ; gradation
de retard 15/30 min du cahier ; détection d'anomalies IA ;
`anonymous_attendance_stat` ; résolution de la dette transactionnelle
d'audit.

---

## 3. Matrice des rôles (Checkpoint 3)

Décision fine calculée **côté serveur** à partir du JWT / contexte Spring
Security + identité de la ressource + périmètre. Jamais d'après un rôle /
identifiant transmis par le client. Le frontend ne fait que restreindre
l'ergonomie (`RoleContextService.effectiveRoles()`).

| Capacité | SUPER_ADMIN / ADMIN | SCHOOL_ADMINISTRATION | PEDAGOGICAL_MANAGER | TEACHER | STUDENT | Anonyme |
|---|---|---|---|---|---|---|
| Lire checkpoints / présences d'une séance | ✅ global | ✅ global | ✅ périmètre | ✅ ses séances | ❌ | ❌ |
| Créer / ouvrir / fermer / annuler un checkpoint | ✅ global | ❌ | ✅ périmètre | ✅ ses séances | ❌ | ❌ |
| Émettre un jeton d'un checkpoint ouvert | ✅ global | ❌ | ✅ périmètre | ✅ ses séances | ❌ | ❌ |
| Valider une présence (QR / code) | ❌ | ❌ | ❌ | ❌ | ✅ (son émargement) | ❌ |
| Présence manuelle | ✅ global | ✅ global | ✅ périmètre | ✅ ses séances | ❌ | ❌ |
| Corriger / annuler une présence | ✅ global | ✅ global | ✅ périmètre | ✅ ses séances | ❌ | ❌ |
| Lire l'historique d'une présence | ✅ global | ✅ global | ✅ périmètre | ✅ ses séances | ✅ (les siennes, via `/me`) | ❌ |
| `GET /me/attendance*` (ses présences) | — | — | — | — | ✅ (sujet JWT) | ❌ |
| Déposer / modifier (si `PENDING`) un justificatif | ❌ (voir note) | ❌ (voir note) | ❌ (voir note) | ❌ | ✅ sur ses absences | ❌ |
| Lister / examiner les justificatifs | ✅ global | ✅ global | ✅ périmètre | ✅ ceux de ses séances (lecture) | ❌ | ❌ |
| Rapports séance / classe / apprenant / synthèse | ✅ global | ✅ global | ✅ périmètre | ✅ limité à ses séances / classes | ❌ | ❌ |
| Export CSV des rapports | ✅ global | ✅ global | ✅ périmètre | ✅ limité à ses séances | ❌ | ❌ |

Notes :

- **Dépôt de justificatif** : réservé à l'apprenant lui-même (le staff
  peut déposer « pour le compte de » d'après docs/02 §21.1, mais c'est
  **reporté** — décision conservative : un seul parcours de dépôt dans
  cette tranche).
- **TEACHER et justificatifs** : lecture seule des justificatifs
  rattachés à une absence d'une **de ses séances** ; l'examen
  (ACCEPTED / REJECTED) est réservé à
  SCHOOL_ADMINISTRATION / PEDAGOGICAL_MANAGER (périmètre) / ADMIN /
  SUPER_ADMIN (docs/02 §21.7).
- **Ressource inconnue** : `404` sans divulgation d'existence ; hors
  périmètre `403` (jamais de « existe mais interdit » exploitable — même
  posture que `CourseSessionAccessGuard` aujourd'hui).

Implémentation : réutilisation de `CourseSessionAccessGuard`
(`isAllowed(teacherUserId, classPublicIds, READ|MANAGE, subject)`),
exposé au module `attendance` via `CourseSessionDirectory` étendu
(§5.1). Nouveau garde `AttendanceReportAccessGuard` interne à
`attendance` pour la lecture des rapports (mêmes règles + filtrage de
périmètre des classes via `AcademicScopeDirectory`).

---

## 4. Décisions de règles métier

### 4.A — N points de contrôle par séance

- V10 **supprime** `uq_attendance_checkpoint_session` et enrichit
  `attendance_checkpoint` : `label`, `checkpoint_type`
  (`START`|`END`|`CUSTOM`), `display_order`, `status`
  (`PLANNED`|`OPEN`|`CLOSED`|`CANCELLED`), `required` (BOOLEAN),
  `opened_at` / `closed_at` (déjà présents), colonnes auteur
  (`created_by_id`, `updated_by_id`).
- **Compatibilité V9** : à la création d'une séance, `CourseSessionService`
  continue de créer automatiquement **un** checkpoint `START`
  (`label = "Arrivée"`, `display_order = 0`, `required = true`,
  `status = PLANNED`). Le parcours d'émargement actuel (une séance = un
  point de contrôle ouvert avec la séance) reste vrai par défaut.
- **Cycle de vie** :
  - création → `PLANNED` ;
  - `POST .../open` → `OPEN` (`opened_at` = horloge) ; possible seulement
    si la séance est `OPEN` ;
  - `POST .../close` → `CLOSED` (`closed_at` = horloge) ; possible
    seulement depuis `OPEN` ;
  - `POST .../cancel` → `CANCELLED` (motif obligatoire) ; possible depuis
    `PLANNED` ou `OPEN` ; un checkpoint `CANCELLED` n'accepte plus
    d'émargement et est exclu des dénominateurs de rapport ;
  - **fermeture de la séance** → tous les checkpoints `OPEN` passent
    `CLOSED` (déjà le cas pour le checkpoint unique ; généralisé).
  - Pas de réouverture d'un checkpoint `CLOSED` / `CANCELLED`.
- **Ordre** : `UNIQUE (course_session_id, display_order)` ; le service
  attribue `max(display_order) + 1` si non fourni.
- **Type `START` / `END`** : au plus un `START` et un `END` par séance
  (garde applicative, pas de contrainte SQL — évolutivité). Le `START`
  auto-créé peut être complété par un `END` et des `CUSTOM`.
- **Jeton d'émargement par checkpoint** : voir §5.3.

### 4.B — Statuts de présence, source, retard

- `attendance_record.status` : `PRESENT` | `LATE` | `ABSENT` |
  `EXCUSED_ABSENCE` | `CANCELLED`. Persisté en texte.
- `attendance_record.source` : `DYNAMIC_QR` | `SHORT_CODE` | `MANUAL` |
  `CORRECTION`. Persisté en texte.
- **Validation QR / code** (parcours apprenant, inchangé dans son
  principe) : crée toujours une présence `PRESENT` **ou** `LATE` :
  - `refTime` = `course_session.starts_at` (heure de référence
    planifiée, pas l'heure d'ouverture du checkpoint) ;
  - `delta = now - refTime` calculé côté serveur avec `Clock` ;
  - `delta <= app.attendance.late-threshold` → `PRESENT`,
    `late_minutes = NULL` ;
  - `delta > seuil` → `LATE`,
    `late_minutes = ceil(delta_en_minutes)` (borné à un `INT`).
  - `app.attendance.late-threshold` : `Duration`, défaut `PT10M`,
    **doit être ≥ 0** ; démarrage refusé sinon (aligné sur
    `AttendanceTokenService` pour le TTL).
- **Présence manuelle** : `source = MANUAL`, `recorded_by_id` = acteur,
  `status` ∈ { `PRESENT`, `LATE`, `ABSENT` } fourni explicitement,
  `comment` **obligatoire** (`ATT_MANUAL_REASON_REQUIRED` sinon),
  `late_minutes` optionnel (cohérent avec `status = LATE`).
  `EXCUSED_ABSENCE` **n'est pas** saisissable directement : il résulte
  d'un justificatif accepté.
- **Correction** : `source` de la ligne inchangé, `status` /
  `late_minutes` / `comment` mis à jour, `last_corrected_at` +
  `corrected_by_id` renseignés, **motif obligatoire**
  (`ATT_CORRECTION_REASON_REQUIRED`), **une ligne
  `attendance_correction` ajoutée** (append-only). Une correction ne
  passe **jamais** par `EXCUSED_ABSENCE` (réservé au workflow
  justificatif). Verrou optimiste (`@Version`) : collision → `409`.
- **Annulation logique** : `status = CANCELLED`, `cancelled_at` +
  motif via `attendance_correction` (action `CANCELLED`). La ligne
  reste, la contrainte `(checkpoint, enrollment)` reste occupée
  (décision conservative : pas de ré-émargement après annulation — la
  correction est le chemin de « revive »). Documenté comme limite.
- **Contrainte `uq_attendance_record_checkpoint_enrollment` conservée**
  telle quelle : autorité anti-double présence, tous statuts confondus.
  Violation concurrente → `409 ATT_ALREADY_RECORDED` (jamais `500`),
  isolation par `AttendanceRecordPersister` (`REQUIRES_NEW`, déjà en
  place) — étendue aux insertions manuelles.

### 4.C — Absences dérivées, justificatifs, demi-journées

- **Aucune** ligne `ABSENT` n'est créée automatiquement pour tout
  l'effectif. Les absents sont **dérivés** dans les rapports :
  `attendus (roster) − présences valides (PRESENT|LATE|EXCUSED_ABSENCE)`
  par point de contrôle, hors inscriptions dont le contexte
  d'alternance est `COMPANY` ce jour-là.
- **Ancrage d'un justificatif** : un justificatif référence une
  `attendance_record`. Un apprenant absent n'a pas de ligne. **Décision
  conservative** (déviation documentée par rapport à
  `POST /me/attendance/{attendanceId}/justification`) : le dépôt se fait
  via `POST /api/v1/me/attendance/justifications` avec
  `{ checkpointPublicId, category, externalReference?, comment }`. Le
  serveur, dans une transaction :
  1. résout l'inscription de l'apprenant pour la séance (même logique
     que la validation QR : sujet JWT → inscription active dont la
     classe est rattachée à la séance ; 0 ou > 1 → refus) ;
  2. si une `attendance_record` existe pour `(checkpoint, enrollment)` :
     - statut `ABSENT` → réutilisée ;
     - statut `PRESENT` / `LATE` / `EXCUSED_ABSENCE` / `CANCELLED` →
       `409 ATT_RECORD_INVALID_STATE` (« aucune absence à justifier ») ;
  3. sinon crée une `attendance_record` `status = ABSENT`,
     `source = MANUAL`, `recorded_by_id` = l'apprenant lui-même,
     `recorded_at` = horloge, + `attendance_correction`
     `CREATED_MANUALLY` (motif = « dépôt de justificatif ») ;
  4. crée la `attendance_justification` `status = PENDING` +
     `attendance_correction` `JUSTIFICATION_ADDED`.
- **Une seule** justification « active » (PENDING ou ACCEPTED) par
  absence : colonne générée `active_justification_key =
  IF(status <> 'REJECTED', attendance_record_id, NULL)` + `UNIQUE`.
  Après `REJECTED`, un nouveau dépôt est possible.
- **Modification** par l'apprenant : seulement tant que `PENDING`
  (`PUT /me/attendance/justifications/{id}` ou re-POST) ;
  `attendance_correction` `JUSTIFICATION_UPDATED`.
- **Examen** (`POST /api/v1/attendance/justifications/{id}/review`,
  body `{ decision: ACCEPTED|REJECTED, decisionReason }`,
  `decisionReason` obligatoire si `REJECTED`) :
  - `ACCEPTED` → justification `ACCEPTED` + la présence passe
    `ABSENT → EXCUSED_ABSENCE` (jamais `PRESENT`) +
    `attendance_correction` `JUSTIFICATION_REVIEWED` + `STATUS_CORRECTED` ;
  - `REJECTED` → justification `REJECTED` + la présence **reste ou
    revient** à `ABSENT` (si elle avait été passée `EXCUSED_ABSENCE`
    par une acceptation antérieure — impossible ici car une seule active,
    mais la règle est explicite) + `attendance_correction`
    `JUSTIFICATION_REVIEWED` ;
  - double examen concurrent → verrou optimiste → `409
    ATT_JUSTIFICATION_INVALID_STATE`, jamais `500`.
- **Formule de demi-journée** (grain : `(inscription, date civile de la
  séance dans son fuseau)`), conforme à la recommandation du plan de
  tranche et compatible docs/02 §17.4/§24.2 :
  - chaque **point de contrôle** est classé matin / après-midi selon
    l'heure locale de la séance (`course_session.time_zone_id`) :
    l'heure de référence du checkpoint = `starts_at` de la séance pour un
    `START`, `ends_at` pour un `END`, et pour un `CUSTOM` son
    `opened_at` s'il existe sinon `starts_at` de la séance ;
    **< 13:00 local → matin**, **≥ 13:00 local → après-midi** ;
  - une séance dont les points de contrôle encadrent 13:00 contribue aux
    **deux** demi-journées ;
  - une demi-journée est **présente** si **tous** ses points de contrôle
    **obligatoires** (`required = true`, non `CANCELLED`) sont
    « satisfaits » = il existe une `attendance_record` de statut
    `PRESENT`, `LATE` ou `EXCUSED_ABSENCE` pour cette inscription ;
  - `LATE` compte comme présent pour la demi-journée mais est **compté
    séparément** (indicateur « retards ») ;
  - `EXCUSED_ABSENCE` : la demi-journée est comptée « excusée » — exclue
    du **taux d'absence injustifiée**, mais visible ; elle **ne compte
    pas** comme présente dans le taux de présence brut ;
  - contexte d'alternance `COMPANY` (résolu par inscription et par date,
    §5.4) : la demi-journée est **exclue du dénominateur scolaire** ;
  - contexte `UNKNOWN` : demi-journée **remontée séparément**
    (`unknownContextHalfDays`), **jamais** comptée comme absence
    certaine ;
  - `2 demi-journées présentes = 1 journée`, `1 = 0,5` (docs/02 §24.2).
- **Aucune** table matérialisée : calcul à la volée dans
  `AttendanceReportService`, `Clock` injecté, déterministe.

### 4.D — Divergences assumées vs documentation existante

| Sujet | Documentation | Décision de la tranche | Raison |
|---|---|---|---|
| Types de checkpoint | docs/02 §17.3 : 4 types fixes | `START`/`END`/`CUSTOM` + ordre + libellé | Généralisation demandée par le plan ; les 4 types restent réalisables |
| Statuts de présence | docs/04 §19.2 : + `PARTIAL`/`TO_CONFIRM` | `PRESENT`/`LATE`/`ABSENT`/`EXCUSED_ABSENCE`/`CANCELLED` | Liste imposée par le plan + annulation logique ; `PARTIAL`/`TO_CONFIRM` reportés |
| Nom du statut excusé | docs/04 : `EXCUSED` | `EXCUSED_ABSENCE` | Liste imposée par le plan ; sémantique identique |
| Seuil de retard | docs/02 §17.6 : 0–15 `PRESENT`, 16–30 `LATE`, > 30 manuel | Seuil unique `app.attendance.late-threshold` (`PT10M`) | Imposé par le plan de tranche ; gradation reportée |
| Justificatif | docs/02 §21.3/§21.4 : fichier JPEG/PNG/PDF 5 Mo | Métadonnée métier sans fichier | Imposé par le plan (« ne pas inventer de stockage ») |
| Dépôt justificatif | `POST /me/attendance/{attendanceId}/justification` | `POST /me/attendance/justifications` avec `checkpointPublicId` | Un absent n'a pas d'`attendanceId` ; la ligne `ABSENT` est créée à la volée |
| Table `daily_attendance_summary` | docs/04 §19.5 (facultative) | Non créée, calcul à la volée | Simplicité, pas de désynchronisation |

---

## 5. Conception backend

### 5.1 Migration V10 (Checkpoint 1)

Fichier unique `V10__extend_attendance_management_and_reporting.sql`,
**additive**, MySQL 8, `ENGINE=InnoDB`, `utf8mb4` /
`utf8mb4_0900_ai_ci` (alignés V1–V9).

**A. `attendance_checkpoint` (module `coursesession`)**

```sql
ALTER TABLE attendance_checkpoint
  DROP INDEX uq_attendance_checkpoint_session;

ALTER TABLE attendance_checkpoint
  ADD COLUMN label          VARCHAR(120)    NOT NULL DEFAULT 'Arrivée' AFTER course_session_id,
  ADD COLUMN checkpoint_type VARCHAR(20)    NOT NULL DEFAULT 'START'   AFTER label,
  ADD COLUMN display_order   INT            NOT NULL DEFAULT 0         AFTER checkpoint_type,
  ADD COLUMN status          VARCHAR(20)    NOT NULL DEFAULT 'PLANNED' AFTER display_order,
  ADD COLUMN required        BOOLEAN        NOT NULL DEFAULT TRUE      AFTER status,
  ADD COLUMN cancel_reason   VARCHAR(500)   NULL                      AFTER closed_at,
  ADD COLUMN created_by_id   BIGINT UNSIGNED NULL                     AFTER created_at,
  ADD COLUMN updated_by_id   BIGINT UNSIGNED NULL                     AFTER updated_at;

-- Reprise déterministe des lignes V9 (statut dérivé de opened_at/closed_at).
UPDATE attendance_checkpoint
   SET status = CASE
       WHEN closed_at IS NOT NULL THEN 'CLOSED'
       WHEN opened_at IS NOT NULL THEN 'OPEN'
       ELSE 'PLANNED' END;
-- (label/type/order/required gardent leur défaut : 'Arrivée'/START/0/TRUE.)

ALTER TABLE attendance_checkpoint
  ALTER COLUMN label DROP DEFAULT,
  ALTER COLUMN checkpoint_type DROP DEFAULT,
  ALTER COLUMN display_order DROP DEFAULT,
  ALTER COLUMN status DROP DEFAULT,
  ALTER COLUMN required DROP DEFAULT;

ALTER TABLE attendance_checkpoint
  ADD CONSTRAINT uq_attendance_checkpoint_order UNIQUE (course_session_id, display_order),
  ADD CONSTRAINT chk_attendance_checkpoint_type
      CHECK (checkpoint_type IN ('START','END','CUSTOM')),
  ADD CONSTRAINT chk_attendance_checkpoint_status
      CHECK (status IN ('PLANNED','OPEN','CLOSED','CANCELLED')),
  ADD CONSTRAINT chk_attendance_checkpoint_open_state CHECK (
      (status = 'PLANNED'   AND opened_at IS NULL) OR
      (status = 'OPEN'      AND opened_at IS NOT NULL AND closed_at IS NULL) OR
      (status = 'CLOSED'    AND opened_at IS NOT NULL AND closed_at IS NOT NULL) OR
      (status = 'CANCELLED')),
  ADD CONSTRAINT fk_attendance_checkpoint_created_by
      FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_attendance_checkpoint_updated_by
      FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT;

CREATE INDEX idx_attendance_checkpoint_status ON attendance_checkpoint (status);
```

> `DROP DEFAULT` : les valeurs par défaut n'existent que le temps du
> backfill ; l'application fournit toujours ces colonnes explicitement.
> `required` garde un défaut applicatif `true` côté entité.

**B. `attendance_record` (module `attendance`)**

```sql
ALTER TABLE attendance_record
  ADD COLUMN status           VARCHAR(24)     NOT NULL DEFAULT 'PRESENT' AFTER source,
  ADD COLUMN late_minutes     INT             NULL                       AFTER status,
  ADD COLUMN comment          VARCHAR(500)    NULL                       AFTER late_minutes,
  ADD COLUMN recorded_by_id   BIGINT UNSIGNED NULL                       AFTER student_user_id,
  ADD COLUMN last_corrected_at TIMESTAMP(6)   NULL                       AFTER updated_at,
  ADD COLUMN corrected_by_id  BIGINT UNSIGNED NULL                       AFTER last_corrected_at,
  ADD COLUMN cancelled_at     TIMESTAMP(6)    NULL                       AFTER corrected_by_id;

UPDATE attendance_record SET status = 'PRESENT';   -- toutes les lignes V9 sont des émargements réussis

ALTER TABLE attendance_record
  ALTER COLUMN status DROP DEFAULT,
  ADD CONSTRAINT chk_attendance_record_status
      CHECK (status IN ('PRESENT','LATE','ABSENT','EXCUSED_ABSENCE','CANCELLED')),
  ADD CONSTRAINT chk_attendance_record_source
      CHECK (source IN ('DYNAMIC_QR','SHORT_CODE','MANUAL','CORRECTION')),
  ADD CONSTRAINT chk_attendance_record_late
      CHECK (late_minutes IS NULL OR late_minutes >= 0),
  ADD CONSTRAINT fk_attendance_record_recorded_by
      FOREIGN KEY (recorded_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_attendance_record_corrected_by
      FOREIGN KEY (corrected_by_id) REFERENCES user_account (id) ON DELETE RESTRICT;

CREATE INDEX idx_attendance_record_status ON attendance_record (status);
```

> `uq_attendance_record_checkpoint_enrollment` **inchangée**.
> `student_user_id` reste `NOT NULL` (V9) : pour une présence manuelle,
> le service résout le compte apprenant depuis l'inscription (toujours
> disponible via `EnrollmentDirectory`).

**C. `attendance_correction` (module `attendance`, append-only)**

```sql
CREATE TABLE attendance_correction (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id             BINARY(16)      NOT NULL,
    attendance_record_id  BIGINT UNSIGNED NOT NULL,
    action                VARCHAR(30)     NOT NULL,
    previous_status       VARCHAR(24)     NULL,
    new_status            VARCHAR(24)     NULL,
    previous_late_minutes INT             NULL,
    new_late_minutes      INT             NULL,
    previous_comment      VARCHAR(500)    NULL,
    new_comment           VARCHAR(500)    NULL,
    reason                VARCHAR(500)    NOT NULL,
    actor_user_id         BIGINT UNSIGNED NULL,
    occurred_at           TIMESTAMP(6)    NOT NULL,
    created_at            TIMESTAMP(6)    NOT NULL,
    version               BIGINT UNSIGNED NOT NULL DEFAULT 0,
    CONSTRAINT uq_attendance_correction_public_id UNIQUE (public_id),
    CONSTRAINT chk_attendance_correction_action CHECK (action IN (
        'CREATED_MANUALLY','STATUS_CORRECTED','CANCELLED',
        'JUSTIFICATION_ADDED','JUSTIFICATION_UPDATED','JUSTIFICATION_REVIEWED')),
    CONSTRAINT chk_attendance_correction_late CHECK (
        (previous_late_minutes IS NULL OR previous_late_minutes >= 0) AND
        (new_late_minutes IS NULL OR new_late_minutes >= 0)),
    CONSTRAINT fk_attendance_correction_record
        FOREIGN KEY (attendance_record_id) REFERENCES attendance_record (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_correction_actor
        FOREIGN KEY (actor_user_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_attendance_correction_record ON attendance_correction (attendance_record_id, occurred_at);
```

**D. `attendance_justification` (module `attendance`, métadonnée sans fichier)**

```sql
CREATE TABLE attendance_justification (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id             BINARY(16)      NOT NULL,
    attendance_record_id  BIGINT UNSIGNED NOT NULL,
    category              VARCHAR(20)     NOT NULL,
    external_reference    VARCHAR(120)    NULL,
    comment               VARCHAR(1000)   NOT NULL,
    status                VARCHAR(16)     NOT NULL,
    submitted_at          TIMESTAMP(6)    NOT NULL,
    submitted_by_id       BIGINT UNSIGNED NOT NULL,
    reviewed_at           TIMESTAMP(6)    NULL,
    reviewed_by_id        BIGINT UNSIGNED NULL,
    decision_reason       VARCHAR(500)    NULL,
    created_at            TIMESTAMP(6)    NOT NULL,
    updated_at            TIMESTAMP(6)    NOT NULL,
    version               BIGINT UNSIGNED NOT NULL DEFAULT 0,
    active_justification_key BIGINT UNSIGNED GENERATED ALWAYS AS (
        IF(status <> 'REJECTED', attendance_record_id, NULL)) VIRTUAL,
    CONSTRAINT uq_attendance_justification_public_id UNIQUE (public_id),
    CONSTRAINT uq_attendance_justification_active UNIQUE (active_justification_key),
    CONSTRAINT chk_attendance_justification_category CHECK (category IN (
        'MEDICAL','TRANSPORT','FAMILY','ADMINISTRATIVE','OTHER')),
    CONSTRAINT chk_attendance_justification_status CHECK (status IN (
        'PENDING','ACCEPTED','REJECTED')),
    CONSTRAINT fk_attendance_justification_record
        FOREIGN KEY (attendance_record_id) REFERENCES attendance_record (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_justification_submitted_by
        FOREIGN KEY (submitted_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_justification_reviewed_by
        FOREIGN KEY (reviewed_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_attendance_justification_status ON attendance_justification (status, submitted_at);
CREATE INDEX idx_attendance_justification_record ON attendance_justification (attendance_record_id);
```

Frontières Spring Modulith : `attendance_checkpoint` reste au module
`coursesession` ; `attendance_record` / `attendance_correction` /
`attendance_justification` au module `attendance`. Les FK inter-modules
(`attendance_record.attendance_checkpoint_id`) restent de simples valeurs
techniques résolues par ports publics. **Aucune donnée métier insérée.**

Tests `@DataJpaTest` (Checkpoint 1) : reprise V9→V10 déterministe,
`CHECK` de statut / source / retard, unicité `(course_session_id,
display_order)`, unicité `active_justification_key` (une seule active,
re-dépôt après `REJECTED`), FK `RESTRICT`.

### 5.2 Domaine `coursesession` — checkpoints (Checkpoint 2)

- `AttendanceCheckpoint` : + `label`, `checkpointType` (enum
  `AttendanceCheckpointType` public : `START`/`END`/`CUSTOM`),
  `displayOrder`, `status` (enum `AttendanceCheckpointStatus` public :
  `PLANNED`/`OPEN`/`CLOSED`/`CANCELLED`), `required`, `cancelReason`,
  `createdById`, `updatedById`. Méthodes `open(at, actorId)`,
  `close(at, actorId)`, `cancel(reason, actorId)`.
- `CourseSession` : plus de `@OneToOne` checkpoint ; `@OneToMany` (déjà
  le cas pour `classes`). `open()` : ouvre le checkpoint `START` s'il est
  `PLANNED` (compat). `close()` : ferme tous les checkpoints `OPEN`.
- Nouveau `AttendanceCheckpointService` (`coursesession.internal`) :
  `list(sessionPublicId, caller)`, `create(sessionPublicId, req, caller)`,
  `open/close/cancel(sessionPublicId, checkpointPublicId, caller)`.
  Contrôle d'accès via `CourseSessionAccessGuard` (READ pour list,
  MANAGE pour le reste). Publie `AttendanceCheckpointChangeEvent` (§5.5).
- Nouveau `AttendanceCheckpointController`
  (`/api/v1/sessions/{sessionId}/checkpoints...`), `@PreAuthorize` repris
  de `CourseSessionWeb` (READ / MANAGE).
- `CourseSessionResponse` : `checkpointPublicId` / `checkpointOpen`
  (uniques) **conservés** pour compatibilité, calculés depuis le
  `START` ; + nouveau champ `checkpoints: List<CheckpointView>` (id,
  label, type, order, status, required, openedAt, closedAt).
- Port `CourseSessionDirectory.SessionRef` : `checkpointInternalId` /
  `checkpointPublicId` / `checkpointOpen` (uniques) **remplacés** par
  `List<CheckpointRef>` `{ internalId, publicId, type, status, required,
  displayOrder, openedAt }`. Méthode ajoutée
  `findCheckpointForAttendance(sessionPublicId, checkpointPublicId)` →
  `Optional<CheckpointRef>` (sans contrôle d'accès — capacité = jeton).
  `AttendanceService` adapté.
- `DefaultCourseSessionDirectory` : construit la liste des checkpoints
  triée par `displayOrder`.

### 5.3 Domaine `attendance` — présence / correction / justificatif

- `AttendanceRecord` : + `status` (enum public `AttendanceStatus`),
  `lateMinutes`, `comment`, `recordedById`, `lastCorrectedAt`,
  `correctedById`, `cancelledAt`. `AttendanceRecordSource` : +
  `MANUAL`, `CORRECTION`.
- `AttendanceService.validate(...)` (parcours apprenant) : le jeton
  résout désormais `(sessionPublicId, checkpointPublicId)` ; vérifie le
  checkpoint `OPEN` ; calcule `PRESENT` / `LATE` (§4.B) ;
  `late_minutes`.
- Nouveau `ManualAttendanceService` /
  `AttendanceCorrectionService` (ou méthodes du service existant) :
  - `recordManual(sessionPublicId, checkpointPublicId, req, caller)` :
    accès MANAGE via `CourseSessionDirectory.resolve` ; `req = { studentProfilePublicId | enrollmentPublicId, status, lateMinutes?, comment }` ;
    `comment` obligatoire ; inscription doit appartenir à une classe de
    la séance et être active ; insertion isolée
    (`AttendanceRecordPersister`, `REQUIRES_NEW`) → `409` si doublon ;
    `attendance_correction` `CREATED_MANUALLY`.
  - `correct(sessionPublicId, attendancePublicId, req, caller)` :
    `req = { status?, lateMinutes?, comment?, reason }` ; `reason`
    obligatoire ; refus si `status` cible = `EXCUSED_ABSENCE` (réservé
    au justificatif) ; `@Version` → `409` ; `attendance_correction`
    `STATUS_CORRECTED`.
  - `cancel(sessionPublicId, attendancePublicId, req, caller)` :
    `req = { reason }` ; `status = CANCELLED`, `cancelled_at` ;
    `attendance_correction` `CANCELLED`. Idempotent : annuler une ligne
    déjà `CANCELLED` → `409 ATT_RECORD_INVALID_STATE`.
  - `history(sessionPublicId, attendancePublicId, caller)` → liste
    `attendance_correction` triée `occurred_at asc`.
- `AttendanceJustificationService` :
  - `submit(checkpointPublicId, req, callerStudentSubject)` (§4.C) ;
  - `updateOwn(justificationPublicId, req, callerStudentSubject)` —
    `PENDING` seulement, propriétaire seulement ;
  - `listOwn(callerStudentSubject, filtres)` /
    `getOwn(justificationPublicId, ...)` ;
  - `listForReview(filtres, caller)` (périmètre) /
    `getForReview(justificationPublicId, caller)` ;
  - `review(justificationPublicId, req, caller)` (§4.C).
- `StudentAttendanceService` (`/me`) : `listOwn(subject, from, to,
  status, page, size, sort)` — jointe aux séances / checkpoints ;
  renvoie les présences réelles **et** les entrées « absence dérivée »
  (`attendancePublicId = null`, `checkpointPublicId` renseigné) pour les
  checkpoints obligatoires fermés d'une séance d'une de ses classes sans
  ligne ; `getOwn(attendancePublicId, subject)` → détail + historique +
  justificatif.
- **Écritures isolées** : toute insertion de `attendance_record` passe
  par `AttendanceRecordPersister` (`REQUIRES_NEW`) ; la retraduction
  `DataIntegrityViolationException` → `409` est étendue.
- `Clock` injecté partout (retard, horodatages, `occurred_at`).

### 5.4 Port `alternation.AlternationDirectory` (nouveau)

```java
package com.esic.connect.alternation;
public interface AlternationDirectory {
    /** Contexte effectif d'une inscription à une date, sans contrôle
     *  d'accès de l'appelant (réservé aux calculs de rapport ; le module
     *  appelant a déjà vérifié le périmètre de la séance / classe). */
    EnrollmentContextView resolveEnrollmentContext(UUID enrollmentPublicId, LocalDate date);
    enum Axis { SCHOOL, COMPANY, UNKNOWN }
    record EnrollmentContextView(Axis effective, Axis pattern, boolean coveredByException) {}
}
```

Impl `alternation.internal.DefaultAlternationDirectory` déléguant à une
méthode `resolveEnrollmentContextUnchecked` extraite de
`AlternationContextService` (le `requireInScope` actuel est sauté pour
ce chemin ; la méthode HTTP existante garde son contrôle). Mapping
`AlternationContext` → `Axis`. `ModularityTests` : `attendance` → `alternation`
(port public uniquement) autorisé, aucune dépendance vers
`alternation.internal`.

### 5.5 Audit (Checkpoint 5)

- `coursesession.CourseSessionResourceType` : + `ATTENDANCE_CHECKPOINT`.
  `CourseSessionChangeAction` : + `CHECKPOINT_CREATED`,
  `CHECKPOINT_OPENED`, `CHECKPOINT_CLOSED`, `CHECKPOINT_CANCELLED`.
  Nouvel événement `AttendanceCheckpointChangeEvent
  { sessionPublicId, checkpointPublicId, actorUserId, action, detail }`
  publié par `AttendanceCheckpointService` et **consommé par
  `attendance`** (purge du jeton Redis à `CHECKPOINT_CLOSED` /
  `_CANCELLED`) **et par `audit`** (catégorie `COURSE_SESSION`).
- `attendance.AttendanceChangeAction` : + `MANUAL_RECORDED`,
  `CORRECTED`, `CANCELLED`, `JUSTIFICATION_SUBMITTED`,
  `JUSTIFICATION_UPDATED`, `JUSTIFICATION_REVIEWED`, `REPORT_EXPORTED`.
  `AttendanceResourceType` : + `ATTENDANCE_JUSTIFICATION`,
  `ATTENDANCE_EXPORT`.
- `AttendanceAuditListener` / `CourseSessionAuditListener` : gèrent les
  nouvelles actions (déjà génériques). Détail non sensible :
  `session=<uuid>;checkpoint=<uuid>`, `record=<uuid>;from=ABSENT;to=EXCUSED_ABSENCE`,
  `justification=<uuid>;decision=ACCEPTED`,
  `report=class;from=...;to=...;rows=NN`. **Jamais** de nom, numéro
  étudiant, commentaire libre complet, motif intégral, jeton, code
  court, IP.
- Export : audit `REPORT_EXPORTED` (type, filtres bornés, nombre de
  lignes) — **jamais** le contenu du CSV.
- Dette transactionnelle : inchangée, documentée (javadoc + §13).

### 5.6 Concurrence et transactions (Checkpoint 5)

| Scénario | Garantie |
|---|---|
| Deux présences manuelles simultanées même (checkpoint, inscription) | `uq_attendance_record_checkpoint_enrollment` → 1×`201`, 1×`409 ATT_ALREADY_RECORDED`, 0×`5xx` |
| Émargement QR + présence manuelle concurrents | idem (même contrainte) |
| Double correction concurrente | `@Version` sur `attendance_record` → 1×`200`, 1×`409 ATT_RECORD_INVALID_STATE` |
| Double examen concurrent d'un justificatif | `@Version` sur `attendance_justification` → 1×`200`, 1×`409 ATT_JUSTIFICATION_INVALID_STATE` |
| Ouverture/fermeture concurrente d'un checkpoint | `@Version` sur `attendance_checkpoint` → 1 succès, 1×`409 ATT_CHECKPOINT_INVALID_STATE` |
| Deux dépôts de justificatif concurrents sur la même absence | `uq_attendance_justification_active` → 1×`201`, 1×`409 ATT_JUSTIFICATION_INVALID_STATE` |

`REQUIRES_NEW` pour les insertions de `attendance_record` ;
`@Transactional` classique (verrou optimiste) pour corrections / examens.

### 5.7 API REST (Checkpoint 4)

Préfixe `/api/v1`. DTO de requête/réponse (jamais d'entité JPA ni
d'identifiant SQL). Bean Validation. Pagination bornée (≤ 100, défaut
20). Tri liste blanche.

**Checkpoints (module `coursesession`)**

| Méthode & URL | Rôles | Corps / réponse |
|---|---|---|
| `GET /api/v1/sessions/{sessionId}/checkpoints` | READ | `CheckpointResponse[]` (trié `displayOrder`) |
| `POST /api/v1/sessions/{sessionId}/checkpoints` | MANAGE | `{ label, type, required?, displayOrder? }` → `201 CheckpointResponse` |
| `POST .../checkpoints/{checkpointId}/open` | MANAGE | `204` |
| `POST .../checkpoints/{checkpointId}/close` | MANAGE | `204` |
| `POST .../checkpoints/{checkpointId}/cancel` | MANAGE | `{ reason }` → `204` |

**Présences (module `attendance`)**

| Méthode & URL | Rôles | Corps / réponse |
|---|---|---|
| `GET /api/v1/sessions/{sessionId}/attendance` | READ | `SessionAttendanceResponse` **enrichi** : par checkpoint, lignes `{ enrollment, profil, numéro, prénom, nom, status, lateMinutes, source, comment, recordedAt, attendancePublicId }` + `expectedCount` + compteurs par statut + `derivedAbsentCount` |
| `GET .../checkpoints/{checkpointId}/attendance-token` **ou** `POST` | MANAGE | conserve l'URL actuelle `POST /api/v1/sessions/{id}/attendance-token` **+** variante par checkpoint `POST .../checkpoints/{checkpointId}/attendance-token` (le `START` reste la cible par défaut de l'ancienne route) |
| `POST /api/v1/sessions/{sessionId}/attendance/manual` | MANAGE | `{ enrollmentPublicId \| studentProfilePublicId, checkpointPublicId, status, lateMinutes?, comment }` → `201` |
| `POST /api/v1/sessions/{sessionId}/attendance/{attendanceId}/correct` | MANAGE | `{ status?, lateMinutes?, comment?, reason }` → `200` |
| `POST /api/v1/sessions/{sessionId}/attendance/{attendanceId}/cancel` | MANAGE | `{ reason }` → `200` |
| `GET /api/v1/sessions/{sessionId}/attendance/{attendanceId}/history` | READ | `AttendanceCorrectionResponse[]` |
| `POST /api/v1/attendance/validate` | STUDENT | inchangé (jeton → checkpoint) |

**Espace apprenant (`/me`, module `attendance`, rôle STUDENT)**

| Méthode & URL | Corps / réponse |
|---|---|
| `GET /api/v1/me/attendance?from&to&status&page&size&sort` | `PageResponse<MyAttendanceRow>` (réelles + absences dérivées) |
| `GET /api/v1/me/attendance/{attendanceId}` | `MyAttendanceDetail` (+ historique + justificatif) |
| `POST /api/v1/me/attendance/justifications` | `{ checkpointPublicId, category, externalReference?, comment }` → `201` |
| `PUT /api/v1/me/attendance/justifications/{id}` | `{ category, externalReference?, comment }` → `200` (PENDING only) |
| `GET /api/v1/me/attendance/justifications?status&page&size` | `PageResponse<MyJustification>` |
| `GET /api/v1/me/attendance/justifications/{id}` | `MyJustificationDetail` |

**Justificatifs gestion (module `attendance`)**

| Méthode & URL | Rôles | Corps / réponse |
|---|---|---|
| `GET /api/v1/attendance/justifications?status&classGroup&from&to&page&size&sort` | staff (périmètre) | `PageResponse<JustificationReviewRow>` |
| `GET /api/v1/attendance/justifications/{id}` | staff (périmètre) | `JustificationReviewDetail` |
| `POST /api/v1/attendance/justifications/{id}/review` | SCHOOL_ADMIN / PEDA_MANAGER(périmètre) / ADMIN / SUPER_ADMIN | `{ decision, decisionReason? }` → `200` |

**Rapports (module `attendance`)**

| Méthode & URL | Rôles | Réponse |
|---|---|---|
| `GET /api/v1/attendance/reports/summary?from&to&classGroup?` | staff (périmètre) | cartes de synthèse (taux présence, retards, absences injustifiées, justifiées, `COMPANY` exclus, `UNKNOWN` signalés, justificatifs en attente) |
| `GET /api/v1/attendance/reports/sessions?from&to&classGroup?&teacher?&page&size&sort` | staff (périmètre) | `PageResponse<SessionReportRow>` |
| `GET /api/v1/attendance/reports/classes?from&to&classGroup?&page&size&sort` | staff (périmètre) | `PageResponse<ClassReportRow>` (par classe : demi-journées attendues / présentes / absentes / excusées / inconnues, taux, retards) |
| `GET /api/v1/attendance/reports/students?from&to&classGroup?&studentProfile?&page&size&sort` | staff (périmètre) | `PageResponse<StudentReportRow>` |
| `GET /api/v1/attendance/reports/{sessions\|classes\|students}/export?...` | idem | `text/csv` (`Content-Disposition: attachment; filename="..."`), mêmes filtres |

Négociation CSV : route `/export` dédiée (plutôt qu'un `Accept` — plus
explicite, testable). En-tête `;`, UTF-8 (BOM `﻿` pour Excel FR),
1 ligne d'en-tête, quoting RFC 4180. **Neutralisation d'injection** :
toute cellule commençant par `=`, `+`, `-`, `@`, tab ou CR est préfixée
d'une apostrophe `'`. Aucune adresse électronique, aucun identifiant
SQL. Filtres identiques au rapport JSON. Audit `REPORT_EXPORTED`.

**Codes d'erreur** (`ApiError`, `RestControllerAdvice` par contrôleur) :

`ATT_CHECKPOINT_NOT_FOUND` (404), `ATT_CHECKPOINT_INVALID_STATE` (409),
`ATT_CHECKPOINT_ORDER_CONFLICT` (409, collision `display_order`),
`ATT_MANUAL_REASON_REQUIRED` (400),
`ATT_RECORD_NOT_FOUND` (404), `ATT_RECORD_INVALID_STATE` (409),
`ATT_CORRECTION_REASON_REQUIRED` (400),
`ATT_NOT_ENROLLED` (409, réutilisé), `ATT_ENROLLMENT_AMBIGUOUS` (409),
`ATT_ALREADY_RECORDED` (409),
`ATT_JUSTIFICATION_NOT_FOUND` (404), `ATT_JUSTIFICATION_INVALID_STATE` (409),
`ATT_JUSTIFICATION_DECISION_REASON_REQUIRED` (400),
`ATT_REPORT_INVALID_FILTER` (400), `ATT_REPORT_INVALID_SORT` (400),
`ATT_OPERATION_FORBIDDEN` (403).
Réutilisés : `SESSION_NOT_FOUND` (404), `SESSION_OPERATION_FORBIDDEN` /
`SESSION_SCOPE_FORBIDDEN` (403), `ATT_SESSION_CLOSED` (409),
`ATT_TOKEN_INVALID` (409), `ATT_TOKEN_BACKEND_UNAVAILABLE` (503),
`VALIDATION_ERROR` (400), `ACCESS_DENIED` (403).

### 5.8 Jeton Redis par point de contrôle (Checkpoint 2)

`AttendanceTokenService` :

- `issue(sessionPublicId, checkpointPublicId)` — clés
  `esic:attendance:token:{t}` / `esic:attendance:code:{c}` → valeur
  `sessionPublicId\ncheckpointPublicId` ; pointeur courant
  `esic:attendance:checkpoint:{checkpointPublicId}` → `t\nc` ; index
  `SADD esic:attendance:session-cp:{sessionPublicId} {checkpointPublicId}`
  (TTL rafraîchi). Rotation + invariant du pointeur courant conservés
  (au grain checkpoint).
- `resolve(token, shortCode)` → `Optional<CheckpointToken
  { sessionPublicId, checkpointPublicId }>`.
- `invalidateCheckpoint(checkpointPublicId)` — purge les 3 clés du
  couple courant + le pointeur ; retire du set de session.
- `invalidateSession(sessionPublicId)` — lit le set `session-cp`,
  `invalidateCheckpoint` pour chacun, supprime le set. Conservé pour la
  fermeture de séance (défense en profondeur) en plus de l'écoute
  `AttendanceCheckpointChangeEvent`.
- Redis indisponible → `503 ATT_TOKEN_BACKEND_UNAVAILABLE` (inchangé).

---

## 6. Conception frontend (Checkpoints 6–10)

### 6.1 Modèles & service API (CP6)

- `sessions.models.ts` étendu : `AttendanceCheckpointType`,
  `AttendanceCheckpointStatus`, `AttendanceStatus`
  (`PRESENT`/`LATE`/`ABSENT`/`EXCUSED_ABSENCE`/`CANCELLED`) + libellés
  FR, `CheckpointResponse`, `SessionAttendanceResponse` enrichi,
  `AttendanceCorrectionResponse`, requêtes `CreateCheckpointRequest`,
  `ManualAttendanceRequest`, `CorrectAttendanceRequest`,
  `CancelAttendanceRequest`.
- Nouveau `features/attendance/attendance.models.ts` : justificatifs
  (`JustificationCategory`, `JustificationStatus`, DTO review + `/me`),
  rapports (`SummaryResponse`, `SessionReportRow`, `ClassReportRow`,
  `StudentReportRow`), `AttendanceReportQuery`.
- `SessionsApiService` étendu (une méthode par endpoint checkpoints /
  manuel / correction / annulation / historique).
- Nouveau `AttendanceApiService` (justificatifs `/me` + gestion,
  rapports, exports). Les exports CSV : `HttpClient.get(..., {
  responseType: 'blob', observe: 'response' })` puis
  `URL.createObjectURL` + `<a download>` **programmatique** ; nom de
  fichier depuis `Content-Disposition`. **Rien** en `localStorage` /
  `sessionStorage` ; aucun jeton / filtre sensible en URL de
  navigation Angular (les filtres de rapport passent en query params
  d'API, pas dans la route — ou en query params de route **non
  sensibles** : dates, classe publicId ; acceptable).
- `attendance-errors.ts` : `toAttendanceError` sur le modèle de
  `toSessionError` (liste blanche explicite des codes `ATT_*` ci-dessus,
  `5xx`/inconnu → message générique).

### 6.2 Fiche séance enrichie (CP7)

`SessionDetail` étendu :

1. **Section checkpoints** : liste (label, type, ordre, statut,
   obligatoire) ; création (`label`, `type`, `required`) ; open / close /
   cancel avec **confirmation en ligne** (pas de `window.confirm`),
   motif requis pour cancel.
2. **Panneau QR** : cible le checkpoint `OPEN` sélectionné ; renouvellement
   ~3 s avant expiration ; arrêt à la destruction / fermeture du
   checkpoint / perte du droit de gestion dans le contexte de rôle actif.
3. **Tableau des présences par checkpoint** : colonnes statut (chip
   couleur **+ texte** — jamais la couleur seule), retard, source,
   commentaire, heure ; compteurs par statut + effectif attendu +
   absents dérivés.
4. **Présence manuelle** : sélection d'un apprenant du roster attendu
   (via une nouvelle route d'effectif, §5.3), statut, retard, commentaire
   obligatoire.
5. **Correction / annulation** : formulaire en ligne, motif obligatoire,
   `disabled` pendant l'appel, double soumission bloquée.
6. **Historique** d'une présence (panneau dépliable).
7. **Indicateurs de séance** (cartes).
8. **Téléchargement CSV** de la séance (bouton → blob).

UX : états loading / vide / erreur / 403 / 404 / conflit / succès ;
`aria-live` sur les retours ; focus après erreur ; responsive ; tables
`overflow-x:auto`. Perte du droit dans le contexte actif → ferme les
formulaires, efface leur contenu, stoppe polling / renouvellement QR,
ignore les réponses tardives.

### 6.3 Espace apprenant « Mes présences » (CP8)

Routes `/my-attendance`, `/my-attendance/:id` (garde `roleGuard(['STUDENT'])`).

- Historique personnel : filtres période / statut ; ligne = séance,
  classe, checkpoint, statut, retard, présence d'un justificatif.
- Détail : statut, retard, séance / classe, historique de correction,
  justificatif + décision.
- Dépôt d'un justificatif **métier sans fichier** : le formulaire
  précise « aucune pièce jointe dans cette tranche » ; catégorie,
  référence externe facultative, commentaire borné (1000). Visible :
  « ces informations sont consultables par les personnels autorisés ».
- Modification uniquement si `PENDING`.
- Aucun accès à une autre identité (le serveur résout depuis le JWT).
- Nav item « Mes présences » (`STUDENT`) ; l'entrée « Émargement »
  actuelle (`/attendance`) est conservée.

### 6.4 Rapports & tableau de bord (CP9)

Route `/attendance-management` (garde
`roleGuard(['ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER','TEACHER'])`),
sous-routes `summary` (défaut), `sessions`, `classes`, `students`,
`justifications`.

- **Synthèse** : cartes (taux de présence, retards, absences
  injustifiées, absences justifiées, demi-journées `COMPANY` exclues,
  `UNKNOWN` signalées, justificatifs en attente). Visualisation
  CSS/SVG simple et accessible si utile ; **tableaux et indicateurs
  prioritaires** ; pas de bibliothèque graphique lourde.
- **Rapports** séance / classe / apprenant : filtres date / classe /
  statut ; pagination + tri serveur ; bouton export CSV.
- **File des justificatifs `PENDING`** : examen accepter / refuser avec
  motif (obligatoire si refus), confirmation en ligne.
- Nav item « Suivi d'assiduité » pour les 5 rôles ci-dessus (un
  `TEACHER` ne voit que ses séances / classes — filtré côté serveur ;
  un `403` est rendu « accès refusé »).

### 6.5 Tests frontend (CP10)

Services API (URL, méthode, params inclus seulement si renseignés,
blob + `Content-Disposition`), `toAttendanceError`, checkpoints (CRUD +
cycle), présence manuelle / correction / annulation / historique,
justificatifs apprenant (dépôt / modif `PENDING` / lecture décision),
examen justificatif, rapports (filtres, pagination, tri), exports
(déclenchement blob, pas de navigation), dashboard, changement de
contexte de rôle (fermeture des formulaires, arrêt polling, réponses
tardives ignorées), double soumission bloquée, **aucun** accès
`localStorage` / `sessionStorage`, **aucun** jeton / code en URL, états
loading / empty / error / 403 / 404, accessibilité élémentaire
(`role`, labels, focus). **Aucun test existant supprimé ou assoupli.**

---

## 7. Plan de commits (≤ 8, Checkpoint 13)

1. `migration + contraintes` — V10 + tests `@DataJpaTest`.
2. `domaine checkpoints` — entité / service / contrôleur `coursesession`,
   port `CourseSessionDirectory` étendu, jeton Redis par checkpoint,
   événement `AttendanceCheckpointChangeEvent`, audit.
3. `présence / correction / justificatifs` — évolution
   `attendance_record`, présence manuelle, correction, annulation,
   historique, justificatifs (`/me` + gestion), port
   `alternation.AlternationDirectory`.
4. `rapports / export / sécurité` — `AttendanceReportService`, CSV +
   neutralisation, gardes de rapport, tests de sécurité HTTP (6 rôles).
5. `frontend API + séance` — modèles, `SessionsApiService` /
   `AttendanceApiService`, `attendance-errors`, `SessionDetail` enrichi.
6. `frontend apprenant + rapports` — `/my-attendance`,
   `/attendance-management`, nav, gardes.
7. `tests et corrections` — tests backend d'intégration / concurrence,
   tests frontend, corrections de régressions.
8. `documentation + démonstration` — `docs/CURRENT-STATE.md`,
   `docs/04`, `docs/05`, `docs/08`, `docs/09`, `docs/10`, `docs/11`,
   ce rapport, `frontend/README.md` / `README.md` si nécessaire.

---

## 8. Vérifications finales (Checkpoints 12–13)

- `cd backend && ./mvnw clean test` — 0 échec, `ModularityTests` vert,
  V10 appliquée ; nombre de tests **avant / après** relevé.
- `cd frontend && npm ci && npm run lint && npm test -- --watch=false &&
  npm run build` — 0 échec, budget bundle sous 500 kB.
- `git diff --check`, `git status --short`,
  `git log --oneline origin/main..HEAD`,
  `git diff --stat origin/main...HEAD`.
- `git grep -n -E 'localStorage|sessionStorage' -- frontend/src` —
  analysé (aucune nouvelle occurrence pour des données sensibles).
- `git grep -n -i -E 'password|jwt|token|short.?code' -- '*.log'
  docs/reports` — analysé.
- `spotless:check` : **non configuré** dans `backend/pom.xml` — ne pas
  prétendre l'exécuter.
- Démonstration locale (profil `demo`, base temporaire isolée si le
  schéma local est contaminé par les tests) : 2 rôles autorisés + 2
  interdits, export CSV réel + vérification d'absence de formule
  injectable, parcours étudiant du justificatif, un scénario de
  concurrence.

---

## 9. Risques et points ouverts

- **Étendue de la tranche** : très large. Mitigation = découpage strict
  en checkpoints, commits atomiques, aucun test existant cassé.
- **Compatibilité V9 → V10** : le `DROP INDEX` +
  `ADD COLUMN ... DEFAULT` + backfill + `DROP DEFAULT` est la partie la
  plus risquée. Testée par `@DataJpaTest` de reprise.
- **`CourseSessionDirectory.SessionRef`** : la suppression de
  `checkpointInternalId`/`checkpointPublicId` uniques touche
  `AttendanceService`, `DefaultCourseSessionDirectory` et leurs tests.
  Refactor confiné, tests adaptés (pas supprimés).
- **Absences dérivées** : calcul à la volée potentiellement coûteux sur
  de grands intervalles — borné par pagination et par le fait que les
  séances sont exceptionnelles (faible volume dans le prototype).
- **Dette transactionnelle d'audit** : **non** résolue (cohérence
  globale requise) ; documentée.
- **Périmètre `PEDAGOGICAL_MANAGER` sur `/me` et justificatifs** :
  l'examen est filtré par `AcademicScopeDirectory` ; le dépôt reste
  STUDENT-only.
- **`spotless` absent** : aucun formateur automatique côté backend ;
  s'en tenir aux conventions du dépôt.
