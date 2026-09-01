-- Cycle de vie avancé des séances (bloc G1-C ;
-- docs/reports/G1_ARCHITECTURE_DECISIONS.md DEC-G1-004, DEC-G1-012 ;
-- docs/reports/G1_REQUIREMENTS_TRACEABILITY.md §4 ; EF-SES-004, EF-SES-005 ;
-- CAD §24 RG-12, CDC §43 RG-015, RG-017 ; CDC §15.4).
--
-- Portée de CE fichier (un domaine = cycle de vie des séances) :
--   A. `course_session` : ajout de l'état terminal `CANCELLED` + colonnes
--      d'annulation ; ajustement du CHECK de cohérence de V9 ;
--   B. `teacher_substitution` : remplacement d'un formateur sur une
--      séance, autorisé et audité (RG-12 CAD, RG-015 CDC).
--
-- Décisions de conception :
--   * une séance PLANNED **ou** OPEN peut être annulée (CDC §15.4 : « Le
--     responsable pédagogique peut annuler une séance » — sans restriction
--     d'état ; l'émargement en cours est interrompu, ses jetons Redis
--     purgés). Une séance CLOSED n'est PAS annulable (l'émargement fait
--     foi). D'où le CHECK ci-dessous : pour CANCELLED on impose seulement
--     `closed_at IS NULL`, `cancelled_at IS NOT NULL`,
--     `cancellation_reason IS NOT NULL` ; `opened_at` peut être nul
--     (annulation d'une PLANNED) ou non (annulation d'une OPEN) ;
--   * annulation **directe** par un rôle autorisé — pas de workflow de
--     demande, donc PAS de table `session_cancellation_request` (les
--     documents ne l'exigent pas : CDC §15.5 décrit une *demande*
--     d'annulation par le formateur, reportée hors G1-C) ;
--   * `teacher_substitution` : le formateur principal de `course_session`
--     n'est JAMAIS écrasé ; la substitution est une ligne datée, à
--     période de validité, avec motif obligatoire, jamais supprimée
--     (fin logique via `ended_at`). Au plus une substitution ACTIVE
--     applicable à un instant donné pour une séance (contrôle applicatif +
--     index de soutien ; MySQL n'a pas d'index partiel).
--
-- Conventions V1/V4..V13 : PK BIGINT UNSIGNED AUTO_INCREMENT, `public_id`
-- BINARY(16) unique, TIMESTAMP(6) UTC, verrou optimiste (`version`),
-- FK RESTRICT vers `user_account`, ENGINE=InnoDB, utf8mb4_0900_ai_ci.
-- Aucune donnée métier insérée. Modifications ADDITIVES (MySQL : DDL
-- auto-commit).

-- ---------------------------------------------------------------------------
-- A. course_session : état CANCELLED + colonnes d'annulation.
-- ---------------------------------------------------------------------------
ALTER TABLE course_session
    ADD COLUMN cancellation_reason VARCHAR(500)    NULL AFTER superseded_by_scheduling,
    ADD COLUMN cancelled_at        TIMESTAMP(6)    NULL AFTER cancellation_reason,
    ADD COLUMN cancelled_by_id     BIGINT UNSIGNED NULL AFTER cancelled_at,
    ADD CONSTRAINT fk_course_session_cancelled_by
        FOREIGN KEY (cancelled_by_id) REFERENCES user_account (id) ON DELETE RESTRICT;

-- Le CHECK de V9 (`chk_course_session_open_state`) ne connaît que
-- PLANNED / OPEN / CLOSED : on le remplace pour accepter CANCELLED.
ALTER TABLE course_session DROP CHECK chk_course_session_open_state;
ALTER TABLE course_session
    ADD CONSTRAINT chk_course_session_open_state CHECK (
        (status = 'PLANNED'  AND opened_at IS NULL     AND closed_at IS NULL AND cancelled_at IS NULL)
        OR (status = 'OPEN'   AND opened_at IS NOT NULL AND closed_at IS NULL AND cancelled_at IS NULL)
        OR (status = 'CLOSED' AND opened_at IS NOT NULL AND closed_at IS NOT NULL AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND closed_at IS NULL
            AND cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL)
    );

CREATE INDEX idx_course_session_cancelled ON course_session (status, cancelled_at);

-- ---------------------------------------------------------------------------
-- B. teacher_substitution : remplacement d'un formateur sur une séance.
-- `course_session_id` est une valeur technique (FK SQL) — pas de partage
-- d'entité JPA : le module `attendance` / autres passent par le port
-- public de `coursesession`.
-- ---------------------------------------------------------------------------
CREATE TABLE teacher_substitution (
    id                          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id                   BINARY(16)      NOT NULL,
    course_session_id           BIGINT UNSIGNED NOT NULL,
    substitute_teacher_user_id  BIGINT UNSIGNED NOT NULL,
    original_teacher_user_id    BIGINT UNSIGNED NOT NULL,   -- figé à la création (traçabilité)
    reason                      VARCHAR(500)    NOT NULL,
    valid_from                  TIMESTAMP(6)    NOT NULL,
    valid_until                 TIMESTAMP(6)    NOT NULL,
    status                      VARCHAR(16)     NOT NULL,   -- ACTIVE | ENDED
    created_at                  TIMESTAMP(6)    NOT NULL,
    created_by_id               BIGINT UNSIGNED NULL,
    ended_at                    TIMESTAMP(6)    NULL,
    ended_by_id                 BIGINT UNSIGNED NULL,
    version                     BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_teacher_substitution_public_id UNIQUE (public_id),
    CONSTRAINT chk_teacher_substitution_status CHECK (status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT chk_teacher_substitution_period CHECK (valid_until > valid_from),
    CONSTRAINT chk_teacher_substitution_distinct CHECK (substitute_teacher_user_id <> original_teacher_user_id),
    CONSTRAINT chk_teacher_substitution_ended CHECK (
        (status = 'ACTIVE' AND ended_at IS NULL)
        OR (status = 'ENDED' AND ended_at IS NOT NULL)
    ),

    CONSTRAINT fk_teacher_substitution_session FOREIGN KEY (course_session_id) REFERENCES course_session (id) ON DELETE RESTRICT,
    CONSTRAINT fk_teacher_substitution_substitute FOREIGN KEY (substitute_teacher_user_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_teacher_substitution_original FOREIGN KEY (original_teacher_user_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_teacher_substitution_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_teacher_substitution_ended_by FOREIGN KEY (ended_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_teacher_substitution_session ON teacher_substitution (course_session_id, status);
CREATE INDEX idx_teacher_substitution_substitute ON teacher_substitution (substitute_teacher_user_id, status);
