-- V27 — Apprenant provisoire (EF-ATT-007 ; docs/02 §16.12).
--
-- Le cahier est net sur ce que cette entrée N'EST PAS : « cette entrée ne
-- crée pas d'inscription officielle […] et reste distincte d'un compte
-- tant que la correspondance n'est pas validée ».
--
-- D'où une table séparée plutôt qu'une ligne d'`attendance_record` :
--
--   * `attendance_record.enrollment_id` est NOT NULL, et le rendre
--     nullable ouvrirait une présence sans inscription dans TOUS les
--     calculs d'assiduité — exactement ce que le cahier interdit ;
--   * une entrée provisoire porte une identité DÉCLARÉE (nom saisi par le
--     formateur), pas une identité vérifiée ; les mélanger reviendrait à
--     traiter les deux comme équivalentes ;
--   * la régularisation est une décision humaine tracée, pas une
--     conversion silencieuse.
--
-- Conséquence assumée : une entrée provisoire n'entre dans aucun rapport
-- d'assiduité tant qu'elle n'est pas régularisée. C'est voulu — elle est
-- un signalement, pas une présence.

CREATE TABLE session_guest_attendance (
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id              BINARY(16)      NOT NULL,
    version                BIGINT          NOT NULL DEFAULT 0,

    course_session_id      BIGINT UNSIGNED NOT NULL,
    attendance_checkpoint_id BIGINT UNSIGNED NULL,

    -- Identité DÉCLARÉE par le formateur, jamais vérifiée.
    first_name             VARCHAR(100)    NOT NULL,
    last_name              VARCHAR(100)    NOT NULL,
    email                  VARCHAR(255)    NULL,
    comment                VARCHAR(500)    NULL,

    status                 VARCHAR(24)     NOT NULL,

    recorded_by_id         BIGINT UNSIGNED NOT NULL,
    recorded_at            TIMESTAMP(6)    NOT NULL,

    -- Régularisation : décision humaine, tracée.
    resolved_by_id         BIGINT UNSIGNED NULL,
    resolved_at            TIMESTAMP(6)    NULL,
    resolution_comment     VARCHAR(500)    NULL,
    linked_enrollment_id   BIGINT UNSIGNED NULL,

    created_at             TIMESTAMP(6)    NOT NULL,
    updated_at             TIMESTAMP(6)    NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_session_guest_attendance_public_id (public_id),
    KEY idx_session_guest_attendance_session (course_session_id, status),
    CONSTRAINT fk_session_guest_attendance_session
        FOREIGN KEY (course_session_id) REFERENCES course_session (id),
    CONSTRAINT fk_session_guest_attendance_checkpoint
        FOREIGN KEY (attendance_checkpoint_id) REFERENCES attendance_checkpoint (id),
    CONSTRAINT fk_session_guest_attendance_recorded_by
        FOREIGN KEY (recorded_by_id) REFERENCES user_account (id),
    CONSTRAINT fk_session_guest_attendance_enrollment
        FOREIGN KEY (linked_enrollment_id) REFERENCES enrollment (id),
    CONSTRAINT chk_session_guest_attendance_status
        CHECK (status IN ('UNREGISTERED_GUEST', 'PENDING_REGISTRATION', 'LINKED', 'DISMISSED')),
    -- Une entrée régularisée porte forcément qui a décidé et quand.
    CONSTRAINT chk_session_guest_attendance_resolution
        CHECK ((status IN ('LINKED', 'DISMISSED'))
               = (resolved_by_id IS NOT NULL AND resolved_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
