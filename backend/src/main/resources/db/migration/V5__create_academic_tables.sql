-- Référentiel académique minimal (docs/04-modele-donnees.md §12).
--
-- Portée : formations (`program`), niveaux (`program_level`), années
-- scolaires (`academic_year`), promotions (`promotion`) et
-- classes/groupes (`class_group`). Les référentiels `academic_year` et
-- `program_level` sont inclus uniquement parce que les clés étrangères de
-- `promotion` et `class_group` l'exigent (docs/04 §12.4, §12.5) ; le
-- domaine n'est pas étendu aux inscriptions, matières ni plannings.
--
-- Conventions identiques à V1/V4 : PK BIGINT UNSIGNED AUTO_INCREMENT,
-- identifiant public UUID (BINARY(16)), suppression RESTRICT, horodatage
-- UTC (TIMESTAMP(6)), verrouillage optimiste (`version`). Colonnes auteur
-- (`*_by_id`) en FK RESTRICT vers `user_account`. Archivage logique
-- (`status` + `archived_*`) : aucune suppression physique. Aucune donnée
-- métier n'est insérée ici.
--
-- Écarts assumés par rapport à docs/04 §12, alignés sur les conventions
-- du socle et sur les modules déjà livrés (identity, organization) :
--   * `program_level` reçoit `public_id`, horodatage, colonnes auteur,
--     archivage et `version` (absents du tableau §12.3) ;
--   * `program`/`promotion`/`class_group` reçoivent les colonnes auteur
--     et d'archivage complètes (`*_by_id`, `archive_reason`) ;
--   * `promotion` reçoit `start_date`/`end_date` optionnelles pour
--     permettre la validation de sa période (ajustement demandé) ;
--   * les colonnes `external_source`/`external_id` de §12.2/§12.5 ne sont
--     pas reprises (pas de synchronisation externe dans ce lot).

