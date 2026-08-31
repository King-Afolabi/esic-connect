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
```

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
- Événements métier : `*ChangeEvent` publiés dans la transaction,
  consommés par `audit` via `@TransactionalEventListener`
  (`AFTER_COMMIT`) — 11 listeners d'audit.
- Front : Angular 21.2 zoneless / standalone / Material ; JWT + contexte
  de rôle **en mémoire seule** (asserté) ; budget de bundle initial
  < 500 kB ; `axe-core` en `devDependencies` (tests a11y).
- Test : profil `test` sur les **mêmes conteneurs** MySQL/Redis que
  `local` ; isolation Testcontainers différée (`FINAL-021`,
  `docs/reports/TEST_ISOLATION_DECISION.md`). Voir `DEC-G1-011`.

---

## DEC-G1-001 — Frontière `planning` ↔ `coursesession`

**Contexte.** Le module `coursesession` gère aujourd'hui des séances
**exceptionnelles** créées manuellement (`exceptional = true`, motif
obligatoire, cycle `PLANNED → OPEN → CLOSED`). G1-B doit créer des
séances **normales** à partir d'un planning publié (EF-SES-001, RG-016).
`coursesession` expose déjà `CourseSessionDirectory` (lecture / accès),
mais **aucun port d'écriture**.

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
- Signature (esquisse, figée à l'implémentation) :
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
  record PlannedSession(UUID entryPublicId, long teacherUserId,
                        UUID roomPublicId /*nullable*/, String title,
                        Instant startsAt, Instant endsAt, String timeZoneId) { }
  record PlanningSyncResult(List<Created> created, List<Reused> reused,
                            List<Superseded> superseded) { }
  ```
- `coursesession` gère : création d'une `course_session`
  (`exceptional = false`, `status = PLANNED`, `planning_entry_public_id`
  renseigné), réutilisation si une séance `planning` de même
  `entryPublicId` existe et que la règle de réutilisation est sûre
  (DEC-G1-002), supersession logique (`status = CANCELLED` +
  `superseded_by_version`) des séances `planning` **futures et `PLANNED`**
  absentes de la nouvelle version. Les séances `OPEN`/`CLOSED` ne sont
  **jamais** réécrites (DEC-G1-004).
- `planning` ne connaît que des `UUID` et le port. `coursesession` ne
  connaît pas `planning`.

**Conséquences.** Une migration `coursesession` (V13) ajoute
`course_session.planning_entry_public_id` (BINARY(16) NULL, indexé),
`superseded_by_scheduling` et la nullabilité de champs aujourd'hui liés à
l'exceptionnel. `ModularityTests` reste vert (nouveau port dans le
package racine).

**Risques.** Publication partielle si le port échoue → **atténué** :
appel synchrone dans la transaction, toute exception rollback l'ensemble.
Duplication de séances → **atténué** : idempotence par `entryPublicId`
(clé unique `(planning_entry_public_id)` partielle).

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

**Options.**
1. Numéro de ligne du fichier — **rejeté** : instable (réordonnancement,
   ajout/suppression au milieu).
2. Hachage de la ligne brute entière — **rejeté** : toute correction de
   forme (espace, casse formateur) casserait l'identité.
3. **Clé métier normalisée** = hachage stable (SHA-256, tronqué) de la
   concaténation canonique de :
   `academic_year` + `class_code` + `session_date` (UTC) +
   `start_time` + `end_time` + `title` normalisé.
   Le formateur et la salle **ne participent pas** à la clé : un
   changement de formateur/salle sur le même créneau est une
   **modification** de la séance, pas une nouvelle séance.

**Décision.** Option 3. La clé est calculée à la simulation, portée par
`planning_import_row.business_key` puis recopiée sur `planning_entry.business_key`
et transmise au port (`entryPublicId` = `public_id` de la `planning_entry`,
elle-même stable tant que la `business_key` est stable entre versions du
même `schedule`).

