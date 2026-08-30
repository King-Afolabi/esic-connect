-- Import CSV contrôlé des apprenants — tables techniques temporaires
-- (docs/01-cadrage.md §8 ; docs/02-cahier-des-charges.md §10 ;
-- docs/04-modele-donnees.md §16 ; docs/07-securite-rgpd.md §9-10 ;
-- docs/08-tests-recette.md §9 ; docs/reports/STUDENT_CSV_IMPORT_DESIGN.md §7 ;
-- EF-IMP-001 / EF-IMP-002 ; US-050 / US-051 ; RG-020 à RG-024 ;
-- branche feature/student-csv-import-cp1).
--
-- Portée du checkpoint CP1 : SCHÉMA UNIQUEMENT. Quatre tables propres au
-- module `studentimport` (`student_import_job` / `student_import_job_issue` /
-- `student_import_row` / `student_import_row_issue`) et une table de
-- séquence `student_number_sequence`. Aucune donnée métier n'est insérée.
-- Aucun endpoint, service, parsing CSV, simulation ni confirmation : ceux-ci
-- relèvent des checkpoints suivants.
--
-- Décisions de conception (rapport §7, §12, §16) :
--   * tables DÉDIÉES au module et non la table générique `import_job` de
--     docs/04 §16 — frontières Spring Modulith (le module `planning` aura
--     ses propres `schedule_import_*`) ;
--   * le fichier téléversé N'EST PAS conservé : seuls le nom assaini
--     (`original_file_name`), l'empreinte SHA-256 (`file_sha256`) et la
--     taille (`file_size_bytes`) sont persistés ;
--   * données temporaires MINIMISÉES : `student_import_row` porte des
--     colonnes typées explicites (11 champs métier normalisés), jamais un
--     duplicata JSON de la ligne brute ; la valeur brute d'une cellule
--     n'est conservée que si elle a produit une anomalie
--     (`student_import_row_issue.received_value`, tronquée) ;
--   * pas de statut `FAILED` dans cette tranche (rapport §3.4) : une
--     confirmation qui échoue rollback intégralement et le job reste
--     `SIMULATED`. `FAILED` est volontairement absent du CHECK de `status`.
--
-- Conventions identiques à V1/V4..V10 : PK BIGINT UNSIGNED AUTO_INCREMENT,
-- identifiant public UUID (BINARY(16)) unique, horodatage UTC
-- (TIMESTAMP(6)), colonne `version` de verrouillage optimiste sur les
-- quatre tables `student_import_*` (elles réutilisent `shared.BaseEntity`,
-- comme `audit_event` ou `attendance_correction` — tables append-only).
-- `student_number_sequence` fait exception : sa clé primaire fonctionnelle
-- est `start_year`, sans `id` technique, sans `public_id`, sans `version`.
-- `requested_by_id` / `confirmed_by_id` sont de simples valeurs techniques
-- (FK SQL RESTRICT vers `user_account`) : le module `studentimport` ne partage aucune
-- entité JPA avec `identity`. Les codes de formation / classe / année du
-- CSV (`scope_*_code`, `input_*`) restent des chaînes fonctionnelles :
-- leur résolution passera par des ports publics aux checkpoints suivants.
--
-- CASCADE / purge : `ON DELETE CASCADE` sur toute la chaîne
-- `student_import_job -> student_import_job_issue / student_import_row ->
-- student_import_row_issue` (acceptable avant confirmation et à la purge —
-- docs/04 §16.4). Les données métier créées à la confirmation (comptes,
-- profils, inscriptions, invitations) ne dépendent d'AUCUNE de ces clés
-- étrangères et ne sont donc jamais supprimées avec un import.
-- `student_number_sequence` n'est jamais purgée (compteur monotone par
-- année de début d'année scolaire).