CREATE TABLE academic_year (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id      BINARY(16)      NOT NULL,
    code           VARCHAR(30)     NOT NULL,
    name           VARCHAR(100)    NOT NULL,
    start_date     DATE            NOT NULL,
    end_date       DATE            NOT NULL,
    status         VARCHAR(30)     NOT NULL,
    archived_at    TIMESTAMP(6)    NULL,
    archived_by_id BIGINT UNSIGNED NULL,
    archive_reason VARCHAR(500)    NULL,
    created_at     TIMESTAMP(6)    NOT NULL,
    created_by_id  BIGINT UNSIGNED NULL,
    updated_at     TIMESTAMP(6)    NOT NULL,
    updated_by_id  BIGINT UNSIGNED NULL,
    version        BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_academic_year_public_id UNIQUE (public_id),
    CONSTRAINT uq_academic_year_code UNIQUE (code),
    CONSTRAINT chk_academic_year_period CHECK (end_date > start_date),

    CONSTRAINT fk_academic_year_archived_by FOREIGN KEY (archived_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_academic_year_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_academic_year_updated_by FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_academic_year_status ON academic_year (status);

CREATE TABLE program (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id      BINARY(16)      NOT NULL,
    code           VARCHAR(50)     NOT NULL,
    name           VARCHAR(191)    NOT NULL,
    program_type   VARCHAR(50)     NOT NULL,
    description    TEXT            NULL,
    status         VARCHAR(30)     NOT NULL,
    archived_at    TIMESTAMP(6)    NULL,
    archived_by_id BIGINT UNSIGNED NULL,
    archive_reason VARCHAR(500)    NULL,
    created_at     TIMESTAMP(6)    NOT NULL,
    created_by_id  BIGINT UNSIGNED NULL,
    updated_at     TIMESTAMP(6)    NOT NULL,
    updated_by_id  BIGINT UNSIGNED NULL,
    version        BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_program_public_id UNIQUE (public_id),
    CONSTRAINT uq_program_code UNIQUE (code),

    CONSTRAINT fk_program_archived_by FOREIGN KEY (archived_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_program_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_program_updated_by FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_program_status ON program (status);

CREATE TABLE program_level (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id       BINARY(16)      NOT NULL,
    program_id      BIGINT UNSIGNED NOT NULL,
    code            VARCHAR(50)     NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    sequence_number SMALLINT        NOT NULL,
    status          VARCHAR(30)     NOT NULL,
    archived_at     TIMESTAMP(6)    NULL,
    archived_by_id  BIGINT UNSIGNED NULL,
    archive_reason  VARCHAR(500)    NULL,
    created_at      TIMESTAMP(6)    NOT NULL,
    created_by_id   BIGINT UNSIGNED NULL,
    updated_at      TIMESTAMP(6)    NOT NULL,
    updated_by_id   BIGINT UNSIGNED NULL,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_program_level_public_id UNIQUE (public_id),
    -- Code unique dans le périmètre d'une formation (docs/04 §12.3).
    CONSTRAINT uq_program_level_program_code UNIQUE (program_id, code),

    CONSTRAINT fk_program_level_program FOREIGN KEY (program_id) REFERENCES program (id) ON DELETE RESTRICT,
    CONSTRAINT fk_program_level_archived_by FOREIGN KEY (archived_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_program_level_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_program_level_updated_by FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_program_level_program ON program_level (program_id);
CREATE INDEX idx_program_level_status ON program_level (status);

CREATE TABLE promotion (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id        BINARY(16)      NOT NULL,
    program_id       BIGINT UNSIGNED NOT NULL,
    academic_year_id BIGINT UNSIGNED NOT NULL,
    code             VARCHAR(80)     NOT NULL,
    name             VARCHAR(191)    NOT NULL,
    start_date       DATE            NULL,
    end_date         DATE            NULL,
    status           VARCHAR(30)     NOT NULL,
    archived_at      TIMESTAMP(6)    NULL,
    archived_by_id   BIGINT UNSIGNED NULL,
    archive_reason   VARCHAR(500)    NULL,
    created_at       TIMESTAMP(6)    NOT NULL,
    created_by_id    BIGINT UNSIGNED NULL,
    updated_at       TIMESTAMP(6)    NOT NULL,
    updated_by_id    BIGINT UNSIGNED NULL,
    version          BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_promotion_public_id UNIQUE (public_id),
    -- Unicité (formation, année scolaire, code) (docs/04 §12.4, §28.1).
    CONSTRAINT uq_promotion_program_year_code UNIQUE (program_id, academic_year_id, code),
    CONSTRAINT chk_promotion_period CHECK (end_date IS NULL OR start_date IS NULL OR end_date > start_date),

    CONSTRAINT fk_promotion_program FOREIGN KEY (program_id) REFERENCES program (id) ON DELETE RESTRICT,
    CONSTRAINT fk_promotion_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_year (id) ON DELETE RESTRICT,
    CONSTRAINT fk_promotion_archived_by FOREIGN KEY (archived_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_promotion_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_promotion_updated_by FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_promotion_program ON promotion (program_id);
CREATE INDEX idx_promotion_academic_year ON promotion (academic_year_id);
CREATE INDEX idx_promotion_status ON promotion (status);

CREATE TABLE class_group (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id        BINARY(16)      NOT NULL,
    promotion_id     BIGINT UNSIGNED NOT NULL,
    program_level_id BIGINT UNSIGNED NOT NULL,
    -- Rattachement au site : valeur technique uniquement (le module
    -- `academic` n'importe jamais `organization.internal`, il ne partage
    -- pas d'entité JPA avec `organization` — décision D4). La cohérence
    -- est garantie par cette FK + le port `organization.SiteDirectory`.
    site_id          BIGINT UNSIGNED NOT NULL,
    code             VARCHAR(80)     NOT NULL,
    name             VARCHAR(191)    NOT NULL,
    capacity         INT UNSIGNED    NULL,
    status           VARCHAR(30)     NOT NULL,
    archived_at      TIMESTAMP(6)    NULL,
    archived_by_id   BIGINT UNSIGNED NULL,
    archive_reason   VARCHAR(500)    NULL,
    created_at       TIMESTAMP(6)    NOT NULL,
    created_by_id    BIGINT UNSIGNED NULL,
    updated_at       TIMESTAMP(6)    NOT NULL,
    updated_by_id    BIGINT UNSIGNED NULL,
    version          BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_class_group_public_id UNIQUE (public_id),
    -- Unicité de la classe dans la promotion (docs/04 §12.5, §28.1).
    CONSTRAINT uq_class_group_promotion_code UNIQUE (promotion_id, code),
    CONSTRAINT chk_class_group_capacity CHECK (capacity IS NULL OR capacity > 0),

    CONSTRAINT fk_class_group_promotion FOREIGN KEY (promotion_id) REFERENCES promotion (id) ON DELETE RESTRICT,
    CONSTRAINT fk_class_group_program_level FOREIGN KEY (program_level_id) REFERENCES program_level (id) ON DELETE RESTRICT,
    CONSTRAINT fk_class_group_site FOREIGN KEY (site_id) REFERENCES site (id) ON DELETE RESTRICT,
    CONSTRAINT fk_class_group_archived_by FOREIGN KEY (archived_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_class_group_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_class_group_updated_by FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_class_group_promotion ON class_group (promotion_id);
CREATE INDEX idx_class_group_program_level ON class_group (program_level_id);
CREATE INDEX idx_class_group_site ON class_group (site_id);
CREATE INDEX idx_class_group_status ON class_group (status);
