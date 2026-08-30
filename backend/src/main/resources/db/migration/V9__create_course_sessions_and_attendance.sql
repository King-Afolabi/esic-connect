-- Séances exceptionnelles et émargement (docs/02-cahier-des-charges.md §15, §17 ;
-- docs/03-architecture.md §7 ; docs/04-modele-donnees.md ; sprint T-J2 ;
-- branche feature/attendance-qr-demonstration).
--
-- Portée du lot (décisions explicites de la tranche) :
--   * une séance est créée manuellement, sans module planning : elle est
--     qualifiée d'« exceptionnelle » et son motif (`exception_reason`) est
--     obligatoire ;
--   * la séance référence directement un compte `user_account` portant un
--     rôle actif `TEACHER` (`teacher_user_id`) ;
--   * une séance cible au moins une classe (`session_class`) ;
--   * cycle de vie PLANNED -> OPEN -> CLOSED, sans réouverture ;
--   * un unique point de contrôle d'émargement par séance
--     (`attendance_checkpoint`, `course_session_id` UNIQUE) ;
--   * une présence unique par point de contrôle et par inscription
--     (`attendance_record`, contrainte UNIQUE — autorité contre la
--     concurrence).
--
-- Ce lot NE crée PAS : attendance_correction, justification, résumé de
-- demi-journée, planning, QR fixe de salle, contrôle réseau. Aucun jeton
-- d'émargement n'est stocké en base (Redis uniquement).
--
-- Conventions identiques à V1/V4..V8 : PK BIGINT UNSIGNED AUTO_INCREMENT,
-- identifiant public UUID (BINARY(16)), suppression RESTRICT, horodatage
-- UTC (TIMESTAMP(6)), verrouillage optimiste (`version`), colonnes auteur
-- (`*_by_id`) en FK RESTRICT vers `user_account`. Aucune donnée métier
-- n'est insérée ici (les données de démonstration relèvent du profil
-- `demo`, jamais d'une migration).
--
-- Frontières modulaires (Spring Modulith) :
--   * `course_session`, `session_class`, `attendance_checkpoint`
--     appartiennent au module `coursesession` ;
--   * `attendance_record` appartient au module `attendance` ;
--   * `attendance_record.attendance_checkpoint_id`,
--     `attendance_record.enrollment_id`, `session_class.class_group_id` et
--     `course_session.teacher_user_id` sont de simples valeurs techniques
--     (clés étrangères SQL) — aucun partage d'entité JPA entre modules,
--     la résolution passe par des ports publics.