-- ---------------------------------------------------------------------------
-- student_import_job : en-tête d'un import (téléversement + simulation).
-- ---------------------------------------------------------------------------
CREATE TABLE student_import_job (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id             BINARY(16)      NOT NULL,
    status                VARCHAR(16)     NOT NULL,   -- SIMULATED | APPLIED | CANCELLED | EXPIRED
    original_file_name    VARCHAR(255)    NOT NULL,   -- nom assaini (basename), jamais un chemin
    file_sha256           CHAR(64)        NOT NULL,   -- empreinte hex du contenu reçu (contenu non conservé)
    file_size_bytes       INT UNSIGNED    NOT NULL,
    csv_separator         CHAR(1)         NOT NULL,   -- ',' ou ';'
    requested_by_id       BIGINT UNSIGNED NOT NULL,
    scope_program_code    VARCHAR(80)     NULL,       -- filtre de périmètre éventuel (PEDAGOGICAL_MANAGER)
    scope_class_code      VARCHAR(80)     NULL,
    total_rows            INT UNSIGNED    NOT NULL DEFAULT 0,
    valid_rows            INT UNSIGNED    NOT NULL DEFAULT 0,
    warning_rows          INT UNSIGNED    NOT NULL DEFAULT 0,
    error_rows            INT UNSIGNED    NOT NULL DEFAULT 0,
    blocking_issue_count  INT UNSIGNED    NOT NULL DEFAULT 0,
    planned_create_rows   INT UNSIGNED    NOT NULL DEFAULT 0,
    planned_update_rows   INT UNSIGNED    NOT NULL DEFAULT 0,
    planned_transfer_rows INT UNSIGNED    NOT NULL DEFAULT 0,
    planned_noop_rows     INT UNSIGNED    NOT NULL DEFAULT 0,
    applied_created       INT UNSIGNED    NULL,       -- renseignés uniquement à l'état APPLIED
    applied_updated       INT UNSIGNED    NULL,
    applied_transferred   INT UNSIGNED    NULL,
    applied_invited       INT UNSIGNED    NULL,
    applied_ignored       INT UNSIGNED    NULL,
    confirmable           BOOLEAN         NOT NULL DEFAULT FALSE,  -- figé à la simulation, re-vérifié à la confirmation
    simulated_at          TIMESTAMP(6)    NOT NULL,
    confirmed_at          TIMESTAMP(6)    NULL,
    confirmed_by_id       BIGINT UNSIGNED NULL,
    expires_at            TIMESTAMP(6)    NOT NULL,   -- simulated_at + TTL de simulation (config)
    created_at            TIMESTAMP(6)    NOT NULL,
    updated_at            TIMESTAMP(6)    NOT NULL,
    version               BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_student_import_job_public_id UNIQUE (public_id),
    CONSTRAINT chk_student_import_job_status CHECK (status IN ('SIMULATED', 'APPLIED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT chk_student_import_job_file_size CHECK (file_size_bytes > 0),

    CONSTRAINT fk_student_import_job_requested_by FOREIGN KEY (requested_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_import_job_confirmed_by FOREIGN KEY (confirmed_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_student_import_job_status_expiry ON student_import_job (status, expires_at);
CREATE INDEX idx_student_import_job_requester ON student_import_job (requested_by_id, created_at);

-- ---------------------------------------------------------------------------
-- student_import_job_issue : anomalies globales d'un import (en-tête, colonnes
-- obligatoires manquantes, trop de lignes, encodage...). Supprimées en
-- CASCADE avec le job.
-- ---------------------------------------------------------------------------
CREATE TABLE student_import_job_issue (
    id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id            BINARY(16)      NOT NULL,
    student_import_job_id BIGINT UNSIGNED NOT NULL,
    severity             VARCHAR(16)     NOT NULL,   -- INFO | WARNING | ERROR | BLOCKING
    error_code           VARCHAR(80)     NOT NULL,
    message              VARCHAR(500)    NOT NULL,
    column_name          VARCHAR(64)     NULL,
    created_at           TIMESTAMP(6)    NOT NULL,
    version              BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_student_import_job_issue_public_id UNIQUE (public_id),
    CONSTRAINT chk_student_import_job_issue_severity CHECK (severity IN ('INFO', 'WARNING', 'ERROR', 'BLOCKING')),

    CONSTRAINT fk_student_import_job_issue_job FOREIGN KEY (student_import_job_id) REFERENCES student_import_job (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_student_import_job_issue_job ON student_import_job_issue (student_import_job_id);

-- ---------------------------------------------------------------------------
-- student_import_row : une ligne de données du CSV, normalisée (colonnes
-- typées explicites, pas de JSON brut — minimisation, rapport §7.3). Supprimée
-- en CASCADE avec le job.
-- ---------------------------------------------------------------------------
CREATE TABLE student_import_row (
    id                            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id                     BINARY(16)      NOT NULL,
    student_import_job_id         BIGINT UNSIGNED NOT NULL,
    `row_number`                  INT UNSIGNED    NOT NULL,   -- numéro de ligne dans le fichier (en-tête = 1) ; `row_number` est réservé en MySQL 8
    input_last_name               VARCHAR(120)    NULL,       -- valeurs normalisées (trim, casse)
    input_first_name              VARCHAR(120)    NULL,
    input_email                   VARCHAR(320)    NULL,
    input_phone                   VARCHAR(32)     NULL,
    input_formation_code          VARCHAR(80)     NULL,
    input_class_code              VARCHAR(80)     NULL,
    input_academic_year           VARCHAR(40)     NULL,
    input_student_number          VARCHAR(60)     NULL,       -- NULL si à générer à la confirmation
    input_birth_date              DATE            NULL,
    input_work_study              BOOLEAN         NULL,
    input_company_name            VARCHAR(191)    NULL,
    row_status                    VARCHAR(12)     NOT NULL,   -- VALID | WARNING | ERROR
    planned_action                VARCHAR(28)     NOT NULL,   -- CREATE_ACCOUNT_AND_ENROLL | ENROLL_EXISTING | UPDATE_PROFILE | TRANSFER_CLASS | NONE
    resolved_class_public_id      BINARY(16)      NULL,       -- trace de résolution
    resolved_user_public_id       BINARY(16)      NULL,       -- compte existant rapproché
    resolved_enrollment_public_id BINARY(16)      NULL,       -- inscription courante (TRANSFER_CLASS)
    student_number_generated      BOOLEAN         NOT NULL DEFAULT FALSE,
    applied_outcome               VARCHAR(20)     NULL,       -- CREATED | ENROLLED | UPDATED | TRANSFERRED | NOOP (à APPLIED)
    created_at                    TIMESTAMP(6)    NOT NULL,
    version                       BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_student_import_row_public_id UNIQUE (public_id),
    CONSTRAINT uq_student_import_row_number UNIQUE (student_import_job_id, `row_number`),
    CONSTRAINT chk_student_import_row_status CHECK (row_status IN ('VALID', 'WARNING', 'ERROR')),
    CONSTRAINT chk_student_import_row_action CHECK (planned_action IN (
        'CREATE_ACCOUNT_AND_ENROLL', 'ENROLL_EXISTING', 'UPDATE_PROFILE', 'TRANSFER_CLASS', 'NONE')),

    CONSTRAINT fk_student_import_row_job FOREIGN KEY (student_import_job_id) REFERENCES student_import_job (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_student_import_row_job_status ON student_import_row (student_import_job_id, row_status);

-- ---------------------------------------------------------------------------
-- student_import_row_issue : anomalies portées par une ligne. `received_value`
-- conserve la valeur reçue TRONQUÉE d'une cellule fautive — jamais reprise
-- dans l'audit (rapport §7.4, §10). Supprimée en CASCADE avec la ligne.
-- ---------------------------------------------------------------------------
CREATE TABLE student_import_row_issue (
    id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id            BINARY(16)      NOT NULL,
    student_import_row_id BIGINT UNSIGNED NOT NULL,
    severity             VARCHAR(16)     NOT NULL,   -- INFO | WARNING | ERROR | BLOCKING
    column_name          VARCHAR(64)     NULL,
    received_value       VARCHAR(200)    NULL,       -- valeur reçue tronquée (jamais dans l'audit)
    error_code           VARCHAR(80)     NOT NULL,
    message              VARCHAR(500)    NOT NULL,
    suggested_value      VARCHAR(200)    NULL,
    created_at           TIMESTAMP(6)    NOT NULL,
    version              BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_student_import_row_issue_public_id UNIQUE (public_id),
    CONSTRAINT chk_student_import_row_issue_severity CHECK (severity IN ('INFO', 'WARNING', 'ERROR', 'BLOCKING')),

    CONSTRAINT fk_student_import_row_issue_row FOREIGN KEY (student_import_row_id) REFERENCES student_import_row (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_student_import_row_issue_row ON student_import_row_issue (student_import_row_id);

-- ---------------------------------------------------------------------------
-- student_number_sequence : compteur monotone par année de début d'année
-- scolaire, alimenté UNIQUEMENT pendant une confirmation d'import (dans sa
-- transaction, verrou de ligne sur `start_year`) pour générer un numéro
-- `ESIC-{annéeDébut}-{séquence}` quand la colonne CSV `student_number` est
-- absente (rapport §3.2). Aucune clé étrangère. Jamais purgée.
-- ---------------------------------------------------------------------------
CREATE TABLE student_number_sequence (
    start_year INT UNSIGNED NOT NULL PRIMARY KEY,
    next_value INT UNSIGNED NOT NULL,   -- prochaine valeur libre
    updated_at TIMESTAMP(6) NOT NULL,

    CONSTRAINT chk_student_number_sequence_next CHECK (next_value > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
