# Conception — Import CSV contrôlé des apprenants (tranche V11)

| Élément | Valeur |
|---|---|
| Branche | `feature/student-csv-import` |
| Date de référence | 30 août 2026 |
| HEAD de départ | `35bd04b` (= `main`, PR #22 / V10 fusionnée, arbre propre) |
| Migration ajoutée (**prévue**, non créée) | `V11__create_student_import_tables.sql` (additive) |
| Migrations V1–V10 | **inchangées** |
| Statut du document | Décisions arrêtées — **Checkpoint 0** ; **aucun code produit** ; implémentation à suivre sur la même branche après validation |
| Révision | R2 — correction de la conception transactionnelle et de la traçabilité (le mot « Livré » est remplacé partout par « Prévu » / « Conçu » ; aucun code d'import n'existe). **Correctif R2** : `audit.internal.StudentImportAuditListener` combine `@TransactionalEventListener(phase = AFTER_COMMIT)` **et** `@Transactional(propagation = REQUIRES_NEW)` — la ligne d'audit est persistée dans une transaction dédiée qui ne démarre qu'**après** le commit de la confirmation. |

---

## 0. Synthèse (à valider) — une page

### 0.1 Périmètre exact de la tranche

- **Import CSV des apprenants uniquement** : téléversement d'un fichier
  `.csv`, **simulation** (analyse, normalisation, validation, doublons,
  calcul des actions — **zéro écriture métier**) puis **confirmation**
  atomique (création / mise à jour des comptes, profils, inscriptions ;
  émission des invitations d'activation).
- **Prévu** (conçu, non implémenté) : nouveau module Spring Modulith
  `studentimport` ; migration additive `V11` (4 tables + 1 table de
  séquence) ; 6 endpoints REST ; 2 écrans Angular ; nouveaux ports
  `identity.StudentAccountProvisioner` et
  `enrollment.StudentEnrollmentProvisioner` ; extension
  `academic.ClassGroupDirectory.resolveForImport`.
- **Hors périmètre** : Excel `.xlsx` / multifeuille (US-052) ; assistant
  IA de correspondance de colonnes ; import planning ; réécriture de
  l'identité civile (nom / prénom / e-mail) d'un compte existant ;
  rattachement d'un rythme d'alternance ; PDF ; API générique publique de
  création de compte.

### 0.2 Décisions validables (prêtes à approuver)

| # | Décision |
|---|---|
| D1 | Module dédié `com.esic.connect.studentimport`, propriétaire des tables `student_import_*`. |
| D2 | Transport **multipart** `.csv` uniquement ; le **fichier n'est pas conservé** (nom assaini + SHA-256 + taille seulement). |
| D3 | Séparateur `,` **ou** `;` auto-détecté sur l'en-tête ; UTF-8 strict (BOM toléré) ; RFC 4180 ; **jamais** d'évaluation de formule. |
| D4 | Limites : ≤ **2 MiB**, ≤ **500 lignes** de données (config ; ≥ 100 exigé). |
| D5 | **Simulation** : persiste **uniquement** `student_import_*` — jamais `user_account` / `student_profile` / `enrollment` / `account_invitation` (RG-020). |
| D6 | `confirmable = (blocking == 0 && errorRows == 0)` (RG-021). |
| D7 | **Confirmation = une seule transaction** (`REQUIRED`, jamais `REQUIRES_NEW`), verrou pessimiste sur le job, re-validation complète, rollback total sur toute erreur. |
| D8 | **Reconfirmation** : job `APPLIED` → `200` + bilan mémorisé + `alreadyApplied=true` (règle unique ; le code `IMP_ALREADY_CONFIRMED` est **supprimé** pour ce cas). |
| D9 | Lignes devenues invalides entre simulation et confirmation → `409 IMP_STALE_SIMULATION`, anomalies rafraîchies persistées, **rien appliqué**. |
| D10 | **`student_number`** : colonne CSV **optionnelle** ; si absente et création de profil nécessaire → **génération serveur atomique** `ESIC-{annéeDébut}-{séquence}` via table `student_number_sequence` (verrou de ligne dans la transaction de confirmation) ; l'unicité SQL `uq_student_profile_student_number` reste l'autorité ; collision → bump + nouvelle tentative bornée. |
| D11 | Données temporaires **minimisées** : colonnes typées explicites sur `student_import_row` (pas de duplicata JSON intégral du CSV) ; seules les cellules en anomalie conservent leur valeur reçue (tronquée). |
| D12 | Purge **courte configurable** : `SIMULATED`/`CANCELLED`/`EXPIRED` supprimés (CASCADE) après `P7D` **par défaut (décision de prototype)** ; jobs `APPLIED` → agrégats conservés, lignes filles purgées après `P30D` **(décision de prototype)**. |
| D13 | Rôles : `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION` global ; `PEDAGOGICAL_MANAGER` périmètre ; `TEACHER`/`STUDENT` → `403`. |
| D14 | Audit sans PII ; `audit.internal.StudentImportAuditListener` porte **à la fois** `@TransactionalEventListener(phase = AFTER_COMMIT)` **et** `@Transactional(propagation = REQUIRES_NEW)` : il ne s'exécute qu'**après** le commit réussi de la transaction de confirmation, puis persiste la ligne `audit_event` dans sa **propre** transaction. Rollback de la confirmation ⇒ phase `AFTER_COMMIT` jamais atteinte ⇒ **aucun** événement traité, **aucune** trace. Ce `REQUIRES_NEW` est sûr car strictement postérieur au commit métier (≠ motif legacy `@EventListener` + `REQUIRES_NEW`). |

### 0.3 Points restant à approuver (ambiguïtés ouvertes)

Détail au §12. Les principaux :

1. **Jeu de colonnes** : docs/01 §8.1 (7) vs docs/02 §10.4 (14). Proposé :
   6 obligatoires + 5 optionnelles ; `level_code` / `promotion_code` /
   `work_study_pattern` **ignorés** (avertissement).
2. **`academic_year`** du CSV rapproché de `academic_year.code` — format
   attendu (`AAAA-AAAA` ?) à confirmer.
3. **Rétention** `P7D` / `P30D` (prototype) vs « 30 à 90 jours » docs/07.
4. **Non-conservation du fichier** d'origine (vs `stored_file_id` docs/04
   §16.1).
5. **Tables dédiées** `student_import_*` vs table générique `import_job`
   docs/04 §16 (frontières Modulith).
6. **Application non partielle** : une ligne `ERROR` bloque toute la
   confirmation (vs « lignes ignorées » IMP-STU-04, réinterprété comme
   les actions `NONE`).
7. **Compte existant, identité différente** → `WARNING`, aucune
   réécriture (vs « propose une mise à jour » docs/02 §10.7).
8. **Confirmation par un tiers** : staff global oui, `PEDAGOGICAL_MANAGER`
   seulement son propre job.
9. **Format du numéro généré** (`ESIC-{annéeDébut}-{séquence 5 chiffres}`)
   et largeur de séquence.
10. Ajout de la configuration `spring.servlet.multipart.*` (aucun impact
    sur les modules existants).

### 0.4 Invariants transactionnels (garantis par la conception)

| Inv. | Énoncé |
|---|---|
| **T1** | La **simulation** n'exécute **aucune** écriture métier : seules `student_import_job` / `_job_issue` / `_row` / `_row_issue` sont écrites. Vérifié par comptage avant/après en test. |
| **T2** | La **confirmation** s'exécute dans **une seule** transaction (`@Transactional`, propagation `REQUIRED`). Aucune méthode d'application (`identity.*`, `enrollment.*`) n'ouvre de transaction autonome (`REQUIRES_NEW` **interdit** sur ce chemin) ni ne passe par `EnrollmentPersister` (qui est `REQUIRES_NEW`). |
| **T3** | Toute exception pendant la confirmation → **rollback complet** : aucun `user_account`, `student_profile`, `enrollment`, `user_role`, `account_invitation` ; aucune valeur de séquence de numéro consommée ; le job reste `SIMULATED` (re-confirmable). |
| **T4** | Aucune **invitation** n'est persistée **ni publiée durablement** si la confirmation échoue : la ligne `account_invitation` est écrite dans la transaction (donc annulée au rollback) ; l'événement `AccountInvitationIssuedEvent` est publié **dans** la transaction et l'e-mail part **uniquement** via `notification.InvitationEmailListener` (`@TransactionalEventListener(AFTER_COMMIT)`) — jamais si la transaction n'est pas committée. |
| **T5** | Aucun événement synchrone `@EventListener` + `REQUIRES_NEW` n'est déclenché par le chemin d'import : le port `identity` **ne publie pas** `AccountLifecycleEvent` ; le port `enrollment` **ne publie pas** `EnrollmentChangeEvent`. L'audit du module (`StudentImportChangeEvent`) est consommé par `audit.internal.StudentImportAuditListener` annoté **à la fois** `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` **et** `@Transactional(propagation = Propagation.REQUIRES_NEW)` : le listener ne s'exécute qu'**après** le commit réussi de la transaction de confirmation, puis persiste la ligne `audit_event` dans une transaction **dédiée**. Ce `REQUIRES_NEW` est sûr ici — contrairement au motif legacy `@EventListener` + `REQUIRES_NEW` — parce qu'il ne démarre **jamais** avant le commit métier : si la confirmation rollback, la phase `AFTER_COMMIT` n'est pas atteinte, **aucun** événement n'est traité et **aucune** ligne d'audit n'est écrite. Une confirmation annulée ne laisse donc **aucune** trace. |
| **T6** | La **reconfirmation** est idempotente : verrou pessimiste sur le job ; job `APPLIED` → `200` + bilan mémorisé, aucune ré-écriture. Exactement un ensemble de comptes créés, même en concurrence. |

---

## 1. Exigences couvertes (couverture **planifiée**)

> Aucune de ces exigences n'est encore implémentée. « Couverture
> planifiée » = décrit et cadré par ce document, à réaliser aux
> checkpoints CP1–CP10.

| Réf. | Exigence | Couverture planifiée |
|---|---|---|
| EF-IMP-001 | Simuler un import apprenant CSV | `POST /api/v1/student-imports` (multipart) — analyse + normalisation + validation + doublons, **aucune écriture métier** |
| EF-IMP-002 | Confirmer un import apprenant | `POST /api/v1/student-imports/{publicId}/confirm` — **une** transaction, création / mise à jour comptes & inscriptions + invitations |
| US-050 | Simuler l'import CSV (RP) | backend + écran Angular |
| US-051 | Confirmer une simulation valide (RP) | backend + écran Angular |
| RG-020 | Aucune donnée métier créée avant confirmation | simulation ne persiste que des lignes techniques temporaires (invariant **T1**) |
| RG-021 | Une erreur bloquante empêche la confirmation | `confirmable = false` dès une anomalie `ERROR`/`BLOCKING` |
| RG-022 | Un utilisateur existant est mis à jour, pas dupliqué | résolution par e-mail normalisé + numéro étudiant ; action `UPDATE`/`TRANSFER`/`NONE` |
| RG-023 | Un changement de classe conserve l'historique | port `provisionTransfer` (clôture `TRANSFERRED`, nouvelle inscription liée, aucune suppression) |
| RG-024 | Une opération groupée exige une confirmation | simulation → revue → confirmation explicite |
| AC-004 | Bilan créations / mises à jour / déplacements / erreurs / avertissements | `StudentImportJobResponse.summary` |
| AC-005 | Un apprenant existant n'est pas recréé | unicité SQL `user_account.email` + pré-contrôle |
| AC-006 | Après changement de classe, ancienne inscription consultable | réutilise la sémantique `transfer` |
| IMP-STU-01..04 (docs/02 §10.9) | Critères Gherkin | tests d'intégration (§11) |
| TI-001..TI-012 (docs/08 §9) | Tests d'import apprenants | §11 ; TI-009 (XLSX) hors périmètre |
| NFR-PERF-03 / TP-004 | Import de 100 apprenants dans un délai acceptable | limite ≥ 100 ; temps mesuré au CP10 |
| docs/07 §10 | Sécurité des imports | §9 |

---

## 2. Audit de l'existant

### 2.1 État Git et base de départ

- Branche `feature/student-csv-import`, HEAD `35bd04b` = `main` (V10
  fusionnée). `git status` propre. Schéma en **version 10**. Prochaine
  migration disponible : **`V11`** (non créée dans ce checkpoint).
- Baselines de test à relever au CP1 puis CP10.

### 2.2 Audit transactionnel des collaborateurs réutilisés (Exigence 3)

**Constat central** : les services `enrollment` existants **ne peuvent
pas** être réutilisés tels quels dans une transaction de confirmation
atomique, car ils écrivent via `EnrollmentPersister`
(`@Transactional(propagation = REQUIRES_NEW)`) et publient des événements
consommés en `@EventListener` synchrone + `REQUIRES_NEW`.

| Élément | Fichier | Propagation / mécanisme | Écritures métier sur rollback de l'appelant | Effets susceptibles de **survivre** au rollback global |
|---|---|---|---|---|
| `EnrollmentPersister.persist(StudentProfile)` / `persist(Enrollment)` | `enrollment/internal/EnrollmentPersister.java` | **`REQUIRES_NEW`** (transaction autonome, commit indépendant) | ❌ **NON annulées** — la ligne `student_profile` / `enrollment` est committée dans sa propre transaction | — (mais la donnée métier elle-même est le fuite) |
| `StudentProfileService.create` | `enrollment/internal/StudentProfileService.java` | **Pas** `@Transactional` ; INSERT délégué à `EnrollmentPersister` (`REQUIRES_NEW`) | ❌ profil **committé** via le persister | `EnrollmentChangeEvent(STUDENT_PROFILE, CREATED)` → `EnrollmentAuditListener` (`@EventListener` + `REQUIRES_NEW`) → **ligne d'audit committée** |
| `EnrollmentService.enroll` | `enrollment/internal/EnrollmentService.java` | **Pas** `@Transactional` ; INSERT délégué à `EnrollmentPersister` (`REQUIRES_NEW`) | ❌ inscription **committée** via le persister | `EnrollmentChangeEvent(ENROLLMENT, CREATED)` → audit `REQUIRES_NEW` → **committée** |
| `EnrollmentService.transfer` | idem | `@Transactional` propagation **`REQUIRED`** (rejoint l'appelant) ; `saveAndFlush` direct (pas de persister) | ✅ UPDATE de clôture + INSERT annulés avec l'appelant | **2×** `EnrollmentChangeEvent` (`TRANSFERRED`, `CREATED`) → audit `REQUIRES_NEW` → **committées** |
| `EnrollmentService.close` | idem | `@Transactional` **`REQUIRED`** | ✅ | `EnrollmentChangeEvent(CLOSED)` → audit `REQUIRES_NEW` → **committée** |
| `EnrollmentChangePublisher.publish(...)` | `enrollment/internal/EnrollmentChangePublisher.java` | `ApplicationEventPublisher.publishEvent` **synchrone** | n/a | déclenche les listeners d'audit `@EventListener` **immédiatement**, dans une transaction `REQUIRES_NEW` qui **commite avant** le rollback de l'appelant |
| `AccountInvitationService.issue` | `identity/internal/AccountInvitationService.java` | `@Transactional` propagation **`REQUIRED`** (rejoint l'appelant) | ✅ `user_role` + `account_invitation` annulés avec l'appelant | (a) `AccountInvitationIssuedEvent` → `notification.InvitationEmailListener` **`@TransactionalEventListener(AFTER_COMMIT)`** → e-mail **seulement après commit** ✅ ; (b) `AccountLifecycleEvent(INVITATION_ISSUED)` → `AccountLifecycleAuditListener` **`@EventListener` + `REQUIRES_NEW`** → **ligne d'audit committée**, survit au rollback ❌ |
| `AccountInvitationService.assignRoleIfAbsent` (interne à `issue`) | idem | dans la transaction de `issue` (`REQUIRED`) | ✅ `user_role` annulé | — |
| Création de `UserAccount` + rôle | seul `DefaultDemoAccountProvisioner` (`@Profile("demo")`, `@Transactional` `REQUIRED`) et `UserManagementService` (`@Transactional` `REQUIRED`, publie `AccountLifecycleEvent` synchrone → audit `REQUIRES_NEW`) | **Aucune API publique non-démo** de création de compte aujourd'hui | n/a | `UserManagementService` : `AccountLifecycleEvent` → audit `REQUIRES_NEW` → committée ❌ |
| Tous les listeners `audit.internal.*` | `audit/internal/*AuditListener.java` | `@EventListener` + `@Transactional(propagation = REQUIRES_NEW)` | n/a | **la ligne `audit_event` commite pendant le `publishEvent`** → survit à tout rollback ultérieur de l'appelant. Dette connue et documentée du projet. |
| `notification.InvitationEmailListener` | `notification/internal/InvitationEmailListener.java` | `@TransactionalEventListener(AFTER_COMMIT)` (défaut `fallbackExecution = false`) | n/a | ✅ **correct** : l'e-mail n'est envoyé que si la transaction qui a publié l'événement **commite** ; publié hors transaction ⇒ **non exécuté**. |

**Synthèse de l'audit.** Trois familles d'effets survivraient au
rollback global si l'on réutilisait les services existants :

1. **Écritures métier via `EnrollmentPersister` (`REQUIRES_NEW`)** —
   `student_profile` et `enrollment` créés par `StudentProfileService.create`
   / `EnrollmentService.enroll` **ne seraient pas annulés**. → **Bloquant.**
2. **Lignes d'audit `@EventListener` + `REQUIRES_NEW`** — chaque
   `EnrollmentChangeEvent` / `AccountLifecycleEvent` publié
   **synchroniquement** écrit une ligne `audit_event` qui commite
   immédiatement. → **Trace trompeuse** après un import annulé.
3. **`AccountInvitationService.issue`** — les *écritures* (`user_role`,
   `account_invitation`) sont sûres (transaction `REQUIRED`), et l'e-mail
   est sûr (`AFTER_COMMIT`), mais le `AccountLifecycleEvent(INVITATION_ISSUED)`
   laisse une ligne d'audit. → **Trace trompeuse.**

Conclusion : **le chemin d'import n'appelle ni `StudentProfileService.create`,
ni `EnrollmentService.enroll`, ni `AccountInvitationService.issue`.** Il
passe par de **nouveaux ports** (§4) qui écrivent en direct (repositories,
`saveAndFlush`) dans la transaction de l'appelant et **ne publient aucun
événement synchrone d'audit**. L'audit de l'import est un **unique**
`StudentImportChangeEvent` consommé par
`audit.internal.StudentImportAuditListener` en
`@TransactionalEventListener(AFTER_COMMIT)` **+**
`@Transactional(REQUIRES_NEW)` : la ligne `audit_event` est écrite dans
une transaction dédiée qui **ne démarre qu'après** le commit de la
confirmation (aucune trace si elle rollback ; cf. **T5**).

### 2.3 Autres éléments réutilisés

| Module | Élément | Usage |
|---|---|---|
| `academic` | `ClassGroupDirectory` (`findByPublicId` / `findByInternalId`) | **À étendre** : `resolveForImport(formation_code, class_code, academic_year)` |
| `academic` | `AcademicScopeDirectory` (`hasGlobalScope` / `isClassInScope` / `visibleClassGroupIds`) | Contrôle de périmètre `PEDAGOGICAL_MANAGER` (403 job / `ERROR` ligne) |
| `identity` | `CurrentUserResolver.resolveInternalId(sub)` | Auteur des écritures |
| `identity` | `EmailNormalization.normalize` | Normalisation d'e-mail |
| `identity` | `InvitationTokenService` (jeton + SHA-256), `AccountInvitationRepository`, `app.security.invitation.token-ttl` | Réutilisés **à l'intérieur** du nouveau port `identity` (pas via `issue`) |
| `identity` | `AccountInvitationIssuedEvent` (type **public** `com.esic.connect.identity`) | Publié par le nouveau port pour déclencher l'e-mail `AFTER_COMMIT` |
| `shared.web` | `ApiError`, `GlobalExceptionHandler` | Format d'erreur commun, codes `IMP_*` |
| `shared` | `BaseEntity` (`id` / `publicId` BINARY(16) / `@Version`), `ClockConfig` | Entités V11, horodatages déterministes |
| `attendance.internal.AttendanceCsvWriter` | Neutralisation d'injection de formule | **Motif** à répliquer dans `studentimport` pour un éventuel ré-export du rapport d'anomalies (copie contrôlée, pas de dépendance inter-module) |

### 2.4 Frontend existant réutilisé

Espace `/students` (guard `EnrollmentWeb.MANAGE_ROLES`) ;
`RoleContextService.effectiveRoles()` ; `roleGuard` ;
`normalizeHttpError` / `SAFE_FALLBACK_MESSAGE` ; `NotificationService` ;
`MatPaginatorIntl` francisé ; conventions `mat-table` + `mat-sort`
(liste blanche) + `mat-paginator` ; confirmations **en ligne** ; JWT en
mémoire seule (RG-085), aucun `localStorage` / `sessionStorage`. Le
fichier est transmis **brut** (`FormData`), jamais lu côté navigateur.

### 2.5 Règles fixées par la documentation

- **docs/01 §8.1 / docs/02 §10.4** — modèle CSV (divergent, §12.A) ;
  obligatoires : nom, prénom, e-mail, code de formation, code de classe,
  année scolaire ; téléphone facultatif.
- **docs/01 §8.2 / docs/02 §10.8** — contrôles ; niveaux d'anomalie
  `INFO` / `WARNING` / `ERROR` / `BLOCKING` ; chaque erreur indique
  fichier, feuille, n° de ligne, colonne, valeur reçue, motif, correction
  attendue, gravité.
- **docs/02 §10.6** — deux phases : simulation (aucune écriture métier
  définitive) puis application.
- **docs/02 §10.7** — utilisateur existant : ne pas dupliquer, proposer
  une mise à jour, clôturer l'ancienne inscription si nécessaire,
  conserver l'historique.
- **docs/02 §10.3 / §23.1 / docs/08 TI-008** — au moins **100
  apprenants**.
- **docs/02 §11 / §8.3** — après création : `PENDING_ACTIVATION`, jeton,
  validité un mois, e-mail d'invitation, journalisation.
- **docs/04 §16** — tables génériques `import_job` / `import_sheet` /
  `import_row` / `import_row_issue` ; statuts `UPLOADED` … `EXPIRED` ;
  `ON DELETE CASCADE` accepté avant confirmation / à la purge ; **les
  données métier créées ne sont jamais supprimées avec l'import**.
- **docs/04 §29.1 / §41** — confirmation en **une transaction** ; échec →
  `FAILED`, **rien** appliqué ; import **verrouillé** pendant la
  confirmation ; `ImportApplied` publié.
- **docs/07 §9 / §10** — pas d'exécution de macro / formule ; pas de
  chemin fourni par le client ; nom généré ; limite de taille et de
  lignes ; rejet des fichiers chiffrés ; transaction de confirmation ;
  nettoyage des données temporaires. Conservation « imports temporaires :
  30 à 90 jours » (proposition non validée, §12.C).
- **RG-006 / RG-012 / RG-023** — une adresse e-mail = un utilisateur
  (permanent BTS→Master) ; au plus une inscription `ACTIVE` par apprenant
  et par année ; historique jamais supprimé.

---

## 3. Décisions de règles métier

### 3.1 Format CSV

Voir §5. Résumé : 6 colonnes obligatoires + 5 optionnelles ; séparateur
`,`/`;` auto-détecté ; UTF-8 strict ; ≤ 2 MiB ; ≤ 500 lignes.

### 3.2 `student_number` — génération serveur atomique (Exigence 2)

- La colonne CSV `student_number` **reste optionnelle**.
- **Si elle est fournie** : valeur rognée, majuscule ; contrôlée en base
  — déjà attribuée à **un autre** compte → anomalie
  `ERROR IMP_STUDENT_NUMBER_TAKEN` ; attribuée au **même** compte
  (résolu par e-mail) → cohérent, aucune génération.
- **Si elle est absente** *et* qu'un `student_profile` doit être créé
  (`student_profile.student_number` est `NOT NULL`) : le serveur
  **génère** le numéro **à la confirmation** (jamais à la simulation —
  aucune valeur de séquence n'est consommée tant que l'import n'est pas
  appliqué).
- **Format recommandé** : `ESIC-{annéeDébut}-{séquence}` où :
  - `annéeDébut` = année de **début** de l'année scolaire cible (dérivée
    de `academic_year.start_date` ou, à défaut, des 4 premiers chiffres
    du code d'année) — format `AAAA` ;
  - `séquence` = compteur **borné**, zéro-padé sur **5 chiffres**
    (`00001`..`99999`), largeur configurable
    (`app.import.student.number-sequence-width`, défaut `5`).
  - Exemple : `ESIC-2026-00042`.
- **Autorité d'unicité** : la contrainte SQL
  `uq_student_profile_student_number` reste **la seule autorité**. La
  génération est un *pré-remplissage*, jamais une garantie.
- **Concurrence** — table de séquence dédiée (créée dans la future
  `V11`, **non créée ici**) :

  ```text
  student_number_sequence (
      start_year   INT UNSIGNED PRIMARY KEY,
      next_value   INT UNSIGNED NOT NULL,        -- prochaine valeur libre
      updated_at   TIMESTAMP(6) NOT NULL
  )
  ```

  - Allocation **dans la transaction de confirmation** (propagation
    `REQUIRED`, aucune transaction autonome) :
    `INSERT INTO student_number_sequence (start_year, next_value, updated_at)
     VALUES (:y, 2, :now)
     ON DUPLICATE KEY UPDATE next_value = next_value + 1, updated_at = :now;`
    puis relecture de la valeur allouée (`next_value - 1`).
    L'`UPDATE` prend un **verrou de ligne** sur `start_year` : deux
    confirmations concurrentes visant la même année **se sérialisent** sur
    cette ligne (contention bornée : une ligne par année).
  - Si le rollback de la confirmation survient, l'`UPDATE` de la séquence
    est **annulé avec la transaction** (invariant **T3**) : aucune valeur
    « brûlée ».
- **Nouvelle tentative bornée** — si le numéro généré entre malgré tout
  en collision avec un numéro **pré-existant** (p. ex. un
  `ESIC-2026-00042` saisi manuellement dans un CSV antérieur) :
  1. `saveAndFlush` du `student_profile` lève
     `DataIntegrityViolationException` sur
     `uq_student_profile_student_number` ;
  2. le service **incrémente à nouveau** la séquence et **réessaie**,
     jusqu'à `app.import.student.number-alloc-max-retries` (défaut `5`)
     fois ;
  3. épuisement → anomalie `ERROR IMP_STUDENT_NUMBER_ALLOC_FAILED`, la
     confirmation **s'abandonne** (rollback complet, atomique).
  - `next_value` dépasse la borne (`10^width`) pour l'année →
    `ERROR IMP_STUDENT_NUMBER_EXHAUSTED` (limite de prototype, largeur
    configurable).
- **Simulation** : les lignes concernées portent `planned_action` normal
  + une anomalie `INFO IMP_STUDENT_NUMBER_WILL_BE_GENERATED`
  (« numéro attribué automatiquement à la confirmation »).
- **Migration non ajoutée ici** : la table `student_number_sequence`
  fait partie de la future `V11` (CP1).

### 3.3 `planned_action` d'une ligne

| Situation (résolue à la simulation, **re-vérifiée** à la confirmation) | `planned_action` | Effet à la confirmation |
|---|---|---|
| E-mail absent de la base | `CREATE_ACCOUNT_AND_ENROLL` | compte `PENDING_ACTIVATION` + rôle `STUDENT` + invitation + profil (+ numéro généré si absent) + inscription |
| E-mail = compte `PENDING_ACTIVATION` sans profil | `CREATE_ACCOUNT_AND_ENROLL` (réémet l'invitation) | profil + inscription (invitation réémise) |
| Compte existant (≥ `ACTIVE`), pas de profil | `ENROLL_EXISTING` | profil + inscription |
| Compte + profil, aucune inscription active l'année cible | `ENROLL_EXISTING` | inscription |
| Compte + profil, inscription active **dans la classe cible** | `NONE` | aucun effet (ligne « ignorée » du bilan) |
| Compte + profil, inscription active **autre classe, même année** | `TRANSFER_CLASS` | `provisionTransfer` (clôture `TRANSFERRED`, nouvelle inscription liée) |
| Compte + profil, inscription active **autre année** | `ENROLL_EXISTING` | nouvelle inscription (année distincte) |
| Compte `ARCHIVED` / `LOCKED` | — | anomalie `ERROR IMP_ACCOUNT_NOT_USABLE` |
| Téléphone / alternance divergents sur un profil existant | `UPDATE_PROFILE` (combiné) | maj `phone` / `work_study` / `company_name` **uniquement** — jamais l'identité |

Bilan **AC-004** : `created` = `CREATE_ACCOUNT_AND_ENROLL` ; `updated` =
`UPDATE_PROFILE` + `ENROLL_EXISTING` ; `moved` = `TRANSFER_CLASS` ;
`ignored` = `NONE` ; `errors` / `warnings` = comptes d'anomalies.

### 3.4 Statuts `student_import_job`

`SIMULATED` → `APPLIED` (chemin nominal) ; `SIMULATED` → `CANCELLED`
(annulation explicite) ; `SIMULATED` → `EXPIRED` (purge).

- **Pas de statut `CONFIRMED` observable** : la transition n'existe que
  le temps de la transaction verrouillée.
- **Pas de statut `FAILED` dans cette tranche** : une confirmation qui
  échoue **rollback intégralement** et le job **reste `SIMULATED`**
  (re-confirmable après correction de la cause, ou annulable).
  Aucune écriture autonome de statut (invariant **T2**). Divergence
  documentée vs docs/04 §29.1 / §41 (qui supposent un pipeline
  asynchrone) — §12.F. `FAILED` reste dans l'énumération, réservé à une
  évolution asynchrone.
- `UPLOADED` / `ANALYZING` / `WAITING_CONFIRMATION` de docs/04 §16.1 :
  l'analyse est **synchrone** → passage direct à `SIMULATED`.

---

## 4. Ports d'import — conçus pour **une seule transaction atomique** (Exigence 4)

Principes appliqués :

- **P1** — Aucune méthode d'application n'ouvre de transaction autonome :
  toutes sont `@Transactional` **propagation `REQUIRED`** (défaut) et
  rejoignent la transaction de la confirmation.
- **P2** — Aucune méthode d'application n'utilise `EnrollmentPersister`
  (`REQUIRES_NEW`) : écriture **directe** par
  `repository.saveAndFlush(...)`.
- **P3** — Aucune méthode d'application ne publie d'événement consommé en
  `@EventListener` synchrone + `REQUIRES_NEW` (ni `EnrollmentChangeEvent`,
  ni `AccountLifecycleEvent`).
- **P4** — La ligne `account_invitation` est écrite **dans** la
  transaction (donc annulée au rollback). L'e-mail est déclenché
  **uniquement** par `AccountInvitationIssuedEvent` publié **dans** la
  transaction et consommé en `@TransactionalEventListener(AFTER_COMMIT)`.
- **P5** — L'audit de l'import est **un seul** `StudentImportChangeEvent`
  publié **dans** la transaction de confirmation, consommé par
  `audit.internal.StudentImportAuditListener` annoté **à la fois** :
  - `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`
    — le listener n'est invoqué qu'**après** le commit réussi de la
    transaction de confirmation ;
  - `@Transactional(propagation = Propagation.REQUIRES_NEW)` — la ligne
    `audit_event` est persistée dans une transaction **dédiée** (la
    transaction de confirmation est déjà terminée quand la phase
    `AFTER_COMMIT` s'exécute ; une transaction neuve est nécessaire pour
    écrire).

  **Ce `REQUIRES_NEW` est sûr ici**, à la différence du motif legacy
  `@EventListener` + `REQUIRES_NEW` du projet : il ne démarre **jamais**
  avant la fin de la transaction métier. Si la confirmation rollback, la
  phase `AFTER_COMMIT` n'est **pas** atteinte, le listener n'est **pas**
  invoqué, **aucun** événement n'est traité et **aucune** ligne d'audit
  n'est écrite. La combinaison `AFTER_COMMIT` + `REQUIRES_NEW` donne donc
  à la fois l'absence de trace sur rollback (propriété de `AFTER_COMMIT`)
  et une persistance effective de l'audit après commit (propriété de
  `REQUIRES_NEW`). C'est une **déviation volontaire** du motif legacy,
  dans le bon sens ; la migration globale reste une dette séparée.

`ModularityTests` doit rester vert : `studentimport` dépend uniquement de
`identity` (ports + événement public), `enrollment` (port), `academic`
(ports), `shared` ; publie `StudentImportChangeEvent` vers `audit`. Aucun
import de `*.internal` d'un autre module.

### 4.1 `identity` — nouveau port `StudentAccountProvisioner`

```java
package com.esic.connect.identity;

public interface StudentAccountProvisioner {

    /** LECTURE SEULE — détection de doublon en base pendant la SIMULATION.
     *  N'ouvre aucune écriture. */
    Optional<ExistingAccountView> findByEmail(String rawEmail);

    /**
     * APPLICATION — s'exécute DANS LA TRANSACTION DE L'APPELANT
     * (propagation REQUIRED ; JAMAIS REQUIRES_NEW). Écrit en direct :
     *   - user_account (PENDING_ACTIVATION) si absent ;
     *   - user_role STUDENT actif si absent ;
     *   - account_invitation (empreinte SHA-256, TTL configuré,
     *     révocation des invitations PENDING antérieures).
     * Publie AccountInvitationIssuedEvent (pour l'e-mail AFTER_COMMIT).
     * NE PUBLIE PAS AccountLifecycleEvent (aucun audit synchrone).
     * Le jeton BRUT ne sort jamais du module identity.
     *
     * Compte déjà PENDING_ACTIVATION -> réémission dans la même
     * transaction. Compte ACTIVE/SUSPENDED/LOCKED/ARCHIVED ->
     * StudentAccountException (retraduite IMP_*), aucune écriture.
     */
    PreparedAccount prepareStudentAccountAndInvitation(NewStudentAccount cmd, Long issuerUserInternalId);

    record ExistingAccountView(UUID publicId, long internalId, StatusView status,
                               String firstName, String lastName, boolean hasActiveStudentRole) {}
    enum StatusView { PENDING_ACTIVATION, ACTIVE, SUSPENDED, LOCKED, ARCHIVED }
    record NewStudentAccount(String rawEmail, String firstName, String lastName, String phone) {}
    record PreparedAccount(UUID userPublicId, long userInternalId,
                           boolean accountCreated, boolean invitationIssued) {}
}
```

Impl `identity.internal.DefaultStudentAccountProvisioner` — reprend la
logique de `AccountInvitationService.issue` **moins** la publication de
`AccountLifecycleEvent`, avec `@Transactional` propagation `REQUIRED`.
`AccountInvitationService.issue` reste **inchangé** pour le parcours HTTP
mono-compte (son `AccountLifecycleEvent` y est acceptable : ce parcours
commite ou échoue comme une unité, sans risque de rollback multi-lignes).

### 4.2 `enrollment` — nouveau port `StudentEnrollmentProvisioner`

```java
package com.esic.connect.enrollment;

public interface StudentEnrollmentProvisioner {

    // --- LECTURE SEULE (simulation) ---
    Optional<StudentProfileView> findProfileByUser(UUID userPublicId);
    Optional<StudentProfileView> findProfileByStudentNumber(String studentNumber);
    boolean studentNumberTaken(String studentNumber);
    EnrollmentSituation describeSituation(UUID studentProfilePublicId, UUID classGroupPublicId);

    // --- APPLICATION (confirm) — DANS LA TRANSACTION DE L'APPELANT ---
    // Propagation REQUIRED ; écriture directe (saveAndFlush) ;
    // AUCUN EnrollmentPersister ; AUCUN EnrollmentChangeEvent.

    /** Crée student_profile. Numéro fourni par l'appelant, ou null ->
     *  généré via student_number_sequence (verrou de ligne, même
     *  transaction, nouvelle tentative bornée — §3.2). Collision
     *  uq_student_profile_* -> EnrollmentProvisioningException
     *  (transaction marquée rollback-only). */
    StudentProfileView provisionProfile(ProvisionProfile cmd);

    /** Nouvelle inscription ACTIVE. */
    EnrollmentView provisionEnrollment(UUID studentProfilePublicId, UUID classGroupPublicId,
                                       LocalDate startDate, Long actorUserInternalId);

    /** Changement de classe conservant l'historique (clôture TRANSFERRED
     *  + nouvelle inscription liée). Écriture directe, aucun événement. */
    EnrollmentView provisionTransfer(UUID currentEnrollmentPublicId, UUID targetClassGroupPublicId,
                                     LocalDate effectiveDate, String reason, Long actorUserInternalId);

    /** Met à jour phone / work_study / company_name — jamais l'identité. */
    void updateProfileContact(UUID studentProfilePublicId, ProfileContactPatch patch);

    enum EnrollmentSituation { NONE, SAME_CLASS, OTHER_CLASS_SAME_YEAR, OTHER_YEAR }
}
```

Impl `enrollment.internal.DefaultStudentEnrollmentProvisioner` — nouveau
collaborateur interne écrivant via `StudentProfileRepository` /
`EnrollmentRepository` **directement** (`saveAndFlush`), **sans**
`EnrollmentPersister`, **sans** `EnrollmentChangePublisher`. Les
collisions d'unicité (`uq_enrollment_active_per_year`,
`uq_student_profile_*`) remontent en `DataIntegrityViolationException` :
comme on est dans la transaction unique, celle-ci devient
`rollback-only` ; le service de confirmation l'attrape, **abandonne
tout** (`409 IMP_STALE_SIMULATION` ou `IMP_STUDENT_NUMBER_ALLOC_FAILED`),
rien n'est appliqué. Les endpoints HTTP existants (`POST /student-profiles`,
`/enrollments`) continuent d'utiliser le chemin actuel
(`EnrollmentPersister` + événement) — **inchangés**. La duplication de
chemin d'écriture est **assumée** et documentée (motif : garantie
transactionnelle stricte de l'import).

### 4.3 `academic` — extension de `ClassGroupDirectory`

```java
/** Résout une classe par codes fonctionnels pour l'import CSV. Vérifie
 *  l'appartenance classe <-> formation <-> année scolaire. Ne renvoie
 *  jamais l'entité ClassGroup. Aucune décision de sécurité ici. */
ClassGroupResolution resolveForImport(String programCode, String classCode, String academicYearCode);

sealed interface ClassGroupResolution {
    record Found(ClassGroupRef ref) implements ClassGroupResolution {}
    enum Miss implements ClassGroupResolution {
        PROGRAM_UNKNOWN, CLASS_UNKNOWN, CLASS_NOT_IN_PROGRAM,
        ACADEMIC_YEAR_UNKNOWN, CLASS_NOT_IN_YEAR, CHAIN_ARCHIVED
    }
}
```

### 4.4 Orchestration de la confirmation (module `studentimport`)

```text
@Transactional              // UNE SEULE, propagation REQUIRED (nouvelle tx)
confirm(jobPublicId, caller):
    job = repo.lockForUpdate(jobPublicId)                 // SELECT ... FOR UPDATE
    if job.status == APPLIED:  return storedResult(job) + alreadyApplied=true   // 200 idempotent
    if job.status == CANCELLED:                 throw 409 IMP_JOB_CANCELLED
    if job.status == EXPIRED || now > expiresAt: throw 409 IMP_SIMULATION_EXPIRED
    if job.status != SIMULATED:                 throw 409 IMP_NOT_CONFIRMABLE
    if job.confirmForbiddenFor(caller):         throw 403 IMP_CONFIRM_FORBIDDEN     // D8 périmètre

    refreshed = revalidateEveryRow(job, liveDb)           // recalcule issues + planned_action
    persist(refreshed.issues, refreshed.rows)             // dans la même tx
    if refreshed.hasBlockingOrError():         throw 409 IMP_STALE_SIMULATION
    if !job.wasConfirmableAtSimulation():      throw 409 IMP_NOT_CONFIRMABLE

    actorId = currentUserResolver.resolveInternalId(caller)
    for row in refreshed.applicableRows():                // ordre déterministe (rowNumber)
        switch row.plannedAction:
          CREATE_ACCOUNT_AND_ENROLL:
             pa = identity.prepareStudentAccountAndInvitation(row.newAccount(), actorId)   // publie AccountInvitationIssuedEvent
             sp = enrollment.provisionProfile(row.profileCmd(pa.userPublicId()))           // numéro généré si null
             enrollment.provisionEnrollment(sp.publicId(), row.classPublicId(), today, actorId)
          ENROLL_EXISTING:
             sp = row.existingProfile().or(() -> enrollment.provisionProfile(...))
             enrollment.provisionEnrollment(sp.publicId(), row.classPublicId(), today, actorId)
          TRANSFER_CLASS:
             enrollment.provisionTransfer(row.currentEnrollmentPublicId(), row.classPublicId(),
                                          today, "import CSV", actorId)
          UPDATE_PROFILE:
             enrollment.updateProfileContact(row.profilePublicId(), row.contactPatch())
          NONE:  /* rien */
        row.appliedOutcome = ...

    job.markApplied(now, actorId, counts)                 // UPDATE dans la même tx
    publish(StudentImportChangeEvent(APPLIED, "job=<uuid>;created=NN;updated=NN;moved=NN;invited=NN;ignored=NN"))
    return result
// COMMIT -> e-mails d'invitation (InvitationEmailListener, AFTER_COMMIT)
//        -> 1 ligne audit STUDENT_IMPORT_CONFIRMED
//           (StudentImportAuditListener : AFTER_COMMIT + REQUIRES_NEW, transaction dédiée post-commit)
// TOUTE exception -> ROLLBACK TOTAL : 0 compte / profil / inscription / rôle / invitation ;
//   séquence de numéro non consommée ; aucun e-mail ; aucune ligne d'audit ; job reste SIMULATED.
```

---

## 5. Format CSV

### 5.1 En-tête

- Première ligne non vide = en-tête. Noms **insensibles à la casse**,
  **rognés**, **ordre libre**. Séparateur `,` **ou** `;` auto-détecté
  (celui qui produit le plus de colonnes reconnues). BOM UTF-8 toléré et
  retiré. Encodage **UTF-8 strict** → séquence invalide = `BLOCKING
  IMP_ENCODING_INVALID`. Fins de ligne `CRLF`/`LF`. Guillemets RFC 4180.

### 5.2 Colonnes

| Colonne | Obligatoire | Normalisation | Contrôles |
|---|---|---|---|
| `last_name` | **oui** | `trim`, espaces réduits | non vide |
| `first_name` | **oui** | `trim` | non vide |
| `email` | **oui** | `trim`, minuscule (`EmailNormalization`) | syntaxe (`@Email` + garde) ; doublon fichier / base |
| `formation_code` | **oui** | `trim`, majuscule | formation existante |
| `class_code` | **oui** | `trim`, majuscule | classe existante, appartenant à la formation |
| `academic_year` | **oui** | `trim` | rapproché de `academic_year.code` ; classe rattachée à cette année |
| `phone` | non | `trim`, espaces/points retirés | `WARNING` si non conforme `^[+0-9 ().-]{6,20}$` |
| `student_number` | non (§3.2) | `trim`, majuscule | `ERROR` si déjà attribué à un **autre** compte ; sinon généré à la confirmation si absent |
| `birth_date` | non | `trim` | `yyyy-MM-dd` ou `dd/MM/yyyy` ; sinon `WARNING`, valeur ignorée |
| `work_study` | non | `trim`, minuscule | `true`/`false`/`oui`/`non`/`1`/`0`/vide ; sinon `WARNING` |
| `company_name` | non | `trim` | requis si `work_study=true` sinon `WARNING` |

- Colonne **inconnue** → `WARNING IMP_UNKNOWN_COLUMN` (ignorée).
- Colonne obligatoire **absente** → `BLOCKING IMP_MISSING_COLUMN`.
- `level_code` / `promotion_code` / `work_study_pattern` présentes →
  `WARNING IMP_COLUMN_IGNORED` (§12.A).

### 5.3 Lignes

- Ligne entièrement vide → ignorée (non comptée).
- Nombre de colonnes ≠ en-tête → `ERROR IMP_COLUMN_COUNT` (ligne).
- `> 500` lignes → `BLOCKING IMP_TOO_MANY_ROWS`.
- Aucune ligne de données → `BLOCKING IMP_NO_DATA_ROWS`.

### 5.4 Aucune exécution

CSV **analysé comme des données**, jamais évalué. Aucun stockage du
fichier. Un éventuel ré-export du rapport d'anomalies neutralise
l'injection de formule (`'` en préfixe pour `= + - @ \t \r`).

---

## 6. Cycle simulation → confirmation

```text
POST /api/v1/student-imports  (multipart .csv, + programCode?/classCode? de périmètre)
  -> contrôles globaux : type/extension, magie PK (ZIP/XLSX), taille, UTF-8,
     en-tête, colonnes obligatoires, nb de lignes, périmètre du filtre job
  -> student_import_job = SIMULATED  (+ student_import_job_issue global/BLOCKING)
  -> par ligne : normalisation -> validation champ -> resolveForImport(classe/année)
     -> doublon fichier -> doublon base (findByEmail / studentNumberTaken)
     -> périmètre (isClassInScope) -> planned_action
  -> student_import_row (VALID|WARNING|ERROR, colonnes typées) + student_import_row_issue
  -> summary { total, valid, warning, error, blocking,
              plannedCreate, plannedUpdate, plannedTransfer, plannedNoop }
     confirmable = (blocking == 0 && error == 0)
  -> audit STUDENT_IMPORT_SIMULATED  (AFTER_COMMIT + REQUIRES_NEW ; la simulation commite toujours)

  ... revue humaine dans /students/import/:publicId ...

POST /api/v1/student-imports/{publicId}/confirm
  -> §4.4 : UNE transaction, verrou pessimiste, re-validation, application via ports
     REQUIRED, job APPLIED, StudentImportChangeEvent
  -> COMMIT -> e-mails d'invitation (AFTER_COMMIT) + 1 ligne audit (AFTER_COMMIT + REQUIRES_NEW, tx dédiée)
  -> exception -> ROLLBACK TOTAL, job reste SIMULATED, 409 typé
```

---

## 7. Modèle de données V11 (proposé — **non créé dans ce checkpoint**)

Fichier unique `V11__create_student_import_tables.sql`, **additif**,
MySQL 8, `ENGINE=InnoDB`, `utf8mb4` / `utf8mb4_0900_ai_ci` (aligné
V1–V10). **Aucune donnée métier insérée.** Tables **propres au module
`studentimport`** (§12.E).

### 7.1 `student_import_job`

| Colonne | Type | Notes |
|---|---|---|
| `id` | BIGINT UNSIGNED AI PK | |
| `public_id` | BINARY(16) | `UNIQUE` |
| `status` | VARCHAR(16) NOT NULL | `CHECK IN ('SIMULATED','APPLIED','CANCELLED','EXPIRED')` (`FAILED` non utilisé — §3.4) |
| `original_file_name` | VARCHAR(255) NOT NULL | **assaini** (basename, `[^A-Za-z0-9._ -]`→`_`, pas de point initial) |
| `file_sha256` | CHAR(64) NOT NULL | empreinte hex du contenu reçu |
| `file_size_bytes` | INT UNSIGNED NOT NULL | `CHECK > 0` |
| `csv_separator` | CHAR(1) NOT NULL | `,` ou `;` |
| `requested_by_id` | BIGINT UNSIGNED NOT NULL | FK `user_account(id)` `RESTRICT` |
| `scope_program_code` / `scope_class_code` | VARCHAR(80) NULL | filtre job éventuel |
| `total_rows` / `valid_rows` / `warning_rows` / `error_rows` | INT UNSIGNED NOT NULL DEFAULT 0 | |
| `blocking_issue_count` | INT UNSIGNED NOT NULL DEFAULT 0 | |
| `planned_create_rows` / `planned_update_rows` / `planned_transfer_rows` / `planned_noop_rows` | INT UNSIGNED NOT NULL DEFAULT 0 | |
| `applied_created` / `applied_updated` / `applied_transferred` / `applied_invited` / `applied_ignored` | INT UNSIGNED NULL | renseignés à `APPLIED` |
| `confirmable` | BOOLEAN NOT NULL DEFAULT FALSE | figé à la simulation ; re-vérifié à la confirmation |
| `simulated_at` | TIMESTAMP(6) NOT NULL | |
| `confirmed_at` | TIMESTAMP(6) NULL | |
| `confirmed_by_id` | BIGINT UNSIGNED NULL | FK `user_account(id)` `RESTRICT` |
| `expires_at` | TIMESTAMP(6) NOT NULL | `simulated_at + P7D` (config) |
| `created_at` / `updated_at` | TIMESTAMP(6) NOT NULL | |
| `version` | BIGINT UNSIGNED NOT NULL DEFAULT 0 | `@Version` |

Index : `(status, expires_at)` (purge), `(requested_by_id, created_at)`.

### 7.2 `student_import_job_issue` (anomalies globales)

`id` PK · `public_id` `UNIQUE` · `student_import_job_id` FK **CASCADE** ·
`severity` `CHECK IN ('INFO','WARNING','ERROR','BLOCKING')` · `error_code`
VARCHAR(80) · `message` VARCHAR(500) · `column_name` VARCHAR(64) NULL ·
`created_at`.

### 7.3 `student_import_row` — **colonnes typées, pas de JSON brut** (Exigence 6)

| Colonne | Type | Notes |
|---|---|---|
| `id` | BIGINT UNSIGNED AI PK | |
| `public_id` | BINARY(16) `UNIQUE` | |
| `student_import_job_id` | BIGINT UNSIGNED NOT NULL | FK **CASCADE** |
| `row_number` | INT UNSIGNED NOT NULL | n° dans le fichier (en-tête = 1) |
| `input_last_name` | VARCHAR(120) NULL | valeur **normalisée** |
| `input_first_name` | VARCHAR(120) NULL | normalisée |
| `input_email` | VARCHAR(320) NULL | normalisée (minuscule) |
| `input_phone` | VARCHAR(32) NULL | normalisée |
| `input_formation_code` | VARCHAR(80) NULL | normalisée (majuscule) |
| `input_class_code` | VARCHAR(80) NULL | normalisée |
| `input_academic_year` | VARCHAR(40) NULL | normalisée |
| `input_student_number` | VARCHAR(60) NULL | normalisée ; `NULL` si à générer |
| `input_birth_date` | DATE NULL | normalisée ; `NULL` si absente/invalide |
| `input_work_study` | BOOLEAN NULL | normalisée |
| `input_company_name` | VARCHAR(191) NULL | normalisée |
| `row_status` | VARCHAR(12) NOT NULL | `CHECK IN ('VALID','WARNING','ERROR')` |
| `planned_action` | VARCHAR(28) NOT NULL | `CHECK IN ('CREATE_ACCOUNT_AND_ENROLL','ENROLL_EXISTING','UPDATE_PROFILE','TRANSFER_CLASS','NONE')` |
| `resolved_class_public_id` | BINARY(16) NULL | trace de résolution |
| `resolved_user_public_id` | BINARY(16) NULL | compte existant rapproché |
| `resolved_enrollment_public_id` | BINARY(16) NULL | inscription courante (pour `TRANSFER_CLASS`) |
| `student_number_generated` | BOOLEAN NOT NULL DEFAULT FALSE | numéro attribué à la confirmation |
| `applied_outcome` | VARCHAR(20) NULL | `CREATED` / `ENROLLED` / `UPDATED` / `TRANSFERRED` / `NOOP` (à `APPLIED`) |
| `created_at` | TIMESTAMP(6) NOT NULL | |

Contraintes : `UNIQUE (student_import_job_id, row_number)`. Index
`(student_import_job_id, row_status)`.

> **Minimisation** : on stocke uniquement les **11 champs métier
> normalisés** (strict nécessaire à la revue **et** à l'application), pas
> un duplicata JSON de la ligne brute. La valeur **brute** d'une cellule
> n'est conservée **que** si elle a produit une anomalie (colonne
> `received_value` ci-dessous, tronquée à 200 caractères).

### 7.4 `student_import_row_issue`

`id` PK · `public_id` `UNIQUE` · `student_import_row_id` FK **CASCADE** ·
`severity` `CHECK IN ('INFO','WARNING','ERROR','BLOCKING')` ·
`column_name` VARCHAR(64) NULL · `received_value` VARCHAR(200) NULL
(valeur reçue **tronquée**, jamais dans l'audit) · `error_code`
VARCHAR(80) · `message` VARCHAR(500) · `suggested_value` VARCHAR(200)
NULL · `created_at`.

### 7.5 `student_number_sequence` (§3.2)

`start_year` INT UNSIGNED **PK** · `next_value` INT UNSIGNED NOT NULL
`CHECK > 0` · `updated_at` TIMESTAMP(6) NOT NULL. Pas de FK. Alimentée
**uniquement** pendant une confirmation (dans sa transaction).

### 7.6 CASCADE / purge

`ON DELETE CASCADE` sur toute la chaîne
`student_import_job → job_issue / row → row_issue` — **acceptable avant
confirmation et à la purge** (docs/04 §16.4). Les données métier créées
ne dépendent d'aucune de ces FK. `student_number_sequence` n'est jamais
purgée (compteur monotone par année).

---

## 8. Endpoints proposés

Préfixe `/api/v1`. Contrôleur `StudentImportController`
(`studentimport.internal`). `@PreAuthorize` via
`StudentImportWeb.MANAGE_ROLES`
(`ADMIN`,`SUPER_ADMIN`,`SCHOOL_ADMINISTRATION`,`PEDAGOGICAL_MANAGER`).
Décision fine de périmètre **dans le service**. DTO **sans** identifiant
SQL, sans jeton, sans `password_hash`.

| Méthode / URL | Corps | Réponse | Erreurs notables |
|---|---|---|---|
| `POST /student-imports` | `multipart/form-data` : `file` (`.csv`), `programCode?`, `classCode?` | `201` `StudentImportJobResponse` | `415 IMP_UNSUPPORTED_MEDIA_TYPE`, `413`/`400 IMP_FILE_TOO_LARGE`, `400 IMP_MISSING_COLUMN` (+ détail), `400 IMP_TOO_MANY_ROWS`, `403` (filtre job hors périmètre) |
| `GET /student-imports` | query : `status?`, `page`, `size`≤50, `sort`∈`{createdAt}` | `200` `PageResponse<StudentImportJobResponse>` (jobs de l'appelant ; global pour les 3 rôles globaux) | — |
| `GET /student-imports/{publicId}` | — | `200` `StudentImportJobResponse` (+ `summary` + `issues[]` globales + `confirmable`) | `404 IMP_JOB_NOT_FOUND`, `403 IMP_JOB_FORBIDDEN` |
| `GET /student-imports/{publicId}/rows` | query : `rowStatus?`, `severity?`, `action?`, `page`, `size`≤100, `sort`∈`{rowNumber}` | `200` `PageResponse<StudentImportRowResponse>` | `404`, `403` |
| `POST /student-imports/{publicId}/confirm` | `{}` | `200` `StudentImportResultResponse` (+ `alreadyApplied`) | `409 IMP_NOT_CONFIRMABLE`, `409 IMP_STALE_SIMULATION` (+ anomalies rafraîchies), `409 IMP_SIMULATION_EXPIRED`, `409 IMP_JOB_CANCELLED`, `403 IMP_CONFIRM_FORBIDDEN` |
| `POST /student-imports/{publicId}/cancel` | `{}` | `204` | `409 IMP_JOB_NOT_CANCELLABLE`, `404`, `403` |

**Reconfirmation (Exigence 5, règle unique)** : `confirm` sur un job déjà
`APPLIED` → **`200`** avec le **bilan mémorisé** (`applied_*` du job) et
`alreadyApplied = true`. Le code `IMP_ALREADY_CONFIRMED` **n'existe pas**.
`CANCELLED`/`EXPIRED` → `409` (codes distincts). `SIMULATED` mais non
`confirmable` → `409 IMP_NOT_CONFIRMABLE`.

`application.yml` : ajout `spring.servlet.multipart.max-file-size: 2MB`,
`max-request-size: 3MB` ; bloc `app.import.student` :
`max-rows` (500), `max-file-bytes` (2 MiB), `simulation-ttl` (`P7D`),
`applied-rows-ttl` (`P30D`), `number-sequence-width` (5),
`number-alloc-max-retries` (5).

---

## 9. Matrice des rôles

Décision fine **côté serveur** (contexte Spring Security). Le frontend ne
fait que restreindre l'ergonomie (`effectiveRoles()`).

| Capacité | SUPER_ADMIN / ADMIN | SCHOOL_ADMINISTRATION | PEDAGOGICAL_MANAGER | TEACHER | STUDENT | Anonyme |
|---|---|---|---|---|---|---|
| Téléverser + simuler | ✅ global | ✅ global | ✅ (ligne hors périmètre → `ERROR`) | ❌ 403 | ❌ 403 | ❌ 401 |
| Lister / consulter un job + ses lignes | ✅ tous | ✅ tous | ✅ **ses** jobs | ❌ | ❌ | ❌ |
| Confirmer | ✅ tout job `confirmable` | ✅ tout job `confirmable` | ✅ **son** job | ❌ | ❌ | ❌ |
| Annuler | ✅ | ✅ | ✅ **son** job | ❌ | ❌ | ❌ |
| Filtre job `programCode`/`classCode` hors périmètre | n/a | n/a | **403** au téléversement | ❌ | ❌ | ❌ |

Ressource inconnue → `404` ; hors périmètre → `403` (posture
`CourseSessionAccessGuard` / `AcademicScopeGuard`).

---

## 10. Sécurité

| Sujet | Mesure |
|---|---|
| Transport | `MultipartFile` uniquement — **jamais** de chemin client. **Aucun** fichier écrit sur disque. |
| Type de fichier | extension `.csv` **et** `Content-Type` toléré **et** contenu texte : rejet si octet nul, magie `PK\x03\x04` (ZIP/XLSX), marqueur de chiffrement → `415 IMP_UNSUPPORTED_MEDIA_TYPE`. |
| Taille | `≤ 2 MiB` (multipart + re-vérifié) → `413`/`400 IMP_FILE_TOO_LARGE`. |
| Lignes | `≤ 500` → `BLOCKING IMP_TOO_MANY_ROWS`. |
| Aucune exécution | CSV = données ; jamais de formule/macro évaluée (docs/07 §10). |
| Nom de fichier | `original_file_name` **assaini** ; jamais utilisé comme chemin. |
| Empreinte | `file_sha256` conservé ; contenu **non** conservé. |
| Périmètre | `AcademicScopeDirectory` — job (403) / ligne (`ERROR`). |
| Transaction | confirmation `@Transactional` unique + verrou pessimiste ; rollback total sur échec (invariants **T2**–**T5**). |
| Idempotence | verrou + garde de statut + `200` idempotent (`APPLIED`). |
| Audit | `STUDENT_IMPORT_SIMULATED` / `_CONFIRMED` / `_CANCELLED` / `_EXPIRED` — détail `job=<uuid>;created=NN;…`. **Jamais** e-mail, nom, n° étudiant, valeur de cellule, IP. Listener **`@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`** : exécuté seulement après le commit de la confirmation, persistance dans une transaction dédiée ; aucune ligne si la confirmation rollback. Les invitations sont **non auditées séparément** par ce chemin (pas d'`AccountLifecycleEvent`) : le bilan `STUDENT_IMPORT_CONFIRMED` porte `invited=NN`. |
| Données personnelles | DTO sans `id` SQL / `password_hash` / jeton ; `received_value` **tronqué**, exclu de l'audit ; jeton d'invitation **jamais** hors du module `identity`. |
| Rétention | `expires_at` + purge `@Scheduled` (§12.C). |
| Front | fichier transmis brut (`FormData`), jamais parsé côté navigateur ; JWT en mémoire seule ; aucun jeton en URL ; rien en `localStorage`/`sessionStorage`. |

---

## 11. Stratégie transactionnelle et concurrente

| Scénario | Garantie |
|---|---|
| Simulation | **T1** — n'écrit **que** `student_import_*`. Aucune ligne `user_account` / `student_profile` / `enrollment` / `account_invitation`. Vérifié par comptage avant/après. |
| Confirmation | **T2** — **une** `@Transactional` (`REQUIRED`). Début : `SELECT … FOR UPDATE` sur `student_import_job`. Puis re-validation, application via ports **REQUIRED** (jamais `REQUIRES_NEW`, jamais `EnrollmentPersister`), `APPLIED`. |
| Toute exception pendant la confirmation | **T3** — **rollback complet** : 0 compte / profil / inscription / rôle / invitation ; séquence de numéro non consommée ; job reste `SIMULATED`. Aucune écriture autonome de statut. |
| Invitation & e-mail | **T4** — `account_invitation` écrit dans la transaction (annulé au rollback) ; `AccountInvitationIssuedEvent` publié **dans** la transaction ; e-mail via `InvitationEmailListener` **`@TransactionalEventListener(AFTER_COMMIT)`** → **jamais** d'e-mail si rollback. |
| Audit | **T5** — aucun `@EventListener` + `REQUIRES_NEW` sur ce chemin. `StudentImportChangeEvent` consommé par `StudentImportAuditListener` en `@TransactionalEventListener(AFTER_COMMIT)` **+** `@Transactional(REQUIRES_NEW)` : le listener ne démarre qu'après le commit de la confirmation et écrit la ligne `audit_event` dans une transaction dédiée. Rollback de la confirmation → phase `AFTER_COMMIT` non atteinte → **aucune** ligne d'audit. Ce `REQUIRES_NEW` est sûr précisément parce qu'il est postérieur au commit métier. |
| Reconfirmation séquentielle | **T6** — 2ᵉ appel voit `APPLIED` → **`200`** + bilan mémorisé + `alreadyApplied=true`. `CANCELLED`/`EXPIRED` → `409`. |
| Reconfirmation concurrente | Le verrou pessimiste sérialise ; le perdant relit `APPLIED` → réponse idempotente. Exactement **un** ensemble de comptes créés. |
| Collision d'unicité pendant l'application | `user_account.email` / `uq_enrollment_active_per_year` / `uq_student_profile_*` → `DataIntegrityViolationException` dans la transaction unique → **abandon** (`409 IMP_STALE_SIMULATION`), rollback total, job re-simulé. Jamais de `500`. |
| Numéro étudiant généré | Verrou de ligne sur `student_number_sequence(start_year)` dans la transaction ; nouvelle tentative bornée sur collision ; épuisement → `409 IMP_STUDENT_NUMBER_ALLOC_FAILED`, rollback total. |
| Lignes devenues invalides entre simulation et confirmation | Re-validation **complète** (classe archivée, e-mail désormais pris par un **autre** apprenant, n° étudiant désormais utilisé, périmètre RP modifié) → `409 IMP_STALE_SIMULATION` + anomalies rafraîchies persistées ; **aucune application partielle**. |
| Deux simulations aux e-mails recoupés | Les deux simulent sans écriture métier. À la 1ʳᵉ confirmation, les comptes sont créés ; à la 2ᵈ, la re-validation reclasse en `ENROLL_EXISTING` / `NONE` / `TRANSFER_CLASS` — jamais de doublon (autorité = unicité SQL de l'e-mail). Reclassement introduisant un `ERROR` → `409 IMP_STALE_SIMULATION`. |
| Dette transactionnelle globale de l'audit | Le chemin d'import ne la subit pas (**T5**). Elle reste ouverte pour les autres modules — hors périmètre. |

---

## 12. Ambiguïtés nécessitant une décision (à valider)

| Réf. | Ambiguïté | Décision proposée (par défaut) |
|---|---|---|
| **A** | Colonnes : docs/01 §8.1 (7) vs docs/02 §10.4 (14) | 6 obligatoires + `phone`/`student_number`/`birth_date`/`work_study`/`company_name` ; `level_code`/`promotion_code`/`work_study_pattern` **ignorés** (`WARNING`). |
| **B** | `academic_year` = quel champ ? | Rapprochement sur **`academic_year.code`** (insensible à la casse). Format `AAAA-AAAA` à confirmer. |
| **C** | Rétention (docs/07 : 30–90 j) | **Décision de prototype** : `SIMULATED`/`CANCELLED`/`EXPIRED` supprimés après **`P7D`** (config) ; lignes filles d'un job `APPLIED` supprimées après **`P30D`** (config), agrégats conservés. À valider RGPD. |
| **D** | Fichier d'origine (docs/04 §16.1 : `stored_file_id`) | **Non stocké** (nom assaini + SHA-256). `stored_file_id` omis. |
| **E** | Table générique `import_job` (docs/04 §16) | **Tables `student_import_*` dédiées** au module (frontières Modulith ; `planning` aura ses `schedule_import_*`). |
| **F** | Statuts `UPLOADED`/`ANALYZING`/`WAITING_CONFIRMATION` + `FAILED` (docs/04 §16.1, §29.1) | Analyse **synchrone** → `SIMULATED` direct ; **pas de `FAILED`** (rollback → job reste `SIMULATED`, re-confirmable). `FAILED` réservé à une évolution asynchrone. |
| **G** | Application partielle (IMP-STU-04 « lignes ignorées ») | **`ERROR` bloque toute la confirmation** ; « ignorées » = actions `NONE`. |
| **H** | Compte existant, identité différente (docs/02 §10.7) | **`WARNING`, aucune réécriture** de nom/prénom/e-mail. |
| **I** | `student_number` absent | **Génération serveur** `ESIC-{annéeDébut}-{séquence 5 chiffres}` (§3.2) ; largeur / borne configurables. À valider le format et la largeur. |
| **J** | Confirmation par un tiers | **Staff global** peut confirmer tout job ; `PEDAGOGICAL_MANAGER` seulement le sien. |
| **K** | Séparateur `,` vs `;` | **Auto-détection** sur l'en-tête. Tabulation non prise en charge. |
| **L** | Multipart non configuré | **Ajout** `spring.servlet.multipart.*` (impact nul sur l'existant). |

---

## 13. Frontend

### 13.1 Routes (enfants de `/students`)

| Route | Garde | Écran |
|---|---|---|
| `/students/import` | `roleGuard(['ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER'])` | `StudentImportHome` : téléversement + liste des jobs récents |
| `/students/import/:publicId` | même garde + `canActivateChild` | `StudentImportReview` : synthèse + lignes + confirmation |

Nav : l'entrée « Apprenants » gagne une action / onglet « Importer »
(filtrée par `effectiveRoles()`).

### 13.2 `StudentImportHome`

- `input[type=file]` `accept=".csv"` + glisser-déposer ; refus client si
  extension ≠ `.csv` ou taille > 2 MiB (contrôle serveur = autorité) ;
  champs optionnels `programCode` / `classCode`.
- « Lancer la simulation » désactivé tant qu'aucun fichier ; barre de
  progression ; `FormData`.
- `201` → navigation vers `/students/import/:publicId`.
- Anomalies globales → message en ligne contrôlé (liste blanche
  `student-import-errors.ts`), jamais le corps brut ; `403` →
  « périmètre » ; `5xx` → générique.
- `mat-table` des jobs récents (`GET /student-imports`).

### 13.3 `StudentImportReview`

- **Cartes de synthèse** : total, à créer, à mettre à jour, à
  transférer, sans changement, avertissements, erreurs (AC-004). Bandeau
  « non confirmable » listant les anomalies globales.
- **Table des lignes** (`GET …/rows`, pagination serveur ≤ 100) : n°,
  nom, prénom, e-mail, classe cible, `plannedAction` (chip), `rowStatus`
  (chip), anomalies dépliables (gravité, colonne, valeur reçue, message,
  correction attendue). Filtres `rowStatus`/`severity`/`action` (remise à
  la page 0), tri par n° de ligne.
- **Confirmation en ligne** : bouton désactivé si `!confirmable` ;
  panneau avec **récapitulatif chiffré** (« X comptes créés + invités,
  Y inscriptions, Z transferts, W sans changement ») ; `disabled` pendant
  l'appel ; double soumission bloquée ; capacité revérifiée au clic.
  - `200` → bandeau succès + bilan + lien retour ; `alreadyApplied` →
    même bilan, message « déjà appliqué ».
  - `409 IMP_STALE_SIMULATION` → rechargement des lignes + anomalies,
    bandeau « la simulation n'est plus à jour », bouton bloqué jusqu'à
    revue.
  - `409 IMP_SIMULATION_EXPIRED` → « simulation expirée, relancez un
    import ».
  - `409 IMP_NOT_CONFIRMABLE` / `403` → message contrôlé, aucun faux
    succès.
- **Annuler** : `POST …/cancel` (confirmation en ligne) → `204`.
- Perte du contexte de rôle d'écriture → formulaires fermés, boutons
  masqués, requêtes en vol ignorées, aucune fausse confirmation.

### 13.4 Fichiers frontend

`frontend/src/app/features/students/import/` : `student-import.models.ts`,
`student-import-api.service.ts` (une méthode par endpoint ; `FormData` ;
jamais de jeton en URL), `student-import-errors.ts` (liste blanche
explicite `IMP_*`, `5xx` → générique), `student-import-home/`,
`student-import-review/`. Modifs : `app.routes.ts`, `navigation.ts`,
specs `navigation` / `app-shell` / `dashboard` / `app.routes`. Aucune
dépendance npm ajoutée.

---

## 14. Tests

### 14.1 Backend — unitaires (purs, sans DB)

- **Parseur CSV** : `,`/`;`, BOM, `CRLF`/`LF`, RFC 4180, cellule
  multi-lignes, colonne inconnue → `WARNING`, colonne obligatoire absente
  → `BLOCKING`, `> maxRows` → `BLOCKING`, vide / sans données →
  `BLOCKING`, octets non-UTF-8 → `BLOCKING`, magie `PK\x03\x04` → rejet,
  nb de colonnes ≠ en-tête → `ERROR` ligne.
- **Validation de champ** : e-mail, `birth_date`, `work_study`, téléphone.
- **Normalisation** : `trim`, e-mail minuscule, codes majuscule.
- **Dé-duplication fichier** : e-mails / `student_number` identiques
  (charge identique → `WARNING` ; divergente → `ERROR` × 2).
- **Résolution de classe** (mock) : `Found` / chaque `Miss.*`.
- **Calcul `planned_action`** : les 9 situations de §3.3.
- **Génération de numéro** : format `ESIC-{annéeDébut}-{NNNNN}`,
  zéro-padding, borne de largeur, incrément.
- **Assainissement du nom de fichier**.

### 14.2 Backend — `@DataJpaTest` (V11)

- `CHECK` de `status` / `severity` / `row_status` / `planned_action`.
- `UNIQUE (student_import_job_id, row_number)`.
- `ON DELETE CASCADE` `job → job_issue` / `job → row → row_issue`.
- FK `RESTRICT` vers `user_account`.
- `public_id` unique sur les 4 tables.
- `student_number_sequence` : `ON DUPLICATE KEY UPDATE` incrémente ;
  `CHECK next_value > 0`.

### 14.3 Backend — intégration `@SpringBootTest`

- **IMP-STU-01 / TI-001 / TI-008** : fichier valide **100** lignes →
  `SIMULATED`, `confirmable=true`, `summary.plannedCreate=100` ;
  comptages `user_account`/`student_profile`/`enrollment`/`account_invitation`
  **inchangés** ; audit `STUDENT_IMPORT_SIMULATED`.
- **Confirmation** → `APPLIED`, 100 comptes `PENDING_ACTIVATION` + rôle
  `STUDENT` + 100 profils + 100 inscriptions + 100 lignes
  `account_invitation` ; 100 e-mails capturés par le mailer
  enregistreur ; **une** ligne d'audit `STUDENT_IMPORT_CONFIRMED`
  (`invited=100`), **aucune** ligne `INVITATION_ISSUED` /
  `ACCOUNT_INVITATION_ISSUED` séparée ; aucune PII dans l'audit.
- **`StudentImportAuditListener` — transaction métier committée** : une
  confirmation réussie produit **exactement une** ligne `audit_event`
  (catégorie `STUDENT_IMPORT`, action `CONFIRMED`), écrite par le
  listener dans sa **transaction dédiée** (`@TransactionalEventListener(AFTER_COMMIT)`
  + `@Transactional(REQUIRES_NEW)`). Assertions : la ligne est visible
  depuis une transaction tierce **après** le retour de l'appel HTTP ; son
  `id` est postérieur à celui des écritures métier de la confirmation
  (transaction distincte, ouverte après le commit) ; détail
  `job=<uuid>;created=NN;…`, aucune PII. Un spy sur le listener confirme
  **une seule** invocation.
- **Numéros générés** : fichier 100 lignes **sans** `student_number` →
  100 profils avec `ESIC-{annéeDébut}-00001..00100` ; `student_number_sequence.next_value = 101`.
- **IMP-STU-03 / TI-002** : colonne `email` absente → `400` /
  `BLOCKING IMP_MISSING_COLUMN`, `confirmable=false`, confirmation →
  `409 IMP_NOT_CONFIRMABLE`.
- **TI-003** : e-mail invalide → ligne `ERROR`.
- **TI-004** : doublon fichier → `WARNING` (identique) / `ERROR`
  (divergent).
- **IMP-STU-02 / TI-005 / TI-006 / AC-005 / AC-006** : apprenant
  existant, autre classe même année → `TRANSFER_CLASS` ; après
  confirmation, ancienne inscription `TRANSFERRED` consultable, **aucun**
  doublon de compte.
- **TI-007** : `PEDAGOGICAL_MANAGER`, filtre job hors périmètre →
  `403` ; ligne hors périmètre → `ERROR IMP_CLASS_OUT_OF_SCOPE`.
- **TI-010** : fichier > 2 MiB → `413`/`400`.
- **TI-011** : contenu `.xlsx` (magie ZIP) → `415`.
- **TI-012** : double confirmation séquentielle → 2ᵉ = **`200`**
  idempotent (`alreadyApplied=true`), même bilan, **aucune** nouvelle
  écriture ; job `CANCELLED` → `409`.
- **Simulation périmée** : classe archivée entre simulation et
  confirmation → `409 IMP_STALE_SIMULATION`, **rien** appliqué, job
  re-simulé.
- **Expiration** : `expires_at` dans le passé → `409
  IMP_SIMULATION_EXPIRED`.
- **Purge `@Scheduled`** : job `SIMULATED` expiré supprimé (CASCADE) ;
  job `APPLIED` : agrégats conservés, lignes filles supprimées après
  `applied-rows-ttl`.

### 14.4 Backend — **rollback précis** (Exigence 4)

- **Échec sur la dernière ligne** : fichier de N lignes toutes valides ;
  un test double fait échouer `provisionEnrollment` de la **Nᵉ** ligne
  (ou la Nᵉ ligne viole `uq_enrollment_active_per_year` via une
  inscription concurrente insérée juste avant). Après la confirmation en
  échec, **assertions** :
  - `count(user_account)` inchangé (**0** créé) ;
  - `count(student_profile)` inchangé ;
  - `count(enrollment)` inchangé ;
  - `count(user_role WHERE role=STUDENT)` inchangé ;
  - `count(account_invitation)` inchangé ;
  - `student_number_sequence.next_value` **inchangé** ;
  - **0** e-mail capturé par le mailer enregistreur ;
  - **0** ligne `audit_event` de catégorie `STUDENT_IMPORT` action
    `CONFIRMED` ; **0** ligne `INVITATION_ISSUED` ;
  - `student_import_job.status` **toujours `SIMULATED`**.
- **`StudentImportAuditListener` — transaction métier en rollback** : une
  confirmation qui échoue (échec injecté sur la dernière ligne) ne
  déclenche **pas** le listener — la transaction de confirmation ne
  committe pas, la phase `AFTER_COMMIT` n'est **jamais** atteinte, donc
  `@Transactional(REQUIRES_NEW)` ne démarre pas. Assertions : `count(audit_event)`
  de catégorie `STUDENT_IMPORT` **inchangé** (comptage avant / après) ;
  un spy sur `StudentImportAuditListener` n'enregistre **aucune**
  invocation ; `StudentImportChangeEvent` a bien été publié dans la
  transaction (spy sur `ApplicationEventPublisher`) mais reste **sans
  effet** puisque la transaction rollback.
- **Garde réflexive** : `DefaultStudentAccountProvisioner.prepareStudentAccountAndInvitation`
  et les méthodes d'application de `DefaultStudentEnrollmentProvisioner`
  portent `@Transactional` **sans** `propagation = REQUIRES_NEW`
  (vérifié par réflexion, comme
  `DefaultDemoAccountProvisionerTests` vérifie `@Profile`).
- **Événements** : spy sur `ApplicationEventPublisher` —
  `prepareStudentAccountAndInvitation` publie `AccountInvitationIssuedEvent`
  et **jamais** `AccountLifecycleEvent` ; les méthodes `enrollment` de
  provisioning ne publient **jamais** `EnrollmentChangeEvent`.
- **E-mail non envoyé sur rollback** : test transactionnel forçant le
  rollback + mailer enregistreur → **0** envoi ;
  `InvitationEmailListener` non invoqué.
- **Idempotence de la reconfirmation** : après un `APPLIED`, un 2ᵉ
  `confirm` ne réémet **aucun** e-mail et ne crée **aucune** ligne.

### 14.5 Backend — sécurité

- `401` anonyme sur les 6 endpoints.
- `403` pour `STUDENT` / `TEACHER` sur tous.
- `PEDAGOGICAL_MANAGER` : simule (lignes de son périmètre), ne voit pas
  le job d'un autre RP (`403 IMP_JOB_FORBIDDEN`), ne confirme pas le job
  d'un autre RP (`403 IMP_CONFIRM_FORBIDDEN`).
- DTO : aucun `id` SQL, `password_hash`, jeton.
- Audit : aucun e-mail / nom / n° étudiant / valeur de cellule / IP.

### 14.6 Backend — concurrence

- Deux `confirm` parallèles (pool 2 threads) sur le même job →
  exactement **un** ensemble de comptes créés, l'autre reçoit `200`
  idempotent ou `409`, jamais `500`, jamais de doublon.
- Deux simulations concurrentes aux e-mails recoupés → 0 écriture
  métier ; confirmation croisée sans doublon de compte.
- Deux confirmations concurrentes générant des numéros pour la même
  année → séquence sérialisée, numéros distincts, `next_value` cohérent.

### 14.7 Frontend (Vitest)

- `StudentImportApiService` : URL / méthode / `FormData` exacts ;
  `GET …/rows` params conditionnels ; `204` sur `cancel` ; aucune
  requête d'écriture sur les lectures.
- `StudentImportHome` : refus client (`.csv`, taille), bouton désactivé,
  mapping des anomalies globales, navigation sur `201`, liste des jobs,
  rien en storage.
- `StudentImportReview` : cartes de synthèse, table + filtres → page 0,
  gating `confirmable`, récapitulatif de confirmation + double-soumission
  bloquée, `409 IMP_STALE_SIMULATION` → rechargement + blocage,
  `alreadyApplied` → bilan sans faux succès, perte du contexte de rôle →
  fermeture + requêtes tardives ignorées, rien en storage.
- Specs mises à jour : `navigation`, `app-shell`, `dashboard`,
  `app.routes` (route `/students/import` gardée ; `TEACHER` →
  `/forbidden`).

### 14.8 Performance (documentée)

- TP-004 : temps de simulation **et** de confirmation d'un fichier de
  100 lignes relevé au CP10.

---

## 15. Découpage d'implémentation en checkpoints

| CP | Contenu | Preuve attendue |
|---|---|---|
| **CP0** | **Ce rapport** (R2) + traçabilité (`CURRENT-STATE`, `10-journal-ia`). Commit + PR. | Document présent, PR #23 à jour |
| **CP1** | `V11__create_student_import_tables.sql` (4 tables + `student_number_sequence`) + `@DataJpaTest`. | `./mvnw test` vert, schéma en version 11 |
| **CP2** | Squelette module `studentimport` : `package-info` (`@ApplicationModule`), entités JPA (colonnes typées), repositories, `StudentImportWeb`, `StudentImportException` + handler, codes `IMP_*`. | `ModularityTests` vert |
| **CP3** | Parsing + normalisation + validation + dé-duplication fichier (composants purs) + génération de numéro (pure, hors DB). | Tests §14.1 |
| **CP4** | Ports : `ClassGroupDirectory.resolveForImport` (+ impl) ; `identity.StudentAccountProvisioner` (+ impl `@Transactional` `REQUIRED`, réutilise `InvitationTokenService` / `AccountInvitationRepository`, publie `AccountInvitationIssuedEvent`, **pas** `AccountLifecycleEvent`) ; `enrollment.StudentEnrollmentProvisioner` (+ impl écriture directe, **pas** de `EnrollmentPersister`, **pas** de `EnrollmentChangeEvent`, allocation de numéro via `student_number_sequence`). | Tests unitaires + `@DataJpaTest` + gardes réflexives §14.4 ; `ModularityTests` vert |
| **CP5** | `StudentImportSimulationService` + `StudentImportController` (téléversement, get, rows, cancel) + config multipart. Persistance `student_import_*` uniquement (**T1**). | Intégration §14.3 (simulation), sécurité §14.5 |
| **CP6** | `StudentImportConfirmationService` : **une** transaction, verrou pessimiste, re-validation, idempotence (**T6**), application via ports **REQUIRED**, `StudentImportChangeEvent`. | Intégration §14.3 (confirmation, stale, expiration, TI-012), **rollback §14.4**, concurrence §14.6 |
| **CP7** | `StudentImportChangeEvent` + `audit.internal.StudentImportAuditListener` (**`@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`**) ; purge `@Scheduled` (jobs expirés + lignes filles des jobs `APPLIED`). | Tests audit : (a) confirmation committée → **une** ligne `audit_event` écrite dans la transaction dédiée du listener (§14.3) ; (b) confirmation en rollback → listener non invoqué, **aucune** ligne (§14.4) ; + tests de purge |
| **CP8** | Frontend : modèles, `StudentImportApiService`, `student-import-errors`, `StudentImportHome`, `StudentImportReview`, routes, nav. | Specs §14.7, `npm test` / `lint` / `build` verts |
| **CP9** | Documentation : `docs/CURRENT-STATE.md` (section détaillée), `docs/09-matrice-rncp.md` (EF-IMP-001/002, US-050/051), `docs/11-guide-demonstration.md` (scénario import). | Docs à jour |
| **CP10** | Vérification globale : `./mvnw clean test` + `npm test` / `lint` / `build` ré-exécutés (baselines relevées), démonstration locale (simulation → revue → confirmation → invitations Mailpit), mesure TP-004, mise à jour de la PR. | Sortie des commandes, statuts HTTP relevés |

Chaque checkpoint = un ou plusieurs commits sur
`feature/student-csv-import`, sans fusion, sans réécriture d'historique,
sans toucher V1–V10.

---

## 16. Divergences assumées vs documentation (récapitulatif)

| Sujet | Documentation | Décision de la tranche | Raison |
|---|---|---|---|
| Jeu de colonnes | docs/02 §10.4 : 14 colonnes | 6 obligatoires + 5 optionnelles ; 3 ignorées | Périmètre prototype |
| Tables d'import | docs/04 §16 : `import_job` générique | `student_import_*` dédiées au module | Frontières Spring Modulith |
| Statuts de job / `FAILED` | docs/04 §16.1, §29.1, §41 : `FAILED`, pipeline | Pas de `FAILED` ; rollback → job reste `SIMULATED` | Aucune écriture autonome de statut (invariant T2) ; observabilité via l'API + logs |
| Fichier d'origine | docs/04 §16.1 : `stored_file_id` | Non stocké | Pas d'infra de stockage ; minimisation |
| Données temporaires | docs/04 §16.3 : `raw_data_json` | Colonnes typées explicites, pas de JSON brut | Minimisation (Exigence 6) ; strict nécessaire à la revue et à l'application |
| Rétention temporaire | docs/07 : 30–90 j (proposition) | `P7D` / `P30D` par défaut, configurables — **décision de prototype** | Proposition non validée ; conservateur |
| Application partielle | IMP-STU-04 : « lignes ignorées » | `ERROR` bloque toute la confirmation ; « ignorées » = actions `NONE` | RG-020 / RG-021 |
| Identité d'un compte existant | docs/02 §10.7 : « propose une mise à jour » | `WARNING`, aucune réécriture nom/prénom/e-mail | Sécurité identité |
| Numéro étudiant | docs/02 §10.4 : optionnel | Généré `ESIC-{annéeDébut}-{séquence}` si absent | `student_profile.student_number` `NOT NULL` |
| Création de compte | Aucune API publique existante | Port `identity.StudentAccountProvisioner` (écriture directe, `REQUIRED`) | Nécessaire ; garantie transactionnelle stricte |
| Chemin d'écriture `enrollment` | Services existants via `EnrollmentPersister` (`REQUIRES_NEW`) + événements | Port dédié écrivant en direct, sans événement synchrone | Atomicité de la confirmation (invariants T2–T5) |
| Audit du module | Motif projet : `@EventListener` + `REQUIRES_NEW` (peut écrire avant le commit métier) | `@TransactionalEventListener(AFTER_COMMIT)` **+** `@Transactional(REQUIRES_NEW)` (transaction d'audit dédiée, démarrée seulement après le commit métier) | Aucune trace d'audit si la confirmation rollback ; persistance effective de l'audit après commit |
| Multipart | Non configuré | Ajout `spring.servlet.multipart.*` | Requis pour le téléversement |

---

## 17. Risques identifiés

| Risque | Impact | Atténuation |
|---|---|---|
| Volume : jusqu'à 500 comptes + profils + inscriptions + invitations dans une seule transaction | Verrou long, mémoire | Prototype ≤ 500 lignes ; mesure TP-004 ; batch possible en évolution |
| Contention sur `student_number_sequence(start_year)` | Sérialisation des confirmations d'une même année | Une ligne par année ; verrou court ; acceptable au volume prototype |
| Duplication du chemin d'écriture `enrollment` (port de provisioning vs services HTTP) | Maintenance | Documenté ; couvert par tests ; motif = garantie transactionnelle |
| `EnrollmentService.transfer` exige `effectiveDate >= start_date` | `TRANSFER_CLASS` refusé si import « rétro-daté » | `effectiveDate = LocalDate.now(clock)` ; `ERROR` si incohérent |
| Simulation obsolète non détectée | Données incohérentes | Re-validation **complète** + verrou pessimiste |
| `student_import_row` contient des données personnelles normalisées | Rétention | Colonnes strictement nécessaires ; `expires_at` + purge ; exclu de l'audit ; accès rôles MANAGE |
| Dette transactionnelle globale de l'audit | Trace trompeuse — **pour les autres modules** | Le chemin d'import ne la subit pas (T5) ; migration globale = dette séparée |

---

*Fin du Checkpoint 0 (révision R2). Aucune implémentation ne commence
avant validation de ce rapport.*
