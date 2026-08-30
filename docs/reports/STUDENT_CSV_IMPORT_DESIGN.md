# Conception — Import CSV contrôlé des apprenants (tranche V11)

| Élément | Valeur |
|---|---|
| Branche | `feature/student-csv-import` |
| Date de référence | 30 août 2026 |
| HEAD de départ | `35bd04b` (= `main`, PR #22 / V10 fusionnée, arbre propre) |
| Migration ajoutée (prévue) | `V11__create_student_import_tables.sql` (additive) |
| Migrations V1–V10 | **inchangées** |
| Statut du document | Décisions arrêtées — **Checkpoint 0**, implémentation à suivre sur la même branche |

Ce document est le livrable du **Checkpoint 0** de la tranche. Il fige les
décisions de conception ; les checkpoints suivants s'y conforment. Toute
divergence ultérieure est consignée ici. **Aucune ligne de code métier
n'est produite dans ce checkpoint.**

---

## 1. Exigences couvertes

| Réf. | Exigence | Couverture de la tranche |
|---|---|---|
| EF-IMP-001 | Simuler un import apprenant CSV | **Livré** — `POST /api/v1/student-imports` (multipart), analyse + normalisation + validation + détection de doublons, **aucune écriture métier** |
| EF-IMP-002 | Confirmer un import apprenant | **Livré** — `POST /api/v1/student-imports/{publicId}/confirm`, transaction unique, création/mise à jour des comptes et inscriptions + invitations |
| US-050 | Simuler l'import CSV des apprenants (RP) | **Livré** (backend + écran Angular) |
| US-051 | Confirmer une simulation valide (RP) | **Livré** (backend + écran Angular) |
| RG-020 | Un fichier invalide ne crée aucune donnée avant confirmation | **Livré** — la simulation ne persiste que des lignes techniques temporaires (`student_import_*`), jamais `user_account` / `student_profile` / `enrollment` / `account_invitation` |
| RG-021 | Une erreur bloquante empêche la confirmation | **Livré** — job non `confirmable` dès une anomalie `ERROR` ou `BLOCKING` |
| RG-022 | Un utilisateur existant est mis à jour, pas dupliqué | **Livré** — résolution par e-mail normalisé + numéro étudiant ; action calculée `UPDATE` / `TRANSFER` / `NONE` |
| RG-023 | Un changement de classe conserve l'historique | **Livré** — réutilise la sémantique `EnrollmentService.transfer` (clôture `TRANSFERRED`, nouvelle inscription liée, aucune suppression) |
| RG-024 | Une opération groupée exige une confirmation | **Livré** — simulation → revue → confirmation explicite |
| AC-004 | Simulation de 100 apprenants → bilan créations / mises à jour / déplacements / erreurs / avertissements | **Livré** — `StudentImportJobResponse.summary` |
| AC-005 | Un apprenant existant n'est pas recréé | **Livré** — unicité SQL `user_account.email` + pré-contrôle, action `ENROLL_EXISTING` / `UPDATE` |
| IMP-STU-01..04 (docs/02 §10.9) | Critères Gherkin d'import | **Couverts** par les tests d'intégration (§11) |
| TI-001..TI-012 (docs/08 §9) | Tests d'import apprenants | **Couverts** ; TI-009 (XLSX multifeuille) **hors périmètre** (US-052 `SHOULD`) ; TI-013..017 = import planning, hors périmètre |
| NFR-PERF-03 / TP-004 | Import de 100 apprenants analysé dans un délai acceptable | **Livré** — limite ≥ 100 lignes ; temps mesuré et documenté au CP10 |
| docs/07 §10 | Sécurité des imports (taille, nb lignes, pas de macro, périmètre, transaction, audit, purge) | **Livré** (§9) |

Hors périmètre (ni livré, ni simulé) : import **Excel `.xlsx`** et classeur
multifeuille (US-052) ; assistant IA de correspondance de colonnes
(EF-AI-001/002, EF-IMP-005) ; import **planning** ; création d'un compte
`PENDING_ACTIVATION` par une API générique publique ; génération
automatique d'un numéro étudiant `ESIC-{ANNÉE}-{SÉQUENCE}` ; mise à jour
de l'identité civile (nom/prénom/e-mail) d'un compte existant ;
rattachement d'un rythme d'alternance (`work_study_pattern`) ; import PDF.

---

## 2. Audit de l'existant

### 2.1 État Git et base de départ

- Branche `feature/student-csv-import`, HEAD `35bd04b` = `main`
  (PR #22 fusionnée : V10 « assiduité et reporting »). `git status`
  propre.
- Schéma en **version 10** (`V1`..`V10` appliquées). Prochaine migration
  disponible : **`V11`**.
- Baselines de test exactes (backend `./mvnw clean test`, frontend
  `npm test`) à relever au CP1 puis au CP10 ; `docs/CURRENT-STATE.md`
  documente ~548 backend / ~454 frontend au moment de la fusion V10.

### 2.2 Modules et ports réutilisables

| Module | Élément | Usage pour cette tranche |
|---|---|---|
| `identity` | `UserDirectory` (`findByPublicId` / `findByInternalId` / `findName`) | Détection d'un compte existant — **à étendre** : `findByEmail(String)` (normalisé) pour la détection de doublon en base |
| `identity` | `AccountInvitationService.issue(email, RoleCode, issuerSubject)` — `identity.internal`, `@Transactional`, publie `AccountInvitationIssuedEvent` (mail asynchrone `AFTER_COMMIT` via `notification`) | Émission des invitations à la confirmation — **exige un port public** (voir §4.1) |
| `identity` | `UserAccount(email, firstName, lastName, AccountStatus.PENDING_ACTIVATION)` + `user_role` | **Aucune API publique de création de compte** aujourd'hui (seul `DefaultDemoAccountProvisioner` sous `@Profile("demo")` en crée). **Nouveau port `identity` requis** (§4.1) |
| `identity` | `EmailNormalization.normalize` | Normalisation d'e-mail (réutilisée côté port) |
| `enrollment` | `StudentProfileService.create` / `EnrollmentService.enroll` / `.transfer` — `enrollment.internal` | Logique métier de profil / inscription / changement de classe — **exige un port public** (§4.2) |
| `enrollment` | `EnrollmentDirectory` (roster, inscription active à une date) | Réutilisé pour l'état courant d'un apprenant existant |
| `academic` | `ClassGroupDirectory` (`findByPublicId` / `findByInternalId`) | **À étendre** : résolution `(formation_code, class_code, academic_year)` → `ClassGroupRef` + raison de non-résolution typée |
| `academic` | `AcademicScopeDirectory` (`hasGlobalScope`, `isClassInScope`, `visibleClassGroupIds`) | Contrôle du périmètre `PEDAGOGICAL_MANAGER`, au niveau job (403) et au niveau ligne (`ERROR`) |
| `audit` | Écouteurs par événement applicatif public (`@EventListener` + `REQUIRES_NEW`) | Nouvel événement `StudentImportChangeEvent` + `audit.internal.StudentImportAuditListener` |
| `shared.web` | `ApiError`, `GlobalExceptionHandler` (`VALIDATION_ERROR`, `ACCESS_DENIED` 403 neutre) | Format d'erreur commun, codes `IMP_*` |
| `shared` | `BaseEntity` (`id` / `publicId` BINARY(16) / `@Version`), `ClockConfig` (bean `Clock` injectable) | Entités V11, horodatages déterministes en test |
| `attendance.internal` | `AttendanceCsvWriter` (UTF-8 + BOM, `;`, RFC 4180, **neutralisation d'injection de formule**) | Motif à répliquer pour l'export éventuel du **rapport d'erreurs** (pas dans le module `attendance` : copie contrôlée dans `studentimport`) |

### 2.3 Contraintes déjà fixées par la documentation

- **docs/01 §8.1 / docs/02 §10.4** — modèle CSV. Les deux listes
  divergent (voir §12.A). Champs obligatoires (les deux docs concordent) :
  nom, prénom, e-mail, code de formation, code de classe, année scolaire.
  Téléphone facultatif.
- **docs/01 §8.2 / docs/02 §10.8** — contrôles : colonnes obligatoires,
  valeurs obligatoires, syntaxe e-mail, existence formation, existence
  classe, appartenance classe↔formation, doublons dans le fichier,
  doublons avec la base, lignes en conflit, périmètre du RP.
- **docs/02 §10.6** — deux phases : **Simulation** (lecture,
  normalisation, validation, détection de doublons et d'existants,
  calcul des changements, affichage des erreurs, **aucune écriture
  métier définitive**) puis **Application** (confirmation, création, mise
  à jour, changement de classe, invitation, rapport d'import, audit).
- **docs/02 §10.7** — utilisateur existant : ne pas dupliquer, afficher
  le compte, proposer une mise à jour, afficher classe actuelle / cible,
  demander confirmation, clôturer l'ancienne inscription si nécessaire,
  créer la nouvelle, conserver l'historique.
- **docs/02 §10.8** — chaque erreur indique : fichier, feuille, n° de
  ligne, colonne, valeur reçue, motif, correction attendue, gravité.
  Niveaux : `INFO`, `WARNING`, `ERROR`, `BLOCKING`.
- **docs/02 §10.3 / §23.1 / docs/08 §9 (TI-008)** — au moins **100
  apprenants** par import, plusieurs imports successifs.
- **docs/02 §11** — après création d'un compte apprenant : statut
  `PENDING_ACTIVATION`, jeton, validité un mois, e-mail d'invitation,
  journalisation. Traçabilité `QUEUED` / `SENT_TO_PROVIDER` /
  `PROCESSING_FAILED`.
- **docs/04 §16** — tables génériques `import_job` / `import_sheet` /
  `import_row` / `import_row_issue`. Statuts `UPLOADED` / `ANALYZING` /
  `SIMULATED` / `WAITING_CONFIRMATION` / `CONFIRMED` / `APPLIED` /
  `FAILED` / `CANCELLED` / `EXPIRED`. `import_type` ∈ { `STUDENT_IMPORT`,
  `SCHEDULE_IMPORT` }. `ON DELETE CASCADE` accepté **avant** confirmation
  / à la purge ; les **données métier créées ne sont jamais supprimées
  avec l'import**.
- **docs/04 §29.1 / §41** — la confirmation utilise **une transaction** ;
  en cas d'échec l'import passe `FAILED` et **rien** n'est appliqué ;
  l'import est **verrouillé** pendant la confirmation ; `ImportApplied`
  publié.
- **docs/07 §9/§10** — pas d'exécution de macro/formule, pas de chemin
  fourni par le client, stockage hors répertoire public, nom généré,
  limite de taille, limite du nombre de lignes, rejet des fichiers
  chiffrés non prévus, transaction de confirmation, nettoyage des
  données temporaires.
- **docs/07 conservation** — « Imports temporaires : 30 à 90 jours » —
  proposition à valider (voir §12.C).
- **RG-006 / RG-012 / RG-023** — une adresse e-mail = un utilisateur
  (permanent du BTS au Master) ; au plus une inscription `ACTIVE` par
  apprenant et par année scolaire ; l'historique n'est jamais supprimé.

### 2.4 Frontend existant réutilisé

- Espace **`/students`** (`StudentList`, `StudentProfile`, guard
  `EnrollmentWeb.MANAGE_ROLES` = `ADMIN` / `SUPER_ADMIN` /
  `SCHOOL_ADMINISTRATION`) — **actuellement en lecture seule**.
- `RoleContextService.effectiveRoles()` (restreint l'ergonomie, jamais
  n'élargit le JWT), `roleGuard`, `normalizeHttpError` /
  `SAFE_FALLBACK_MESSAGE`, `NotificationService`, `MatPaginatorIntl`
  francisé, conventions `mat-table` + `mat-sort` (liste blanche) +
  `mat-paginator`, confirmations **en ligne**, JWT en mémoire seule
  (RG-085), aucun `localStorage` / `sessionStorage`.
- Aucune dépendance de parsing CSV côté frontend : le fichier est
  transmis **brut** au backend (`FormData`), jamais lu dans le
  navigateur.

---

## 3. Décisions proposées (synthèse)

| # | Sujet | Décision |
|---|---|---|
| D1 | Nouveau module | `com.esic.connect.studentimport` (Spring Modulith), propriétaire des tables `student_import_*` |
| D2 | Colonnes CSV | 6 obligatoires + 4 optionnelles (§7) ; `level_code` / `promotion_code` / `work_study_pattern` **ignorés** dans cette tranche |
| D3 | Transport | **Multipart** `file` (`.csv` uniquement) ; le fichier **n'est jamais stocké** (nom assaini + SHA-256 + taille seulement) |
| D4 | Séparateur / encodage | UTF-8 obligatoire (BOM toléré et retiré) ; séparateur `,` **ou** `;` **auto-détecté** sur la ligne d'en-tête ; RFC 4180 |
| D5 | Limites | ≤ **2 MiB** et ≤ **500 lignes de données** (config ; ≥ 100 exigé) ; dépassement → anomalie `BLOCKING` |
| D6 | Simulation | Persiste `student_import_job` (`SIMULATED`) + `student_import_row` + `student_import_row_issue` + `student_import_job_issue` **uniquement** |
| D7 | Confirmabilité | `confirmable = (blocking_issue_count == 0 && error_rows == 0)` |
| D8 | Confirmation | **Une** `@Transactional` ; verrou pessimiste `SELECT … FOR UPDATE` sur le job ; **re-validation complète** contre la base vivante ; rollback total → `FAILED` |
| D9 | Double confirmation | Job `APPLIED` → réponse **idempotente** `200` (bilan mémorisé) ; `CONFIRMED` / `FAILED` / `EXPIRED` / `CANCELLED` → `409` |
| D10 | Lignes devenues invalides entre simulation et confirmation | Confirmation **abandonnée** (`409 IMP_STALE_SIMULATION`), job re-simulé en place avec anomalies rafraîchies, **rien appliqué** — jamais d'application partielle |
| D11 | Rétention de la simulation | `expires_at = created_at + P7D` (config) ; purge `@Scheduled` des jobs non `APPLIED` expirés ; confirmation refusée après expiration |
| D12 | Rétention du fichier | **Non conservé** ; `stored_file_id` de docs/04 §16.1 laissé `NULL`/omis |
| D13 | Rôles | `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION` (global) ; `PEDAGOGICAL_MANAGER` (périmètre) ; `TEACHER` / `STUDENT` → `403` |
| D14 | Périmètre RP | Filtre job hors périmètre → `403` ; ligne hors périmètre → anomalie `ERROR` `IMP_CLASS_OUT_OF_SCOPE` |
| D15 | Confirmation par un tiers | Autorisée pour `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION` ; un `PEDAGOGICAL_MANAGER` ne confirme que **son** job |
| D16 | Compte existant, identité différente | Anomalie `WARNING`, **aucune** réécriture de nom / prénom / e-mail |
| D17 | Numéro étudiant | Optionnel dans le CSV ; s'il est absent → profil créé **sans** numéro n'est **pas** possible (le modèle actuel l'exige `NOT NULL`) → **anomalie `ERROR`** si absent et compte sans profil (voir §12.G) |
| D18 | Audit | `StudentImportChangeEvent` → `audit` ; jamais d'e-mail, nom, n° étudiant, contenu de fichier, IP |
| D19 | Génération de tables | V11 crée `student_import_*` **propres au module** (divergence assumée vs table générique docs/04 §16 — §12.E) |
| D20 | Frontend | Routes `/students/import`, `/students/import/:publicId` + liste des jobs récents ; confirmation en ligne avec récapitulatif chiffré |

---

## 4. Frontières Spring Modulith et ports

Le module `studentimport` **orchestre** : il ne possède ni `user_account`,
ni `student_profile`, ni `enrollment`, ni `account_invitation`. Toute
écriture métier passe par un **port public** du module propriétaire.
`ModularityTests` doit rester vert : `studentimport` dépend uniquement de
`identity` (ports), `enrollment` (port), `academic` (ports), `shared` ;
il publie vers `audit`. Aucun import de `*.internal` d'un autre module.

### 4.1 `identity` — nouveau port `StudentAccountProvisioner`

```java
package com.esic.connect.identity;

public interface StudentAccountProvisioner {

    /** Résout un compte existant par e-mail normalisé (détection de
     *  doublon en base pendant la simulation). */
    Optional<ExistingAccount> findByEmail(String rawEmail);

    /** Crée un compte PENDING_ACTIVATION + rôle actif STUDENT, dans la
     *  transaction courante de l'appelant, puis émet une invitation
     *  d'activation (jeton + e-mail asynchrone AFTER_COMMIT). Idempotent
     *  au sens : si un compte PENDING_ACTIVATION existe déjà pour cet
     *  e-mail, il est réutilisé et une invitation est (ré)émise ; si un
     *  compte ACTIVE / SUSPENDED / LOCKED / ARCHIVED existe →
     *  StudentAccountException (retraduite IMP_*). */
    ProvisionResult provisionPendingStudentAndInvite(NewStudentAccount command, String issuerSubject);

    record ExistingAccount(UUID publicId, long internalId, AccountStatusView status,
                           String firstName, String lastName, boolean hasActiveStudentRole) {}
    enum AccountStatusView { PENDING_ACTIVATION, ACTIVE, SUSPENDED, LOCKED, ARCHIVED }
    record NewStudentAccount(String rawEmail, String firstName, String lastName, String phone) {}
    record ProvisionResult(UUID userPublicId, long userInternalId, boolean created, boolean invited) {}
}
```

Implémentation `identity.internal.DefaultStudentAccountProvisioner` :
réutilise `UserAccountRepository`, `UserRoleRepository`,
`RoleRepository`, `AccountInvitationService.issue`. **Aucune** logique
d'invitation dupliquée. Publie déjà `AccountLifecycleEvent`
(`INVITATION_ISSUED`) et `AccountInvitationIssuedEvent` par
`AccountInvitationService`.

> Un compte existant `ACTIVE` porté par un e-mail réutilisé (apprenant du
> BTS au Master, RG-006) n'est **pas** une erreur : la ligne devient une
> action d'inscription / mise à jour, pas de création. Le port renvoie
> alors seulement `findByEmail` ; `provision…` n'est appelé que pour un
> compte **absent** ou **`PENDING_ACTIVATION`**.

### 4.2 `enrollment` — nouveau port `StudentEnrollmentProvisioner`

```java
package com.esic.connect.enrollment;

public interface StudentEnrollmentProvisioner {

    Optional<StudentProfileView> findProfileByUser(UUID userPublicId);
    Optional<StudentProfileView> findProfileByStudentNumber(String studentNumber);

    /** Crée le student_profile (compte STUDENT actif requis) —
     *  dans la transaction de l'appelant. */
    StudentProfileView createProfile(NewStudentProfile command, String callerSubject);

    /** État de l'inscription courante de l'apprenant pour une classe /
     *  année : NONE / SAME_CLASS / OTHER_CLASS_SAME_YEAR / OTHER_YEAR. */
    EnrollmentSituation describeSituation(UUID studentProfilePublicId, UUID classGroupPublicId);

    /** Inscrit (nouvelle inscription ACTIVE) — transaction de l'appelant. */
    EnrollmentView enroll(UUID studentProfilePublicId, UUID classGroupPublicId, LocalDate startDate, String callerSubject);

    /** Change de classe en conservant l'historique (RG-023). */
    EnrollmentView transfer(UUID currentEnrollmentPublicId, UUID targetClassGroupPublicId,
                            LocalDate effectiveDate, String reason, String callerSubject);
}
```

Implémentation `enrollment.internal.DefaultStudentEnrollmentProvisioner` :
délègue à `StudentProfileService` / `EnrollmentService` (méthodes rendues
accessibles au sein du module, pas au monde). Les collisions d'unicité
(`uq_student_profile_*`, `uq_enrollment_active_per_year`) restent
retraduites côté `enrollment` (déjà en place) et remontent en
`DataIntegrityViolationException` / exception métier que `studentimport`
retraduit en `IMP_*` sans jamais produire de `500`.

### 4.3 `academic` — extension de `ClassGroupDirectory`

```java
/** Résout une classe par ses codes fonctionnels pour l'import CSV.
 *  Vérifie l'appartenance classe ↔ formation ↔ année scolaire
 *  (docs/01 §8.2). Ne renvoie jamais l'entité ClassGroup. */
ClassGroupResolution resolveForImport(String programCode, String classCode, String academicYearCode);

sealed interface ClassGroupResolution {
    record Found(ClassGroupRef ref) implements ClassGroupResolution {}
    enum Miss implements ClassGroupResolution {
        PROGRAM_UNKNOWN, CLASS_UNKNOWN, CLASS_NOT_IN_PROGRAM,
        ACADEMIC_YEAR_UNKNOWN, CLASS_NOT_IN_YEAR, CHAIN_ARCHIVED
    }
}
```

`academic_year` du CSV est rapproché de `academic_year.code` (§12.B).
Le contrôle de périmètre reste sur `AcademicScopeDirectory` (le port ne
décide pas de sécurité).

### 4.4 `identity` — extension de `UserDirectory`

`Optional<UserRef> findByEmail(String rawEmail)` (e-mail normalisé) — pour
la détection de doublon en base pendant la **simulation** sans passer par
le port d'écriture. (Alternative : exposer uniquement
`StudentAccountProvisioner.findByEmail` — retenue si l'on veut limiter la
surface de `UserDirectory`. Décision : **ajouter à `StudentAccountProvisioner`
seulement**, `UserDirectory` inchangé.)

---

## 5. Format CSV

### 5.1 En-tête

- Première ligne non vide = en-tête. Noms de colonnes **insensibles à la
  casse**, **rognés**, **ordre libre**, séparateur `,` ou `;`
  auto-détecté (celui qui produit le plus de colonnes reconnues).
- BOM UTF-8 en tête de fichier toléré et retiré.
- Encodage : **UTF-8 strict**. Séquence d'octets invalide → anomalie
  `BLOCKING` `IMP_ENCODING_INVALID`.
- Fins de ligne `CRLF` ou `LF`. Guillemets RFC 4180 (`"…"`, `""`
  échappé). Cellule multi-lignes entre guillemets acceptée.

### 5.2 Colonnes

| Colonne | Obligatoire | Normalisation | Contrôles |
|---|---|---|---|
| `last_name` | **oui** | `trim`, espaces multiples réduits | non vide |
| `first_name` | **oui** | `trim` | non vide |
| `email` | **oui** | `trim`, minuscule (`EmailNormalization`) | syntaxe RFC (jakarta `@Email` + garde applicative), doublon fichier/base |
| `formation_code` | **oui** | `trim`, majuscule | formation existante (§4.3) |
| `class_code` | **oui** | `trim`, majuscule | classe existante, appartenant à la formation |
| `academic_year` | **oui** | `trim` | rapproché de `academic_year.code` ; classe rattachée à cette année |
| `phone` | non | `trim`, espaces/points retirés | format libre toléré ; `WARNING` si non conforme `^[+0-9 ().-]{6,20}$` |
| `student_number` | non (voir §12.G) | `trim`, majuscule | unicité en base ; `ERROR` si déjà attribué à un **autre** compte |
| `birth_date` | non | `trim` | `ISO-8601` (`yyyy-MM-dd`) ou `dd/MM/yyyy` ; sinon `WARNING`, valeur ignorée |
| `work_study` | non | `trim`, minuscule | `true`/`false`/`oui`/`non`/`1`/`0`/vide ; sinon `WARNING` |
| `company_name` | non | `trim` | libre ; requis si `work_study=true` → sinon `WARNING` |

Colonnes **inconnues** → anomalie `WARNING` `IMP_UNKNOWN_COLUMN` (non
bloquante, colonne ignorée). Colonnes obligatoires **absentes** →
anomalie `BLOCKING` `IMP_MISSING_COLUMN` (job non `confirmable`).

Colonnes de docs/02 §10.4 **non prises en charge** dans cette tranche :
`level_code`, `promotion_code`, `work_study_pattern` → `WARNING`
`IMP_COLUMN_IGNORED` si présentes (documenté, §12.A).

### 5.3 Lignes

- Ligne entièrement vide → ignorée silencieusement (non comptée).
- Nombre de colonnes ≠ en-tête → anomalie `ERROR` `IMP_COLUMN_COUNT` sur
  la ligne.
- `> 500` lignes de données → anomalie `BLOCKING` `IMP_TOO_MANY_ROWS`.
- Aucune ligne de données → `BLOCKING` `IMP_NO_DATA_ROWS`.

### 5.4 Aucune exécution

Le CSV est **analysé comme des données**, jamais évalué. Le
`raw_data_json` stocke les valeurs brutes (échappées JSON). Tout
ré-export du rapport d'anomalies (option frontend) neutralise l'injection
de formule (`'` en préfixe pour `= + - @ \t \r`), motif repris de
`AttendanceCsvWriter`.

---

## 6. Cycle simulation → confirmation

```text
Téléversement (multipart .csv)
        │  POST /api/v1/student-imports
        ▼
[Contrôles globaux]  type/extension, taille, magie ZIP/PK, UTF-8, en-tête,
        │            colonnes obligatoires, nb de lignes, périmètre du filtre job
        ▼
student_import_job = SIMULATED   ──►  student_import_job_issue (global/BLOCKING)
        │
        ▼
[Par ligne]  normalisation → validation champ → résolution classe/année →
        │    détection doublon fichier → détection existant base →
        │    contrôle périmètre → calcul planned_action
        ▼
student_import_row (VALID|WARNING|ERROR) + student_import_row_issue
        │
        ▼
summary  { total, valid, warning, error, blocking,
           plannedCreate, plannedUpdate, plannedTransfer, plannedNoop }
confirmable = (blocking == 0 && error == 0)
audit STUDENT_IMPORT_SIMULATED
        │
        │  (revue humaine dans l'écran /students/import/:publicId)
        │
        ▼
POST /api/v1/student-imports/{publicId}/confirm
        │
        ▼
[TX unique]  SELECT … FOR UPDATE (job)  →  garde de statut / expiration
        │    RE-VALIDATION complète de chaque ligne contre la base vivante
        │      └─ nouvelle anomalie ERROR/BLOCKING → ROLLBACK, job SIMULATED (rafraîchi), 409 IMP_STALE_SIMULATION
        │    pour chaque ligne applicable :
        │      CREATE_ACCOUNT_AND_ENROLL → provisionPendingStudentAndInvite + createProfile + enroll
        │      ENROLL_EXISTING           → createProfile? + enroll
        │      UPDATE_PROFILE            → maj profil (téléphone / alternance) ; pas d'identité
        │      TRANSFER_CLASS            → transfer (historique conservé)
        │      NONE                      → aucun effet (ligne « ignorée » du bilan)
        │    job = APPLIED, applied_* renseignés, confirmed_by / confirmed_at
        │    audit STUDENT_IMPORT_CONFIRMED
        ▼
COMMIT  →  e-mails d'invitation envoyés (AFTER_COMMIT, notification)
        │
        └─ échec inattendu → ROLLBACK, job = FAILED, audit STUDENT_IMPORT_FAILED, 409 / 500 contrôlé
```

### 6.1 Statuts `student_import_job`

`SIMULATED` → `CONFIRMED` (transitoire, pendant la TX) → `APPLIED`
| `FAILED` ; `SIMULATED` → `CANCELLED` (annulation explicite) ;
`SIMULATED` → `EXPIRED` (purge). Pas de retour de `APPLIED`.
`UPLOADED` / `ANALYZING` / `WAITING_CONFIRMATION` de docs/04 §16.1 :
l'analyse est **synchrone** dans cette tranche → on passe directement à
`SIMULATED` (documenté §12.F).

### 6.2 `planned_action` d'une ligne

| Situation | `planned_action` | Effet à la confirmation |
|---|---|---|
| E-mail absent de la base, aucun conflit | `CREATE_ACCOUNT_AND_ENROLL` | compte `PENDING_ACTIVATION` + rôle `STUDENT` + invitation + profil + inscription |
| E-mail = compte `PENDING_ACTIVATION` sans profil | `CREATE_ACCOUNT_AND_ENROLL` (réémet l'invitation) | profil + inscription (+ invitation réémise) |
| E-mail = compte existant (tout statut ≥ `ACTIVE`), pas de profil | `ENROLL_EXISTING` | profil + inscription |
| Compte + profil, aucune inscription active l'année cible | `ENROLL_EXISTING` | inscription |
| Compte + profil, inscription active **dans la classe cible** | `NONE` | aucun (ligne « ignorée » du bilan) |
| Compte + profil, inscription active **autre classe, même année** | `TRANSFER_CLASS` | `transfer` (clôture `TRANSFERRED`, nouvelle inscription liée) |
| Compte + profil, inscription active **autre année** | `ENROLL_EXISTING` | nouvelle inscription (année distincte) |
| Compte `ARCHIVED` / `LOCKED` | — | anomalie `ERROR` `IMP_ACCOUNT_NOT_USABLE` |
| Téléphone / alternance divergents sur un profil existant | `UPDATE_PROFILE` (combiné aux cas ci-dessus) | maj `phone` / `work_study` / `company_name` **uniquement** |

Le bilan `AC-004` : `created` = `CREATE_ACCOUNT_AND_ENROLL` appliqués ;
`updated` = `UPDATE_PROFILE` + `ENROLL_EXISTING` ; `moved` =
`TRANSFER_CLASS` ; `ignored` = `NONE` ; `errors` / `warnings` = comptes
d'anomalies.

---

## 7. Modèle de données V11 (proposé)

Fichier unique `V11__create_student_import_tables.sql`, **additif**,
MySQL 8, `ENGINE=InnoDB`, `utf8mb4` / `utf8mb4_0900_ai_ci` (aligné
V1–V10). **Aucune donnée métier insérée.** Tables **propres au module
`studentimport`** (divergence assumée vs table générique docs/04 §16,
§12.E).

### 7.1 `student_import_job`

| Colonne | Type | Notes |
|---|---|---|
| `id` | BIGINT UNSIGNED AI PK | |
| `public_id` | BINARY(16) | `UNIQUE` |
| `status` | VARCHAR(24) NOT NULL | `CHECK IN ('SIMULATED','CONFIRMED','APPLIED','FAILED','CANCELLED','EXPIRED')` |
| `original_file_name` | VARCHAR(255) NOT NULL | **assaini** (basename, `[^A-Za-z0-9._ -]`→`_`, pas de point en tête) |
| `file_sha256` | CHAR(64) NOT NULL | empreinte hex du contenu reçu |
| `file_size_bytes` | INT UNSIGNED NOT NULL | `CHECK > 0` |
| `csv_separator` | CHAR(1) NOT NULL | `,` ou `;` |
| `requested_by_id` | BIGINT UNSIGNED NOT NULL | FK `user_account(id)` `ON DELETE RESTRICT` |
| `scope_program_code` | VARCHAR(80) NULL | filtre job éventuel |
| `scope_class_code` | VARCHAR(80) NULL | |
| `total_rows` | INT UNSIGNED NOT NULL DEFAULT 0 | |
| `valid_rows` / `warning_rows` / `error_rows` | INT UNSIGNED NOT NULL DEFAULT 0 | |
| `blocking_issue_count` | INT UNSIGNED NOT NULL DEFAULT 0 | anomalies globales |
| `planned_create_rows` / `planned_update_rows` / `planned_transfer_rows` / `planned_noop_rows` | INT UNSIGNED NOT NULL DEFAULT 0 | |
| `applied_created` / `applied_updated` / `applied_transferred` / `applied_invited` / `applied_ignored` | INT UNSIGNED NULL | renseignés à `APPLIED` |
| `simulated_at` | TIMESTAMP(6) NOT NULL | |
| `confirmed_at` | TIMESTAMP(6) NULL | |
| `confirmed_by_id` | BIGINT UNSIGNED NULL | FK `user_account(id)` `ON DELETE RESTRICT` |
| `failed_reason` | VARCHAR(120) NULL | code non sensible |
| `expires_at` | TIMESTAMP(6) NOT NULL | `simulated_at + P7D` (config) |
| `created_at` / `updated_at` | TIMESTAMP(6) NOT NULL | |
| `version` | BIGINT UNSIGNED NOT NULL DEFAULT 0 | `@Version` |

Index : `(status, expires_at)` (purge), `(requested_by_id, created_at)`.

### 7.2 `student_import_job_issue` (anomalies globales)

| Colonne | Type | Notes |
|---|---|---|
| `id` | BIGINT UNSIGNED AI PK | |
| `public_id` | BINARY(16) `UNIQUE` | |
| `student_import_job_id` | BIGINT UNSIGNED NOT NULL | FK `ON DELETE CASCADE` |
| `severity` | VARCHAR(12) NOT NULL | `CHECK IN ('INFO','WARNING','ERROR','BLOCKING')` |
| `error_code` | VARCHAR(80) NOT NULL | `IMP_MISSING_COLUMN`, `IMP_TOO_MANY_ROWS`, … |
| `message` | VARCHAR(500) NOT NULL | message non sensible |
| `column_name` | VARCHAR(64) NULL | |
| `created_at` | TIMESTAMP(6) NOT NULL | |

### 7.3 `student_import_row`

| Colonne | Type | Notes |
|---|---|---|
| `id` | BIGINT UNSIGNED AI PK | |
| `public_id` | BINARY(16) `UNIQUE` | |
| `student_import_job_id` | BIGINT UNSIGNED NOT NULL | FK `ON DELETE CASCADE` |
| `row_number` | INT UNSIGNED NOT NULL | n° de ligne dans le fichier (en-tête = 1) |
| `sheet_name` | VARCHAR(191) NULL | réservé XLSX futur (toujours `NULL` ici) |
| `raw_data_json` | JSON NOT NULL | valeurs brutes par colonne connue |
| `normalized_data_json` | JSON NULL | valeurs normalisées |
| `row_status` | VARCHAR(12) NOT NULL | `CHECK IN ('VALID','WARNING','ERROR')` |
| `planned_action` | VARCHAR(28) NOT NULL | `CHECK IN ('CREATE_ACCOUNT_AND_ENROLL','ENROLL_EXISTING','UPDATE_PROFILE','TRANSFER_CLASS','NONE','SKIPPED_ERROR')` |
| `resolved_class_public_id` | BINARY(16) NULL | trace de résolution |
| `resolved_user_public_id` | BINARY(16) NULL | compte existant rapproché |
| `applied_outcome` | VARCHAR(28) NULL | renseigné à `APPLIED` (`CREATED`, `ENROLLED`, `UPDATED`, `TRANSFERRED`, `NOOP`) |
| `created_at` | TIMESTAMP(6) NOT NULL | |

Contraintes : `UNIQUE (student_import_job_id, row_number)`. Index
`(student_import_job_id, row_status)`.

### 7.4 `student_import_row_issue`

| Colonne | Type | Notes |
|---|---|---|
| `id` | BIGINT UNSIGNED AI PK | |
| `public_id` | BINARY(16) `UNIQUE` | |
| `student_import_row_id` | BIGINT UNSIGNED NOT NULL | FK `ON DELETE CASCADE` |
| `severity` | VARCHAR(12) NOT NULL | `CHECK IN ('INFO','WARNING','ERROR','BLOCKING')` |
| `column_name` | VARCHAR(64) NULL | |
| `received_value` | VARCHAR(500) NULL | valeur reçue (tronquée) — **pas** d'e-mail complet en clair dans l'audit, mais visible ici pour la revue (accès RP autorisé) |
| `error_code` | VARCHAR(80) NOT NULL | `IMP_*` |
| `message` | VARCHAR(500) NOT NULL | |
| `suggested_value` | VARCHAR(500) NULL | correction attendue (docs/02 §10.8) |
| `created_at` | TIMESTAMP(6) NOT NULL | |

`ON DELETE CASCADE` sur toute la chaîne
`student_import_job → job_issue / row → row_issue`, **acceptable avant
confirmation et à la purge** (docs/04 §16.4). Les données métier créées
ne dépendent d'aucune de ces FK.

---

## 8. Endpoints proposés

Préfixe `/api/v1`. Contrôleur `StudentImportController`
(`studentimport.internal`). `@PreAuthorize` via constante
`StudentImportWeb.MANAGE_ROLES`
(`ADMIN`,`SUPER_ADMIN`,`SCHOOL_ADMINISTRATION`,`PEDAGOGICAL_MANAGER`).
Décision fine de périmètre **dans le service** (contexte Spring Security,
jamais un paramètre client). DTO **sans** identifiant SQL, sans jeton,
sans `password_hash`.

| Méthode / URL | Corps | Rôles | Réponse | Erreurs notables |
|---|---|---|---|---|
| `POST /student-imports` | `multipart/form-data` : `file` (`.csv`), `programCode?`, `classCode?` | MANAGE | `201` `StudentImportJobResponse` | `415 IMP_UNSUPPORTED_MEDIA_TYPE`, `413`/`400 IMP_FILE_TOO_LARGE`, `400 IMP_MISSING_COLUMN` (+ détail), `403` (filtre job hors périmètre) |
| `GET /student-imports` | — (query : `status?`, `page`, `size`≤50, `sort`∈`{createdAt}`) | MANAGE | `200` `PageResponse<StudentImportJobResponse>` (jobs de l'appelant ; global pour `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION`) | — |
| `GET /student-imports/{publicId}` | — | MANAGE | `200` `StudentImportJobResponse` (job + `summary` + `issues[]` globales + `confirmable`) | `404 IMP_JOB_NOT_FOUND`, `403 IMP_JOB_FORBIDDEN` |
| `GET /student-imports/{publicId}/rows` | query : `rowStatus?`, `severity?`, `action?`, `page`, `size`≤100, `sort`∈`{rowNumber}` | MANAGE | `200` `PageResponse<StudentImportRowResponse>` (n°, valeurs normalisées non sensibles, `plannedAction`, `rowStatus`, `issues[]`) | `404`, `403` |
| `POST /student-imports/{publicId}/confirm` | `{}` | MANAGE (+ D15) | `200` `StudentImportResultResponse` | `409 IMP_NOT_CONFIRMABLE`, `409 IMP_ALREADY_CONFIRMED` (ou `200` idempotent si `APPLIED`), `409 IMP_STALE_SIMULATION` (+ anomalies rafraîchies), `409 IMP_SIMULATION_EXPIRED`, `403 IMP_CONFIRM_FORBIDDEN` |
| `POST /student-imports/{publicId}/cancel` | `{}` | MANAGE (+ D15) | `204` | `409 IMP_JOB_NOT_CANCELLABLE`, `404`, `403` |

Pas de `PATCH`, pas de route de téléchargement du fichier d'origine (non
stocké). Pas de correction de ligne côté serveur dans cette tranche
(l'utilisateur corrige le fichier et re-simule — cohérent avec « aucune
transformation incertaine » ; l'édition en ligne relève d'un lot
ultérieur avec l'assistant IA).

`application.yml` : ajout
`spring.servlet.multipart.max-file-size: 2MB`,
`max-request-size: 3MB` ; bloc `app.import.student` :
`max-rows` (500), `max-file-bytes` (2 MiB), `simulation-ttl` (`P7D`).
Ces valeurs doivent rester cohérentes (démarrage documenté, pas de
`fail-fast` imposé).

---

## 9. Matrice des rôles

Décision fine calculée **côté serveur**. Le frontend ne fait que
restreindre l'ergonomie (`RoleContextService.effectiveRoles()`).

| Capacité | SUPER_ADMIN / ADMIN | SCHOOL_ADMINISTRATION | PEDAGOGICAL_MANAGER | TEACHER | STUDENT | Anonyme |
|---|---|---|---|---|---|---|
| Téléverser + simuler | ✅ global | ✅ global | ✅ (lignes de son périmètre ; ligne hors périmètre → `ERROR`) | ❌ 403 | ❌ 403 | ❌ 401 |
| Lister / consulter un job + ses lignes | ✅ tous | ✅ tous | ✅ **ses** jobs uniquement | ❌ | ❌ | ❌ |
| Confirmer | ✅ tout job `confirmable` | ✅ tout job `confirmable` | ✅ **son** job uniquement | ❌ | ❌ | ❌ |
| Annuler | ✅ | ✅ | ✅ **son** job | ❌ | ❌ | ❌ |
| Filtre job `programCode`/`classCode` hors périmètre | n/a (global) | n/a | **403** au téléversement | ❌ | ❌ | ❌ |

- Ressource inconnue → `404` sans divulgation ; hors périmètre → `403`
  (même posture que `CourseSessionAccessGuard` / `AcademicScopeGuard`).
- Les endpoints d'écriture métier appelés à la confirmation
  (`identity` / `enrollment`) restent gouvernés par leurs propres
  `@PreAuthorize` — l'appelant `studentimport` porte déjà une autorité
  MANAGE ; le contrôle de périmètre par ligne est fait **avant** l'appel.

---

## 10. Sécurité

| Sujet | Mesure |
|---|---|
| Transport fichier | `MultipartFile` uniquement — **jamais** un chemin fourni par le client. Aucun fichier écrit sur disque. |
| Type de fichier | extension `.csv` **et** `Content-Type` ∈ liste tolérante (`text/csv`, `text/plain`, `application/vnd.ms-excel`, `application/octet-stream`) **et** contenu texte : rejet si octet nul, si magie `PK\x03\x04` (ZIP/XLSX), si en-tête de chiffrement — `415 IMP_UNSUPPORTED_MEDIA_TYPE`. |
| Taille | `≤ 2 MiB` au niveau multipart **et** re-vérifiée ; `> ` → `413`/`400 IMP_FILE_TOO_LARGE`. |
| Nombre de lignes | `≤ 500` ; `> ` → `BLOCKING IMP_TOO_MANY_ROWS`. |
| Aucune exécution | CSV analysé comme données ; jamais de formule/macro évaluée (docs/07 §10) ; ré-export du rapport d'anomalies avec neutralisation d'injection de formule. |
| Nom de fichier | `original_file_name` **assaini** (basename, caractères non `[A-Za-z0-9._ -]` → `_`, longueur ≤ 255, pas de point initial) ; jamais utilisé comme chemin. |
| Empreinte | `file_sha256` (hex) conservé pour la traçabilité ; le contenu du fichier n'est **pas** conservé. |
| Périmètre | `AcademicScopeDirectory` — job (403) et ligne (`ERROR`). |
| Transaction | confirmation `@Transactional` unique + verrou pessimiste ; échec → rollback total, `FAILED` (docs/04 §29.1). |
| Idempotence | garde de statut + verrou + réponse idempotente (`APPLIED`). |
| Audit | `STUDENT_IMPORT_SIMULATED` / `_CONFIRMED` / `_FAILED` / `_EXPIRED` / `_CANCELLED` — détail `job=<uuid>;rows=NN;created=NN;…`. **Jamais** d'e-mail, nom, n° étudiant, contenu de fichier, valeur de cellule, IP. Les invitations émises sont auditées par `identity` (`ACCOUNT_INVITATION_ISSUED`) sans duplication de PII ici. |
| Données personnelles | DTO sans `id` SQL / `password_hash` / jeton ; `student_import_row_issue.received_value` (visible en revue par un rôle autorisé) est **tronqué** et **exclu** de l'audit. |
| Rétention | `expires_at` + purge `@Scheduled` (jobs non `APPLIED`) ; docs/04 §16.4 / docs/07 §10 « nettoyage des données temporaires ». Le résumé d'un job `APPLIED` est conservé ; ses `student_import_row` peuvent être purgés séparément (checkpoint ultérieur — pas dans cette tranche). |
| Front | fichier transmis brut (`FormData`), jamais lu/parse côté navigateur ; JWT en mémoire seule ; aucun jeton en URL ; aucune donnée en `localStorage`/`sessionStorage`. |

---

## 11. Stratégie transactionnelle et concurrente

| Scénario | Garantie |
|---|---|
| Simulation | Non transactionnelle au sens métier : n'écrit **que** `student_import_*`. Aucune ligne `user_account` / `student_profile` / `enrollment` / `account_invitation`. Vérifié en test par comptage avant/après. |
| Confirmation | **Une** `@Transactional`. Début : `SELECT … FOR UPDATE` sur `student_import_job` (sérialise les confirmations concurrentes). Puis re-validation complète, puis application, puis `APPLIED`. Toute exception inattendue → rollback JPA → job `FAILED` (écrit dans une transaction séparée `REQUIRES_NEW` du gestionnaire, pour survivre au rollback) → `409`/`500 IMP_APPLY_FAILED` contrôlé. |
| Double confirmation (séquentielle) | 2ᵉ appel voit `APPLIED` → **`200` idempotent** avec le bilan mémorisé (`alreadyApplied=true`) ; voit `FAILED`/`CANCELLED`/`EXPIRED` → `409`. (TI-012) |
| Double confirmation (concurrente) | Le verrou pessimiste sérialise ; le perdant relit `APPLIED` après acquisition → réponse idempotente. Exactement **un** ensemble de comptes créés. |
| Collision d'unicité pendant l'application | `user_account.email` (unicité SQL) : si un autre import a créé le compte entre-temps → `DataIntegrityViolationException` retraduite → **abandon** de la confirmation (`409 IMP_STALE_SIMULATION`), rien d'appliqué (rollback), job re-simulé. `uq_enrollment_active_per_year` : idem. Jamais de `500`. |
| Lignes devenues invalides entre simulation et confirmation | Détectées par la re-validation (classe archivée, e-mail désormais pris par un **autre** apprenant, n° étudiant désormais utilisé, périmètre RP modifié) → `409 IMP_STALE_SIMULATION` + anomalies rafraîchies persistées ; **aucune application partielle** (D10). |
| Deux simulations avec e-mails qui se recoupent | Les deux simulent sans écriture métier. À la première confirmation, les comptes sont créés ; à la seconde confirmation, la re-validation reclasse ces lignes en `ENROLL_EXISTING` / `NONE` / `TRANSFER_CLASS` — **jamais** de doublon de compte (autorité = unicité SQL de l'e-mail). Si le reclassement introduit un `ERROR` → `409 IMP_STALE_SIMULATION`. |
| Invitations e-mail | `AccountInvitationService.issue` publie `AccountInvitationIssuedEvent` ; l'envoi SMTP se fait **`AFTER_COMMIT`** (module `notification`, déjà en place). Un échec d'envoi n'annule pas l'import (dette connue : pas de file persistante — docs/03 §18). |
| Dette transactionnelle de l'audit | `@EventListener` synchrone `REQUIRES_NEW` (comme tous les listeners d'audit du projet) — **non résolue** dans cette tranche ; javadoc + mention ici, migration globale vers `@TransactionalEventListener(AFTER_COMMIT)` à planifier. |

---

## 12. Ambiguïtés nécessitant une décision (à valider)

| Réf. | Ambiguïté | Options | Décision proposée (par défaut) |
|---|---|---|---|
| **A** | Jeu de colonnes : docs/01 §8.1 (7 colonnes) vs docs/02 §10.4 (14 colonnes) | (1) modèle minimal seul ; (2) modèle étendu complet ; (3) minimal + sous-ensemble optionnel | **(3)** : 6 obligatoires + `phone` / `student_number` / `birth_date` / `work_study` / `company_name` ; `level_code` / `promotion_code` / `work_study_pattern` **ignorés** (`WARNING`). À valider. |
| **B** | `academic_year` du CSV = quel champ ? Le modèle n'a pas de « code d'année » standardisé | rapprocher de `academic_year.code` / `academic_year.name` / format libre `AAAA-AAAA` | Rapprochement sur **`academic_year.code`** (insensible à la casse, rogné). Format attendu à confirmer avec l'équipe. |
| **C** | Rétention de la simulation : docs/07 « 30 à 90 jours » (proposition non validée) | 7 / 30 / 90 jours | **`P7D`** par défaut, **configurable** (`app.import.student.simulation-ttl`). À valider avec le référent RGPD. |
| **D** | Conservation du fichier d'origine : docs/04 §16.1 a `stored_file_id` (nullable) | stocker le fichier / ne garder que nom + SHA-256 | **Ne pas stocker** le fichier (pas d'infra de stockage, minimisation). `stored_file_id` omis. À valider. |
| **E** | Table générique `import_job` (docs/04 §16) partagée STUDENT/SCHEDULE vs table par module | table générique / tables `student_import_*` dédiées | **Tables dédiées** au module `studentimport` (frontières Modulith ; `planning` aura ses `schedule_import_*`). Divergence documentée, risque faible. |
| **F** | Statuts `UPLOADED` / `ANALYZING` / `WAITING_CONFIRMATION` de docs/04 §16.1 | implémenter le cycle complet / cycle réduit synchrone | **Cycle réduit** : analyse synchrone → `SIMULATED` directement. Les statuts intermédiaires restent réservables si l'analyse devient asynchrone. |
| **G** | `student_number` : `NOT NULL` sur `student_profile` aujourd'hui ; le CSV le rend optionnel | rendre le numéro obligatoire dans le CSV / générer `ESIC-{ANNÉE}-{SEQ}` / `ERROR` si absent | **`ERROR IMP_STUDENT_NUMBER_REQUIRED`** si absent **et** aucun profil existant (création impossible sans numéro). La génération automatique (docs/04 §3.5) est une **dette listée**, pas cette tranche. À valider. |
| **H** | Application partielle : IMP-STU-04 « lignes ignorées » vs « aucune confirmation partielle » | ERROR bloque tout / ERROR = ligne sautée + bilan explicite | **ERROR bloque toute la confirmation** ; « lignes ignorées » = actions `NONE` (déjà à jour). Simplicité + RG-020/RG-021. À valider. |
| **I** | Compte existant avec identité (nom/prénom) différente du CSV | `ERROR` / `WARNING` sans réécriture / réécrire l'identité | **`WARNING`, aucune réécriture** de nom/prénom/e-mail. La mise à jour d'identité relève d'un lot d'administration des comptes. À valider. |
| **J** | Confirmation par un utilisateur ≠ créateur du job | créateur seul / staff global autorisé | **Staff global** (`ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION`) peut confirmer n'importe quel job ; un `PEDAGOGICAL_MANAGER` seulement le sien (D15). À valider. |
| **K** | Séparateur : imposer `;` (Excel FR) ou `,` (RFC) | imposer l'un / auto-détecter | **Auto-détection** sur l'en-tête (`,` ou `;`). Tabulation non prise en charge. À valider. |
| **L** | Multipart non configuré dans le projet | ajouter la config multipart globale | **Ajout** `spring.servlet.multipart.*` (impact nul sur les modules existants). Documenté. |

---

## 13. Frontend

### 13.1 Routes (enfants de `/students`, coquille `AppShell`)

| Route | Garde | Écran |
|---|---|---|
| `/students/import` | `roleGuard(['ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER'])` | `StudentImportHome` : téléversement + liste des jobs récents |
| `/students/import/:publicId` | même garde + `canActivateChild` | `StudentImportReview` : synthèse + lignes + confirmation |

`/students` (liste, fiche) : inchangé. Nav : l'entrée « Apprenants »
gagne une action / onglet secondaire « Importer » (visible pour les 4
rôles d'écriture, filtrée par `effectiveRoles()`).

### 13.2 `StudentImportHome`

- `input[type=file]` `accept=".csv"` (fichier unique) + zone
  glisser-déposer ; refus côté client si extension ≠ `.csv` ou taille
  > 2 MiB (contrôle serveur reste l'autorité) ; champs optionnels
  `programCode` / `classCode`.
- Bouton « Lancer la simulation » désactivé tant qu'aucun fichier ;
  barre de progression ; `FormData` via `StudentImportApiService`.
- Sur `201` → navigation vers `/students/import/:publicId`.
- Anomalies globales (`IMP_MISSING_COLUMN`, `IMP_FILE_TOO_LARGE`,
  `IMP_UNSUPPORTED_MEDIA_TYPE`, `IMP_TOO_MANY_ROWS`) → message en ligne
  contrôlé (liste blanche `student-import-errors.ts`), jamais le corps
  brut ; `403` → « périmètre » ; `5xx` → message générique.
- Table `mat-table` des jobs récents (`GET /student-imports`) : date,
  fichier, statut (chip), total, à créer / à mettre à jour / à
  transférer, erreurs, `confirmable`, lien « Consulter ».

### 13.3 `StudentImportReview`

- **Cartes de synthèse** : total, à créer, à mettre à jour, à
  transférer, sans changement, avertissements, erreurs (AC-004).
  Bandeau « non confirmable » si `blocking`/`error` > 0, listant les
  anomalies globales.
- **Table des lignes** (`GET …/rows`, pagination serveur ≤ 100) : n° de
  ligne, nom, prénom, e-mail, classe cible, `plannedAction` (chip),
  `rowStatus` (chip), anomalies dépliables (gravité, colonne, valeur
  reçue, message, correction attendue). Filtres `rowStatus` / `severity`
  / `action` (remise à la page 0), tri par n° de ligne.
- **Confirmation en ligne** : bouton « Confirmer l'import » désactivé si
  `!confirmable` ; panneau de confirmation avec **récapitulatif chiffré**
  (« X comptes créés + invités, Y inscriptions, Z transferts, W lignes
  sans changement ») ; `disabled` pendant l'appel ; double soumission
  bloquée ; capacité revérifiée au clic (`effectiveRoles()`).
  - `200` → bandeau succès + bilan appliqué + lien retour ;
    `alreadyApplied` → même bilan, message « déjà appliqué ».
  - `409 IMP_STALE_SIMULATION` → rechargement des lignes + anomalies,
    bandeau « la simulation n'est plus à jour, vérifiez puis
    reconfirmez », bouton bloqué jusqu'à revue.
  - `409 IMP_SIMULATION_EXPIRED` → message « simulation expirée, relancez
    un import ».
  - `409 IMP_NOT_CONFIRMABLE` / `403` → message contrôlé, aucun faux
    succès.
- **Annuler** : `POST …/cancel` (confirmation en ligne) → `204` → retour
  à la liste.
- Perte du contexte de rôle d'écriture → formulaires fermés, boutons
  masqués, requêtes en vol ignorées (motif monotone), aucune fausse
  confirmation.

### 13.4 Fichiers frontend

`frontend/src/app/features/students/import/` :
`student-import.models.ts`, `student-import-api.service.ts` (une méthode
par endpoint réel ; `FormData` ; jamais de jeton en URL),
`student-import-errors.ts` (liste blanche explicite de codes `IMP_*`,
`5xx` → générique), `student-import-home/` (`.ts`/`.html`/`.scss`/`.spec`),
`student-import-review/` (idem). Modifs : `app.routes.ts`,
`core/navigation/navigation.ts`, specs `navigation`, `app-shell`,
`dashboard`, `app.routes`. Aucune dépendance npm ajoutée.

---

## 14. Tests

### 14.1 Backend — unitaires (purs, sans DB)

- **Parseur CSV** : détection `,`/`;`, retrait BOM, `CRLF`/`LF`,
  guillemets RFC 4180, cellule multi-lignes, colonne inconnue →
  `WARNING`, colonne obligatoire absente → `BLOCKING`, `> maxRows` →
  `BLOCKING`, fichier vide / sans données → `BLOCKING`, octets non-UTF-8
  → `BLOCKING`, magie `PK\x03\x04` → rejet, nombre de colonnes ≠ en-tête
  → `ERROR` ligne.
- **Validation de champ** : e-mail (valide / invalide / vide),
  `birth_date` (`ISO` / `dd/MM/yyyy` / invalide → `WARNING`),
  `work_study` (variantes), téléphone (`WARNING` si non conforme).
- **Normalisation** : `trim`, e-mail minuscule, codes majuscule.
- **Dé-duplication fichier** : deux e-mails identiques + charge utile
  identique → `WARNING` ligne ignorée ; charges divergentes → `ERROR`
  sur les deux ; même `student_number` deux fois → idem.
- **Résolution de classe** (mock `ClassGroupDirectory`) : `Found` /
  chaque `Miss.*`.
- **Calcul `planned_action`** : les 9 situations de §6.2.
- **Assainissement du nom de fichier**.

### 14.2 Backend — `@DataJpaTest` (V11)

- `CHECK` de `status` / `severity` / `row_status` / `planned_action`.
- `UNIQUE (student_import_job_id, row_number)`.
- `ON DELETE CASCADE` `job → job_issue` / `job → row → row_issue`.
- FK `RESTRICT` vers `user_account` (`requested_by_id`,
  `confirmed_by_id`).
- `public_id` unique sur les 4 tables.
- Défaut `expires_at` calculé par le service (horloge figée).

### 14.3 Backend — intégration `@SpringBootTest`

- **IMP-STU-01 / TI-001 / TI-008** : fichier valide de **100** lignes →
  `SIMULATED`, `confirmable=true`, `summary.plannedCreate=100` ;
  comptages `user_account` / `student_profile` / `enrollment` /
  `account_invitation` **inchangés** ; audit `STUDENT_IMPORT_SIMULATED`.
- **Confirmation** → `APPLIED`, 100 comptes `PENDING_ACTIVATION` + rôle
  `STUDENT` actif + 100 profils + 100 inscriptions + 100 invitations ;
  `summary.applied*` cohérent ; audit `STUDENT_IMPORT_CONFIRMED` +
  `ACCOUNT_INVITATION_ISSUED` ×100, **sans PII**.
- **IMP-STU-03 / TI-002** : colonne `email` absente → `400` /
  `BLOCKING IMP_MISSING_COLUMN`, `confirmable=false`, confirmation → `409
  IMP_NOT_CONFIRMABLE`.
- **TI-003** : e-mail invalide → ligne `ERROR`, job non `confirmable`.
- **TI-004** : doublon dans le fichier → `WARNING` (identique) / `ERROR`
  (divergent).
- **IMP-STU-02 / TI-005 / TI-006 / AC-005 / AC-006** : apprenant
  existant, autre classe même année → `TRANSFER_CLASS` ; après
  confirmation, ancienne inscription `TRANSFERRED` consultable, **aucun**
  doublon de compte.
- **TI-007** : `PEDAGOGICAL_MANAGER`, filtre job hors périmètre → `403` ;
  ligne hors périmètre → `ERROR IMP_CLASS_OUT_OF_SCOPE`.
- **TI-010** : fichier > 2 MiB → `413`/`400 IMP_FILE_TOO_LARGE`.
- **TI-011** : contenu `.xlsx` (magie ZIP) → `415
  IMP_UNSUPPORTED_MEDIA_TYPE`.
- **TI-012** : double confirmation séquentielle → 2ᵉ = `200` idempotent
  (`alreadyApplied`) ; job `FAILED` → `409`.
- **Simulation périmée** : classe archivée entre simulation et
  confirmation → `409 IMP_STALE_SIMULATION`, **rien** appliqué, job
  re-simulé avec anomalie rafraîchie.
- **Expiration** : `expires_at` dans le passé → `409
  IMP_SIMULATION_EXPIRED`.
- **Rollback** : injection d'un échec sur la Nᵉ ligne → `FAILED`, **0**
  compte / profil / inscription créé.
- **Purge** `@Scheduled` : job `SIMULATED` expiré supprimé (CASCADE),
  job `APPLIED` conservé.

### 14.4 Backend — sécurité `@SpringBootTest`

- `401` anonyme sur les 6 endpoints.
- `403` pour `STUDENT` / `TEACHER` sur tous.
- `PEDAGOGICAL_MANAGER` : simule (lignes de son périmètre), ne voit pas
  le job d'un autre RP (`403 IMP_JOB_FORBIDDEN`), ne confirme pas le job
  d'un autre RP (`403 IMP_CONFIRM_FORBIDDEN`).
- DTO : aucun `id` SQL, `password_hash`, jeton.
- Audit : aucun e-mail / nom / n° étudiant / valeur de cellule / IP.

### 14.5 Backend — concurrence

- Deux `confirm` parallèles sur le même job (pool 2 threads) →
  exactement **un** ensemble de comptes créés, l'autre reçoit `200`
  idempotent ou `409`, jamais `500`, jamais de doublon.
- Deux simulations concurrentes avec e-mails recoupés → 0 écriture
  métier ; à la confirmation croisée, aucun doublon de compte (autorité
  = unicité SQL).

### 14.6 Frontend (Vitest)

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

### 14.7 Performance (documentée, non bloquante)

- TP-004 : temps de simulation **et** de confirmation d'un fichier de
  100 lignes relevé au CP10 (`docs/CURRENT-STATE.md`).

---

## 15. Découpage d'implémentation en checkpoints

| CP | Contenu | Preuve attendue |
|---|---|---|
| **CP0** | **Ce rapport de conception** + traçabilité minimale (`CURRENT-STATE`, `10-journal-ia`). Commit + PR. | Document présent, PR ouverte |
| **CP1** | `V11__create_student_import_tables.sql` (additive) + `@DataJpaTest`. | `./mvnw test` vert, schéma en version 11 |
| **CP2** | Squelette module `studentimport` : `package-info` (`@ApplicationModule`), entités JPA, repositories, `StudentImportWeb`, `StudentImportException` + handler, codes `IMP_*` dans `ApiError`. | `ModularityTests` vert |
| **CP3** | Parsing + normalisation + validation de champ + dé-duplication fichier (composants purs). | Tests unitaires §14.1 |
| **CP4** | Ports : extension `ClassGroupDirectory.resolveForImport` (+ impl) ; nouveau `identity.StudentAccountProvisioner` (+ impl, réutilise `AccountInvitationService`) ; nouveau `enrollment.StudentEnrollmentProvisioner` (+ impl, délègue aux services existants). | Tests unitaires + `@DataJpaTest` des impls ; `ModularityTests` vert |
| **CP5** | `StudentImportSimulationService` + `StudentImportController` (téléversement, get, rows, cancel) + config multipart. Persistance `student_import_*` uniquement. | Intégration §14.3 (simulation), sécurité §14.4 |
| **CP6** | `StudentImportConfirmationService` : transaction unique, verrou pessimiste, re-validation, idempotence, application via ports, invitations. | Intégration §14.3 (confirmation, stale, rollback, TI-012), concurrence §14.5 |
| **CP7** | Audit : `StudentImportChangeEvent` + `audit.internal.StudentImportAuditListener` ; purge `@Scheduled` des jobs expirés. | Tests audit + purge |
| **CP8** | Frontend : modèles, `StudentImportApiService`, `student-import-errors`, `StudentImportHome`, `StudentImportReview`, routes, nav. | Specs §14.6, `npm test` / `lint` / `build` verts |
| **CP9** | Documentation : `docs/CURRENT-STATE.md` (section détaillée), `docs/09-matrice-rncp.md` (EF-IMP-001/002, US-050/051), `docs/11-guide-demonstration.md` (scénario import). | Docs à jour |
| **CP10** | Vérification globale : `./mvnw clean test` + `npm test` / `lint` / `build` ré-exécutés (baselines relevées), démonstration locale du parcours (simulation → revue → confirmation → invitations Mailpit), mesure TP-004, mise à jour de la PR. | Sortie des commandes, statuts HTTP relevés |

Chaque checkpoint est un ou plusieurs commits sur `feature/student-csv-import`,
sans fusion, sans réécriture d'historique, sans toucher V1–V10.

---

## 16. Divergences assumées vs documentation (récapitulatif)

| Sujet | Documentation | Décision de la tranche | Raison |
|---|---|---|---|
| Jeu de colonnes | docs/02 §10.4 : 14 colonnes | 6 obligatoires + 5 optionnelles ; 3 ignorées | Périmètre prototype ; `level`/`promotion`/`pattern` non nécessaires pour identifier la classe |
| Tables d'import | docs/04 §16 : `import_job` générique partagée | `student_import_*` dédiées au module | Frontières Spring Modulith (propriété de table par module) |
| Statuts de job | docs/04 §16.1 : `UPLOADED`/`ANALYZING`/`WAITING_CONFIRMATION` | Analyse synchrone → `SIMULATED` direct | Simplicité ; réservables si analyse asynchrone |
| Fichier d'origine | docs/04 §16.1 : `stored_file_id` | Non stocké (nom assaini + SHA-256) | Pas d'infra de stockage ; minimisation (docs/07) |
| Rétention temporaire | docs/07 : 30–90 jours (proposition) | `P7D` par défaut, configurable | Proposition non validée ; conservateur |
| Application partielle | IMP-STU-04 : « lignes ignorées » | `ERROR` bloque toute la confirmation ; « ignorées » = actions `NONE` | RG-020 / RG-021 ; pas d'application partielle silencieuse |
| Identité d'un compte existant | docs/02 §10.7 : « propose une mise à jour » | `WARNING`, aucune réécriture nom/prénom/e-mail | Sécurité identité ; relève de l'administration des comptes |
| Numéro étudiant | docs/02 §10.4 : optionnel | `ERROR` si absent et création de profil nécessaire | `student_profile.student_number` `NOT NULL` ; génération auto = dette listée |
| Création de compte | Aucune API publique existante | Nouveau port `identity.StudentAccountProvisioner` | Nécessaire ; réutilise l'invitation existante |
| Multipart | Non configuré | Ajout `spring.servlet.multipart.*` | Requis pour le téléversement |

---

## 17. Risques identifiés

| Risque | Impact | Atténuation |
|---|---|---|
| Volume : 100 comptes + profils + inscriptions + invitations dans une seule transaction | Verrou long, mémoire | Prototype ≤ 500 lignes ; mesure TP-004 ; batch possible en évolution |
| Réutilisation d'`AccountInvitationService` dans la transaction d'import | Couplage transactionnel | L'e-mail part `AFTER_COMMIT` (déjà en place) ; échec d'envoi non bloquant (dette connue) |
| `EnrollmentService.transfer` exige `effectiveDate >= start_date` | `TRANSFER_CLASS` refusé si import « rétro-daté » | `effectiveDate = LocalDate.now(clock)` ; anomalie `ERROR` si incohérent |
| Simulation obsolète non détectée | Données incohérentes | Re-validation **complète** à la confirmation (D10) + verrou pessimiste |
| `student_import_row.raw_data_json` contient des données personnelles | Rétention | `expires_at` + purge `@Scheduled` ; exclu de l'audit ; accès limité aux rôles MANAGE |
| Dette transactionnelle de l'audit | Ligne d'audit avant commit métier | Documentée, non résolue (cohérence globale du projet) |

---

*Fin du Checkpoint 0. Aucune implémentation ne commence avant validation
de ce rapport.*
