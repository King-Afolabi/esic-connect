-- Invitation d'activation de compte (docs/04-modele-donnees.md §10.4,
-- cahier §11). Le jeton brut n'est JAMAIS stocké : seule son empreinte
-- SHA-256 (hex) est conservée. Une seule invitation PENDING peut exister
-- par compte à un instant donné ; l'historique des invitations révoquées
-- ou acceptées reste consultable.
--
-- Conventions identiques à V1 : PK BIGINT UNSIGNED AUTO_INCREMENT,
-- identifiant public UUID (BINARY(16)), suppression RESTRICT, horodatage
-- UTC, verrouillage optimiste (`version`).

CREATE TABLE account_invitation (
    id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id            BINARY(16)      NOT NULL,
    user_id              BIGINT UNSIGNED NOT NULL,
    token_hash           VARCHAR(255)    NOT NULL,
    status               VARCHAR(30)     NOT NULL,
    expires_at           TIMESTAMP(6)    NOT NULL,
    used_at              TIMESTAMP(6)    NULL,
    revoked_at           TIMESTAMP(6)    NULL,
    created_at           TIMESTAMP(6)    NOT NULL,
    created_by_id        BIGINT UNSIGNED NULL,
    version              BIGINT UNSIGNED NOT NULL DEFAULT 0,

    -- Colonne générée : vaut 1 tant que l'invitation est PENDING, NULL
    -- sinon. MySQL autorisant plusieurs NULL dans un index UNIQUE, la
    -- contrainte ci-dessous garantit une seule invitation PENDING par
    -- compte tout en laissant l'historique (ACCEPTED / REVOKED) libre.
    active_invitation_key TINYINT UNSIGNED GENERATED ALWAYS AS (IF(status = 'PENDING', 1, NULL)) VIRTUAL,

    CONSTRAINT uq_account_invitation_public_id UNIQUE (public_id),
    CONSTRAINT uq_account_invitation_token_hash UNIQUE (token_hash),
    CONSTRAINT uq_account_invitation_active UNIQUE (user_id, active_invitation_key),

    CONSTRAINT fk_account_invitation_user FOREIGN KEY (user_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_account_invitation_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_account_invitation_user_id ON account_invitation (user_id);
CREATE INDEX idx_account_invitation_status ON account_invitation (status);
CREATE INDEX idx_account_invitation_expires_at ON account_invitation (expires_at);
