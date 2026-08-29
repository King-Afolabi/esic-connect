-- Rythmes d'alternance (docs/02-cahier-des-charges.md §8 ; docs/03-architecture.md §7.4 ;
-- docs/04-modele-donnees.md §14 ; backlog EP-07 / US-060 à US-063 ;
-- sprint T-J1-033).
--
-- Portée du lot : modèles réutilisables de rythme (`work_study_pattern`),
-- affectation historisée d'un rythme à une classe
-- (`class_work_study_pattern`), exceptions individuelles de calendrier
-- (`student_schedule_exception`). Ce lot ne calcule PAS l'assiduité : les
-- modules `planning`, `coursesession` et `attendance` n'existent pas
-- encore. Il ne fait que résoudre le contexte attendu SCHOOL / COMPANY /
-- UNKNOWN pour une classe (ou une inscription) et une date.
--
-- Conventions identiques à V1/V4/V5/V6/V7 : PK BIGINT UNSIGNED
-- AUTO_INCREMENT, identifiant public UUID (BINARY(16)), suppression
-- RESTRICT, horodatage UTC (TIMESTAMP(6)), verrouillage optimiste
-- (`version`), colonnes auteur (`*_by_id`) en FK RESTRICT vers
-- `user_account`. Aucune suppression physique : une entité retirée de
-- l'usage courant passe en statut ARCHIVED / CLOSED / CANCELLED et son
-- historique est conservé. Aucune donnée métier de référence n'est
-- insérée ici (les trois rythmes MVP sont créés par l'API ou les
-- fixtures de tests).
--
-- `class_work_study_pattern.class_group_id` est une valeur technique (FK
-- SQL vers `class_group`) : le module `alternation` n'importe jamais
-- `academic.internal` et ne partage aucune entité JPA avec `academic` ;
-- la cohérence repose sur cette FK et sur le port
-- `academic.ClassGroupDirectory`. De même,
-- `student_schedule_exception.enrollment_id` est une valeur technique (FK
-- SQL vers `enrollment`), résolue via le nouveau port
-- `enrollment.EnrollmentDirectory`.