**Conséquences.** Deux entrées de la même version avec la même
`business_key` = doublon fichier → ligne `ERROR` (RG-034). Entre
versions, même `business_key` ⇒ réutilisation de la séance ;
`business_key` absente de la nouvelle version ⇒ supersession si la séance
est `PLANNED` future.

**Risques.** Deux créneaux légitimement identiques (même classe, même
horaire, même titre, deux salles) → considérés comme un doublon. Jugé
acceptable pour un prototype ; documenté comme limite. Contournement :
différencier le `title`.

**Sécurité.** La `business_key` ne contient pas de donnée personnelle
(pas d'email formateur). Non exposée au client (interne à la trace de
résolution).

**Transactions.** N/A (valeur dérivée).

**Tests attendus.** stabilité de la clé sous réordonnancement /
correction de casse ; détection de doublon intra-fichier ; réutilisation
inter-versions ; supersession.

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
   `SIMULATED → PUBLISHED | CANCELLED | EXPIRED` (+ `FAILED` **présent**
   ici, cf. §10.1 du prompt), verrou de ligne à la publication,
   re-validation, idempotence par état + par `entryPublicId`, TTL de
   simulation (`expires_at`), audit `AFTER_COMMIT` / `REQUIRES_NEW`.

**Décision.** Option 2. Différence assumée avec `studentimport` :
`planning_import_job` **a** un statut `FAILED` (une publication qui
échoue après avoir dépassé la re-validation — p. ex. le port
`coursesession` lève — rollback tout, et le job passe `FAILED` avec une
`planning_import_job_issue` explicative ; il n'est plus republiable, un
nouvel import est nécessaire). Raison : la publication planning touche un
autre module (séances) et un échec y est un signal fort, à distinguer
d'un simple `SIMULATED` réutilisable.

**Conséquences.** `CHECK (status IN ('SIMULATED','PUBLISHED','CANCELLED','EXPIRED','FAILED'))`.
Republication d'un job **`PUBLISHED`** = idempotent (retourne le même
résultat, ne recrée rien). Republication d'un job `FAILED` / `CANCELLED`
/ `EXPIRED` → `409`.

**Risques.** Concurrence de deux publications du même job → verrou de
ligne : la seconde voit l'état changé après acquisition → idempotent
(si `PUBLISHED`) ou `409` métier. **Jamais `500`.**

**Sécurité.** Périmètre re-vérifié à la publication (pas seulement à la
simulation) : un RP dont le périmètre a changé entre les deux ne peut
pas publier hors périmètre.

**Transactions.** Une seule transaction pour toute la publication
(job + version + entrées + séances via port). Rollback total testé (T3
équivalent).

**Tests attendus.** T1 (simulation sans écriture), T3 (rollback total),
idempotence, concurrence, `EXPIRED` refusé, `FAILED` non republiable.

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
3. Séances `planning` en statut **`OPEN` ou `CLOSED`** : **jamais**
   modifiées ni supersédées (l'émargement en cours / fait fait foi).
   Si leur `business_key` disparaît de la version N+1, elles restent
   telles quelles ; une `planning_import_job_issue` de niveau `WARNING`
   le signale.
4. Séances `planning` **futures et `PLANNED`** dont la `business_key`
   n'est plus dans la version N+1 : `status = CANCELLED`,
   `cancellation_reason = "Superseded by schedule version N+1"`,
   `superseded_by_scheduling = true`. **Aucune suppression physique.**
5. Entrées dont la `business_key` est inchangée : la séance est
   **réutilisée** ; si le formateur / la salle / l'horaire diffèrent et
   que la séance est `PLANNED`, elle est **mise à jour** (et un
   `PlanningEntryChangedEvent` porte les champs modifiés pour G1-D).
6. Nouvelles `business_key` : **création** d'une séance.
7. Séances **exceptionnelles** (`exceptional = true`) : hors du champ du
   planning, jamais touchées par une publication.

**Conséquences.** Migration V13 : `course_session.superseded_by_scheduling`
BOOLEAN NOT NULL DEFAULT FALSE ; `planning_entry.session_public_id`
BINARY(16) NULL.

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
de table (email d'activation seul, `@TransactionalEventListener`
`AFTER_COMMIT` synchrone). Spring Modulith 1.4.12 est présent, **sans**
`spring-modulith-starter-jpa` (donc sans Event Publication Registry).

**Documents.** ARCH §8.2, §8.3, §18.2 ; MDD §23.1 ; CDC §14, §23, §43
RG-033 ; `backend/pom.xml`.

**Options.**
1. Ajouter `spring-modulith-starter-jpa` → Event Publication Registry
   (table `event_publication`, republication au démarrage des events non
   traités). Avantage : garantie de livraison « au moins une fois ».
   Coût : nouvelle dépendance, nouvelle table gérée par Modulith (ou
   par une migration Flyway `event_publication`), changement de
   sémantique des listeners d'audit existants (risque de régression sur
   11 listeners), schéma géré hors `ddl-auto=validate`.
