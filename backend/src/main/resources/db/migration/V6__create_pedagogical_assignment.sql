-- Périmètre pédagogique : affectation d'un responsable pédagogique à une
-- formation (docs/02-cahier-des-charges.md §6.5, §29.3 ; RG-004, RG-010,
-- RG-011). Une affectation relie un compte porteur du rôle
-- PEDAGOGICAL_MANAGER à un `program`, avec un rôle d'affectation
-- (PRIMARY_MANAGER unique par formation, DELEGATE multiples et
-- chevauchements autorisés) et une période de validité.
--
-- Conventions identiques à V1/V4/V5 : PK BIGINT UNSIGNED AUTO_INCREMENT,
-- identifiant public UUID (BINARY(16)), suppression RESTRICT, horodatage
-- UTC (TIMESTAMP(6)), verrouillage optimiste (`version`), colonnes auteur
-- en FK RESTRICT vers `user_account`. Aucune donnée métier insérée ici.
--
-- La validité est exprimée en DATE (jour civil), pas en instant : la
-- borne `valid_until` est inclusive (une affectation reste effective le
-- jour de `valid_until`). `delegated_by_id` trace l'auteur de la
-- délégation ; `reason` porte le motif de l'affectation, `close_reason`
-- le motif de la clôture.
--
-- `manager_user_id` et `delegated_by_id` sont des valeurs techniques (FK
-- SQL vers `user_account`) : le module `academic` n'importe jamais
-- `identity.internal` et ne partage aucune entité JPA avec `identity` ;
-- la cohérence repose sur ces FK et sur le port `identity.UserDirectory`.

CREATE TABLE pedagogical_assignment (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id          BINARY(16)      NOT NULL,
    program_id         BIGINT UNSIGNED NOT NULL,
    manager_user_id    BIGINT UNSIGNED NOT NULL,
    assignment_role    VARCHAR(30)     NOT NULL,  -- PRIMARY_MANAGER | DELEGATE
    status             VARCHAR(30)     NOT NULL,  -- ACTIVE | CLOSED
    valid_from         DATE            NOT NULL,
    valid_until        DATE            NULL,      -- inclusif ; NULL = ouvert
    reason             VARCHAR(500)    NULL,
    close_reason       VARCHAR(500)    NULL,
    delegated_by_id    BIGINT UNSIGNED NULL,
    -- Un seul PRIMARY_MANAGER ACTIF par formation. Le créneau n'est libéré
    -- que par une clôture explicite (status = CLOSED), jamais par la seule
    -- expiration de la période. Colonne virtuelle indexable (cf. V1
    -- `user_role.active_assignment_key`).
    active_primary_key BIGINT UNSIGNED GENERATED ALWAYS AS (
        IF(status = 'ACTIVE' AND assignment_role = 'PRIMARY_MANAGER', program_id, NULL)) VIRTUAL,
    created_at         TIMESTAMP(6)    NOT NULL,
    created_by_id      BIGINT UNSIGNED NULL,
    updated_at         TIMESTAMP(6)    NOT NULL,
    updated_by_id      BIGINT UNSIGNED NULL,
    version            BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_pedagogical_assignment_public_id UNIQUE (public_id),
    CONSTRAINT uq_pedagogical_assignment_active_primary UNIQUE (active_primary_key),
    CONSTRAINT chk_pedagogical_assignment_period CHECK (valid_until IS NULL OR valid_until >= valid_from),

    CONSTRAINT fk_pedagogical_assignment_program FOREIGN KEY (program_id) REFERENCES program (id) ON DELETE RESTRICT,
    CONSTRAINT fk_pedagogical_assignment_manager FOREIGN KEY (manager_user_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_pedagogical_assignment_delegated_by FOREIGN KEY (delegated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_pedagogical_assignment_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_pedagogical_assignment_updated_by FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_pedagogical_assignment_program ON pedagogical_assignment (program_id);
CREATE INDEX idx_pedagogical_assignment_manager ON pedagogical_assignment (manager_user_id);
CREATE INDEX idx_pedagogical_assignment_status ON pedagogical_assignment (status);
