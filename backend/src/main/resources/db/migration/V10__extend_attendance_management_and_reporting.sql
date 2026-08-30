-- Gestion de l'assiduité et reporting (docs/02-cahier-des-charges.md §17,
-- §21, §24 ; docs/04-modele-donnees.md §19, §21 ; branche
-- feature/attendance-management-and-reporting ;
-- docs/reports/ATTENDANCE_MANAGEMENT_DESIGN.md).
--
-- Migration ADDITIVE : aucune migration V1..V9 n'est modifiée, aucune
-- colonne ni table supprimée (seul l'index d'unicité
-- `uq_attendance_checkpoint_session` est retiré — voir A). Aucune donnée
-- métier n'est insérée.
--
-- Portée du lot (décisions figées dans le rapport de conception) :
--   A. plusieurs points de contrôle par séance, typés START/END/CUSTOM,
--      avec libellé, ordre d'affichage, caractère obligatoire et cycle de
--      vie propre PLANNED -> OPEN -> CLOSED / CANCELLED ;
--   B. présences enrichies : statut métier
--      (PRESENT|LATE|ABSENT|EXCUSED_ABSENCE|CANCELLED), source étendue
--      (+ MANUAL, CORRECTION), minute de retard, commentaire, acteur de
--      saisie / correction, horodatage de correction / d'annulation ;
--   C. historique de correction append-only (`attendance_correction`) ;
--   D. justificatif métier SANS fichier (`attendance_justification`) :
--      catégorie, référence externe, commentaire, cycle
--      PENDING -> ACCEPTED / REJECTED, examinateur, motif de décision.
--
-- Ce lot NE crée PAS : stockage de fichier justificatif, planning, QR
-- fixe de salle, contrôle réseau, WebAuthn, table matérialisée de
-- demi-journées (`daily_attendance_summary` — calcul à la volée). Les
-- statuts PARTIAL / TO_CONFIRM de docs/04 §19.2 ne sont pas repris dans
-- cette tranche (divergence documentée).
--
-- Conventions identiques à V1/V4..V9 : PK BIGINT UNSIGNED
-- AUTO_INCREMENT, identifiant public UUID (BINARY(16)), suppression
-- RESTRICT, horodatage UTC (TIMESTAMP(6)), verrouillage optimiste
-- (`version`), colonnes auteur (`*_by_id`) en FK RESTRICT vers
-- `user_account`. Aucune suppression physique d'une présence ou d'un
-- justificatif : l'état terminal est porté par le statut, l'historique
-- est conservé.
--
-- Frontières modulaires (Spring Modulith) :
--   * `attendance_checkpoint` appartient au module `coursesession` ;
--   * `attendance_record`, `attendance_correction` et
--     `attendance_justification` appartiennent au module `attendance` ;
--   * `attendance_record.attendance_checkpoint_id` reste une simple
--     valeur technique (FK SQL) résolue par le port public
--     `coursesession.CourseSessionDirectory`.
--
-- Note sur les valeurs par défaut : les nouvelles colonnes NOT NULL
-- reçoivent un DEFAULT SQL conservé (et non retiré après reprise). Ce
-- DEFAULT n'est jamais utilisé par l'application — l'entité JPA
-- renseigne toujours ces colonnes explicitement — mais il garantit la
-- compatibilité des insertions SQL natives (fixtures de test V9) et rend
-- la reprise déterministe.

-- ===========================================================================
-- A. attendance_checkpoint : plusieurs points de contrôle par séance
--    (module `coursesession`).
-- ===========================================================================

-- Retrait de l'unicité « un point de contrôle par séance » de V9. La FK
-- fk_attendance_checkpoint_session (course_session_id) s'appuie sur cet
-- index unique : InnoDB refuse de le supprimer tant qu'aucun autre index
-- ne couvre la colonne. On crée donc d'abord un index simple, puis on
-- retire l'unicité.
CREATE INDEX idx_attendance_checkpoint_session ON attendance_checkpoint (course_session_id);

