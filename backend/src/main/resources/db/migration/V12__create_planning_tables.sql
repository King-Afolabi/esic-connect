-- Module `planning` — import CSV, versionnement et publication d'un
-- planning de classe (docs/02-cahier-des-charges.md §13 ; docs/04-modele-donnees.md
-- §17-18 ; docs/reports/G1_ARCHITECTURE_DECISIONS.md DEC-G1-001..006, 012 ;
-- EF-PLAN-001..007, EF-SES-001 ; RG-016, RG-030..RG-035 ; AC-007, AC-008 ;
-- branche feature/master-level-product-expansion, bloc G1-B).
--
-- Portée de CE fichier : SCHÉMA du module `planning` uniquement. Sept
-- tables propres au module. Aucune donnée métier insérée. La modification
-- additive de `course_session` (lien vers une entrée de planning +
-- discriminant d'origine) relève de V13.
--
-- Décisions de conception (G1-0 / G1-0.1) :
--   * `planning_schedule` : un planning par (classe, année scolaire) ;
--     `planning_version` : une version par publication (jamais supprimée,
--     RG-032) ; `planning_entry` : un créneau d'UNE version (les versions
--     historiques conservent leurs entrées) ;
--   * identité stable d'un créneau = colonne `slot_key` FOURNIE dans le
--     CSV G1 (DEC-G1-002, extension assumée de docs/02 §13.3) ; unicité
--     `(planning_version_id, slot_key)` ;
--   * `planning_entry.teacher_user_id` est une valeur technique (FK SQL
--     RESTRICT vers `user_account`) INTERNE au module — jamais exposée
--     par le port `coursesession.PlanningSessionWriter`, qui ne manipule
--     que des UUID publics (DEC-G1-001, correctif G1-0.1) ;
--   * la salle reste un CODE fonctionnel (`room_code`), pas une FK : une
--     salle est affectable après l'import (RG-035) et sert seulement à la
--     détection de conflit ; aucun port de résolution de salle requis en
--     G1 ;
--   * job d'import : statuts `SIMULATED | PUBLISHED | CANCELLED | EXPIRED
--     | FAILED` (DEC-G1-003). `FAILED` est écrit HORS de la transaction
--     de publication (transaction `REQUIRES_NEW` distincte, sans aucune
--     donnée métier publiée) ;
--   * le fichier téléversé N'EST JAMAIS conservé : nom assaini, empreinte
--     SHA-256 et taille uniquement (comme `studentimport`, V11).
--
-- Conventions identiques à V1/V4..V11 : PK BIGINT UNSIGNED AUTO_INCREMENT,
-- identifiant public UUID (BINARY(16)) unique, horodatage UTC
-- (TIMESTAMP(6)), verrouillage optimiste (`version`), colonnes auteur
-- (`*_by_id`) en FK RESTRICT vers `user_account`. `ON DELETE CASCADE`
-- uniquement sur la chaîne technique temporaire
-- `planning_import_job -> planning_import_job_issue / planning_import_row
-- -> planning_import_row_issue`. `planning_schedule` / `planning_version`
-- / `planning_entry` sont durables : suppression RESTRICT.
--
-- Frontières modulaires (Spring Modulith) : toutes ces tables
-- appartiennent au module `planning`. `class_group_id`,
-- `academic_year_id`, `teacher_user_id`, `requested_by_id` sont de
-- simples valeurs techniques (FK SQL) — aucun partage d'entité JPA ; la
-- résolution passe par des ports publics (`academic.ClassGroupDirectory`,
-- `identity.TeacherDirectory`, `academic.AcademicScopeDirectory`,
-- `identity.CurrentUserResolver`).

-- ---------------------------------------------------------------------------
-- planning_schedule : planning d'une classe pour une année scolaire.
-- Un seul par (classe, année). `current_version_number` = numéro de la
-- version PUBLISHED courante (0 tant qu'aucune publication).
-- ---------------------------------------------------------------------------
CREATE TABLE planning_schedule (
    id                     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id              BINARY(16)      NOT NULL,
    class_group_id         BIGINT UNSIGNED NOT NULL,
    academic_year_id       BIGINT UNSIGNED NOT NULL,
    current_version_number INT UNSIGNED    NOT NULL DEFAULT 0,
    status                 VARCHAR(16)     NOT NULL,   -- DRAFT | ACTIVE | ARCHIVED
    created_at             TIMESTAMP(6)    NOT NULL,
    created_by_id          BIGINT UNSIGNED NULL,
    updated_at             TIMESTAMP(6)    NOT NULL,
    updated_by_id          BIGINT UNSIGNED NULL,
    version                BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_planning_schedule_public_id UNIQUE (public_id),
    CONSTRAINT uq_planning_schedule_class_year UNIQUE (class_group_id, academic_year_id),
    CONSTRAINT chk_planning_schedule_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),

    CONSTRAINT fk_planning_schedule_class_group FOREIGN KEY (class_group_id) REFERENCES class_group (id) ON DELETE RESTRICT,
    CONSTRAINT fk_planning_schedule_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_year (id) ON DELETE RESTRICT,
    CONSTRAINT fk_planning_schedule_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_planning_schedule_updated_by FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_planning_schedule_class_group ON planning_schedule (class_group_id);

-- ---------------------------------------------------------------------------
-- planning_version : une version d'un planning. Créée à chaque publication
-- (RG-032, EF-PLAN-005/007). Jamais supprimée. `replaced_by_version_id`
-- pointe la version suivante quand celle-ci est SUPERSEDED.
-- ---------------------------------------------------------------------------
CREATE TABLE planning_version (
    id                     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id              BINARY(16)      NOT NULL,
    planning_schedule_id   BIGINT UNSIGNED NOT NULL,
    version_number         INT UNSIGNED    NOT NULL,
    status                 VARCHAR(16)     NOT NULL,   -- DRAFT | PUBLISHED | SUPERSEDED
    source_import_job_id   BIGINT UNSIGNED NULL,
    replaced_by_version_id BIGINT UNSIGNED NULL,
    entry_count            INT UNSIGNED    NOT NULL DEFAULT 0,
    change_summary         VARCHAR(500)    NULL,       -- ex. "3 ajout(s), 1 modification(s), 0 retrait(s)"
    published_at           TIMESTAMP(6)    NULL,
    published_by_id        BIGINT UNSIGNED NULL,
    created_at             TIMESTAMP(6)    NOT NULL,
    updated_at             TIMESTAMP(6)    NOT NULL,
    version                BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_planning_version_public_id UNIQUE (public_id),
    CONSTRAINT uq_planning_version_number UNIQUE (planning_schedule_id, version_number),
    CONSTRAINT chk_planning_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED')),

    CONSTRAINT fk_planning_version_schedule FOREIGN KEY (planning_schedule_id) REFERENCES planning_schedule (id) ON DELETE RESTRICT,
    CONSTRAINT fk_planning_version_replaced_by FOREIGN KEY (replaced_by_version_id) REFERENCES planning_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_planning_version_published_by FOREIGN KEY (published_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_planning_version_schedule ON planning_version (planning_schedule_id, version_number);

-- ---------------------------------------------------------------------------
-- planning_entry : un créneau d'UNE version publiée.
--
-- IDENTITÉ (corrigée à l'audit G1-B.1, 1er sept. 2026 — DEC-G1-002) :
--   * `public_id`      = identifiant public de CETTE ligne de version.
--     ALÉATOIRE et unique : deux versions successives d'un même créneau
--     portent deux `planning_entry.public_id` DIFFÉRENTS.
--   * `slot_public_id` = identité STABLE du créneau À TRAVERS LES
--     VERSIONS. Déterministe : `UUIDv3(planning_schedule.public_id || '|'
--     || slot_key)`. C'est cette valeur — et jamais `public_id` — qui est
--     transmise au port `coursesession.PlanningSessionWriter` comme
--     `slotPublicId` et stockée dans `course_session.planning_slot_public_id`.
--   * `slot_key`       = libellé de créneau fourni dans le CSV, unique
--     `(planning_version_id, slot_key)` au sein d'une version.
-- `teacher_user_id` interne au module. `room_code` = code fonctionnel
-- (pas de FK, RG-035). `session_public_id` = séance `course_session`
-- créée / réutilisée à la publication (lien renseigné par
-- `PlanningSessionWriter`, DEC-G1-001).
-- ---------------------------------------------------------------------------
CREATE TABLE planning_entry (
    id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id            BINARY(16)      NOT NULL,
    planning_version_id  BIGINT UNSIGNED NOT NULL,
    planning_schedule_id BIGINT UNSIGNED NOT NULL,   -- dénormalisé (requêtes par planning)
    slot_key             VARCHAR(64)     NOT NULL,
    slot_public_id       BINARY(16)      NOT NULL,   -- identité stable du créneau inter-versions (DEC-G1-002)
    class_group_id       BIGINT UNSIGNED NOT NULL,
    teacher_user_id      BIGINT UNSIGNED NOT NULL,
    room_code            VARCHAR(50)     NULL,
    title                VARCHAR(191)    NOT NULL,
    starts_at            TIMESTAMP(6)    NOT NULL,
    ends_at              TIMESTAMP(6)    NOT NULL,
    time_zone_id         VARCHAR(64)     NOT NULL,
    session_public_id    BINARY(16)      NULL,
    created_at           TIMESTAMP(6)    NOT NULL,
    version              BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_planning_entry_public_id UNIQUE (public_id),
    CONSTRAINT uq_planning_entry_slot UNIQUE (planning_version_id, slot_key),
    CONSTRAINT chk_planning_entry_period CHECK (ends_at > starts_at),

    CONSTRAINT fk_planning_entry_version FOREIGN KEY (planning_version_id) REFERENCES planning_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_planning_entry_schedule FOREIGN KEY (planning_schedule_id) REFERENCES planning_schedule (id) ON DELETE RESTRICT,
    CONSTRAINT fk_planning_entry_class_group FOREIGN KEY (class_group_id) REFERENCES class_group (id) ON DELETE RESTRICT,
    CONSTRAINT fk_planning_entry_teacher FOREIGN KEY (teacher_user_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_planning_entry_schedule ON planning_entry (planning_schedule_id);
CREATE INDEX idx_planning_entry_window ON planning_entry (starts_at, ends_at);
CREATE INDEX idx_planning_entry_teacher ON planning_entry (teacher_user_id);
-- Retrouver toutes les versions historiques d'un même créneau stable.
CREATE INDEX idx_planning_entry_slot ON planning_entry (planning_schedule_id, slot_public_id);

-- ---------------------------------------------------------------------------
-- planning_import_job : en-tête d'un import (téléversement + simulation).
-- Rattaché au `planning_schedule` cible (créé à la volée si absent — la
-- création est dans la transaction de publication, jamais à la
-- simulation, invariant T1).
-- ---------------------------------------------------------------------------
CREATE TABLE planning_import_job (
    id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id            BINARY(16)      NOT NULL,
    status               VARCHAR(16)     NOT NULL,   -- SIMULATED | PUBLISHED | CANCELLED | EXPIRED | FAILED
    class_group_id       BIGINT UNSIGNED NOT NULL,
    academic_year_id     BIGINT UNSIGNED NOT NULL,
    planning_schedule_id BIGINT UNSIGNED NULL,       -- renseigné à la publication
    published_version_id BIGINT UNSIGNED NULL,
    original_file_name   VARCHAR(255)    NOT NULL,
    file_sha256          CHAR(64)        NOT NULL,
    file_size_bytes      INT UNSIGNED    NOT NULL,
    csv_separator        CHAR(1)         NOT NULL,   -- ',' ou ';'
    requested_by_id      BIGINT UNSIGNED NOT NULL,
    total_rows           INT UNSIGNED    NOT NULL DEFAULT 0,
    valid_rows           INT UNSIGNED    NOT NULL DEFAULT 0,
    warning_rows         INT UNSIGNED    NOT NULL DEFAULT 0,
    error_rows           INT UNSIGNED    NOT NULL DEFAULT 0,
    blocking_issue_count INT UNSIGNED    NOT NULL DEFAULT 0,
    added_rows           INT UNSIGNED    NOT NULL DEFAULT 0,
    modified_rows        INT UNSIGNED    NOT NULL DEFAULT 0,
    unchanged_rows       INT UNSIGNED    NOT NULL DEFAULT 0,
    removed_entries      INT UNSIGNED    NOT NULL DEFAULT 0,
    confirmable          BOOLEAN         NOT NULL DEFAULT FALSE,
    simulated_at         TIMESTAMP(6)    NOT NULL,
    published_at         TIMESTAMP(6)    NULL,
    published_by_id      BIGINT UNSIGNED NULL,
    failure_reason       VARCHAR(200)    NULL,       -- catégorie non sensible (statut FAILED)
    expires_at           TIMESTAMP(6)    NOT NULL,
    created_at           TIMESTAMP(6)    NOT NULL,
    updated_at           TIMESTAMP(6)    NOT NULL,
    version              BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_planning_import_job_public_id UNIQUE (public_id),
    CONSTRAINT chk_planning_import_job_status CHECK (status IN ('SIMULATED', 'PUBLISHED', 'CANCELLED', 'EXPIRED', 'FAILED')),
    CONSTRAINT chk_planning_import_job_file_size CHECK (file_size_bytes > 0),

    CONSTRAINT fk_planning_import_job_class_group FOREIGN KEY (class_group_id) REFERENCES class_group (id) ON DELETE RESTRICT,
    CONSTRAINT fk_planning_import_job_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_year (id) ON DELETE RESTRICT,
    CONSTRAINT fk_planning_import_job_schedule FOREIGN KEY (planning_schedule_id) REFERENCES planning_schedule (id) ON DELETE RESTRICT,
    CONSTRAINT fk_planning_import_job_published_version FOREIGN KEY (published_version_id) REFERENCES planning_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_planning_import_job_requested_by FOREIGN KEY (requested_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_planning_import_job_published_by FOREIGN KEY (published_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_planning_import_job_status_expiry ON planning_import_job (status, expires_at);
CREATE INDEX idx_planning_import_job_requester ON planning_import_job (requested_by_id, created_at);
CREATE INDEX idx_planning_import_job_target ON planning_import_job (class_group_id, academic_year_id);

-- ---------------------------------------------------------------------------
-- planning_import_job_issue : anomalies globales (en-tête, colonnes
-- obligatoires manquantes, trop de lignes, encodage, échec de publication).
-- CASCADE avec le job.
-- ---------------------------------------------------------------------------
CREATE TABLE planning_import_job_issue (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id             BINARY(16)      NOT NULL,
    planning_import_job_id BIGINT UNSIGNED NOT NULL,
    severity              VARCHAR(16)     NOT NULL,   -- INFO | WARNING | ERROR | BLOCKING
    error_code            VARCHAR(80)     NOT NULL,
    message               VARCHAR(500)    NOT NULL,
    column_name           VARCHAR(64)     NULL,
    created_at            TIMESTAMP(6)    NOT NULL,
    version               BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_planning_import_job_issue_public_id UNIQUE (public_id),
    CONSTRAINT chk_planning_import_job_issue_severity CHECK (severity IN ('INFO', 'WARNING', 'ERROR', 'BLOCKING')),

    CONSTRAINT fk_planning_import_job_issue_job FOREIGN KEY (planning_import_job_id) REFERENCES planning_import_job (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_planning_import_job_issue_job ON planning_import_job_issue (planning_import_job_id);

-- ---------------------------------------------------------------------------
-- planning_import_row : une ligne du CSV, normalisée (colonnes typées,
-- pas de JSON brut). `planned_action` = résultat de la simulation
-- (comparaison avec la version publiée courante, DEC-G1-002/004).
-- CASCADE avec le job.
-- ---------------------------------------------------------------------------
CREATE TABLE planning_import_row (
    id                       BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id                BINARY(16)      NOT NULL,
    planning_import_job_id    BIGINT UNSIGNED NOT NULL,
    `row_number`             INT UNSIGNED    NOT NULL,   -- ligne dans le fichier (en-tête = 1)
    input_slot_key           VARCHAR(64)     NULL,
    input_session_date       VARCHAR(40)     NULL,
    input_start_time         VARCHAR(20)     NULL,
    input_end_time           VARCHAR(20)     NULL,
    input_time_zone_id       VARCHAR(64)     NULL,
    input_title              VARCHAR(191)    NULL,
    input_teacher_public_id  VARCHAR(64)     NULL,
    input_room_code          VARCHAR(50)     NULL,
    resolved_teacher_user_id BIGINT UNSIGNED NULL,       -- trace de résolution
    resolved_starts_at       TIMESTAMP(6)    NULL,
    resolved_ends_at         TIMESTAMP(6)    NULL,
    row_status               VARCHAR(12)     NOT NULL,   -- VALID | WARNING | ERROR
    planned_action           VARCHAR(12)     NOT NULL,   -- ADDED | MODIFIED | UNCHANGED | REMOVED | CONFLICT
    created_at               TIMESTAMP(6)    NOT NULL,
    version                  BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_planning_import_row_public_id UNIQUE (public_id),
    CONSTRAINT uq_planning_import_row_number UNIQUE (planning_import_job_id, `row_number`),
    CONSTRAINT chk_planning_import_row_status CHECK (row_status IN ('VALID', 'WARNING', 'ERROR')),
    CONSTRAINT chk_planning_import_row_action CHECK (planned_action IN ('ADDED', 'MODIFIED', 'UNCHANGED', 'REMOVED', 'CONFLICT')),

    CONSTRAINT fk_planning_import_row_job FOREIGN KEY (planning_import_job_id) REFERENCES planning_import_job (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_planning_import_row_job_status ON planning_import_row (planning_import_job_id, row_status);

-- ---------------------------------------------------------------------------
-- planning_import_row_issue : anomalies portées par une ligne.
-- `received_value` tronquée, jamais reprise dans l'audit. CASCADE avec la
-- ligne.
-- ---------------------------------------------------------------------------
CREATE TABLE planning_import_row_issue (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id             BINARY(16)      NOT NULL,
    planning_import_row_id BIGINT UNSIGNED NOT NULL,
    severity              VARCHAR(16)     NOT NULL,   -- INFO | WARNING | ERROR | BLOCKING
    column_name           VARCHAR(64)     NULL,
    received_value        VARCHAR(200)    NULL,
    error_code            VARCHAR(80)     NOT NULL,
    message               VARCHAR(500)    NOT NULL,
    created_at            TIMESTAMP(6)    NOT NULL,
    version               BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_planning_import_row_issue_public_id UNIQUE (public_id),
    CONSTRAINT chk_planning_import_row_issue_severity CHECK (severity IN ('INFO', 'WARNING', 'ERROR', 'BLOCKING')),

    CONSTRAINT fk_planning_import_row_issue_row FOREIGN KEY (planning_import_row_id) REFERENCES planning_import_row (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_planning_import_row_issue_row ON planning_import_row_issue (planning_import_row_id);
