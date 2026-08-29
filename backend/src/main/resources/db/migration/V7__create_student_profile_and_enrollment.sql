-- Inscriptions historiques (docs/02-cahier-des-charges.md §7.6, §9.2, §13 ;
-- docs/04-modele-donnees.md §11.1 et §13 ; RG-006, RG-012, RG-022, RG-023 ;
-- AC-006 ; sprint T-J1-032 / backlog US-053).
--
-- Portée : profil apprenant (`student_profile`) et inscription
-- (`enrollment`), avec conservation de l'historique lors d'un changement
-- de classe (§13.2). N'aborde ni l'import CSV des apprenants, ni les
-- rythmes d'alternance, ni les apprenants provisoires, ni Angular.
--
-- Conventions identiques à V1/V4/V5/V6 : PK BIGINT UNSIGNED
-- AUTO_INCREMENT, identifiant public UUID (BINARY(16)), suppression
-- RESTRICT, horodatage UTC (TIMESTAMP(6)), verrouillage optimiste
-- (`version`), colonnes auteur en FK RESTRICT vers `user_account`. Aucune
-- donnée métier n'est insérée ici.
--
-- `student_profile.user_id` est une valeur technique (FK SQL vers
-- `user_account`) : le module `enrollment` n'importe jamais
-- `identity.internal` et ne partage aucune entité JPA avec `identity` ;
-- la cohérence repose sur cette FK et sur le port `identity.UserDirectory`.
-- De même, `enrollment.class_group_id` / `academic_year_id` sont des
-- valeurs techniques (FK SQL vers `class_group` / `academic_year`),
-- résolues via le port `academic.ClassGroupDirectory`.

CREATE TABLE student_profile (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id      BINARY(16)      NOT NULL,
    user_id        BIGINT UNSIGNED NOT NULL,
    student_number VARCHAR(50)     NOT NULL,
    birth_date     DATE            NULL,
    work_study     BOOLEAN         NOT NULL DEFAULT FALSE,
    company_name   VARCHAR(191)    NULL,
    status         VARCHAR(30)     NOT NULL,  -- ACTIVE | ARCHIVED
    created_at     TIMESTAMP(6)    NOT NULL,
    created_by_id  BIGINT UNSIGNED NULL,
    updated_at     TIMESTAMP(6)    NOT NULL,
    updated_by_id  BIGINT UNSIGNED NULL,
    version        BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_student_profile_public_id UNIQUE (public_id),
    -- Un seul profil apprenant par compte (docs/04 §11.1).
    CONSTRAINT uq_student_profile_user UNIQUE (user_id),
    -- Numéro étudiant unique lorsqu'il est attribué (docs/04 §3.5).
    CONSTRAINT uq_student_profile_student_number UNIQUE (student_number),

    CONSTRAINT fk_student_profile_user FOREIGN KEY (user_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_profile_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_profile_updated_by FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_student_profile_status ON student_profile (status);

CREATE TABLE enrollment (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id             BINARY(16)      NOT NULL,
    student_profile_id    BIGINT UNSIGNED NOT NULL,
    class_group_id        BIGINT UNSIGNED NOT NULL,
    academic_year_id      BIGINT UNSIGNED NOT NULL,
    start_date            DATE            NOT NULL,
    end_date              DATE            NULL,       -- renseigné à la clôture (§13.2)
    status                VARCHAR(30)     NOT NULL,   -- PENDING|ACTIVE|COMPLETED|TRANSFERRED|WITHDRAWN|SUSPENDED|ARCHIVED
    enrollment_source     VARCHAR(50)     NOT NULL,   -- MANUAL | CLASS_TRANSFER
    change_reason         VARCHAR(500)    NULL,
    previous_enrollment_id BIGINT UNSIGNED NULL,      -- inscription clôturée dont celle-ci prend la suite

    -- Unicité d'une inscription ACTIVE par apprenant et par période
    -- (docs/04 §13.3, RG-012). MySQL n'a pas d'index partiel : deux
    -- colonnes virtuelles ne portent la valeur que pour une inscription
    -- ACTIVE (NULL sinon), et une UNIQUE composite ne contraint donc que
    -- les lignes ACTIVE (cf. V6 `active_primary_key`). Une clôture
    -- (status != ACTIVE) libère immédiatement le créneau.
    active_student_key    BIGINT UNSIGNED GENERATED ALWAYS AS (
        IF(status = 'ACTIVE', student_profile_id, NULL)) VIRTUAL,
    active_year_key       BIGINT UNSIGNED GENERATED ALWAYS AS (
        IF(status = 'ACTIVE', academic_year_id, NULL)) VIRTUAL,

    created_at            TIMESTAMP(6)    NOT NULL,
    created_by_id         BIGINT UNSIGNED NULL,
    updated_at            TIMESTAMP(6)    NOT NULL,
    updated_by_id         BIGINT UNSIGNED NULL,
    version               BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_enrollment_public_id UNIQUE (public_id),
    CONSTRAINT uq_enrollment_active_per_year UNIQUE (active_student_key, active_year_key),
    CONSTRAINT chk_enrollment_period CHECK (end_date IS NULL OR end_date >= start_date),

    CONSTRAINT fk_enrollment_student_profile FOREIGN KEY (student_profile_id) REFERENCES student_profile (id) ON DELETE RESTRICT,
    CONSTRAINT fk_enrollment_class_group FOREIGN KEY (class_group_id) REFERENCES class_group (id) ON DELETE RESTRICT,
    CONSTRAINT fk_enrollment_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_year (id) ON DELETE RESTRICT,
    CONSTRAINT fk_enrollment_previous FOREIGN KEY (previous_enrollment_id) REFERENCES enrollment (id) ON DELETE RESTRICT,
    CONSTRAINT fk_enrollment_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_enrollment_updated_by FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_enrollment_student_profile ON enrollment (student_profile_id);
CREATE INDEX idx_enrollment_class_group ON enrollment (class_group_id);
CREATE INDEX idx_enrollment_academic_year ON enrollment (academic_year_id);
CREATE INDEX idx_enrollment_status ON enrollment (status);
CREATE INDEX idx_enrollment_previous ON enrollment (previous_enrollment_id);
