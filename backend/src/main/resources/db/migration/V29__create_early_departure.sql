-- V29 — Départ anticipé (EF-ATT-013 ; docs/02 §16.13).
--
-- « L'apprenant signale son départ au formateur, qui accepte, refuse,
-- recommande favorablement ou transmet au responsable pédagogique. Le
-- dossier porte apprenant, séance, heure de départ, motif, avis du
-- formateur, décision, auteur et commentaire. »
--
-- Table séparée, et non une colonne d'`attendance_record` :
--
--   * un départ anticipé porte sur une SÉANCE, pas sur un point de
--     contrôle ; il peut être déposé avant qu'aucune présence n'existe ;
--   * il a son propre cycle de décision (demande → avis → décision), que
--     `attendance_record` n'a pas et ne doit pas acquérir ;
--   * l'effet (`PARTIAL` / `EXCUSED_PARTIAL` / `TO_CONFIRM`) est DÉRIVÉ de
--     la décision, jamais stocké : une copie dériverait de sa source dès
--     la première décision révisée.
--
-- Aucune donnée personnelle ici : ni nom, ni adresse. L'apprenant est
-- désigné par son inscription, les acteurs par leur compte.

CREATE TABLE early_departure (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id                BINARY(16)      NOT NULL,
    version                  BIGINT          NOT NULL DEFAULT 0,

    course_session_id        BIGINT UNSIGNED NOT NULL,
    enrollment_id            BIGINT UNSIGNED NOT NULL,

    -- Heure de départ annoncée par l'apprenant.
    departure_at             TIMESTAMP(6)    NOT NULL,
    reason                   VARCHAR(500)    NOT NULL,

    status                   VARCHAR(16)     NOT NULL,

    requested_by_id          BIGINT UNSIGNED NOT NULL,
    requested_at             TIMESTAMP(6)    NOT NULL,

    -- Avis du formateur : facultatif tant qu'il ne s'est pas prononcé.
    teacher_opinion          VARCHAR(16)     NULL,
    teacher_opinion_comment  VARCHAR(500)    NULL,
    teacher_opinion_by_id    BIGINT UNSIGNED NULL,
    teacher_opinion_at       TIMESTAMP(6)    NULL,

    -- Décision finale : accepter ou refuser. Toujours motivée.
    decided_by_id            BIGINT UNSIGNED NULL,
    decided_at               TIMESTAMP(6)    NULL,
    decision_comment         VARCHAR(500)    NULL,

    created_at               TIMESTAMP(6)    NOT NULL,
    updated_at               TIMESTAMP(6)    NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_early_departure_public_id (public_id),
    KEY idx_early_departure_session (course_session_id, status),
    KEY idx_early_departure_enrollment (enrollment_id, departure_at),
    CONSTRAINT fk_early_departure_session
        FOREIGN KEY (course_session_id) REFERENCES course_session (id),
    CONSTRAINT fk_early_departure_enrollment
        FOREIGN KEY (enrollment_id) REFERENCES enrollment (id),
    CONSTRAINT fk_early_departure_requested_by
        FOREIGN KEY (requested_by_id) REFERENCES user_account (id),
    CONSTRAINT fk_early_departure_opinion_by
        FOREIGN KEY (teacher_opinion_by_id) REFERENCES user_account (id),
    CONSTRAINT fk_early_departure_decided_by
        FOREIGN KEY (decided_by_id) REFERENCES user_account (id),
    CONSTRAINT chk_early_departure_status
        CHECK (status IN ('REQUESTED', 'FORWARDED', 'ACCEPTED', 'REFUSED')),
    CONSTRAINT chk_early_departure_opinion
        CHECK (teacher_opinion IS NULL
               OR teacher_opinion IN ('FAVOURABLE', 'UNFAVOURABLE')),
    -- Un avis exprimé porte forcément qui l'a émis et quand.
    CONSTRAINT chk_early_departure_opinion_author
        CHECK ((teacher_opinion IS NULL)
               = (teacher_opinion_by_id IS NULL AND teacher_opinion_at IS NULL)),
    -- Une décision prise porte forcément qui a décidé et quand : sans
    -- cela, un dossier tranché serait indistinguable d'un dossier ouvert.
    CONSTRAINT chk_early_departure_decision
        CHECK ((status IN ('ACCEPTED', 'REFUSED'))
               = (decided_by_id IS NOT NULL AND decided_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