-- ---------------------------------------------------------------------------
-- course_session : séance exceptionnelle (module `coursesession`).
-- ---------------------------------------------------------------------------
CREATE TABLE course_session (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id        BINARY(16)      NOT NULL,
    teacher_user_id  BIGINT UNSIGNED NOT NULL,
    title            VARCHAR(191)    NULL,       -- libellé libre facultatif (aucun module matière)
    status           VARCHAR(20)     NOT NULL,   -- PLANNED | OPEN | CLOSED
    starts_at        TIMESTAMP(6)    NOT NULL,
    ends_at          TIMESTAMP(6)    NOT NULL,
    time_zone_id     VARCHAR(64)     NOT NULL,   -- fuseau IANA de saisie (affichage)
    exception_reason VARCHAR(500)    NOT NULL,   -- motif obligatoire de la séance exceptionnelle
    opened_at        TIMESTAMP(6)    NULL,
    opened_by_id     BIGINT UNSIGNED NULL,
    closed_at        TIMESTAMP(6)    NULL,
    closed_by_id     BIGINT UNSIGNED NULL,
    created_at       TIMESTAMP(6)    NOT NULL,
    created_by_id    BIGINT UNSIGNED NULL,
    updated_at       TIMESTAMP(6)    NOT NULL,
    updated_by_id    BIGINT UNSIGNED NULL,
    version          BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_course_session_public_id UNIQUE (public_id),
    CONSTRAINT chk_course_session_period CHECK (ends_at > starts_at),
    -- Cohérence minimale du cycle de vie : opened_at renseigné dès qu'on
    -- dépasse PLANNED, closed_at renseigné uniquement en CLOSED.
    CONSTRAINT chk_course_session_open_state CHECK (
        (status = 'PLANNED' AND opened_at IS NULL AND closed_at IS NULL)
        OR (status = 'OPEN' AND opened_at IS NOT NULL AND closed_at IS NULL)
        OR (status = 'CLOSED' AND opened_at IS NOT NULL AND closed_at IS NOT NULL)
    ),

    CONSTRAINT fk_course_session_teacher FOREIGN KEY (teacher_user_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_session_opened_by FOREIGN KEY (opened_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_session_closed_by FOREIGN KEY (closed_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_session_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_session_updated_by FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_course_session_teacher ON course_session (teacher_user_id);
CREATE INDEX idx_course_session_status ON course_session (status);
CREATE INDEX idx_course_session_window ON course_session (starts_at, ends_at);

-- ---------------------------------------------------------------------------
-- session_class : classes rattachées à une séance (module `coursesession`).
-- Table de jointure ; au moins une ligne par séance (contrôle applicatif).
-- ---------------------------------------------------------------------------
CREATE TABLE session_class (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id         BINARY(16)      NOT NULL,
    course_session_id BIGINT UNSIGNED NOT NULL,
    class_group_id    BIGINT UNSIGNED NOT NULL,
    created_at        TIMESTAMP(6)    NOT NULL,
    version           BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_session_class_public_id UNIQUE (public_id),
    -- Aucune classe en double pour une même séance.
    CONSTRAINT uq_session_class_pair UNIQUE (course_session_id, class_group_id),

    CONSTRAINT fk_session_class_session FOREIGN KEY (course_session_id) REFERENCES course_session (id) ON DELETE RESTRICT,
    CONSTRAINT fk_session_class_class_group FOREIGN KEY (class_group_id) REFERENCES class_group (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_session_class_session ON session_class (course_session_id);
CREATE INDEX idx_session_class_class_group ON session_class (class_group_id);

-- ---------------------------------------------------------------------------
-- attendance_checkpoint : point de contrôle unique d'une séance
-- (module `coursesession`). Créé en même temps que la séance ; ouvert /
-- fermé avec elle. `course_session_id` UNIQUE = un seul point de contrôle
-- par séance dans cette tranche (limite explicitement documentée). Aucun
-- statut propre : l'état vient de la séance (`opened_at` / `closed_at`).
-- ---------------------------------------------------------------------------
CREATE TABLE attendance_checkpoint (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id         BINARY(16)      NOT NULL,
    course_session_id BIGINT UNSIGNED NOT NULL,
    opened_at         TIMESTAMP(6)    NULL,
    closed_at         TIMESTAMP(6)    NULL,
    created_at        TIMESTAMP(6)    NOT NULL,
    updated_at        TIMESTAMP(6)    NOT NULL,
    version           BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_attendance_checkpoint_public_id UNIQUE (public_id),
    CONSTRAINT uq_attendance_checkpoint_session UNIQUE (course_session_id),

    CONSTRAINT fk_attendance_checkpoint_session FOREIGN KEY (course_session_id) REFERENCES course_session (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- attendance_record : présence enregistrée (module `attendance`).
--
-- `attendance_checkpoint_id` et `enrollment_id` sont des valeurs
-- techniques (FK SQL) résolues via des ports publics
-- (`coursesession.CourseSessionDirectory`, `enrollment.EnrollmentDirectory`).
-- `student_user_id` fige le compte de l'apprenant émargeur pour la
-- traçabilité.
--
-- Contrainte centrale (RG-015, docs/02 §17.13) : une présence unique par
-- point de contrôle et par inscription. C'est cette contrainte SQL — et
-- non un pré-contrôle applicatif — qui garantit l'anti-double émargement
-- en concurrence ; une violation est retraduite en conflit métier (409),
-- jamais en 500.
-- ---------------------------------------------------------------------------
CREATE TABLE attendance_record (
    id                       BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id                BINARY(16)      NOT NULL,
    attendance_checkpoint_id BIGINT UNSIGNED NOT NULL,
    enrollment_id            BIGINT UNSIGNED NOT NULL,
    student_user_id          BIGINT UNSIGNED NOT NULL,
    recorded_at              TIMESTAMP(6)    NOT NULL,
    source                   VARCHAR(20)     NOT NULL,   -- DYNAMIC_QR | SHORT_CODE
    created_at               TIMESTAMP(6)    NOT NULL,
    updated_at               TIMESTAMP(6)    NOT NULL,
    version                  BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_attendance_record_public_id UNIQUE (public_id),
    CONSTRAINT uq_attendance_record_checkpoint_enrollment UNIQUE (attendance_checkpoint_id, enrollment_id),

    CONSTRAINT fk_attendance_record_checkpoint FOREIGN KEY (attendance_checkpoint_id) REFERENCES attendance_checkpoint (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_record_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollment (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_record_student FOREIGN KEY (student_user_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_attendance_record_checkpoint ON attendance_record (attendance_checkpoint_id);
CREATE INDEX idx_attendance_record_enrollment ON attendance_record (enrollment_id);
