-- Réinitialisation de mot de passe et révocation des identifiants
-- (docs/02-cahier-des-charges.md §17.7 et §17.8 ; EF-AUTH-005,
-- EF-AUTH-014 ; RG-010).
--
-- Le jeton brut n'est JAMAIS stocké : seule son empreinte SHA-256 (hex)
-- l'est, comme pour `account_invitation` (V3). Une seule demande PENDING
-- peut exister par compte à un instant donné ; l'historique des demandes
-- consommées ou révoquées reste consultable pour l'audit.
--
-- Conventions identiques à V1 / V3 : PK BIGINT UNSIGNED AUTO_INCREMENT,
-- identifiant public UUID (BINARY(16)), suppression RESTRICT, horodatage
-- UTC, verrouillage optimiste (`version`).

CREATE TABLE password_reset_token (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id             BINARY(16)      NOT NULL,
    user_id               BIGINT UNSIGNED NOT NULL,
    token_hash            VARCHAR(255)    NOT NULL,
    status                VARCHAR(30)     NOT NULL,
    expires_at            TIMESTAMP(6)    NOT NULL,
    consumed_at           TIMESTAMP(6)    NULL,
    revoked_at            TIMESTAMP(6)    NULL,
    created_at            TIMESTAMP(6)    NOT NULL,
    version               BIGINT UNSIGNED NOT NULL DEFAULT 0,

    -- Même mécanisme que V3 : vaut 1 tant que la demande est PENDING,
    -- NULL sinon. MySQL autorisant plusieurs NULL dans un index UNIQUE,
    -- la contrainte garantit une seule demande active par compte sans
    -- gêner l'historique.
    active_reset_key      TINYINT UNSIGNED GENERATED ALWAYS AS (IF(status = 'PENDING', 1, NULL)) VIRTUAL,

    CONSTRAINT uq_password_reset_token_public_id UNIQUE (public_id),
    CONSTRAINT uq_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT uq_password_reset_token_active UNIQUE (user_id, active_reset_key),

    CONSTRAINT ck_password_reset_token_status
        CHECK (status IN ('PENDING', 'CONSUMED', 'REVOKED', 'EXPIRED')),

    CONSTRAINT fk_password_reset_token_user FOREIGN KEY (user_id)
        REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_password_reset_token_user_id ON password_reset_token (user_id);
CREATE INDEX idx_password_reset_token_expires_at ON password_reset_token (expires_at);

-- Révocation globale des jetons d'accès d'un compte.
--
-- L'API est stateless : un JWT valide reste utilisable jusqu'à son
-- expiration, même après un changement de mot de passe. Cette colonne
-- porte l'instant à partir duquel tout jeton émis AVANT est refusé
-- (RG-010, docs/02 §17.7 : « la session est invalidée lors d'une
-- réinitialisation de mot de passe, d'une révocation, d'un incident ou
-- d'une désactivation de compte »).
--
-- Volontairement à la SECONDE près côté comparaison applicative : le
-- claim `iat` d'un JWT est exprimé en secondes. La révocation d'un
-- jeton individuel (déconnexion) passe par Redis, pas par cette colonne.
ALTER TABLE user_account
    ADD COLUMN credentials_invalidated_at TIMESTAMP(6) NULL
        COMMENT 'Tout jeton d''acces emis avant cet instant est refuse (EF-AUTH-014).';
