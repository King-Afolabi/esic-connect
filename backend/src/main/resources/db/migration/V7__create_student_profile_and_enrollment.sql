-- Inscriptions des apprenants (docs/02-cahier-des-charges.md §7.6, §9.2,
-- §13 ; docs/04-modele-donnees.md §11.1 et §13 ; RG-006, RG-012, RG-022,
-- RG-023 ; AC-006).
--
-- Modèle final du parcours apprenant (2026-09) :
--
--     user_account
--         |-- user_role -> role -> STUDENT   (statut apprenant : SEULE source de vérité)
--         `-- enrollment (0..N)              (historique d'inscriptions)
--
-- Un compte est un apprenant s'il porte un rôle actif STUDENT — ni plus,
-- ni moins. Aucune autre table ne conditionne ce statut. `enrollment`
-- référence directement `user_account` (`user_id`) : une inscription ne
-- présuppose ni ne crée aucun objet intermédiaire. Un compte STUDENT sans
-- aucune ligne `enrollment` reste un apprenant valide (apprenant non
-- encore affecté à une classe) ; plusieurs `enrollment` pour un même
-- compte restent un seul et même apprenant.
--
-- Il n'existe PLUS de table `student_profile`. Les données qu'elle
-- portait ont été réparties selon leur nature réelle (revue du contenu et
-- des usages, pas une décision par défaut) :
--   * `student_number` (numéro étudiant) et `birth_date` (date de
--     naissance) sont des données personnelles générales, indépendantes
--     de toute inscription particulière (un numéro étudiant ne change pas
--     quand l'apprenant change de classe) -> déplacées sur `user_account`
--     (migration V1), colonnes facultatives, sans lien vers ce fichier ;
--   * `work_study` (alternance) et `company_name` (entreprise) décrivent
--     la situation de l'apprenant PENDANT une inscription donnée (elle
--     peut changer d'une année/classe à l'autre) -> déplacées sur
--     `enrollment`, ci-dessous ;
--   * le statut du profil (`ACTIVE` / `ARCHIVED`) n'a plus d'objet : le
--     statut du compte (`user_account.status`) et celui de l'inscription
--     (`enrollment.status`) couvrent déjà tout besoin fonctionnel réel.
--
-- Conventions identiques à V1/V4/V5/V6 : PK BIGINT UNSIGNED
-- AUTO_INCREMENT, identifiant public UUID (BINARY(16)), suppression
-- RESTRICT, horodatage UTC (TIMESTAMP(6)), verrouillage optimiste
-- (`version`), colonnes auteur en FK RESTRICT vers `user_account`. Aucune
-- donnée métier n'est insérée ici.
--
-- `enrollment.user_id` est une valeur technique (FK SQL vers
-- `user_account`) : le module `enrollment` n'importe jamais
-- `identity.internal` et ne partage aucune entité JPA avec `identity` ;
-- la cohérence repose sur cette FK et sur le port `identity.UserDirectory`.
-- De même, `enrollment.class_group_id` / `academic_year_id` sont des
-- valeurs techniques (FK SQL vers `class_group` / `academic_year`),
-- résolues via le port `academic.ClassGroupDirectory`.

CREATE TABLE enrollment (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id             BINARY(16)      NOT NULL,
    -- Compte apprenant directement rattaché (module identity, rôle
    -- STUDENT). Aucune indirection : ni profil, ni autre table.
    user_id               BIGINT UNSIGNED NOT NULL,
    class_group_id        BIGINT UNSIGNED NOT NULL,
    academic_year_id      BIGINT UNSIGNED NOT NULL,
    start_date            DATE            NOT NULL,
    end_date              DATE            NULL,       -- renseigné à la clôture (§13.2)
    status                VARCHAR(30)     NOT NULL,   -- PENDING|ACTIVE|COMPLETED|TRANSFERRED|WITHDRAWN|SUSPENDED|ARCHIVED
    enrollment_source     VARCHAR(50)     NOT NULL,   -- MANUAL | CLASS_TRANSFER
    change_reason         VARCHAR(500)    NULL,
    previous_enrollment_id BIGINT UNSIGNED NULL,      -- inscription clôturée dont celle-ci prend la suite
    -- Situation d'alternance PENDANT cette inscription (ex-`student_profile`,
    -- déplacée ici car elle est propre à une période d'inscription, pas à
    -- la personne en général — cf. note en tête de fichier). Renseignée par
    -- la création manuelle ou l'import CSV (studentimport) ; sans lien avec
    -- les rythmes d'alternance par classe (`work_study_pattern` /
    -- `class_work_study_pattern`, module `alternation`, migration V8), qui
    -- restent le mécanisme de référence pour le calcul d'assiduité.
    work_study            BOOLEAN         NOT NULL DEFAULT FALSE,
    company_name          VARCHAR(191)    NULL,

    -- Unicité d'une inscription ACTIVE par apprenant et par période
    -- (docs/04 §13.3, RG-012). MySQL n'a pas d'index partiel : deux
    -- colonnes virtuelles ne portent la valeur que pour une inscription
    -- ACTIVE (NULL sinon), et une UNIQUE composite ne contraint donc que
    -- les lignes ACTIVE (cf. V6 `active_primary_key`). Une clôture
    -- (status != ACTIVE) libère immédiatement le créneau.
    active_student_key    BIGINT UNSIGNED GENERATED ALWAYS AS (
        IF(status = 'ACTIVE', user_id, NULL)) VIRTUAL,
    active_year_key       BIGINT UNSIGNED GENERATED ALWAYS AS (
        IF(status = 'ACTIVE', academic_year_id, NULL)) VIRTUAL,

    created_at            TIMESTAMP(6)    NOT NULL,
    created_by_id         BIGINT UNSIGNED NULL,
    updated_at            TIMESTAMP(6)    NOT NULL,
    updated_by_id         BIGINT UNSIGNED NULL,
    version               BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_enrollment_public_id UNIQUE (public_id),
    CONSTRAINT uq_enrollment_active_per_year UNIQUE (active_student_key, active_year_key),
    CONSTRAINT chk_enrollment_period CHECK (end_date IS NULL OR end_date >= start_date),

    CONSTRAINT fk_enrollment_user FOREIGN KEY (user_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_enrollment_class_group FOREIGN KEY (class_group_id) REFERENCES class_group (id) ON DELETE RESTRICT,
    CONSTRAINT fk_enrollment_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_year (id) ON DELETE RESTRICT,
    CONSTRAINT fk_enrollment_previous FOREIGN KEY (previous_enrollment_id) REFERENCES enrollment (id) ON DELETE RESTRICT,
    CONSTRAINT fk_enrollment_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_enrollment_updated_by FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_enrollment_user ON enrollment (user_id);
CREATE INDEX idx_enrollment_class_group ON enrollment (class_group_id);
CREATE INDEX idx_enrollment_academic_year ON enrollment (academic_year_id);
CREATE INDEX idx_enrollment_status ON enrollment (status);
CREATE INDEX idx_enrollment_previous ON enrollment (previous_enrollment_id);
