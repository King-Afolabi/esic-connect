-- V24 — Modalité d'enseignement et suivi à distance individuel
-- (EF-ENR-004 ; docs/02 §15).
--
-- Deux besoins distincts, une seule migration parce que le second n'a de
-- sens qu'avec le premier : autoriser un apprenant « à distance » ne veut
-- rien dire tant qu'une séance n'a pas de modalité.
--
-- 1. `course_session.attendance_mode` — ON_SITE / REMOTE / HYBRID
--    (docs/02 §15.1 à §15.4). Défaut ON_SITE : c'est la modalité de
--    l'immense majorité des séances, et une séance déjà créée ne peut pas
--    être devinée rétroactivement comme distancielle.
--
-- 2. `course_session.remote_link` — lien de visioconférence, saisi ou créé
--    par l'intégration Microsoft quand elle sera active (§15.2). Colonne
--    volontairement large : les URL Teams sont longues.
--
-- 3. `remote_attendance_authorization` — un apprenant autorisé à suivre à
--    distance alors que sa classe est en présentiel (§15.3). Porte
--    l'apprenant, l'auteur, le motif, la période, le statut et la date de
--    décision, exactement comme le cahier l'exige.
--
-- La période est bornée par `valid_from` / `valid_until` (inclusive,
-- `NULL` = ouverte) plutôt que par une portée « séance / période / année »
-- énumérée : une autorisation pour une seule séance s'exprime par un
-- intervalle d'un jour, et le calcul de couverture reste unique.

ALTER TABLE course_session
    ADD COLUMN attendance_mode VARCHAR(20) NOT NULL DEFAULT 'ON_SITE' AFTER room_code,
    ADD COLUMN remote_link VARCHAR(500) NULL AFTER attendance_mode;

ALTER TABLE course_session
    ADD CONSTRAINT chk_course_session_attendance_mode
        CHECK (attendance_mode IN ('ON_SITE', 'REMOTE', 'HYBRID'));

CREATE TABLE remote_attendance_authorization (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id         BINARY(16)      NOT NULL,
    version           BIGINT          NOT NULL DEFAULT 0,

    student_user_id   BIGINT UNSIGNED NOT NULL,
    class_group_id    BIGINT UNSIGNED NULL,

    status            VARCHAR(20)     NOT NULL,
    reason            VARCHAR(500)    NOT NULL,
    valid_from        DATE            NOT NULL,
    valid_until       DATE            NULL,

    decided_by_id     BIGINT UNSIGNED NOT NULL,
    decided_at        TIMESTAMP(6)    NOT NULL,
    revoked_by_id     BIGINT UNSIGNED NULL,
    revoked_at        TIMESTAMP(6)    NULL,
    revocation_reason VARCHAR(500)    NULL,

    created_at        TIMESTAMP(6)    NOT NULL,
    updated_at        TIMESTAMP(6)    NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_remote_attendance_authorization_public_id (public_id),
    KEY idx_remote_attendance_authorization_student (student_user_id, valid_from),
    CONSTRAINT fk_remote_attendance_authorization_student
        FOREIGN KEY (student_user_id) REFERENCES user_account (id),
    CONSTRAINT fk_remote_attendance_authorization_class
        FOREIGN KEY (class_group_id) REFERENCES class_group (id),
    CONSTRAINT fk_remote_attendance_authorization_decided_by
        FOREIGN KEY (decided_by_id) REFERENCES user_account (id),
    CONSTRAINT chk_remote_attendance_authorization_status
        CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT chk_remote_attendance_authorization_period
        CHECK (valid_until IS NULL OR valid_until >= valid_from)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 4. Canaux distants (docs/02 §15.4). La contrainte posée par V10
--    énumérait les quatre canaux d'alors ; elle est remplacée, jamais
--    modifiée sur place (V10 garde sa somme de contrôle).
--
--    REMOTE_QR / REMOTE_CODE enregistrent ce que l'apprenant a DÉCLARÉ,
--    pas une localisation vérifiée. Le contrôle réel de la présence sur
--    site est le QR fixe de salle associé à la plage réseau
--    (EF-ATT-008/010, sprint 8).

ALTER TABLE attendance_record
    DROP CHECK chk_attendance_record_source;

ALTER TABLE attendance_record
    ADD CONSTRAINT chk_attendance_record_source
        CHECK (source IN ('DYNAMIC_QR', 'SHORT_CODE', 'MANUAL', 'CORRECTION',
                          'REMOTE_QR', 'REMOTE_CODE'));
