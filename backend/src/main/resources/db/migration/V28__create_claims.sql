-- V28 — Réclamations (EF-CLAIM-001 à 004 ; docs/02 §20).
--
-- Une réclamation est un fil rattaché à un objet précis, adressé à un
-- GUICHET (formateur, responsable pédagogique, administration) et non à
-- une personne nommée : le cahier prévoit le transfert de l'un à l'autre
-- (§20.3), et désigner une personne rendrait ce transfert impossible dès
-- qu'elle est absente.
--
-- RG-088 : « une réclamation conserve son historique complet, y compris
-- après réouverture ». Deux tables append-only en découlent —
-- `claim_message` (le fil) et `claim_event` (les décisions : transfert,
-- résolution, réouverture). Rien n'est jamais supprimé ni écrasé.

CREATE TABLE claim (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id             BINARY(16)      NOT NULL,
    version               BIGINT          NOT NULL DEFAULT 0,

    author_user_id        BIGINT UNSIGNED NOT NULL,
    category              VARCHAR(24)     NOT NULL,
    subject               VARCHAR(191)    NOT NULL,
    status                VARCHAR(24)     NOT NULL,
    audience              VARCHAR(32)     NOT NULL,

    -- Objet concerné, facultatif : une réclamation peut porter sur une
    -- séance, une période, ou rien de précis.
    course_session_id     BIGINT UNSIGNED NULL,
    period_start          DATE            NULL,
    period_end            DATE            NULL,

    -- Périmètre de lecture : la classe permet au responsable pédagogique
    -- de ne voir que son périmètre sans recalculer l'inscription.
    class_group_id        BIGINT UNSIGNED NULL,

    closed_at             TIMESTAMP(6)    NULL,
    closed_by_id          BIGINT UNSIGNED NULL,

    created_at            TIMESTAMP(6)    NOT NULL,
    updated_at            TIMESTAMP(6)    NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_claim_public_id (public_id),
    KEY idx_claim_author (author_user_id, created_at),
    KEY idx_claim_audience (audience, status),
    KEY idx_claim_class (class_group_id, status),
    CONSTRAINT fk_claim_author FOREIGN KEY (author_user_id) REFERENCES user_account (id),
    CONSTRAINT fk_claim_session FOREIGN KEY (course_session_id) REFERENCES course_session (id),
    CONSTRAINT fk_claim_class FOREIGN KEY (class_group_id) REFERENCES class_group (id),
    CONSTRAINT chk_claim_category
        CHECK (category IN ('ATTENDANCE', 'JUSTIFICATION', 'SCHEDULE', 'ACCOUNT', 'OTHER')),
    CONSTRAINT chk_claim_status
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'WAITING_FOR_STUDENT', 'TRANSFERRED',
                          'RESOLVED', 'CLOSED', 'REJECTED', 'REOPENED')),
    CONSTRAINT chk_claim_audience
        CHECK (audience IN ('TEACHER', 'PEDAGOGICAL_MANAGER', 'SCHOOL_ADMINISTRATION')),
    CONSTRAINT chk_claim_period
        CHECK (period_end IS NULL OR period_start IS NULL OR period_end >= period_start)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Fil de messages. Append-only : aucune modification, aucune suppression.
-- `author_role` enregistre le rôle EMPLOYÉ au moment du message, pas le
-- rôle courant de l'auteur : relire un fil deux ans plus tard doit
-- montrer qui parlait en quelle qualité.
CREATE TABLE claim_message (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id     BINARY(16)      NOT NULL,
    version       BIGINT          NOT NULL DEFAULT 0,

    claim_id      BIGINT UNSIGNED NOT NULL,
    author_user_id BIGINT UNSIGNED NOT NULL,
    author_role   VARCHAR(32)     NOT NULL,
    body          TEXT            NOT NULL,

    created_at    TIMESTAMP(6)    NOT NULL,
    updated_at    TIMESTAMP(6)    NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_claim_message_public_id (public_id),
    KEY idx_claim_message_claim (claim_id, created_at),
    CONSTRAINT fk_claim_message_claim FOREIGN KEY (claim_id) REFERENCES claim (id),
    CONSTRAINT fk_claim_message_author FOREIGN KEY (author_user_id) REFERENCES user_account (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Décisions : transfert, résolution, réouverture. Séparées des messages
-- parce qu'elles ne sont pas de la conversation — les mélanger rendrait
-- l'historique des décisions illisible dans un fil bavard.
CREATE TABLE claim_event (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id      BINARY(16)      NOT NULL,
    version        BIGINT          NOT NULL DEFAULT 0,

    claim_id       BIGINT UNSIGNED NOT NULL,
    event_type     VARCHAR(24)     NOT NULL,
    actor_user_id  BIGINT UNSIGNED NOT NULL,
    from_status    VARCHAR(24)     NULL,
    to_status      VARCHAR(24)     NULL,
    from_audience  VARCHAR(32)     NULL,
    to_audience    VARCHAR(32)     NULL,
    motive         VARCHAR(500)    NOT NULL,

    created_at     TIMESTAMP(6)    NOT NULL,
    updated_at     TIMESTAMP(6)    NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_claim_event_public_id (public_id),
    KEY idx_claim_event_claim (claim_id, created_at),
    CONSTRAINT fk_claim_event_claim FOREIGN KEY (claim_id) REFERENCES claim (id),
    CONSTRAINT fk_claim_event_actor FOREIGN KEY (actor_user_id) REFERENCES user_account (id),
    CONSTRAINT chk_claim_event_type
        CHECK (event_type IN ('CREATED', 'TRANSFERRED', 'STATUS_CHANGED', 'REOPENED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
