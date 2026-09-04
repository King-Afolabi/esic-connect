-- Import Excel, classeur multifeuille et correction de ligne avant
-- confirmation (docs/02-cahier-des-charges.md §10.4, §10.7, §36.4 ;
-- EF-IMP-003, EF-IMP-004, EF-IMP-006).

-- ---------------------------------------------------------------------
-- Feuille d'origine d'une ligne (EF-IMP-004)
-- ---------------------------------------------------------------------
-- Le cahier exige de situer une anomalie « fichier, feuille, ligne,
-- colonne » (§10.7). Sans cette colonne, une erreur dans un classeur de
-- trois feuilles serait introuvable : le numéro de ligne seul ne dit pas
-- de quelle feuille il s'agit. NULL pour un CSV, qui n'a pas de feuille.
ALTER TABLE student_import_row
    ADD COLUMN sheet_name VARCHAR(120) NULL
        COMMENT 'Feuille du classeur dont provient la ligne ; NULL pour un CSV (EF-IMP-004).';

-- L'unicité `(job, row_number)` de V11 supposait un fichier plat. Dans un
-- classeur, la ligne 2 existe dans CHAQUE feuille : la contrainte
-- refusait alors la deuxième feuille avec une violation d'unicité — un
-- défaut réel, découvert par le test du classeur de trois feuilles.
--
-- La clé devient `(job, feuille, ligne)`. Une colonne générée porte la
-- feuille ramenée à la chaîne vide pour un CSV : utiliser directement
-- `sheet_name`, qui est NULL, affaiblirait la garantie — MySQL autorise
-- plusieurs NULL dans un index UNIQUE, et deux lignes CSV de même numéro
-- redeviendraient possibles.
ALTER TABLE student_import_row
    DROP INDEX uq_student_import_row_number;

ALTER TABLE student_import_row
    ADD COLUMN sheet_key VARCHAR(120)
        GENERATED ALWAYS AS (IFNULL(sheet_name, '')) STORED;

ALTER TABLE student_import_row
    ADD CONSTRAINT uq_student_import_row_number
        UNIQUE (student_import_job_id, sheet_key, `row_number`);

-- ---------------------------------------------------------------------
-- Correction d'une ligne avant confirmation (EF-IMP-006)
-- ---------------------------------------------------------------------
-- Corriger une ligne en anomalie sans recommencer tout l'import. La
-- correction est TRACÉE : qui, quand, quelle valeur avant, quelle valeur
-- après (§13.6 « journalisation de la correction avec son auteur »).
-- Une ligne peut être corrigée plusieurs fois ; l'historique est
-- conservé, jamais écrasé.
CREATE TABLE student_import_row_correction (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id             BINARY(16)      NOT NULL,
    student_import_row_id BIGINT UNSIGNED NOT NULL,
    field_name            VARCHAR(64)     NOT NULL,
    -- Valeurs bornées : ce sont des champs d'identité et de rattachement,
    -- jamais du texte libre volumineux.
    previous_value        VARCHAR(320)    NULL,
    new_value             VARCHAR(320)    NULL,
    corrected_by_id       BIGINT UNSIGNED NOT NULL,
    corrected_at          TIMESTAMP(6)    NOT NULL,
    created_at            TIMESTAMP(6)    NOT NULL,
    -- Verrouillage optimiste hérité de BaseEntity, comme toutes les
    -- entités du socle (docs/04 §4.1).
    version               BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_student_import_row_correction_public_id UNIQUE (public_id),
    CONSTRAINT fk_student_import_row_correction_row FOREIGN KEY (student_import_row_id)
        REFERENCES student_import_row (id) ON DELETE CASCADE,
    CONSTRAINT fk_student_import_row_correction_actor FOREIGN KEY (corrected_by_id)
        REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_student_import_row_correction_row
    ON student_import_row_correction (student_import_row_id);
