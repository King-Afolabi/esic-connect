-- Pièces jointes des justificatifs d'absence (bloc G1-E ;
-- docs/reports/G1_ARCHITECTURE_DECISIONS.md DEC-G1-008, DEC-G1-009,
-- DEC-G1-012 ; docs/reports/G1_REQUIREMENTS_TRACEABILITY.md §7 ;
-- EF-JUS-001, EF-JUS-002 ; CDC §21.5 ; CDC §43 RG-071/072/073/075/076 ;
-- MDD §21.1).
--
-- Portée de CE fichier : une table `justification_attachment`. Aucune
-- donnée métier insérée. Modification purement additive.
--
-- Décisions de conception (DEC-G1-008 / DEC-G1-009) :
--   * le CONTENU du fichier n'est JAMAIS en base : seules les métadonnées
--     ici (nom d'origine assaini, clé de stockage opaque, type MIME
--     re-dérivé des magic bytes, taille, empreinte SHA-256) ;
--   * `storage_key` = clé opaque aléatoire, JAMAIS dérivée du nom client,
--     unique — le fichier est stocké HORS webroot par le port
--     `com.esic.connect.attendance.JustificationFileStorage` (adaptateur
--     local `LocalFilesystemJustificationFileStorage`) ;
--   * pas de transaction distribuée base <-> système de fichiers :
--     séquence avec compensation (DEC-G1-009). `status` :
--       - `PENDING_STORAGE` : ligne insérée, fichier pas encore déplacé
--         dans sa zone définitive ; JAMAIS renvoyée par l'API ;
--       - `STORED` : fichier en place, pièce visible ;
--       - `DELETED` : suppression logique ; le fichier est retiré par la
--         tâche de réconciliation (ou immédiatement, best effort).
--   * un seul fichier ACTIF (non `DELETED`) par justificatif — colonne
--     générée `active_attachment_key` + index UNIQUE (même motif que
--     `attendance_justification.active_justification_key`, V10) ;
--   * FK RESTRICT vers `attendance_justification` et `user_account` :
--     aucun partage d'entité JPA entre modules.
--
-- Conventions V1/V4..V15 : PK BIGINT UNSIGNED AUTO_INCREMENT, `public_id`
-- BINARY(16) unique, TIMESTAMP(6) UTC, verrou optimiste (`version`),
-- ENGINE=InnoDB, utf8mb4_0900_ai_ci.

CREATE TABLE justification_attachment (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id          BINARY(16)      NOT NULL,
    justification_id   BIGINT UNSIGNED NOT NULL,
    original_file_name VARCHAR(255)    NOT NULL,   -- nom d'origine ASSAINI, borné (affichage seulement)
    storage_key        VARCHAR(180)    NOT NULL,   -- clé opaque, jamais dérivée du nom client
    content_type       VARCHAR(100)    NOT NULL,   -- re-dérivé des magic bytes : application/pdf | image/jpeg | image/png
    size_bytes         BIGINT UNSIGNED NOT NULL,
    sha256             CHAR(64)        NOT NULL,    -- empreinte hexadécimale du contenu
    status             VARCHAR(16)     NOT NULL,   -- PENDING_STORAGE | STORED | DELETED
    created_at         TIMESTAMP(6)    NOT NULL,
    created_by_id      BIGINT UNSIGNED NOT NULL,   -- auteur du dépôt
    stored_at          TIMESTAMP(6)    NULL,
    deleted_at         TIMESTAMP(6)    NULL,
    version            BIGINT UNSIGNED NOT NULL DEFAULT 0,

    -- Colonne générée : vaut justification_id tant que la pièce n'est pas
    -- DELETED, NULL sinon. MySQL autorisant plusieurs NULL dans un index
    -- UNIQUE, la contrainte n'interdit qu'UNE SEULE pièce active par
    -- justificatif (cf. V10 active_justification_key).
    active_attachment_key BIGINT UNSIGNED GENERATED ALWAYS AS (
        IF(status <> 'DELETED', justification_id, NULL)) VIRTUAL,

    CONSTRAINT uq_justification_attachment_public_id UNIQUE (public_id),
    CONSTRAINT uq_justification_attachment_storage_key UNIQUE (storage_key),
    CONSTRAINT uq_justification_attachment_active UNIQUE (active_attachment_key),
    CONSTRAINT chk_justification_attachment_status CHECK (status IN (
        'PENDING_STORAGE', 'STORED', 'DELETED')),
    CONSTRAINT chk_justification_attachment_content_type CHECK (content_type IN (
        'application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT chk_justification_attachment_size CHECK (size_bytes > 0),
    -- `stored_at` renseigné si et seulement si la pièce a atteint STORED
    -- (elle peut ensuite passer DELETED en conservant `stored_at`).
    CONSTRAINT chk_justification_attachment_stored_at CHECK (
        (status = 'PENDING_STORAGE' AND stored_at IS NULL) OR (status <> 'PENDING_STORAGE')),

    CONSTRAINT fk_justification_attachment_justification
        FOREIGN KEY (justification_id) REFERENCES attendance_justification (id) ON DELETE RESTRICT,
    CONSTRAINT fk_justification_attachment_created_by
        FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Réconciliation : lignes PENDING_STORAGE anciennes / DELETED à purger.
CREATE INDEX idx_justification_attachment_status_created
    ON justification_attachment (status, created_at);
CREATE INDEX idx_justification_attachment_justification
    ON justification_attachment (justification_id);
