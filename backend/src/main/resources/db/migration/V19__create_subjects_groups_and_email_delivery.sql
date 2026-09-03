-- Matières, groupes temporaires et suivi de délivrabilité des courriels
-- (docs/02-cahier-des-charges.md §6.4, §6.5, §11.3 ; EF-ACA-006,
-- EF-ACA-007, EF-USER-008).
--
-- Conventions identiques à V1 / V5 / V7 : PK BIGINT UNSIGNED
-- AUTO_INCREMENT, identifiant public UUID (BINARY(16)), suppression
-- RESTRICT, horodatage UTC, verrouillage optimiste (`version`).

-- ---------------------------------------------------------------------
-- Matières (EF-ACA-006, docs/02 §6.4)
-- ---------------------------------------------------------------------
-- Une matière n'a PAS de formateur unique global : l'affectation se fait
-- au niveau de la séance, d'une période, ou d'une association
-- classe–matière–période. Aucune colonne `teacher_id` ici, donc, et il
-- ne faut pas en ajouter.
CREATE TABLE subject (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id      BINARY(16)      NOT NULL,
    code           VARCHAR(40)     NOT NULL,
    name           VARCHAR(200)    NOT NULL,
    description    VARCHAR(1000)   NULL,
    -- Volume horaire INDICATIF (docs/02 §6.4) : sert au cadrage
    -- pédagogique, jamais au calcul d'assiduité, qui se fonde sur les
    -- séances réellement attendues.
    -- INT et non SMALLINT UNSIGNED : Hibernate valide le schéma en
    -- comparant les types, et un entier non signé ne correspond à
    -- aucun type Java. La borne utile est portée par le CHECK.
    hourly_volume  INT             NULL,
    status         VARCHAR(20)     NOT NULL,
    archived_at    TIMESTAMP(6)    NULL,
    created_at     TIMESTAMP(6)    NOT NULL,
    updated_at     TIMESTAMP(6)    NOT NULL,
    created_by_id  BIGINT UNSIGNED NULL,
    updated_by_id  BIGINT UNSIGNED NULL,
    version        BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_subject_public_id UNIQUE (public_id),
    CONSTRAINT uq_subject_code UNIQUE (code),
    CONSTRAINT ck_subject_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_subject_hourly_volume CHECK (hourly_volume IS NULL OR hourly_volume > 0),
    CONSTRAINT fk_subject_created_by FOREIGN KEY (created_by_id)
        REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_subject_updated_by FOREIGN KEY (updated_by_id)
        REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_subject_status ON subject (status);

-- Rattachement d'une matière à une ou plusieurs formations (docs/02 §6.4).
CREATE TABLE subject_program (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    subject_id  BIGINT UNSIGNED NOT NULL,
    program_id  BIGINT UNSIGNED NOT NULL,
    created_at  TIMESTAMP(6)    NOT NULL,

    CONSTRAINT uq_subject_program UNIQUE (subject_id, program_id),
    CONSTRAINT fk_subject_program_subject FOREIGN KEY (subject_id)
        REFERENCES subject (id) ON DELETE RESTRICT,
    CONSTRAINT fk_subject_program_program FOREIGN KEY (program_id)
        REFERENCES program (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_subject_program_program ON subject_program (program_id);

-- ---------------------------------------------------------------------
-- Groupes temporaires (EF-ACA-007, docs/02 §6.5)
-- ---------------------------------------------------------------------
-- Un groupe rassemble des apprenants issus d'UNE OU PLUSIEURS classes,
-- pour une période (langues, options, projets). Il peut être la cible
-- d'un créneau de planning, mais ne remplace jamais la classe principale
-- de l'apprenant (RG-022).
CREATE TABLE student_group (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id         BINARY(16)      NOT NULL,
    code              VARCHAR(40)     NOT NULL,
    name              VARCHAR(200)    NOT NULL,
    academic_year_id  BIGINT UNSIGNED NOT NULL,
    -- Formation de rattachement : porte le périmètre pédagogique. Un
    -- responsable ne gère que les groupes de SES formations.
    program_id        BIGINT UNSIGNED NOT NULL,
    subject_id        BIGINT UNSIGNED NULL,
    starts_on         DATE            NULL,
    ends_on           DATE            NULL,
    status            VARCHAR(20)     NOT NULL,
    archived_at       TIMESTAMP(6)    NULL,
    created_at        TIMESTAMP(6)    NOT NULL,
    updated_at        TIMESTAMP(6)    NOT NULL,
    created_by_id     BIGINT UNSIGNED NULL,
    updated_by_id     BIGINT UNSIGNED NULL,
    version           BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_student_group_public_id UNIQUE (public_id),
    CONSTRAINT uq_student_group_code_year UNIQUE (code, academic_year_id),
    CONSTRAINT ck_student_group_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_student_group_period CHECK (starts_on IS NULL OR ends_on IS NULL OR starts_on <= ends_on),
    CONSTRAINT fk_student_group_year FOREIGN KEY (academic_year_id)
        REFERENCES academic_year (id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_group_program FOREIGN KEY (program_id)
        REFERENCES program (id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_group_subject FOREIGN KEY (subject_id)
        REFERENCES subject (id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_group_created_by FOREIGN KEY (created_by_id)
        REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_group_updated_by FOREIGN KEY (updated_by_id)
        REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_student_group_program ON student_group (program_id, status);

-- Appartenance à un groupe. La cible est l'INSCRIPTION, non le profil :
-- un apprenant qui change de classe en cours d'année garde une trace de
-- son appartenance au groupe pour la période concernée (RG-006).
CREATE TABLE student_group_member (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id         BINARY(16)      NOT NULL,
    student_group_id  BIGINT UNSIGNED NOT NULL,
    enrollment_id     BIGINT UNSIGNED NOT NULL,
    status            VARCHAR(20)     NOT NULL,
    joined_at         TIMESTAMP(6)    NOT NULL,
    left_at           TIMESTAMP(6)    NULL,
    created_at        TIMESTAMP(6)    NOT NULL,
    created_by_id     BIGINT UNSIGNED NULL,
    version           BIGINT UNSIGNED NOT NULL DEFAULT 0,

    -- Vaut 1 tant que l'appartenance est active, NULL sinon : un même
    -- apprenant ne peut pas être deux fois dans le même groupe, sans
    -- empêcher un retour après un départ.
    active_member_key TINYINT UNSIGNED GENERATED ALWAYS AS
                          (IF(status = 'ACTIVE', 1, NULL)) VIRTUAL,

    CONSTRAINT uq_student_group_member_public_id UNIQUE (public_id),
    CONSTRAINT uq_student_group_member_active
        UNIQUE (student_group_id, enrollment_id, active_member_key),
    CONSTRAINT ck_student_group_member_status CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT fk_student_group_member_group FOREIGN KEY (student_group_id)
        REFERENCES student_group (id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_group_member_enrollment FOREIGN KEY (enrollment_id)
        REFERENCES enrollment (id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_group_member_created_by FOREIGN KEY (created_by_id)
        REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_student_group_member_enrollment ON student_group_member (enrollment_id);

-- ---------------------------------------------------------------------
-- Suivi de délivrabilité des courriels (EF-USER-008, docs/02 §11.3)
-- ---------------------------------------------------------------------
-- DEUX AXES DISTINCTS, et c'est tout l'objet de cette table :
--   * `internal_status` — ce que NOUS savons : le message a été mis en
--     file, remis au serveur de messagerie, ou son traitement a échoué ;
--   * `provider_status` — ce que le FOURNISSEUR nous remonte : délivré,
--     rejeté, plainte… Reste `UNKNOWN` tant qu'il ne dit rien, ce qui est
--     le cas de Mailpit en local.
--
-- « Remis au serveur de messagerie » n'est PAS « délivré » : confondre
-- les deux ferait croire qu'une invitation est arrivée alors que
-- l'adresse est erronée (docs/02 §11.3, risque « adresses électroniques
-- invalides »).
--
-- L'adresse n'est PAS stockée en clair : seule son empreinte l'est, plus
-- une forme masquée destinée à l'affichage (`j***@e***.fr`). L'écran de
-- suivi montre à qui de droit ce qu'il faut pour agir, sans constituer
-- un annuaire exploitable en cas de fuite (docs/08 minimisation).
CREATE TABLE email_delivery (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id         BINARY(16)      NOT NULL,
    recipient_hash    VARCHAR(64)     NOT NULL,
    recipient_masked  VARCHAR(120)    NOT NULL,
    user_id           BIGINT UNSIGNED NULL,
    message_type      VARCHAR(40)     NOT NULL,
    internal_status   VARCHAR(30)     NOT NULL,
    provider_status   VARCHAR(30)     NOT NULL,
    attempts          INT             NOT NULL DEFAULT 0,
    last_attempt_at   TIMESTAMP(6)    NULL,
    -- Motif d'échec NON SENSIBLE : catégorie technique, jamais le corps
    -- du message ni le jeton qu'il contenait.
    last_error        VARCHAR(500)    NULL,
    created_at        TIMESTAMP(6)    NOT NULL,
    updated_at        TIMESTAMP(6)    NOT NULL,
    version           BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_email_delivery_public_id UNIQUE (public_id),
    CONSTRAINT ck_email_delivery_internal_status
        CHECK (internal_status IN ('QUEUED', 'SENT_TO_PROVIDER', 'PROCESSING_FAILED')),
    CONSTRAINT ck_email_delivery_provider_status
        CHECK (provider_status IN ('UNKNOWN', 'DELIVERED', 'BOUNCED', 'REJECTED', 'COMPLAINED')),
    CONSTRAINT fk_email_delivery_user FOREIGN KEY (user_id)
        REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_email_delivery_user ON email_delivery (user_id, created_at);
CREATE INDEX idx_email_delivery_status ON email_delivery (internal_status, provider_status);
