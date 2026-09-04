-- Report d'une séance annulée et demande d'annulation par le formateur
-- (docs/02-cahier-des-charges.md §14.4 ; EF-SES-007, EF-SES-008).

-- ---------------------------------------------------------------------
-- Report (EF-SES-007)
-- ---------------------------------------------------------------------
-- « Une séance annulée n'est pas reportée automatiquement : le
-- responsable définit une nouvelle date, ce qui crée une séance liée à
-- l'originale » (§14.4). Le lien est donc porté par la séance d'origine,
-- et la séance de remplacement est une séance à part entière — pas une
-- copie fantôme. L'originale reste consultable en historique.
ALTER TABLE course_session
    ADD COLUMN postponed_to_session_id BIGINT UNSIGNED NULL
        COMMENT 'Seance de remplacement creee lors d''un report (EF-SES-007).',
    ADD CONSTRAINT fk_course_session_postponed_to FOREIGN KEY (postponed_to_session_id)
        REFERENCES course_session (id) ON DELETE RESTRICT;

-- Une séance de remplacement ne peut pas l'être de deux séances à la
-- fois : sans cette contrainte, un double report produirait un historique
-- ambigu, impossible à lire.
CREATE UNIQUE INDEX uq_course_session_postponed_to
    ON course_session (postponed_to_session_id);

-- ---------------------------------------------------------------------
-- Demande d'annulation par le formateur (EF-SES-008)
-- ---------------------------------------------------------------------
-- « Le responsable pédagogique annule ; le formateur DEMANDE une
-- annulation » (§14.4). Le formateur ne décide donc jamais lui-même —
-- c'est la même logique que pour le remplacement, qu'il propose sans
-- pouvoir le valider (RG-024).
CREATE TABLE session_cancellation_request (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id           BINARY(16)      NOT NULL,
    course_session_id   BIGINT UNSIGNED NOT NULL,
    requested_by_id     BIGINT UNSIGNED NOT NULL,
    reason              VARCHAR(500)    NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    decided_by_id       BIGINT UNSIGNED NULL,
    decided_at          TIMESTAMP(6)    NULL,
    decision_comment    VARCHAR(500)    NULL,
    created_at          TIMESTAMP(6)    NOT NULL,
    updated_at          TIMESTAMP(6)    NOT NULL,
    version             BIGINT UNSIGNED NOT NULL DEFAULT 0,

    -- Vaut 1 tant que la demande est en attente, NULL sinon : une seule
    -- demande vivante par séance, sans gêner l'historique des décisions.
    pending_request_key TINYINT UNSIGNED GENERATED ALWAYS AS
                            (IF(status = 'REQUESTED', 1, NULL)) VIRTUAL,

    CONSTRAINT uq_session_cancellation_request_public_id UNIQUE (public_id),
    CONSTRAINT uq_session_cancellation_request_pending
        UNIQUE (course_session_id, pending_request_key),
    CONSTRAINT ck_session_cancellation_request_status
        CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT fk_session_cancellation_request_session FOREIGN KEY (course_session_id)
        REFERENCES course_session (id) ON DELETE RESTRICT,
    CONSTRAINT fk_session_cancellation_request_requester FOREIGN KEY (requested_by_id)
        REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_session_cancellation_request_decider FOREIGN KEY (decided_by_id)
        REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_session_cancellation_request_session
    ON session_cancellation_request (course_session_id, status);
