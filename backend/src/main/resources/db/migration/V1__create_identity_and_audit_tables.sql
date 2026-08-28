-- Socle de persistance de l'identité et de l'audit
-- (docs/04-modele-donnees.md §10 et §24).
--
-- Conventions : clé interne BIGINT UNSIGNED AUTO_INCREMENT, identifiant
-- public UUID (BINARY(16)), suppression RESTRICT par défaut, horodatage
-- UTC, verrouillage optimiste (`version`). Seule exception au RESTRICT :
-- `audit_event.actor_user_id` (ON DELETE SET NULL, cf. §24.2).

CREATE TABLE user_account (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id           BINARY(16)      NOT NULL,
    external_source     VARCHAR(50)     NULL,
    external_id         VARCHAR(191)    NULL,
    email               VARCHAR(254)    NOT NULL,
    password_hash       VARCHAR(255)    NULL,
    first_name          VARCHAR(100)    NOT NULL,
    last_name           VARCHAR(100)    NOT NULL,
    phone               VARCHAR(30)     NULL,
    preferred_time_zone VARCHAR(64)     NULL,
    status              VARCHAR(30)     NOT NULL,
    email_verified_at   TIMESTAMP(6)    NULL,
    last_login_at       TIMESTAMP(6)    NULL,
    suspended_at        TIMESTAMP(6)    NULL,
    suspended_by_id     BIGINT UNSIGNED NULL,
    suspension_reason   VARCHAR(500)    NULL,
    archived_at         TIMESTAMP(6)    NULL,
    external_synced_at  TIMESTAMP(6)    NULL,
    created_at          TIMESTAMP(6)    NOT NULL,
    created_by_id       BIGINT UNSIGNED NULL,
    updated_at          TIMESTAMP(6)    NOT NULL,
    updated_by_id       BIGINT UNSIGNED NULL,
    version             BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_user_account_public_id UNIQUE (public_id),
    CONSTRAINT uq_user_account_email UNIQUE (email),
    CONSTRAINT uq_user_account_external UNIQUE (external_source, external_id),

    CONSTRAINT fk_user_account_suspended_by FOREIGN KEY (suspended_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_account_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_account_updated_by FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_user_account_status ON user_account (status);

CREATE TABLE role (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id   BINARY(16)      NOT NULL,
    code        VARCHAR(50)     NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    description VARCHAR(500)    NULL,
    system_role BOOLEAN         NOT NULL DEFAULT TRUE,
    active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP(6)    NOT NULL,
    updated_at  TIMESTAMP(6)    NOT NULL,
    version     BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_role_public_id UNIQUE (public_id),
    CONSTRAINT uq_role_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_role (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id             BINARY(16)      NOT NULL,
    user_id               BIGINT UNSIGNED NOT NULL,
    role_id               BIGINT UNSIGNED NOT NULL,
    valid_from            TIMESTAMP(6)    NOT NULL,
    valid_until           TIMESTAMP(6)    NULL,
    active                BOOLEAN         NOT NULL DEFAULT TRUE,
    assigned_by_id        BIGINT UNSIGNED NULL,
    assignment_reason     VARCHAR(500)    NULL,
    created_at            TIMESTAMP(6)    NOT NULL,
    version               BIGINT UNSIGNED NOT NULL DEFAULT 0,

    -- Colonne générée : vaut 1 quand l'affectation est active, NULL sinon.
    -- MySQL autorise plusieurs NULL dans un index UNIQUE : la contrainte
    -- ci-dessous n'empêche donc que DEUX affectations actives simultanées
    -- du même rôle au même utilisateur, tout en laissant l'historique des
    -- affectations clôturées libre d'accumuler plusieurs lignes pour le
    -- même couple (user_id, role_id). La cohérence temporelle complète
    -- (chevauchement de périodes, etc.) reste à valider côté service.
    active_assignment_key TINYINT UNSIGNED GENERATED ALWAYS AS (IF(active, 1, NULL)) VIRTUAL,

    CONSTRAINT uq_user_role_public_id UNIQUE (public_id),
    CONSTRAINT uq_user_role_active_assignment UNIQUE (user_id, role_id, active_assignment_key),

    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_role_assigned_by FOREIGN KEY (assigned_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE audit_event (
    id                        BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id                 BINARY(16)      NOT NULL,
    occurred_at                TIMESTAMP(6)    NOT NULL,
    actor_user_id              BIGINT UNSIGNED NULL,
    actor_public_id_snapshot   BINARY(16)      NULL,
    actor_display_snapshot     VARCHAR(191)    NULL,
    actor_role                 VARCHAR(50)     NULL,
    action                      VARCHAR(100)    NOT NULL,
    category                    VARCHAR(50)     NOT NULL,
    resource_type               VARCHAR(100)    NOT NULL,
    resource_public_id          BINARY(16)      NULL,
    result                      VARCHAR(30)     NOT NULL,
    reason                      VARCHAR(1000)   NULL,
    old_values_json             JSON            NULL,
    new_values_json             JSON            NULL,
    correlation_id              BINARY(16)      NULL,
    metadata_json               JSON            NULL,
    version                     BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_audit_event_public_id UNIQUE (public_id),

    -- Seule exception au RESTRICT par défaut (docs/04 §24.2) : l'audit
    -- doit rester lisible même si le compte de l'acteur est supprimé,
    -- grâce aux colonnes de "snapshot" ci-dessus.
    CONSTRAINT fk_audit_event_actor FOREIGN KEY (actor_user_id) REFERENCES user_account (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_audit_event_occurred_at ON audit_event (occurred_at);
CREATE INDEX idx_audit_event_actor_user_id ON audit_event (actor_user_id);
CREATE INDEX idx_audit_event_correlation_id ON audit_event (correlation_id);
CREATE INDEX idx_audit_event_resource ON audit_event (resource_type, resource_public_id);
