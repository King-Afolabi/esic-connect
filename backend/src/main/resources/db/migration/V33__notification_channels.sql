-- V33 — Canaux de notification : préférences par catégorie et
-- abonnements à la poussée web (EF-NOTIF-005, EF-NOTIF-006 ; docs/02
-- §21.1, §21.6, §29.3).
--
-- Conventions V1/V15/V31 : PK BIGINT UNSIGNED AUTO_INCREMENT, `public_id`
-- BINARY(16) unique, TIMESTAMP(6) UTC, verrou optimiste (`version`),
-- FK RESTRICT vers `user_account`, ENGINE=InnoDB, utf8mb4_0900_ai_ci.
-- Aucune donnée métier insérée.

-- ---------------------------------------------------------------------
-- Préférences de notification (EF-NOTIF-006)
-- ---------------------------------------------------------------------
--
-- UNE LIGNE PAR EXCEPTION, PAS PAR COMBINAISON. La table ne contient que
-- les choix qui s'écartent du défaut. L'absence de ligne signifie donc
-- « réglage par défaut », et non « rien reçu » : une catégorie ajoutée au
-- sprint suivant est immédiatement active pour tout le monde, sans
-- migration de données et sans que personne ne cesse silencieusement
-- d'être prévenu.
--
-- LE CANAL `IN_APP` N'EST PAS STOCKÉ ICI, et c'est délibéré : le centre
-- de notifications est la trace consultable de ce qui a été adressé à la
-- personne (docs/02 §21.5 — « masquer sans effacer »). Le rendre
-- désactivable transformerait un silence de préférence en trou dans
-- l'historique.
--
-- LA CATÉGORIE `SECURITY` EST REFUSÉE PAR CONTRAINTE : « les
-- notifications critiques de sécurité restent obligatoires » (§21.6).
-- L'écrire dans le schéma, et pas seulement dans le service, évite qu'un
-- futur appel mal contrôlé ne désactive l'alerte de compromission d'un
-- compte.

CREATE TABLE notification_preference (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id  BINARY(16)      NOT NULL,
    user_id    BIGINT UNSIGNED NOT NULL,
    category   VARCHAR(32)     NOT NULL,
    channel    VARCHAR(16)     NOT NULL,
    enabled    BOOLEAN         NOT NULL,
    created_at TIMESTAMP(6)    NOT NULL,
    updated_at TIMESTAMP(6)    NOT NULL,
    version    BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_notification_preference_public_id UNIQUE (public_id),
    CONSTRAINT uq_notification_preference_scope UNIQUE (user_id, category, channel),
    CONSTRAINT chk_notification_preference_category
        CHECK (category IN ('PLANNING', 'SESSION', 'ATTENDANCE', 'JUSTIFICATION', 'CLAIM')),
    CONSTRAINT chk_notification_preference_channel
        CHECK (channel IN ('EMAIL', 'PUSH')),
    CONSTRAINT fk_notification_preference_user FOREIGN KEY (user_id)
        REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_notification_preference_user ON notification_preference (user_id);

-- ---------------------------------------------------------------------
-- Abonnements à la poussée web (EF-NOTIF-005 ; docs/02 §29.3)
-- ---------------------------------------------------------------------
--
-- Un abonnement est produit par le navigateur : une URL de terminaison
-- chez le service de poussée, plus deux clés servant au chiffrement de
-- bout en bout (RFC 8291). L'URL contient un jeton opaque propre à
-- l'appareil : elle est traitée comme un secret — jamais journalisée,
-- jamais renvoyée par l'API, et indexée par empreinte plutôt qu'en clair
-- pour que l'unicité n'oblige pas à indexer la valeur elle-même.
--
-- « Abonnement par appareil, révocable » (§29.3) : la révocation est une
-- date, pas une suppression, afin qu'un abonnement révoqué ne soit pas
-- recréé à l'identique par une page restée ouverte.

CREATE TABLE push_subscription (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id     BINARY(16)      NOT NULL,
    user_id       BIGINT UNSIGNED NOT NULL,
    endpoint      VARCHAR(1024)   NOT NULL,
    endpoint_hash CHAR(64)        NOT NULL,
    -- Clé publique ECDH P-256 de l'abonné et secret d'authentification,
    -- en base64url tels que fournis par le navigateur.
    p256dh_key    VARCHAR(255)    NOT NULL,
    auth_secret   VARCHAR(255)    NOT NULL,
    created_at    TIMESTAMP(6)    NOT NULL,
    last_used_at  TIMESTAMP(6)    NULL,
    revoked_at    TIMESTAMP(6)    NULL,
    version       BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_push_subscription_public_id UNIQUE (public_id),
    -- Un même appareil ne s'abonne qu'une fois : un renouvellement
    -- réutilise la ligne au lieu d'en empiler.
    CONSTRAINT uq_push_subscription_endpoint UNIQUE (endpoint_hash),
    CONSTRAINT fk_push_subscription_user FOREIGN KEY (user_id)
        REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_push_subscription_user ON push_subscription (user_id, revoked_at);