ALTER TABLE attendance_checkpoint
    DROP INDEX uq_attendance_checkpoint_session;

ALTER TABLE attendance_checkpoint
    ADD COLUMN label           VARCHAR(120)    NOT NULL DEFAULT 'Arrivée'  AFTER course_session_id,
    ADD COLUMN checkpoint_type VARCHAR(20)     NOT NULL DEFAULT 'START'    AFTER label,
    ADD COLUMN display_order   INT             NOT NULL DEFAULT 0          AFTER checkpoint_type,
    ADD COLUMN status          VARCHAR(20)     NOT NULL DEFAULT 'PLANNED'  AFTER display_order,
    ADD COLUMN required        BOOLEAN         NOT NULL DEFAULT TRUE       AFTER status,
    ADD COLUMN cancel_reason   VARCHAR(500)    NULL                        AFTER closed_at,
    ADD COLUMN created_by_id   BIGINT UNSIGNED NULL                        AFTER created_at,
    ADD COLUMN updated_by_id   BIGINT UNSIGNED NULL                        AFTER updated_at;

-- Reprise déterministe des points de contrôle créés par V9 : ils sont
-- tous le point de contrôle « START » unique de leur séance ; leur
-- statut se déduit de opened_at / closed_at (l'état venait de la séance
-- en V9).
UPDATE attendance_checkpoint
   SET status = CASE
                    WHEN closed_at IS NOT NULL THEN 'CLOSED'
                    WHEN opened_at IS NOT NULL THEN 'OPEN'
                    ELSE 'PLANNED'
                END,
       label = 'Arrivée',
       checkpoint_type = 'START',
       display_order = 0,
       required = TRUE;

ALTER TABLE attendance_checkpoint
    ADD CONSTRAINT uq_attendance_checkpoint_order UNIQUE (course_session_id, display_order),
    ADD CONSTRAINT chk_attendance_checkpoint_type
        CHECK (checkpoint_type IN ('START', 'END', 'CUSTOM')),
    ADD CONSTRAINT chk_attendance_checkpoint_status
        CHECK (status IN ('PLANNED', 'OPEN', 'CLOSED', 'CANCELLED')),
    ADD CONSTRAINT chk_attendance_checkpoint_display_order
        CHECK (display_order >= 0),
    -- Cohérence du cycle de vie propre au point de contrôle :
    --   PLANNED    : jamais ouvert ;
    --   OPEN       : ouvert, pas encore fermé ;
    --   CLOSED     : ouvert puis fermé ;
    --   CANCELLED  : aucune contrainte sur opened_at / closed_at
    --                (un point de contrôle peut être annulé avant ou
    --                 après ouverture).
    ADD CONSTRAINT chk_attendance_checkpoint_open_state CHECK (
        (status = 'PLANNED'   AND opened_at IS NULL) OR
        (status = 'OPEN'      AND opened_at IS NOT NULL AND closed_at IS NULL) OR
        (status = 'CLOSED'    AND opened_at IS NOT NULL AND closed_at IS NOT NULL) OR
        (status = 'CANCELLED')
    ),
    ADD CONSTRAINT fk_attendance_checkpoint_created_by
        FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_attendance_checkpoint_updated_by
        FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT;

CREATE INDEX idx_attendance_checkpoint_status ON attendance_checkpoint (status);

-- ===========================================================================
-- B. attendance_record : présences enrichies (module `attendance`).
--    La contrainte uq_attendance_record_checkpoint_enrollment de V9 est
--    CONSERVÉE telle quelle — elle reste l'autorité anti-double présence
--    (tous statuts confondus).
-- ===========================================================================

