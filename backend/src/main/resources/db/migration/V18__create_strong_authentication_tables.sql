-- Authentification forte : second facteur TOTP, codes de récupération,
-- passkeys WebAuthn et appareils de confiance
-- (docs/02-cahier-des-charges.md §17.2, §17.3, §17.4, §17.7 ;
-- EF-AUTH-006..010, EF-AUTH-013, EF-AUTH-015 ; RG-007, RG-008, RG-009).
--
-- Conventions identiques à V1 / V3 / V17 : PK BIGINT UNSIGNED
-- AUTO_INCREMENT, identifiant public UUID (BINARY(16)), suppression
-- RESTRICT, horodatage UTC, verrouillage optimiste (`version`).
--
-- Aucune donnée biométrique n'apparaît ici et ne peut y apparaître : une
-- passkey ne transmet au serveur qu'une clé publique et un compteur de
-- signature (RG-091, AC-020). Le secret TOTP est chiffré au repos, jamais
-- stocké en clair.

-- ---------------------------------------------------------------------
-- Second facteur TOTP (RFC 6238)
-- ---------------------------------------------------------------------
CREATE TABLE mfa_credential (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id         BINARY(16)      NOT NULL,
    user_id           BIGINT UNSIGNED NOT NULL,
    -- Secret partagé CHIFFRÉ (AES-GCM), jamais en clair, jamais journalisé.
    secret_cipher     VARCHAR(512)    NOT NULL,
    status            VARCHAR(30)     NOT NULL,
    confirmed_at      TIMESTAMP(6)    NULL,
    revoked_at        TIMESTAMP(6)    NULL,
    -- Dernier pas de temps consommé : un même code TOTP ne peut pas être
    -- rejoué pendant sa fenêtre de validité (docs/02 §16.10).
    last_used_step    BIGINT          NULL,
    created_at        TIMESTAMP(6)    NOT NULL,
    updated_at        TIMESTAMP(6)    NOT NULL,
    version           BIGINT UNSIGNED NOT NULL DEFAULT 0,

    -- Vaut 1 tant que le facteur est PENDING ou ACTIVE, NULL sinon :
    -- un compte n'a qu'un facteur vivant à la fois, l'historique révoqué
    -- reste consultable (MySQL autorise plusieurs NULL dans un UNIQUE).
    active_mfa_key    TINYINT UNSIGNED GENERATED ALWAYS AS
                          (IF(status IN ('PENDING', 'ACTIVE'), 1, NULL)) VIRTUAL,

    CONSTRAINT uq_mfa_credential_public_id UNIQUE (public_id),
    CONSTRAINT uq_mfa_credential_active UNIQUE (user_id, active_mfa_key),
    CONSTRAINT ck_mfa_credential_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED')),
    CONSTRAINT fk_mfa_credential_user FOREIGN KEY (user_id)
        REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_mfa_credential_user_id ON mfa_credential (user_id);

-- ---------------------------------------------------------------------
-- Codes de récupération (EF-AUTH-009)
-- ---------------------------------------------------------------------
-- Le code brut n'est jamais stocké : seule son empreinte SHA-256 l'est,
-- comme pour les jetons d'invitation (V3) et de réinitialisation (V17).
CREATE TABLE mfa_recovery_code (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id         BINARY(16)      NOT NULL,
    user_id           BIGINT UNSIGNED NOT NULL,
    code_hash         VARCHAR(255)    NOT NULL,
    status            VARCHAR(30)     NOT NULL,
    consumed_at       TIMESTAMP(6)    NULL,
    created_at        TIMESTAMP(6)    NOT NULL,
    version           BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_mfa_recovery_code_public_id UNIQUE (public_id),
    CONSTRAINT uq_mfa_recovery_code_hash UNIQUE (code_hash),
    CONSTRAINT ck_mfa_recovery_code_status
        CHECK (status IN ('ACTIVE', 'CONSUMED', 'REVOKED')),
    CONSTRAINT fk_mfa_recovery_code_user FOREIGN KEY (user_id)
        REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_mfa_recovery_code_user_status ON mfa_recovery_code (user_id, status);

-- ---------------------------------------------------------------------
-- Passkeys WebAuthn (EF-AUTH-006, EF-AUTH-007 ; AC-020)
-- ---------------------------------------------------------------------
-- Le serveur ne reçoit qu'une clé PUBLIQUE et un compteur de signature.
-- Aucune empreinte digitale, aucun modèle facial : la vérification de
-- l'utilisateur reste sur le terminal (RG-091).
CREATE TABLE webauthn_credential (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id         BINARY(16)      NOT NULL,
    user_id           BIGINT UNSIGNED NOT NULL,
    -- Identifiant de justificatif produit par l'authentificateur, encodé
    -- en base64url : sert de clé de recherche à la connexion.
    credential_id     VARCHAR(512)    NOT NULL,
    -- Enregistrement sérialisé (CBOR) de la clé publique et de ses
    -- attributs, tel que produit par la bibliothèque de vérification.
    -- VARBINARY et non BLOB : la taille réelle est de quelques centaines
    -- d'octets (AAGUID + identifiant + clé COSE), et un type borné permet
    -- à Hibernate de valider le schéma sans ambiguïté de dialecte.
    credential_record VARBINARY(2048) NOT NULL,
    signature_count   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    label             VARCHAR(120)    NOT NULL,
    status            VARCHAR(30)     NOT NULL,
    last_used_at      TIMESTAMP(6)    NULL,
    revoked_at        TIMESTAMP(6)    NULL,
    created_at        TIMESTAMP(6)    NOT NULL,
    updated_at        TIMESTAMP(6)    NOT NULL,
    version           BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_webauthn_credential_public_id UNIQUE (public_id),
    CONSTRAINT uq_webauthn_credential_credential_id UNIQUE (credential_id),
    CONSTRAINT ck_webauthn_credential_status
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT fk_webauthn_credential_user FOREIGN KEY (user_id)
        REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_webauthn_credential_user_status ON webauthn_credential (user_id, status);

-- ---------------------------------------------------------------------
-- Appareils de confiance (EF-AUTH-013) et authentification adaptative
-- ---------------------------------------------------------------------
-- L'appareil n'est identifié que par une EMPREINTE : ni user-agent en
-- clair, ni adresse IP (RG-094, docs/02 §16.7). L'empreinte suffit à
-- reconnaître un retour sur le même terminal, elle ne permet pas de
-- reconstituer le terminal.
CREATE TABLE trusted_device (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id         BINARY(16)      NOT NULL,
    user_id           BIGINT UNSIGNED NOT NULL,
    device_hash       VARCHAR(64)     NOT NULL,
    label             VARCHAR(120)    NOT NULL,
    status            VARCHAR(30)     NOT NULL,
    first_seen_at     TIMESTAMP(6)    NOT NULL,
    last_seen_at      TIMESTAMP(6)    NOT NULL,
    expires_at        TIMESTAMP(6)    NOT NULL,
    revoked_at        TIMESTAMP(6)    NULL,
    created_at        TIMESTAMP(6)    NOT NULL,
    updated_at        TIMESTAMP(6)    NOT NULL,
    version           BIGINT UNSIGNED NOT NULL DEFAULT 0,

    active_device_key TINYINT UNSIGNED GENERATED ALWAYS AS
                          (IF(status = 'ACTIVE', 1, NULL)) VIRTUAL,

    CONSTRAINT uq_trusted_device_public_id UNIQUE (public_id),
    CONSTRAINT uq_trusted_device_active UNIQUE (user_id, device_hash, active_device_key),
    CONSTRAINT ck_trusted_device_status
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT fk_trusted_device_user FOREIGN KEY (user_id)
        REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_trusted_device_user_status ON trusted_device (user_id, status);
