-- Centre de notifications métier persistantes (bloc G1-D ;
-- docs/reports/G1_ARCHITECTURE_DECISIONS.md DEC-G1-007, DEC-G1-012 ;
-- docs/reports/G1_REQUIREMENTS_TRACEABILITY.md §5 ; EF-NOTIF-001,
-- EF-NOTIF-002 ; CDC §43 RG-033 ; CDC §14, §23 ; MDD §23.1).
--
-- Portée de CE fichier : une table `notification`. Aucune donnée métier
-- insérée. Modification purement additive (MySQL : DDL auto-commit).
--
-- Décisions de conception (DEC-G1-007) :
--   * livraison via un listener applicatif
--     `@TransactionalEventListener(AFTER_COMMIT)` + écriture en
--     `@Transactional(REQUIRES_NEW)` : une notification n'est créée
--     qu'APRÈS le commit réussi de l'événement source ; un échec de
--     notification ne rollbacke jamais le métier déjà committé ;
--   * idempotence par `dedup_key` UNIQUE = hachage stable de
--     (type, resource_public_id, recipient_user_id, identifiant
--     d'occurrence de l'événement) → AU PLUS UNE notification par
--     (destinataire, événement), même si le listener est rejoué ;
--   * `title` / `body` NEUTRES : jamais de jeton, code court, IP, contenu
--     de justificatif, chemin de fichier, secret, nom propre au-delà de
--     ce qui est déjà public (MDD §23.1, §24.3) ;
--   * destinataires DÉRIVÉS CÔTÉ SERVEUR (formateur principal, remplaçant
--     de la séance) — jamais d'un identifiant fourni par le client ;
--   * un compte inactif (archivé) n'est jamais destinataire.
--
-- Conventions V1/V4..V14 : PK BIGINT UNSIGNED AUTO_INCREMENT, `public_id`
-- BINARY(16) unique, TIMESTAMP(6) UTC, verrou optimiste (`version`),
-- FK RESTRICT vers `user_account`, ENGINE=InnoDB, utf8mb4_0900_ai_ci.
-- `recipient_user_id` est une valeur technique (FK SQL) — le module
-- `notification` ne partage aucune entité JPA avec `identity`.

CREATE TABLE notification (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id          BINARY(16)      NOT NULL,
    recipient_user_id  BIGINT UNSIGNED NOT NULL,
    type               VARCHAR(48)     NOT NULL,   -- PLANNING_PUBLISHED | SESSION_CANCELLED | SESSION_SUBSTITUTION_ADDED | SESSION_SUBSTITUTION_ENDED
    title              VARCHAR(150)    NOT NULL,
    body               VARCHAR(500)    NOT NULL,   -- neutre
    resource_type      VARCHAR(32)     NOT NULL,   -- COURSE_SESSION | PLANNING_VERSION
    resource_public_id BINARY(16)      NOT NULL,
    status             VARCHAR(12)     NOT NULL,   -- UNREAD | READ | ARCHIVED
    dedup_key          CHAR(64)        NOT NULL,   -- SHA-256 hex
    created_at         TIMESTAMP(6)    NOT NULL,
    read_at            TIMESTAMP(6)    NULL,
    version            BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_notification_public_id UNIQUE (public_id),
    CONSTRAINT uq_notification_dedup UNIQUE (dedup_key),
    CONSTRAINT chk_notification_status CHECK (status IN ('UNREAD', 'READ', 'ARCHIVED')),
    -- `read_at` renseigné si et seulement si la notification n'est plus UNREAD.
    CONSTRAINT chk_notification_read_at CHECK ((status = 'UNREAD') = (read_at IS NULL)),

    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_user_id)
        REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Liste paginée « mes notifications » (filtre statut optionnel, tri newest-first)
-- et compteur non lus.
CREATE INDEX idx_notification_recipient_status_created
    ON notification (recipient_user_id, status, created_at);
CREATE INDEX idx_notification_recipient_created
    ON notification (recipient_user_id, created_at);