2. **Listeners applicatifs `@TransactionalEventListener(AFTER_COMMIT)` +
   idempotence en base** (clé `dedup_key` unique sur `notification`) +
   transaction **`REQUIRES_NEW`** pour l'écriture des notifications, de
   sorte qu'un échec de notification **ne rollback pas** le métier
   (exigence §12 du prompt). Reprise = tâche `@Scheduled` optionnelle
   qui rejoue les événements « ratés » depuis les données métier
   (idempotence garantit l'absence de doublon).

**Décision.** Option 2 pour G1 (ne pas changer la version ni le socle
Modulith sans justification forte — règle §12 du prompt). L'Event
Publication Registry est **recommandé pour l'après-G1** et tracé comme
dette dans `docs/07` et le rapport final.

**Conséquences.**
- Migration `V14` : table `notification` (`id`, `public_id`,
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
ajoutés par migration `V16` **uniquement si** un test de non-régression
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

**Décision — attribution des numéros (un domaine par migration).**

| Migration | Bloc | Contenu |
|---|---|---|
| `V12__create_planning_tables.sql` | G1-B | `planning_import_job`, `planning_import_job_issue`, `planning_import_row`, `planning_import_row_issue`, `planning_schedule`, `planning_version`, `planning_entry` (+ index, `CHECK`) |
| `V13__extend_course_session_for_planning_and_lifecycle.sql` | G1-B + G1-C | `course_session.planning_entry_public_id`, `superseded_by_scheduling`, nullabilité ; `teacher_substitution` ; `session_cancellation_request` (si retenu) ; index |
| `V14__create_notification_table.sql` | G1-D | `notification` (+ `dedup_key` UNIQUE, index) |
| `V15__create_justification_attachment_table.sql` | G1-E | `justification_attachment` (+ FK vers le justificatif, `status`, `sha256`, index) |
| `V16__…` | G1-F | **uniquement si** un index de performance s'avère nécessaire ; sinon **non créée** |

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
G1-E) est cohérent avec l'ordre des numéros. Si G1-C n'a pas besoin de
nouvelle colonne au-delà de V13, aucune migration V-supplémentaire.

**Risques.** Migration destructive impossible à rollback
automatiquement → **aucune** migration G1 n'est destructive (toutes
additives : `CREATE TABLE`, `ADD COLUMN` nullable). Documenté dans
`docs/06`.

**Sécurité.** N/A.

**Transactions.** Flyway par migration (MySQL : DDL auto-commit — d'où
l'exigence « additive uniquement »).

**Tests attendus.** `./mvnw test` sur base vierge (Flyway rejoue V1→V16),
`ModularityTests`, `ddl-auto=validate` sans erreur.

**Impact déploiement.** Migrations rejouables sur une base de staging
vierge ; pas de downtime (additif).