-- ---------------------------------------------------------------------------
-- work_study_pattern : modèle réutilisable de rythme (docs/04 §14.1).
-- `configuration_json` est validé par un composant métier pur
-- (AlternationConfigParser) : aucun JSON inconnu ou incohérent n'est
-- accepté silencieusement. Le contrat exact de chaque `pattern_type` est
-- documenté dans docs/CURRENT-STATE.md et dans les réponses d'erreur de
-- l'API. `cycle_start_date` n'est PAS porté ici : il est propre à chaque
-- affectation de classe (`class_work_study_pattern`).
-- ---------------------------------------------------------------------------
CREATE TABLE work_study_pattern (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id          BINARY(16)      NOT NULL,
    code               VARCHAR(80)     NOT NULL,
    name               VARCHAR(191)    NOT NULL,
    description        VARCHAR(500)    NULL,
    pattern_type       VARCHAR(50)     NOT NULL,  -- THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY | ONE_WEEK_SCHOOL_OUT_OF_FOUR | TWO_WEEKS_SCHOOL_OUT_OF_FOUR | CUSTOM
    -- Nombre de semaines du cycle ; normalisé selon le type. `INT` (et non
    -- `SMALLINT` comme esquissé docs/04 §14.1) pour correspondre au type
    -- `Integer` de l'entité JPA et à la validation de schéma Hibernate.
    cycle_length_weeks INT             NULL,
    configuration_json JSON            NOT NULL,
    status             VARCHAR(30)     NOT NULL,  -- ACTIVE | ARCHIVED
    archived_at        TIMESTAMP(6)    NULL,
    archived_by_id     BIGINT UNSIGNED NULL,
    archive_reason     VARCHAR(500)    NULL,
    created_at         TIMESTAMP(6)    NOT NULL,
    created_by_id      BIGINT UNSIGNED NULL,
    updated_at         TIMESTAMP(6)    NOT NULL,
    updated_by_id      BIGINT UNSIGNED NULL,
    version            BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_work_study_pattern_public_id UNIQUE (public_id),
    -- Code fonctionnel unique et immuable après création (docs/04 §14.1).
    CONSTRAINT uq_work_study_pattern_code UNIQUE (code),
    CONSTRAINT chk_work_study_pattern_cycle CHECK (cycle_length_weeks IS NULL OR cycle_length_weeks > 0),

    CONSTRAINT fk_work_study_pattern_archived_by FOREIGN KEY (archived_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_work_study_pattern_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_work_study_pattern_updated_by FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_work_study_pattern_status ON work_study_pattern (status);
CREATE INDEX idx_work_study_pattern_type ON work_study_pattern (pattern_type);

-- ---------------------------------------------------------------------------
-- class_work_study_pattern : affectation historisée d'un rythme à une
-- classe (docs/04 §14.2). Une classe peut changer de rythme au fil du
-- temps : l'ancienne affectation est clôturée (status = CLOSED,
-- valid_until renseigné), jamais supprimée ; la nouvelle affectation ne
-- remplace pas la précédente. Bornes de validité inclusives.
--
-- Invariants (pré-contrôle applicatif + garde SQL) :
--   * valid_until >= valid_from (CHECK) ;
--   * aucun chevauchement de périodes pour une même classe — deux
--     périodes adjacentes ([a,b] puis [b+1,c]) sont autorisées, mais
--     elles ne doivent partager aucun jour ; contrôle applicatif
--     complet ;
--   * au plus une affectation ACTIVE « ouverte » (valid_until NULL) par
--     classe — colonne générée `active_open_key` + UNIQUE : c'est la
--     course concurrente réaliste (poser un nouveau rythme courant sans
--     avoir clôturé le précédent), traduite en 409, jamais 500. Une
--     clôture libère immédiatement le créneau.
-- ---------------------------------------------------------------------------
CREATE TABLE class_work_study_pattern (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id             BINARY(16)      NOT NULL,
    class_group_id        BIGINT UNSIGNED NOT NULL,
    work_study_pattern_id BIGINT UNSIGNED NOT NULL,
    cycle_start_date      DATE            NOT NULL,  -- ancre du cycle : jour de la semaine 1 du rythme
    valid_from            DATE            NOT NULL,
    valid_until           DATE            NULL,      -- inclusif ; NULL = affectation ouverte
    status                VARCHAR(30)     NOT NULL,  -- ACTIVE | CLOSED
    close_reason          VARCHAR(500)    NULL,

    -- Colonne générée : vaut `class_group_id` tant que l'affectation est
    -- ACTIVE et ouverte, NULL sinon. MySQL autorisant plusieurs NULL dans
    -- un index UNIQUE, la contrainte n'interdit qu'une seule affectation
    -- ACTIVE ouverte par classe (cf. V6 `active_primary_key`, V7
    -- `active_student_key`).
    active_open_key       BIGINT UNSIGNED GENERATED ALWAYS AS (
        IF(status = 'ACTIVE' AND valid_until IS NULL, class_group_id, NULL)) VIRTUAL,

    created_at            TIMESTAMP(6)    NOT NULL,
    created_by_id         BIGINT UNSIGNED NULL,
    updated_at            TIMESTAMP(6)    NOT NULL,
    updated_by_id         BIGINT UNSIGNED NULL,
    version               BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_class_work_study_pattern_public_id UNIQUE (public_id),
    CONSTRAINT uq_class_work_study_pattern_active_open UNIQUE (active_open_key),
    CONSTRAINT chk_class_work_study_pattern_period CHECK (valid_until IS NULL OR valid_until >= valid_from),

    CONSTRAINT fk_class_work_study_pattern_class_group FOREIGN KEY (class_group_id) REFERENCES class_group (id) ON DELETE RESTRICT,
    CONSTRAINT fk_class_work_study_pattern_pattern FOREIGN KEY (work_study_pattern_id) REFERENCES work_study_pattern (id) ON DELETE RESTRICT,
    CONSTRAINT fk_class_work_study_pattern_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_class_work_study_pattern_updated_by FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_class_work_study_pattern_class_group ON class_work_study_pattern (class_group_id);
CREATE INDEX idx_class_work_study_pattern_pattern ON class_work_study_pattern (work_study_pattern_id);
CREATE INDEX idx_class_work_study_pattern_status ON class_work_study_pattern (status);
CREATE INDEX idx_class_work_study_pattern_window ON class_work_study_pattern (class_group_id, valid_from, valid_until);

-- ---------------------------------------------------------------------------
-- student_schedule_exception : exception individuelle de calendrier
-- (docs/04 §14.3, docs/02 §8.3). Rattachée à une inscription. Types
-- retenus (minimum nécessaire aux cas explicitement décrits, aucune
-- valeur arbitraire ajoutée) :
--   * REMOTE_ALLOWED           — autorisation de suivre à distance ;
--   * ON_SITE_REQUIRED         — présence exceptionnelle à l'école ;
--   * COMPANY_PERIOD           — période en entreprise ;
--   * VALIDATED_UNAVAILABILITY — indisponibilité validée.
-- Statut : ACTIVE | CANCELLED (annulation sans suppression, historique
-- conservé). `start_at` / `end_at` sont des instants UTC ; `time_zone_id`
-- est le fuseau IANA de saisie, conservé pour l'affichage et la
-- projection sur un jour civil.
-- ---------------------------------------------------------------------------
CREATE TABLE student_schedule_exception (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id      BINARY(16)      NOT NULL,
    enrollment_id  BIGINT UNSIGNED NOT NULL,
    exception_type VARCHAR(50)     NOT NULL,
    start_at       TIMESTAMP(6)    NOT NULL,
    end_at         TIMESTAMP(6)    NOT NULL,
    time_zone_id   VARCHAR(64)     NOT NULL,
    reason         VARCHAR(500)    NOT NULL,
    status         VARCHAR(30)     NOT NULL,  -- ACTIVE | CANCELLED
    cancel_reason  VARCHAR(500)    NULL,
    created_at     TIMESTAMP(6)    NOT NULL,
    created_by_id  BIGINT UNSIGNED NULL,
    updated_at     TIMESTAMP(6)    NOT NULL,
    updated_by_id  BIGINT UNSIGNED NULL,
    version        BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_student_schedule_exception_public_id UNIQUE (public_id),
    CONSTRAINT chk_student_schedule_exception_period CHECK (end_at > start_at),

    CONSTRAINT fk_student_schedule_exception_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollment (id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_schedule_exception_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_schedule_exception_updated_by FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_student_schedule_exception_enrollment ON student_schedule_exception (enrollment_id);
CREATE INDEX idx_student_schedule_exception_status ON student_schedule_exception (status);
CREATE INDEX idx_student_schedule_exception_window ON student_schedule_exception (enrollment_id, start_at, end_at);
