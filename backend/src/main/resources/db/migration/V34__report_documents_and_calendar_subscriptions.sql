-- V34 — Documents officiels produits et abonnements calendrier
-- (EF-REP-006, EF-INT-001 ; docs/02 §22.4, §28.3 ; AC-033, AC-034).
--
-- Conventions V1/V31/V33 : PK BIGINT UNSIGNED AUTO_INCREMENT, `public_id`
-- BINARY(16) unique, TIMESTAMP(6) UTC, verrou optimiste (`version`),
-- FK RESTRICT vers `user_account`, ENGINE=InnoDB, utf8mb4_0900_ai_ci.
-- Aucune donnée métier insérée.

-- ---------------------------------------------------------------------
-- Registre des documents officiels produits (EF-REP-006, AC-033)
-- ---------------------------------------------------------------------
--
-- « Une attestation porte un identifiant vérifiable » (§22.4). Vérifiable
-- suppose un registre : sans lui, l'identifiant imprimé sur le papier
-- n'est qu'une décoration que personne ne peut confronter à quoi que ce
-- soit. Chaque génération inscrit donc une ligne, et c'est cette ligne
-- qui répond à « ce document est-il bien sorti d'ESIC Connect ? ».
--
-- LE CONTENU DU PDF N'EST PAS STOCKÉ, et c'est délibéré :
--   * il est reproductible à l'identique depuis les données d'assiduité,
--     qui restent la source de vérité ;
--   * le conserver dupliquerait des données personnelles dans un second
--     emplacement, avec sa propre durée de conservation à gouverner
--     (docs/02 §33) — pour un gain nul.
-- Est conservée à la place une EMPREINTE SHA-256 du document remis :
-- elle permet de dire si un PDF présenté est bien celui qui a été émis,
-- sans avoir à en garder une copie.
--
-- `document_id` est la référence lisible imprimée sur le document. Elle
-- porte une part aléatoire : une séquence devinable permettrait de
-- fabriquer une référence plausible, ce qui viderait la vérification de
-- son sens.
--
-- Le sujet est désigné par son `public_id` d'apprenant, jamais par son
-- nom : le nom est figé dans un instantané destiné à rester lisible si
-- le profil change, mais il n'est pas la clé.

CREATE TABLE report_document (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id           BINARY(16)      NOT NULL,
    document_id         VARCHAR(48)     NOT NULL,
    document_type       VARCHAR(32)     NOT NULL,
    -- Apprenant concerné (attestation individuelle) ; NULL pour un
    -- document collectif.
    subject_public_id   BINARY(16)      NULL,
    subject_snapshot    VARCHAR(255)    NULL,
    class_group_public_id BINARY(16)    NULL,
    period_start        DATE            NULL,
    period_end          DATE            NULL,
    issued_by_user_id   BIGINT UNSIGNED NULL,
    issued_by_snapshot  VARCHAR(255)    NOT NULL,
    issued_at           TIMESTAMP(6)    NOT NULL,
    -- Empreinte SHA-256 du PDF remis (64 caractères hexadécimaux).
    content_hash        CHAR(64)        NOT NULL,
    revoked_at          TIMESTAMP(6)    NULL,
    revocation_reason   VARCHAR(255)    NULL,
    created_at          TIMESTAMP(6)    NOT NULL,
    updated_at          TIMESTAMP(6)    NOT NULL,
    version             BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_report_document_public_id UNIQUE (public_id),
    CONSTRAINT uq_report_document_document_id UNIQUE (document_id),
    CONSTRAINT chk_report_document_type
        CHECK (document_type IN ('ATTENDANCE_CERTIFICATE')),
    CONSTRAINT chk_report_document_period
        CHECK (period_start IS NULL OR period_end IS NULL OR period_start <= period_end),
    CONSTRAINT fk_report_document_issuer FOREIGN KEY (issued_by_user_id)
        REFERENCES user_account (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_report_document_subject ON report_document (subject_public_id, issued_at);
CREATE INDEX idx_report_document_issued_at ON report_document (issued_at);

-- ---------------------------------------------------------------------
-- Abonnements iCalendar (EF-INT-001, AC-034)
-- ---------------------------------------------------------------------
--
-- « Un flux iCalendar signé, propre à chaque utilisateur et révocable »
-- (§28.3). Un agenda externe ne sait pas s'authentifier : il ne fait que
-- rappeler une URL. Le secret EST donc l'URL, ce qui impose trois choses.
--
--   1. Le jeton est aléatoire et n'est PAS stocké en clair : seule son
--      empreinte SHA-256 l'est. Une fuite de la base ne rend donc pas les
--      plannings lisibles.
--   2. `feed_key` est une référence publique et non secrète, présente
--      dans l'URL à côté du jeton : elle permet de retrouver la ligne
--      d'un seul index sans comparer l'empreinte à toutes les autres.
--   3. La révocation est une DATE, pas une suppression : l'agenda
--      continuera d'appeler l'URL pendant des semaines, et il faut
--      pouvoir répondre « révoqué » plutôt que « inconnu », et savoir
--      depuis quand.
--
-- Un utilisateur peut détenir plusieurs abonnements actifs — un par
-- agenda — et en révoquer un sans casser les autres.

CREATE TABLE calendar_subscription (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id    BINARY(16)      NOT NULL,
    user_id      BIGINT UNSIGNED NOT NULL,
    feed_key     CHAR(32)        NOT NULL,
    token_hash   CHAR(64)        NOT NULL,
    label        VARCHAR(120)    NULL,
    created_at   TIMESTAMP(6)    NOT NULL,
    last_used_at TIMESTAMP(6)    NULL,
    revoked_at   TIMESTAMP(6)    NULL,
    version      BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_calendar_subscription_public_id UNIQUE (public_id),
    CONSTRAINT uq_calendar_subscription_feed_key UNIQUE (feed_key),
    CONSTRAINT fk_calendar_subscription_user FOREIGN KEY (user_id)
        REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_calendar_subscription_user ON calendar_subscription (user_id, revoked_at);