ALTER TABLE attendance_record
    ADD COLUMN status            VARCHAR(24)     NOT NULL DEFAULT 'PRESENT' AFTER source,
    ADD COLUMN late_minutes      INT             NULL                       AFTER status,
    ADD COLUMN comment           VARCHAR(500)    NULL                       AFTER late_minutes,
    ADD COLUMN recorded_by_id    BIGINT UNSIGNED NULL                       AFTER student_user_id,
    ADD COLUMN last_corrected_at TIMESTAMP(6)    NULL                       AFTER updated_at,
    ADD COLUMN corrected_by_id   BIGINT UNSIGNED NULL                       AFTER last_corrected_at,
    ADD COLUMN cancelled_at      TIMESTAMP(6)    NULL                       AFTER corrected_by_id;

-- Toutes les présences créées par V9 sont des émargements QR / code
-- court réussis : statut PRESENT.
UPDATE attendance_record SET status = 'PRESENT';

ALTER TABLE attendance_record
    ADD CONSTRAINT chk_attendance_record_status
        CHECK (status IN ('PRESENT', 'LATE', 'ABSENT', 'EXCUSED_ABSENCE', 'CANCELLED')),
    ADD CONSTRAINT chk_attendance_record_source
        CHECK (source IN ('DYNAMIC_QR', 'SHORT_CODE', 'MANUAL', 'CORRECTION')),
    ADD CONSTRAINT chk_attendance_record_late
        CHECK (late_minutes IS NULL OR late_minutes >= 0),
    ADD CONSTRAINT fk_attendance_record_recorded_by
        FOREIGN KEY (recorded_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_attendance_record_corrected_by
        FOREIGN KEY (corrected_by_id) REFERENCES user_account (id) ON DELETE RESTRICT;

CREATE INDEX idx_attendance_record_status ON attendance_record (status);

-- ===========================================================================
-- C. attendance_correction : historique de correction APPEND-ONLY
--    (module `attendance`). Aucune ligne n'est jamais mise à jour ni
--    supprimée : chaque évolution d'une présence ajoute une ligne.
-- ===========================================================================

CREATE TABLE attendance_correction (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id             BINARY(16)      NOT NULL,
    attendance_record_id  BIGINT UNSIGNED NOT NULL,
    action                VARCHAR(30)     NOT NULL,   -- CREATED_MANUALLY | STATUS_CORRECTED | CANCELLED | JUSTIFICATION_ADDED | JUSTIFICATION_UPDATED | JUSTIFICATION_REVIEWED
    previous_status       VARCHAR(24)     NULL,
    new_status            VARCHAR(24)     NULL,
    previous_late_minutes INT             NULL,
    new_late_minutes      INT             NULL,
    previous_comment      VARCHAR(500)    NULL,
    new_comment           VARCHAR(500)    NULL,
    reason                VARCHAR(500)    NOT NULL,   -- motif obligatoire de toute action manuelle
    actor_user_id         BIGINT UNSIGNED NULL,       -- FK SET NULL logique (RESTRICT ici : pas de suppression de compte)
    occurred_at           TIMESTAMP(6)    NOT NULL,
    created_at            TIMESTAMP(6)    NOT NULL,
    version               BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_attendance_correction_public_id UNIQUE (public_id),
    CONSTRAINT chk_attendance_correction_action CHECK (action IN (
        'CREATED_MANUALLY', 'STATUS_CORRECTED', 'CANCELLED',
        'JUSTIFICATION_ADDED', 'JUSTIFICATION_UPDATED', 'JUSTIFICATION_REVIEWED')),
    CONSTRAINT chk_attendance_correction_previous_status CHECK (
        previous_status IS NULL OR previous_status IN
        ('PRESENT', 'LATE', 'ABSENT', 'EXCUSED_ABSENCE', 'CANCELLED')),
    CONSTRAINT chk_attendance_correction_new_status CHECK (
        new_status IS NULL OR new_status IN
        ('PRESENT', 'LATE', 'ABSENT', 'EXCUSED_ABSENCE', 'CANCELLED')),
    CONSTRAINT chk_attendance_correction_late CHECK (
        (previous_late_minutes IS NULL OR previous_late_minutes >= 0) AND
        (new_late_minutes IS NULL OR new_late_minutes >= 0)),

    CONSTRAINT fk_attendance_correction_record
        FOREIGN KEY (attendance_record_id) REFERENCES attendance_record (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_correction_actor
        FOREIGN KEY (actor_user_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_attendance_correction_record
    ON attendance_correction (attendance_record_id, occurred_at);

-- ===========================================================================
-- D. attendance_justification : justificatif métier SANS fichier
--    (module `attendance`). Rattaché à la présence ABSENT qu'il justifie.
--    Un justificatif accepté fait passer la présence
--    ABSENT -> EXCUSED_ABSENCE (jamais PRESENT) ; un refus la laisse (ou
--    la remet) ABSENT. L'historique est conservé : après un refus, un
--    nouveau dépôt est possible.
-- ===========================================================================

CREATE TABLE attendance_justification (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id             BINARY(16)      NOT NULL,
    attendance_record_id  BIGINT UNSIGNED NOT NULL,
    category              VARCHAR(20)     NOT NULL,   -- MEDICAL | TRANSPORT | FAMILY | ADMINISTRATIVE | OTHER
    external_reference    VARCHAR(120)    NULL,       -- référence externe facultative (n° dossier, ...)
    comment               VARCHAR(1000)   NOT NULL,   -- commentaire borné
    status                VARCHAR(16)     NOT NULL,   -- PENDING | ACCEPTED | REJECTED
    submitted_at          TIMESTAMP(6)    NOT NULL,
    submitted_by_id       BIGINT UNSIGNED NOT NULL,   -- l'apprenant qui dépose (parcours unique dans cette tranche)
    reviewed_at           TIMESTAMP(6)    NULL,
    reviewed_by_id        BIGINT UNSIGNED NULL,
    decision_reason       VARCHAR(500)    NULL,       -- obligatoire (contrôle applicatif) si status = REJECTED
    created_at            TIMESTAMP(6)    NOT NULL,
    updated_at            TIMESTAMP(6)    NOT NULL,
    version               BIGINT UNSIGNED NOT NULL DEFAULT 0,

    -- Colonne générée : vaut attendance_record_id tant que le
    -- justificatif n'est pas REJETÉ (PENDING ou ACCEPTED = « actif »),
    -- NULL sinon. MySQL autorisant plusieurs NULL dans un index UNIQUE,
    -- la contrainte n'interdit qu'UN SEUL justificatif actif par absence
    -- (cf. V6 active_primary_key, V7 active_student_key, V8
    -- active_open_key).
    active_justification_key BIGINT UNSIGNED GENERATED ALWAYS AS (
        IF(status <> 'REJECTED', attendance_record_id, NULL)) VIRTUAL,

    CONSTRAINT uq_attendance_justification_public_id UNIQUE (public_id),
    CONSTRAINT uq_attendance_justification_active UNIQUE (active_justification_key),
    CONSTRAINT chk_attendance_justification_category CHECK (category IN (
        'MEDICAL', 'TRANSPORT', 'FAMILY', 'ADMINISTRATIVE', 'OTHER')),
    CONSTRAINT chk_attendance_justification_status CHECK (status IN (
        'PENDING', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT chk_attendance_justification_review CHECK (
        (status = 'PENDING'  AND reviewed_at IS NULL AND reviewed_by_id IS NULL) OR
        (status IN ('ACCEPTED', 'REJECTED') AND reviewed_at IS NOT NULL AND reviewed_by_id IS NOT NULL)
    ),

    CONSTRAINT fk_attendance_justification_record
        FOREIGN KEY (attendance_record_id) REFERENCES attendance_record (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_justification_submitted_by
        FOREIGN KEY (submitted_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_justification_reviewed_by
        FOREIGN KEY (reviewed_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_attendance_justification_status
    ON attendance_justification (status, submitted_at);
CREATE INDEX idx_attendance_justification_record
    ON attendance_justification (attendance_record_id);
