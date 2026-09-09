-- V30 — Analyse antivirus des pièces jointes (EF-JUS-002 ; docs/02 §19.2 :
-- « Une analyse antivirus est appliquée avant mise à disposition. Tant
-- qu'elle n'a pas rendu son verdict, la pièce est en quarantaine et n'est
-- pas téléchargeable. »).
--
-- Le verdict est une colonne à part entière, et non un booléen « safe » :
-- il faut pouvoir distinguer QUATRE situations que rien ne permettait de
-- séparer jusqu'ici, et dont trois ne sont pas « sain » :
--
--   NOT_SCANNED  aucun analyseur n'est configuré — le produit le DIT
--                plutôt que de laisser croire à un contrôle ;
--   CLEAN        un analyseur s'est prononcé et n'a rien trouvé ;
--   INFECTED     signature détectée : le contenu n'est jamais servi ;
--   UNAVAILABLE  l'analyseur est configuré mais n'a pas répondu.
--
-- `NOT_SCANNED` par défaut : les pièces déjà stockées n'ont jamais été
-- analysées, et les marquer `CLEAN` rétroactivement serait une affirmation
-- que rien ne fonde.

ALTER TABLE justification_attachment
    ADD COLUMN scan_status VARCHAR(16) NOT NULL DEFAULT 'NOT_SCANNED' AFTER status,
    ADD COLUMN scanned_at TIMESTAMP(6) NULL AFTER scan_status,
    -- Nom de signature renvoyé par l'analyseur. Jamais de chemin, jamais
    -- de contenu de fichier.
    ADD COLUMN scan_signature VARCHAR(255) NULL AFTER scanned_at;

ALTER TABLE justification_attachment
    ADD CONSTRAINT chk_justification_attachment_scan_status
        CHECK (scan_status IN ('NOT_SCANNED', 'CLEAN', 'INFECTED', 'UNAVAILABLE'));

-- Un verdict rendu porte forcément sa date : sans quoi « analysé » et
-- « jamais soumis à l'analyse » se confondraient.
ALTER TABLE justification_attachment
    ADD CONSTRAINT chk_justification_attachment_scan_dated
        CHECK ((scan_status = 'NOT_SCANNED') OR (scanned_at IS NOT NULL));

CREATE INDEX idx_justification_attachment_scan
    ON justification_attachment (scan_status, storage_key);
