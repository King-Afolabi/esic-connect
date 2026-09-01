# G1 — Décisions d'architecture (`DEC-G1-*`)

> Décisions de conception du grand lot produit G1, arrêtées au bloc
> **G1-0** avant tout code métier. Chaque décision est une **décision de
> conception** : elle n'est jamais présentée comme une exigence du
> cahier des charges. Les exigences sont tracées dans
> `docs/reports/G1_REQUIREMENTS_TRACEABILITY.md`.
>
> Format : contexte vérifié · documents · options · décision ·
> conséquences · risques · sécurité · transactions · tests attendus ·
> impact futur (déploiement).

## Date

```text
31 août 2026
1er septembre 2026 — révision après l'audit correctif G1-B.1
```

## Révision G1-B.1 (1er septembre 2026) — identité d'un créneau

L'implémentation G1-B stockait, dans une colonne nommée
`course_session.planning_entry_public_id` et un champ de port
`entryPublicId`, une valeur qui **n'était pas** un `planning_entry.public_id`
(celui-ci est aléatoire et propre à chaque version) mais un **UUID
déterministe** dérivé de `planning_schedule.public_id + "|" + slot_key`.
Nom trompeur, interdit par l'audit. Corrigé **sans migration
supplémentaire** (V12 et V13 n'ont jamais été poussées ni appliquées hors
d'une base jetable — décision documentée en tête de `V13`) :

- `planning_entry.public_id` = identifiant **de ligne de version**,
  aléatoire (inchangé) ;
- **nouveau** `planning_entry.slot_public_id BINARY(16) NOT NULL` =
  identité **stable** du créneau à travers les versions, déterministe
  (`UUIDv3(schedule.public_id || '|' || slot_key)`) ;
- `course_session.planning_entry_public_id` → **renommée**
  `course_session.planning_slot_public_id` (porte cette identité stable) ;
- port `PlanningSessionWriter` : `PlannedSession.entryPublicId` →
  `slotPublicId` ; `SyncedSession.entryPublicId` → `slotPublicId` ;
  `SupersededSession.previousEntryPublicId` → `previousSlotPublicId` ;
  `PlanningSessionSyncException.entryPublicId()` → `slotPublicId()` ;
- DTO : `PlanningResponses.VersionEntryResponse` expose désormais
  explicitement `slotPublicId` **en plus** de `publicId`.

Formule centralisée dans `planning.internal.PlanningSlotIds` (utilisée à
la simulation **et** à la publication). Les mentions
`planning_entry_public_id` de `DEC-G1-001` / `DEC-G1-004` ci-dessous se
lisent désormais `planning_slot_public_id` ; « discriminant d'origine »
(NULL ⇒ séance manuelle) reste valable sur la colonne renommée.

## Contexte technique vérifié (commun)

- Back-end : Spring Boot 3.5.16, Java 21, Maven ; Spring Modulith
  **1.4.12** (`spring-modulith-starter-core` + `-starter-test` ;
  **pas** de `spring-modulith-starter-jpa`, donc **pas** d'Event
  Publication Registry aujourd'hui) — `backend/pom.xml`.
- 12 modules réels : `identity`, `organization`, `academic`,
  `enrollment`, `alternation`, `coursesession`, `attendance`,
  `studentimport`, `notification`, `audit`, `bootstrap`, `shared`
  (`docs/CURRENT-STATE.md`). `ModularityTests` vert.
- Migrations Flyway **V1 → V11** ; `spring.jpa.hibernate.ddl-auto =
  validate` ; aucune donnée métier insérée par migration.
- Frontières inter-modules déjà en place **uniquement par ports
  publics** : `identity.CurrentUserResolver` / `TeacherDirectory` /
  `UserDirectory`, `academic.ClassGroupDirectory` /
  `AcademicScopeDirectory`, `enrollment.EnrollmentDirectory`,
  `coursesession.CourseSessionDirectory` — aucun partage d'entité JPA,
  vérifié par `ModularityTests`.
- Événements métier : `*ChangeEvent` publiés dans la transaction
  appelante. **Motif d'audit réel vérifié** (`audit/internal/`, 1er sept.
  2026) : **10 classes listener**, dont **9** en `@EventListener`
  *synchrone* + `@Transactional(propagation = REQUIRES_NEW)`
  (`Academic`, `AccountLifecycle`, `Alternation`, `Attendance`,
  `AttendanceCheckpoint`, `CourseSession`, `Enrollment`, `Organization`,
  `SecurityAuditEventListener` — cette dernière avec 2 méthodes, soit
  **11 méthodes de handler** au total) et **1 seule**
  (`StudentImportAuditListener`) en
  `@TransactionalEventListener(phase = AFTER_COMMIT)` +
  `@Transactional(REQUIRES_NEW)`. Les javadoc du code le disent
  explicitement : « contrairement au motif `@EventListener` +
  `REQUIRES_NEW` du reste du projet » / « la migration globale vers
  `@TransactionalEventListener(AFTER_COMMIT)` reste à planifier ». Un
  audit écrit en `@EventListener` + `REQUIRES_NEW` est **committé même si
  la transaction métier appelante rollback ensuite** — dette connue,
  assumée. La formulation antérieure de cette note (« 11 listeners
  `AFTER_COMMIT` ») était fausse : corrigée au checkpoint G1-0.1.
- Front : Angular 21.2 zoneless / standalone / Material ; JWT + contexte
  de rôle **en mémoire seule** (asserté) ; budget de bundle initial
  < 500 kB ; `axe-core` en `devDependencies` (tests a11y).
- Test : profil `test` sur les **mêmes conteneurs** MySQL/Redis que
  `local` ; isolation Testcontainers différée (`FINAL-021`,
  `docs/reports/TEST_ISOLATION_DECISION.md`). Voir `DEC-G1-011`.

---

## DEC-G1-001 — Frontière `planning` ↔ `coursesession`

**Contexte.** Le module `coursesession` gère aujourd'hui des séances
créées manuellement, toutes **exceptionnelles au sens fonctionnel** :
`exception_reason` (`VARCHAR(500) NOT NULL`) obligatoire sur **toute**
séance, cycle `PLANNED → OPEN → CLOSED`. **Schéma réel vérifié** (V9 +
`CourseSession.java`, 1er sept. 2026) : colonnes `public_id`,
`teacher_user_id`, `title` (NULL), `status ∈ {PLANNED,OPEN,CLOSED}`,
`starts_at`, `ends_at`, `time_zone_id`, `exception_reason`, `opened_*`,
`closed_*`, horodatage, `version`. **Il n'y a PAS** de colonne
`exceptional`, ni `room_id` / `site_id`, ni `subject`. « Exceptionnelle »
est donc une *description*, pas un champ — G1-B devra **ajouter** en
`V13` un discriminant d'origine (voir Conséquences). `coursesession`
expose déjà `CourseSessionDirectory` (lecture / accès), mais **aucun
port d'écriture**.

**Documents.** ARCH §7.5, §7.7, §8.1–§8.4 ; MDD §17–§18 ; CDC §43
RG-016, RG-030, RG-031 ; `G1_REQUIREMENTS_TRACEABILITY.md` §3.

**Options.**
1. `planning` importe `coursesession.internal` (repositories, entités) —
   **rejeté** : viole `ModularityTests` et la règle ARCH §6.6.
2. `planning` publie un événement `PlanningPublishedEvent` et
   `coursesession` réagit **après commit** pour créer les séances —
   rejeté pour la **création synchrone** : la publication doit être
   tout-ou-rien (les séances doivent exister à la fin de la transaction
   de publication, sinon AC-007 « produire des séances … après
   publication » n'est pas garanti et l'IHM afficherait un planning
   publié sans séances).
3. `coursesession` expose un **port d'écriture public**
   `PlanningSessionWriter` appelé **synchronement dans la transaction de
   publication** ; commande immuable en entrée, résultat par
   `sessionPublicId` en sortie ; aucune entité JPA traversée.

**Décision.** Option 3.

- Nouveau port : `com.esic.connect.coursesession.PlanningSessionWriter`
  (package racine du module, à côté de `CourseSessionDirectory`).
- Signature (esquisse, figée à l'implémentation) — **aucune clé SQL
  interne dans le port** (correctif G1-0.1) : les références d'identité
  sont des `UUID` publics, `coursesession` les résout en interne.
  ```java
  interface PlanningSessionWriter {
      /** Applique un lot d'entrées de planning publié à l'ensemble des
       *  séances "planning" d'une classe + année. Idempotent par
       *  entryPublicId. Exécuté dans la transaction de l'appelant. */
      PlanningSyncResult sync(PlanningSyncCommand command);
  }
  record PlanningSyncCommand(UUID scheduleVersionPublicId,
                             UUID classGroupPublicId,
                             UUID academicYearPublicId,
                             List<PlannedSession> entries) { }
  record PlannedSession(UUID entryPublicId, UUID teacherPublicId,
                        UUID roomPublicId /*nullable, si un référentiel salle est branché*/,
                        String title,
                        Instant startsAt, Instant endsAt, String timeZoneId) { }
  record PlanningSyncResult(List<Created> created, List<Reused> reused,
                            List<Superseded> superseded) { }
  ```
  `teacherPublicId` = `public_id` du `user_account` (jamais
  `teacher_user_id`). `roomPublicId` n'est transmis que si un référentiel
  de salle est effectivement consommé par `coursesession` (aujourd'hui
  `course_session` **n'a pas** de `room_id` — cf. Contexte) ; sinon il
  reste `null` (`RG-035` : salle affectable après l'import).
- `coursesession` gère : création d'une `course_session` d'origine
  planning (`status = PLANNED`, `planning_entry_public_id` renseigné — le
  discriminant d'origine ajouté en `V13`), réutilisation si une séance
  planning de même `entryPublicId` existe et que la règle de réutilisation
  est sûre (DEC-G1-002), supersession logique (`status = CANCELLED` +
  `superseded_by_scheduling = true`) des séances planning **futures et
  `PLANNED`** absentes de la nouvelle version. Les séances `OPEN`/`CLOSED`
  ne sont **jamais** réécrites (DEC-G1-004).
- `planning` ne connaît que des `UUID` et le port. `coursesession` ne
  connaît pas `planning`.

**Conséquences.** Migration `coursesession` **`V13`** :
`course_session.planning_entry_public_id` (`BINARY(16) NULL`, `UNIQUE`,
indexé) — sert **à la fois** de lien vers l'entrée de planning et de
**discriminant d'origine** (`NULL` ⇒ séance exceptionnelle manuelle, non
`NULL` ⇒ séance issue d'un planning) ; `superseded_by_scheduling BOOLEAN
NOT NULL DEFAULT FALSE`. `exception_reason` doit devenir **nullable**
(une séance planning n'a pas de motif d'exception) — `ALTER … MODIFY …
NULL`, additif non destructif. `ModularityTests` reste vert (nouveau port
dans le package racine).

**Risques.** Publication partielle si le port échoue → **atténué** :
appel synchrone dans la transaction, toute exception rollback l'ensemble.
Duplication de séances → **atténué** : idempotence par `entryPublicId` via
`UNIQUE (planning_entry_public_id)`. MySQL **n'a pas** d'index partiel :
une contrainte `UNIQUE` sur une colonne nullable autorise **plusieurs
`NULL`** (les séances exceptionnelles) tout en gardant les UUID non nuls
uniques — c'est exactement le comportement voulu, aucune colonne générée
nécessaire.

**Sécurité.** Le port ne prend **aucun** paramètre d'identité client :
l'autorité de publication est vérifiée dans `planning` (rôle + périmètre
`AcademicScopeDirectory`) avant l'appel. Le port n'expose pas de
`course_session` hors périmètre.

**Transactions.** `PlanningPublicationService.publish` ouvre la
transaction, verrouille le job (`SELECT … FOR UPDATE`), appelle
`PlanningSessionWriter.sync` **dans** cette transaction. Événement
`PlanningPublishedEvent` publié dans la transaction, listeners après
commit.

**Tests attendus.** création / réutilisation / supersession ; rollback
total si le writer lève ; idempotence (double publish) ; `ModularityTests`.

**Impact déploiement.** Aucun service nouveau. Migration additive
réversible logiquement (colonnes nullables).

---

## DEC-G1-002 — Clé d'identité stable d'une entrée de planning

**Contexte.** Le versionnement (EF-PLAN-005, AC-008) exige de savoir,
d'une version à la suivante, si une ligne « est la même » (⇒ réutiliser
la séance) ou « est nouvelle » (⇒ créer). Le CSV n'a pas d'identifiant
de ligne.

**Documents.** MDD §17.4 (`schedule_slot.source_import_row_id`) ; CDC
§13.3 ; `G1_REQUIREMENTS_TRACEABILITY.md` §3 (EF-PLAN-005, AC-008).

**Contradiction corrigée (G1-0.1).** L'esquisse initiale mettait
`start_time`/`end_time` **dans** la clé, tout en présentant ailleurs
(DEC-G1-004 règle 5) un changement d'horaire comme une *modification* du
même créneau. C'est contradictoire : si l'horaire est dans la clé, un
changement d'horaire produit une **nouvelle** clé (donc `REMOVED` +
`ADDED`), jamais une modification reconnue. Il faut une **identité
stable** explicite, indépendante des propriétés modifiables.

**Options.**
1. Numéro de ligne du fichier — **rejeté** : instable (réordonnancement,
   ajout/suppression au milieu).
2. Hachage de la ligne brute entière ou d'un sous-ensemble incluant
   l'horaire — **rejeté** : toute correction de forme ou de créneau
   casse l'identité (cf. contradiction ci-dessus).
3. **Colonne `slot_key` obligatoire dans le CSV G1** : identifiant de
   créneau **fourni par le responsable**, stable d'une version à la
   suivante. `CDC §13.3` liste les colonnes du planning sans `slot_key`
   mais ne l'interdit pas — G1 l'**ajoute** comme colonne obligatoire
   (extension assumée, tracée ici et dans `docs/02` le jour du bloc
   G1-B). Unicité : `(planning_schedule_id, slot_key)`. La clé métier de
   simulation (`planning_import_row.business_key` /
   `planning_entry.business_key`) **dérive** de
   `(planning_schedule_id, slot_key)` — c'est un identifiant technique
   stable, pas un hachage de propriétés. `session_date`, `start_time`,
   `end_time`, `title`, `teacher_email`, `room_code` sont des
   **propriétés modifiables** du créneau, hors de la clé.

**Décision.** Option 3.
- même `slot_key` d'une version à l'autre, propriétés identiques ⇒
  `UNCHANGED` ;
- même `slot_key`, au moins une propriété différente ⇒ `MODIFIED`
  (séance réutilisée puis mise à jour si `PLANNED` — DEC-G1-004) ;
- `slot_key` présent seulement dans la nouvelle version ⇒ `ADDED`
  (création) ;
- `slot_key` disparu de la nouvelle version ⇒ `REMOVED` (supersession si
  la séance est `PLANNED` future).

**Repli explicite** si la colonne `slot_key` est refusée à la revue
métier : l'identité stable **n'existe pas**, donc un changement
d'horaire (ou de toute propriété entrant alors dans une clé dérivée)
devient `REMOVED` + `ADDED`. On ne prétendra **pas** reconnaître une
« modification » de créneau sans identité stable. DEC-G1-004 règle 5 est
alignée sur ce repli (elle ne couvre alors que formateur / salle).

**Conséquences.** Deux lignes de la même version avec le même `slot_key`
= doublon fichier → ligne `ERROR` (RG-034). `slot_key` absent / vide sur
une ligne → `ERROR` (colonne obligatoire). `entryPublicId` =
`public_id` de la `planning_entry`, stable tant que le `slot_key` est
stable pour le même `schedule`.

**Risques.** Le responsable doit gérer des `slot_key` cohérents entre
imports. Atténué : l'assistant d'import (hors périmètre G1, `CDC §13.10`)
pourra les proposer ; en G1, un `slot_key` = simple libellé court
documenté dans le modèle CSV.

**Sécurité.** `slot_key` et la `business_key` dérivée ne contiennent
aucune donnée personnelle (pas d'email formateur). Non exposées au
client (internes à la trace de résolution).

**Transactions.** N/A (valeur dérivée).

**Tests attendus.** stabilité de l'identité (`slot_key`) sous
réordonnancement / correction de casse des propriétés ; `slot_key`
manquant ⇒ `ERROR` ; doublon de `slot_key` intra-fichier ⇒ `ERROR` ;
`UNCHANGED` / `MODIFIED` / `ADDED` / `REMOVED` inter-versions ; (mode
repli) changement d'horaire ⇒ `REMOVED` + `ADDED`.

**Impact déploiement.** Aucun.

---

## DEC-G1-003 — Simulation, publication et idempotence

**Contexte.** L'import apprenant (`studentimport`) a fixé un modèle
éprouvé : job `SIMULATED` sans écriture métier (invariant T1),
confirmation transactionnelle unique avec verrou `SELECT … FOR UPDATE`,
re-validation complète, idempotence `APPLIED`, rollback total,
e-mail/audit `AFTER_COMMIT`. G1-B doit s'aligner.

**Documents.** `docs/reports/STUDENT_CSV_IMPORT_DESIGN.md` ; MDD §29.1,
§29.4 ; CDC §13.8 ; V11.

**Options.**
1. Réécrire une mécanique ad hoc pour le planning — rejeté (risque,
   incohérence).
2. **Reprendre le modèle `studentimport`** : statuts
   `SIMULATED → PUBLISHED | CANCELLED | EXPIRED`, verrou de ligne à la
   publication, re-validation, idempotence par état + par `entryPublicId`,
   TTL de simulation (`expires_at`), audit `AFTER_COMMIT` /
   `REQUIRES_NEW`. **Pas** de statut `FAILED` : un échec de publication
   rollback tout et le job reste `SIMULATED`, republiable après
   correction.
3. Option 2 **+ statut `FAILED`**, écrit hors de la transaction de
   publication.

**Contradiction corrigée (G1-0.1).** L'esquisse initiale disait
« rollback tout **et** le job passe `FAILED` [dans la même transaction] »
— impossible : si la transaction de publication rollback, l'écriture du
statut `FAILED` est annulée avec elle.

**Décision.** **Option 3**, avec séparation transactionnelle stricte :

- `PlanningPublicationOrchestrator` (hors transaction) :
  1. appelle `PlanningPublicationService.publish(jobId)` — **une seule
     transaction atomique** : verrou `SELECT … FOR UPDATE` du job,
     re-validation complète, création/mise à jour/supersession de **toutes**
     les séances via `PlanningSessionWriter.sync`, création des
     remplacements éventuels, écriture de la `planning_version` `PUBLISHED`
     + bascule de l'ancienne en `SUPERSEDED`, passage du job `PUBLISHED`.
     **Aucun état partiellement publié.**
  2. si `publish` lève : la transaction a rollback (aucune séance, aucune
     version). L'orchestrateur écrit alors `status = FAILED` +
     `planning_import_job_issue` explicative dans une transaction
     **`REQUIRES_NEW` distincte**, qui **ne contient aucune donnée métier
     publiée** (uniquement le statut du job et son issue).
- Un **conflit métier attendu** (ligne `ERROR`, périmètre, état de job
  incompatible, concurrence) n'est **pas** un `FAILED` : c'est un
  `ProblemDetail` contrôlé (`409` / `422`), le job reste `SIMULATED`,
  republiable. `FAILED` est réservé à un échec **inattendu** après
  re-validation (p. ex. le port `coursesession` lève).

**Conséquences.** `CHECK (status IN
('SIMULATED','PUBLISHED','CANCELLED','EXPIRED','FAILED'))`. Republication
d'un job `PUBLISHED` = idempotente (même résultat, ne recrée rien).
Republication d'un job `FAILED` / `CANCELLED` / `EXPIRED` → `409` : un
nouvel import est nécessaire. Jamais de `500` générique pour un conflit
métier.

**Risques.** Concurrence de deux publications du même job → verrou de
ligne : la seconde voit l'état changé après acquisition → idempotent
(si `PUBLISHED`) ou `409` métier. **Jamais `500`.** Crash de la JVM
entre le rollback et l'écriture `FAILED` → le job reste `SIMULATED`
(aucune donnée publiée) : re-tentative possible, pas d'incohérence.

**Sécurité.** Périmètre re-vérifié à la publication (pas seulement à la
simulation) : un RP dont le périmètre a changé entre les deux ne peut
pas publier hors périmètre.

**Transactions.** Une seule transaction pour toute la publication
(job + version + entrées + séances via port + remplacements). Rollback
total testé (T3 équivalent). Le passage `FAILED` est une transaction
`REQUIRES_NEW` **séparée**, portée par l'orchestrateur, sans donnée
métier.

**Tests attendus.** T1 (simulation sans écriture), T3 (rollback total ⇒
0 séance, 0 version, job non `PUBLISHED`), `FAILED` écrit en
`REQUIRES_NEW` après un port qui lève (et ne contient aucune séance),
conflit métier ⇒ `ProblemDetail` `409`/`422` et job resté `SIMULATED`,
idempotence (double publish `PUBLISHED`), concurrence (2 publications),
`EXPIRED` refusé, `FAILED` non republiable.

**Impact déploiement.** Tâche `@Scheduled` de purge des jobs
`SIMULATED`/`EXPIRED` (comme `studentimport`) — documentée dans
`docs/07`.

---

## DEC-G1-004 — Nouvelle version et devenir des séances

**Contexte.** EF-PLAN-005 / EF-PLAN-007 / AC-008 : une modification d'un
planning publié crée une version N+1 ; les versions ne sont pas
supprimées. Il faut décider du sort des séances déjà créées.

**Documents.** MDD §17.2, §17.4, §18.1 ; CDC §13.9, §43 RG-032, §45
AC-008.

**Décision (règles testées une à une).**
1. Publication initiale ⇒ `planning_version` `version_number = 1`,
   statut `PUBLISHED`.
2. Republication (même `schedule`) ⇒ `version_number = N+1`, statut
   `PUBLISHED` ; l'ancienne version passe `SUPERSEDED`
   (`replaced_by_version_id`).
3. Séances planning en statut **`OPEN` ou `CLOSED`** : **jamais**
   modifiées ni supersédées (l'émargement en cours / fait fait foi).
   Si leur `slot_key` disparaît de la version N+1, elles restent
   telles quelles ; une `planning_import_job_issue` de niveau `WARNING`
   le signale.
4. Séances planning **futures et `PLANNED`** dont le `slot_key` n'est
   plus dans la version N+1 : `status = CANCELLED`,
   `cancellation_reason = "Superseded by schedule version N+1"`,
   `superseded_by_scheduling = true`. **Aucune suppression physique.**
5. Entrées dont le `slot_key` est inchangé : la séance est **réutilisée** ;
   si une propriété modifiable (formateur, salle, **horaire**, titre)
   diffère et que la séance est `PLANNED`, elle est **mise à jour** (et un
   `PlanningEntryChangedEvent` porte les champs modifiés pour G1-D).
   *Mode repli DEC-G1-002 (sans `slot_key`)* : seuls formateur / salle
   déclenchent une mise à jour ; un changement d'horaire est un
   `REMOVED` + `ADDED` (règles 4 + 6).
6. Nouveaux `slot_key` : **création** d'une séance.
7. Séances **exceptionnelles manuelles** (`planning_entry_public_id IS
   NULL` — le discriminant d'origine ajouté en `V13`, cf. DEC-G1-001) :
   hors du champ du planning, jamais touchées par une publication.

**Conséquences.** Migration **`V13`** (cf. DEC-G1-001, DEC-G1-012) :
`course_session.superseded_by_scheduling BOOLEAN NOT NULL DEFAULT FALSE`,
`course_session.planning_entry_public_id BINARY(16) NULL UNIQUE`
(lien **et** discriminant d'origine), `exception_reason` rendue nullable ;
`planning_entry.session_public_id BINARY(16) NULL`.

**Risques.** Un formateur ayant ouvert par erreur une séance « fantôme »
la fige. Accepté : le formateur peut la clôturer, le RP peut créer une
séance exceptionnelle de remplacement.

**Sécurité / Transactions.** cf. DEC-G1-001, DEC-G1-003.

**Tests attendus.** chaque règle 1–7, plus « `OPEN`/`CLOSED` inchangée »,
« supersession sans suppression », « comparaison de versions ».

**Impact déploiement.** Aucun.

---

## DEC-G1-005 — Conflits formateur / classe / salle

**Contexte.** `RG-21` (CAD `docs/01-cadrage.md` §24 : « les conflits de
planning sont signalés ») / `RG-034` (CDC §43) / US-074 (BKL §9,
« Détecter les conflits ») : la simulation doit détecter les
chevauchements.

**Documents.** CDC §14.3, §21.5 (conflits de salle), §43 RG-034 ; CAD
§21.5 ; `G1_REQUIREMENTS_TRACEABILITY.md` §3.

**Décision.**
- La simulation calcule, sur l'ensemble `{lignes du fichier} ∪ {séances
  `planning` publiées existantes de la même année, non `CANCELLED`}` :
  - **conflit formateur** : deux créneaux du même `teacher_email` qui se
    chevauchent (intervalle `[start, end)` UTC) → `ERROR` ;
  - **conflit classe** : deux créneaux de la même `class_code` qui se
    chevauchent → `ERROR` ;
  - **conflit salle** : deux créneaux du même `room_code` (non nul) qui
    se chevauchent → `ERROR` ;
  - **hors horaires configurés** (CDC §15.3, plages configurables) →
    `WARNING` ;
  - **durée anormale** (< 15 min ou > 8 h, bornes DEC) → `WARNING`.
- Une ligne `ERROR` rend le job **non publiable** (RG-034) → tentative de
  publication = `409 PLAN_BLOCKING_ISSUES`.
- Les séances `OPEN`/`CLOSED` existantes participent à la détection mais
  ne sont jamais modifiées.

**Conséquences.** `planning_import_row.row_status ∈ {VALID, WARNING, ERROR}` ;
anomalies détaillées en `planning_import_row_issue` (code, colonne,
valeur reçue tronquée, message, gravité) — même modèle que
`student_import_row_issue`.

**Risques.** Faux positifs sur des cours communs à plusieurs classes
(une séance multi-classes). Traité : la clé de conflit classe utilise la
`class_code` de la ligne ; le multi-classes n'est pas supporté à
l'import G1-B (une ligne = une classe) — documenté comme limite,
cohérent avec `session_class` géré côté `coursesession` post-création si
besoin.

**Sécurité / Transactions.** N/A (lecture).

**Tests attendus.** chaque type de conflit ; `WARNING` publiable,
`ERROR` non publiable ; chevauchement avec une séance publiée existante.

**Impact déploiement.** Aucun.

---

## DEC-G1-006 — Alternance `SCHOOL` / `COMPANY` / `UNKNOWN`

**Contexte.** Le module `alternation` résout, pour une classe/inscription
et une date, un contexte `SCHOOL` / `COMPANY` / `UNKNOWN`
(`AlternationDirectory`). Une séance de planning tombant un jour
« entreprise » doit être signalée (CDC §8.4 : « une période en entreprise
ne doit pas être comptabilisée comme une absence » ; le reporting
d'assiduité exclut déjà `COMPANY` du dénominateur).

**Documents.** ARCH §7.4 ; CDC §8.4 ; CS (« contexte `COMPANY` exclu du
dénominateur, `UNKNOWN` compté à part ») ; `G1_REQUIREMENTS_TRACEABILITY.md`
§3.

**Options.** (a) refuser toute séance un jour `COMPANY` ; (b) l'accepter
sans rien dire ; (c) **avertir** et laisser publier.

**Décision.**
- `SCHOOL` → ligne `VALID` (aucune anomalie d'alternance).
- `COMPANY` → ligne **`WARNING`** par défaut (code
  `PLAN_ALTERNATION_COMPANY_DAY`), **publiable** : un cours exceptionnel
  peut légitimement être planifié un jour entreprise (CDC §8.3
  « présence exceptionnelle à l'école »). Un futur paramètre pourrait
  durcir en `ERROR` — non implémenté en G1.
- `UNKNOWN` (aucun rythme configuré / hors cycle) → ligne **`WARNING`**
  (code `PLAN_ALTERNATION_UNKNOWN`) : ne pas masquer l'absence de
  configuration.
- Le module `planning` consomme `alternation` **via un port public
  existant** (`AlternationDirectory` / `AlternationScheduleDirectory` —
  nom exact vérifié à l'implémentation), jamais `alternation.internal`.

**Conséquences.** `planning` dépend de `alternation` (nouvelle arête
inter-modules, par port). `ModularityTests` mis à jour si nécessaire
(dépendance déclarée).

**Risques.** Trop de `WARNING` noient le signal utile. Atténué : la
synthèse du job agrège les compteurs par code d'anomalie.

**Sécurité / Transactions.** Lecture seule ; le port `alternation`
n'élargit pas le périmètre.

**Tests attendus.** `SCHOOL` → `VALID` ; `COMPANY` → `WARNING` publiable ;
`UNKNOWN` → `WARNING`.

**Impact déploiement.** Aucun.

---

## DEC-G1-007 — Notifications persistantes et livraison après commit

**Contexte.** G1-D crée un **centre de notifications persistantes**
(EF-NOTIF-001/002, RG-033). Le module `notification` actuel n'a **pas**
de table : seul `InvitationEmailListener` existe, en
`@TransactionalEventListener(phase = AFTER_COMMIT)`, envoi SMTP dans un
`try/catch` (vérifié). Spring Modulith 1.4.12 est présent, **sans**
`spring-modulith-starter-jpa` (donc sans Event Publication Registry).

**Documents.** ARCH §8.2, §8.3, §18.2 ; MDD §23.1 ; CDC §14, §23, §43
RG-033 ; `backend/pom.xml`.

**État réel du module `notification`** (vérifié 1er sept. 2026) :
`InvitationEmailListener` = `@TransactionalEventListener(AFTER_COMMIT)`,
envoi SMTP dans un `try/catch` (aucune table, aucune transaction propre).
Le module `audit`, lui, utilise majoritairement `@EventListener`
*synchrone* + `REQUIRES_NEW` (cf. §Contexte, corrigé en G1-0.1) — ce
**n'est pas** le motif à imiter ici.

**Options.**
1. Ajouter `spring-modulith-starter-jpa` → Event Publication Registry
   (table `event_publication`, republication au démarrage des events non
   traités). Avantage : garantie de livraison « au moins une fois ».
   Coût : nouvelle dépendance, nouvelle table gérée par Modulith (ou
   par une migration Flyway `event_publication`), impact potentiel sur
   **tous** les listeners d'événements du projet (10 classes d'audit +
   `InvitationEmailListener`), schéma géré hors `ddl-auto=validate`.
2. **Listener applicatif `@TransactionalEventListener(AFTER_COMMIT)` +
   idempotence en base** (clé `dedup_key` unique sur `notification`) +
   transaction **`REQUIRES_NEW`** pour l'écriture des notifications, de
   sorte qu'un échec de notification **ne rollback pas** le métier.
   C'est le motif du **seul** `StudentImportAuditListener`
   (`AFTER_COMMIT` + `REQUIRES_NEW`), pas celui de la majorité des
   listeners d'audit. Reprise = tâche `@Scheduled` optionnelle qui
   rejoue les événements « ratés » depuis les données métier
   (idempotence garantit l'absence de doublon).

**Décision.** Option 2 pour G1 (ne pas changer la version ni le socle
Modulith sans justification forte). L'Event Publication Registry est
**recommandé pour l'après-G1** et tracé comme dette dans `docs/07` et le
rapport final. Migrer *aussi* les 9 listeners d'audit `@EventListener`
vers `AFTER_COMMIT` est une dette distincte, hors périmètre G1.

**Conséquences.**
- Migration **`V15`** (renumérotée en G1-0.1, cf. DEC-G1-012) : table
  `notification` (`id`, `public_id`,
  `recipient_user_id`, `type`, `title`, `body` neutre, `resource_type`,
  `resource_public_id`, `status ∈ {UNREAD, READ, ARCHIVED}`,
  `dedup_key` UNIQUE, `created_at`, `read_at`, `version`).
- `dedup_key` = hachage stable de `(type, resource_public_id,
  recipient_user_id, événement-métier-id)` → **au plus une** notification
  par (destinataire, événement).
- Chaque listener écrit en `REQUIRES_NEW` ; une `DataIntegrityViolation`
  sur `dedup_key` est avalée (déjà notifié).
- Un échec (SMTP futur, DB indisponible) est journalisé **sans PII**, ne
  propage pas.

**Risques.** Perte d'un événement si la JVM meurt entre le commit métier
et l'écriture de la notification. Atténué : tâche de reconciliation
optionnelle + acceptation documentée (prototype).

**Sécurité.** Corps de notification **neutre** : jamais de jeton, code
court, IP, contenu de justificatif, chemin de fichier, secret (MDD §23.1,
§24.3). Destinataires **dérivés côté serveur** (classes, inscriptions,
formateur, remplaçant, RP) — jamais d'un identifiant client.

**Transactions.** Métier = transaction principale ; notification =
`REQUIRES_NEW` après commit. Testé : rollback métier ⇒ 0 notification ;
double émission ⇒ 1 notification.

**Tests attendus.** after-commit, transaction indépendante, idempotence,
rollback métier, destinataires, isolation (AC-017), contenu sans PII,
`401/403`.

**Impact déploiement.** Table supplémentaire ; pas de service nouveau ;
volume borné (purge à prévoir, `docs/07`).

### Révision à la livraison G1-D (1er septembre 2026)

- **Idempotence par occurrence.** `CourseSessionChangeEvent` gagne un
  champ `UUID eventId` (additif, un seul site de construction dans
  `CourseSessionChangePublisher`, `audit` l'ignore). `dedup_key` =
  `SHA-256(type | resourcePublicId | recipientUserId | eventKey)` où
  `eventKey` = `eventId` pour un événement de séance, `versionPublicId`
  pour un `PlanningPublishedEvent` (unique par publication). Pas
  d'ajout de `spring-modulith-starter-jpa` : décision inchangée.
- **Une transaction par ligne.** `NotificationWriter` (orchestration,
  **sans** `@Transactional`) délègue à `NotificationRowWriter`
  (`@Transactional(REQUIRES_NEW)`) : un doublon de `dedup_key` (course
  entre deux livraisons) fait échouer *uniquement* la ligne concernée,
  jamais les autres destinataires du même événement (un flush en échec
  aurait sinon empoisonné la transaction du lot entier).
- **Périmètre des destinataires G1-D = formateurs.** Le port
  `coursesession.CourseSessionDirectory` est étendu de deux méthodes
  **100 % UUID publics** : `findSessionNotificationInfo(sessionPublicId)`
  (formateur principal + remplaçants `ACTIVE`) et
  `findPrincipalTeacherPublicIds(sessionPublicIds)` (chargement groupé,
  pas de N+1). Notifier aussi les **apprenants** des classes et les
  **responsables pédagogiques** du périmètre demande de nouveaux ports
  `enrollment` / `academic` : tracé comme prolongement de G1-D
  (`G1_IMPLEMENTATION_PROGRESS.md`), CDC §18/§23 les conditionne à
  « si requis » — non numériquement exigé.
- **Pas de `notification_preference`.** Le seul canal livré est in-app ;
  une désactivation par type est un agrément post-G1 (DEC-G1-007 la
  conditionne à une exigence réelle, non établie).
- **`ARCHIVED`** reste une valeur d'énumération réservée : aucune action
  d'archivage exposée en G1-D.

### Révision à l'audit G1-D.1 (1er septembre 2026)

- **Contrat d'événement enrichi pour cibler un utilisateur non
  retrouvable après commit.** `CourseSessionChangeEvent` gagne un champ
  additif `Set<UUID> affectedUserPublicIds` (jamais `null`, copie
  immuable). Pour `SUBSTITUTION_ADDED` / `SUBSTITUTION_ENDED`, il porte
  l'UUID **public** du remplaçant concerné — le remplaçant qui vient de
  terminer n'étant plus `ACTIVE`, `findSessionNotificationInfo` ne le
  retrouverait pas. Aucune clé SQL, aucune entité JPA, aucun motif
  nominatif ; `audit` et `attendance` ignorent le champ. `end` renseigne
  aussi le `detail` d'audit `substitute=<uuid public>` (symétrie avec
  `ADDED`).
- **Garantie de livraison — position honnête.** Le modèle reste
  **« au mieux » après commit** (option 2, pas d'outbox). Sont
  **garantis** : aucune notification sans commit métier, aucun rollback
  métier sur échec de notification, absence de duplication
  (`dedup_key`), **isolation par destinataire** (l'échec d'un
  destinataire n'interrompt plus les suivants). Ne sont **pas**
  garantis : la reprise après crash JVM entre le commit métier et
  l'écriture, la relivraison automatique. Statut : persistance /
  consultation / idempotence / isolation `IMPLEMENTED_AND_TESTED` ;
  livraison + reprise `PARTIAL`. Les documents (CDC §18.3 / §23.3 /
  §32.3) n'exigent **pas** de reprise garantie (file + DLQ = architecture
  cible « pouvant rester non implémentée ») ⇒ pas de changement de
  socle ; **dette G1-D-OUTBOX** (`notification_outbox` transactionnelle +
  worker `@Scheduled` idempotent + backoff) tracée avec critères de
  résolution dans `docs/05` §9bis et `docs/06` R-G1-29.
- **Frontière transactionnelle par ligne — bug corrigé.**
  `NotificationRowWriter` ne rattrape **plus** l'exception de
  persistance dans sa transaction `REQUIRES_NEW` (elle serait devenue
  `rollback-only`, provoquant une `UnexpectedRollbackException` qui
  interrompait la boucle). L'exception remonte ; `NotificationWriter`
  (non transactionnel, `AFTER_COMMIT`) décide **par destinataire** :
  doublon `dedup_key` ⇒ succès idempotent ; autre erreur ⇒ journalisée
  sans PII, destinataire suivant traité.
- **Audience G1-D = formateurs, reclassée `PARTIAL`.** Aucun document ne
  numérote l'audience ; une séance annulée / modifiée concerne aussi les
  **apprenants** de la classe (CDC §13.9, §23.2). `EF-NOTIF-002` /
  `RG-033` repassent `PARTIAL`. Étendre exige les ports publics
  `enrollment.findActiveStudentUserPublicIdsForClasses(classes, date)` et
  `academic.findActiveManagerUserPublicIdsForClasses(classes, date)`
  (UUID publics, inscriptions / affectations actives à la date, comptes
  archivés exclus par `identity`, pas de repository internal importé dans
  `notification`) — **dette G1-D-AUDIENCE**.
- **Rétention & préférences — non inventées.** Aucune durée de
  conservation documentaire pour les notifications ⇒ **aucune purge
  ajoutée**, risque `R-G1-30`, `docs/07` §14 = `À_DÉFINIR`, conformité
  RGPD non revendiquée sur ce point. `notification_preference` non créé
  (non exigé).
- **Liens front — liste blanche par rôle.** Le centre de notifications
  ne propose un lien que si le `resourceType` est dans une liste blanche
  **et** que le rôle réel de l'appelant couvre la garde de la route
  cible (`SESSION_LINK_ROLES` / `PLANNING_LINK_ROLES`, repris à
  l'identique de `app.routes.ts`). Aucun `targetPath` serveur, aucune
  URL ni paramètre libre ; le corps reste lisible sans lien ; le
  back-end reste l'autorité.

---

## DEC-G1-008 — Stockage des pièces jointes

**Contexte.** G1-E ajoute des pièces jointes aux justificatifs
(EF-JUS-001, CDC §21.5). Le métier ne doit pas dépendre de
`java.nio.file` (portabilité S3 future — ARCH §12.3, §19).

**Documents.** ARCH §12.3, §19.1–§19.3 ; MDD §21.1 (`file_asset`) ; CDC
§21.5 ; CAD §25.3.

**Décision.**
- Port public `com.esic.connect.attendance.JustificationFileStorage`
  (le justificatif métier vit dans `attendance` — CS) :
  ```java
  interface JustificationFileStorage {
      StoredRef store(TempUpload upload);          // déplace un fichier validé
      InputStreamResource open(String storageKey); // lecture pour téléchargement
      void delete(String storageKey);              // compensation / purge
  }
  ```
- Implémentation G1 : `LocalFilesystemJustificationFileStorage` —
  répertoire **hors webroot**, chemin configurable
  (`JUSTIFICATION_STORAGE_PATH`), `storageKey` = clé aléatoire
  (`UUID` + sous-répertoires de dispersion), écriture via fichier
  temporaire puis **déplacement atomique** (`Files.move(..., ATOMIC_MOVE)`),
  aucun chemin fourni par le client, garde anti-traversal.
- Le métier n'importe jamais `java.nio.file` : seulement le port.
- Métadonnées en MySQL (`justification_attachment`), **contenu jamais en
  base**.

**Conséquences.** Nouvelle variable d'environnement
`JUSTIFICATION_STORAGE_PATH` (défaut : `${UPLOAD_DIRECTORY}/justifications`
— `UPLOAD_DIRECTORY` existe déjà dans `.env.example`) et
`JUSTIFICATION_MAX_FILE_BYTES` (défaut `5242880`). Documentées sans
valeur dans `.env.example`.

**Risques.** Volume disque non borné → dette de purge (`docs/07`,
`docs/06` R-G1). Déploiement futur avec système de fichiers éphémère →
le port permet de substituer un adaptateur objet sans toucher au métier.

**Sécurité.** cf. DEC-G1-009 pour les contrôles de contenu.
Téléchargement : `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`,
type MIME **re-dérivé** des magic bytes (pas celui déclaré), jamais de
rendu HTML.

**Transactions.** cf. DEC-G1-009.

**Tests attendus.** store/open/delete ; clé non prédictible ; anti-traversal ;
en-têtes de téléchargement.

**Impact déploiement.** Volume persistant à monter
(`JUSTIFICATION_STORAGE_PATH`) ; stratégie objet S3-compatible décrite
dans le rapport final.

---

## DEC-G1-009 — Cohérence transactionnelle base / fichier

**Contexte.** Une pièce jointe = 1 écriture MySQL (métadonnées) + 1
écriture disque (contenu). Il n'existe pas de transaction distribuée
fiable base ↔ système de fichiers.

**Documents.** ARCH §19 ; MDD §21 ; CDC §21.5.

**Décision — séquence avec compensation (pas d'atomicité prétendue).**
1. Réception → fichier **temporaire** hors zone finale.
2. Validation complète (extension, MIME déclaré, **magic bytes**, taille,
   nom neutralisé, SHA-256, rejet ZIP/OLE/exécutable/polyglotte simple).
   Échec ⇒ suppression du temporaire, `400`/`413`/`415`, **aucune**
   écriture base.
3. Transaction base : insertion `justification_attachment` en statut
   `PENDING_STORAGE`, rattachée au justificatif (contrôle de propriété /
   périmètre), verrou optimiste. Commit.
4. Après commit : `JustificationFileStorage.store` déplace le fichier ;
   puis une courte transaction passe la ligne en `STORED`.
5. Si l'étape 4 échoue : la ligne reste `PENDING_STORAGE` ; une tâche
   `@Scheduled` de réconciliation supprime les lignes `PENDING_STORAGE`
   plus vieilles que N minutes **et** leurs fichiers éventuels
   (compensation). Le front n'affiche que les pièces `STORED`.
6. Suppression logique d'une pièce (`DELETED`) : la ligne est marquée,
   le fichier est supprimé par la même tâche (ou immédiatement, best
   effort).

**Conséquences.** `justification_attachment.status ∈ {PENDING_STORAGE,
STORED, DELETED}`. Le mécanisme de **compensation** est explicitement
documenté ; aucune prétention d'atomicité parfaite.

**Risques.** Fichier orphelin si crash entre 4 et 5 → tâche de
réconciliation. Ligne `PENDING_STORAGE` visible en base mais pas dans
l'IHM → filtrage systématique côté API.

**Sécurité.** Validation **avant** toute écriture base. Le `storageKey`
n'est jamais dérivé du nom client.

**Transactions.** Étape 3 = transaction courte ; étape 4 hors
transaction base ; étape 5 = compensation idempotente.

**Tests attendus.** rollback étape 3 (⇒ pas de fichier, pas de ligne) ;
échec étape 4 (⇒ ligne `PENDING_STORAGE`, pas visible IHM, nettoyée) ;
suppression logique (⇒ fichier retiré) ; upload concurrent.

**Impact déploiement.** Tâche `@Scheduled` documentée ; fenêtre de
réconciliation configurable.

---

## DEC-G1-010 — Agrégats des tableaux de bord

**Contexte.** G1-F remplace le dashboard générique par des vues par
rôle (CDC §25). Chaque carte doit être reliée à une donnée réelle,
bornée, sans N+1.

**Documents.** CDC §25.1–§25.4 ; CS (endpoints existants) ;
`G1_REQUIREMENTS_TRACEABILITY.md` §6.

**Décision.**
- **Un** endpoint `GET /api/v1/me/dashboard` renvoyant un DTO **typé par
  le rôle de contexte** de l'appelant (déterminé serveur depuis le JWT,
  jamais d'un paramètre client). Un `403` si aucun rôle exploitable.
- Chaque section du DTO est calculée par une **requête agrégat dédiée**
  (`COUNT` / `GROUP BY` bornés, `LIMIT` sur les listes courtes ≤ 10),
  jamais par chargement de collections JPA. Repositories : projections
  ou requêtes JPQL/natives explicites.
- Périmètre : `PEDAGOGICAL_MANAGER` filtré par `AcademicScopeDirectory` ;
  `TEACHER` = ses séances ; `STUDENT` = ses données (AC-017).
- Cartes retenues, par rôle : **exactement** la liste de
  `G1_REQUIREMENTS_TRACEABILITY.md` §6, chacune adossée à une table /
  requête nommée dans `G1_IMPLEMENTATION_PLAN.md`.
- Cache Redis : **non** en G1 (pas de nouvel usage Redis ; les agrégats
  restent des requêtes SQL bornées). Tracé comme évolution possible.

**Conséquences.** Pas de nouvelle table. Éventuels index de couverture
ajoutés par migration `V17` **uniquement si** un test de non-régression
de performance le justifie (sinon aucun).

**Risques.** N+1 masqué. Atténué : test qui compte les requêtes (compteur
Hibernate statistics) sur au moins l'endpoint `PEDAGOGICAL_MANAGER`.

**Sécurité.** Aucune donnée d'un autre périmètre ; audit sans PII pour
la carte « dernières opérations ».

**Transactions.** Lecture seule (`@Transactional(readOnly = true)`).

**Tests attendus.** par rôle, périmètre, bornes (`LIMIT`), données vides,
absence de N+1 (au moins un endpoint), `401/403`.

**Impact déploiement.** Aucun.

---

## DEC-G1-011 — Stratégie de tests e2e

**Contexte.** G1-G doit prouver le parcours complet. Le front est
Angular 21 (build `@angular/build`, tests Vitest). Aucune dépendance e2e
aujourd'hui. Isolation d'intégration back = dette connue (`FINAL-021`).

**Documents.** CDC §46, §47 ; `docs/reports/TEST_ISOLATION_DECISION.md` ;
`frontend/package.json`.

**Décision.**
- Cible : **Playwright** (`@playwright/test`), un seul navigateur
  (Chromium) installé, exécuté hors CI par défaut (script npm dédié,
  non branché sur `frontend-ci.yml` sans validation `npm audit`).
- **Avant ajout** : vérifier compatibilité Node/Angular 21, lancer
  `npm audit --audit-level=high`, justifier la dépendance dans le
  journal IA et le rapport final.
- **Repli** (si Playwright bloqué : incompatibilité, vulnérabilité,
  téléchargement navigateur impossible) : **démonstration API
  automatisée** (script `scripts/test/g1-e2e-api.sh` ou test
  d'intégration `@SpringBootTest` de bout en bout) rejouant
  organisation → affectation → import planning → simulation →
  publication → séances → remplacement → notification → émargement →
  justificatif+fichier → examen → dashboard. Statut alors `PARTIAL`,
  **jamais** « e2e livré ».
- Parcours e2e minimal (si Playwright) : connexion admin → organisation
  → affectation responsable → import planning → simulation → publication
  → planning étudiant → planning enseignant → notification → dashboard.

**Conséquences.** Éventuel `frontend/e2e/**`, `playwright.config.ts`,
script `npm run e2e`. Données via seed idempotent (`scripts/seed-demo.sh`
étendu).

**Risques.** Flakiness e2e. Atténué : un seul parcours, données seedées,
sélecteurs stables (`data-testid`).

**Sécurité.** Comptes `example.test` uniquement, mots de passe de démo
locaux non commités.

**Transactions.** N/A.

**Tests attendus.** le parcours lui-même ; résultats exacts consignés
dans `G1_FINAL_REPORT.md`.

**Impact déploiement.** Aucun (outil de test).

---

## DEC-G1-012 — Stratégie de migrations

**Contexte.** Schéma en **V11**. G1 ajoute plusieurs domaines. Règle
absolue : ne jamais modifier V1–V11, un seul fichier par numéro, pas de
numéro réservé sans fichier.

**Documents.** MDD §44 ; V1–V11 ; CS (« schéma en version 11,
`ddl-auto=validate` »).

**Décision — attribution des numéros (un domaine par migration ;
renumérotée en G1-0.1 pour ne pas mélanger G1-B et G1-C dans un même
fichier).**

| Migration | Bloc | Contenu |
|---|---|---|
| `V12__create_planning_tables.sql` | G1-B | `planning_import_job`, `planning_import_job_issue`, `planning_import_row`, `planning_import_row_issue`, `planning_schedule`, `planning_version`, `planning_entry` (+ index, `CHECK`) |
| `V13__link_course_session_to_planning.sql` | **G1-B** | `course_session.planning_entry_public_id BINARY(16) NULL UNIQUE` (lien **et** discriminant d'origine), `superseded_by_scheduling BOOLEAN NOT NULL DEFAULT FALSE`, `exception_reason` rendue nullable ; `planning_entry.session_public_id BINARY(16) NULL` ; index |
| `V14__create_session_lifecycle_tables.sql` | **G1-C** | `teacher_substitution` (MDD §18.3) ; `session_cancellation_request` **uniquement si** le workflow de demande est retenu (sinon annulation directe, pas de table) ; index |
| `V15__create_notification_table.sql` | G1-D | `notification` (+ `dedup_key` UNIQUE, index) |
| `V16__create_justification_attachment_table.sql` | G1-E | `justification_attachment` (+ FK vers le justificatif, `status`, `sha256`, index) |
| `V17__…` | G1-F | **uniquement si** un index de performance s'avère nécessaire (test de non-régression) ; sinon **non créée** |

Chaque checkpoint a donc **ses** migrations : G1-B → `V12`+`V13`, G1-C →
`V14`, G1-D → `V15`, G1-E → `V16`. Aucune structure d'un bloc ultérieur
n'est posée par anticipation dans la migration d'un bloc antérieur.

- Chaque migration suit les conventions V1/V4–V11 : PK `BIGINT UNSIGNED
  AUTO_INCREMENT`, `public_id BINARY(16)` unique, `TIMESTAMP(6)` UTC,
  `version BIGINT UNSIGNED` (verrou optimiste), FK `RESTRICT` vers
  `user_account`, `CASCADE` seulement sur les chaînes techniques
  temporaires (`planning_import_*`), `ENGINE=InnoDB`,
  `utf8mb4_0900_ai_ci`.
- Aucune donnée métier insérée.
- Le numéro n'est **écrit qu'avec son fichier**, au moment du bloc.
- `ddl-auto` reste `validate` : chaque entité JPA nouvelle est couverte
  par sa migration.

**Conséquences.** L'ordre des blocs (G1-B avant G1-C avant G1-D avant
G1-E) est cohérent avec l'ordre des numéros, un domaine par fichier.

**Risques.** Migration destructive impossible à rollback
automatiquement → **aucune** migration G1 n'est destructive (toutes
additives : `CREATE TABLE`, `ADD COLUMN` nullable). Documenté dans
`docs/06`.

**Sécurité.** N/A.

**Transactions.** Flyway par migration (MySQL : DDL auto-commit — d'où
l'exigence « additive uniquement »).

**Tests attendus.** `./mvnw test` sur base vierge (Flyway rejoue
V1→V17), `ModularityTests`, `ddl-auto=validate` sans erreur.

**Impact déploiement.** Migrations rejouables sur une base de staging
vierge ; pas de downtime (additif).
