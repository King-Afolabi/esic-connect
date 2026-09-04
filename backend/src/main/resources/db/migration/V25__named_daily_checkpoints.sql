-- V25 — Quatre points de contrôle journaliers nommés (EF-ATT-003 ;
-- docs/02 §16.2) et résultat journalier (EF-ATT-004 ; §16.3).
--
-- V10 avait généralisé le point de contrôle unique de V9 en points typés
-- START / END / CUSTOM, en indiquant que les quatre types du cahier
-- restaient « réalisables via des points CUSTOM libellés ». C'était vrai
-- fonctionnellement et faux structurellement : un calcul journalier ne
-- peut pas se fonder sur un libellé libre. Les quatre types deviennent
-- donc des valeurs à part entière.
--
-- START et END sont CONSERVÉS : ils portent les séances existantes et le
-- parcours d'émargement nominal, qui ne relève pas du découpage
-- journalier. Les remplacer aurait cassé toutes les séances déjà créées
-- pour un gain nul.
--
-- V10 n'est pas modifiée : sa contrainte est remplacée par une nouvelle.

-- `AFTERNOON_BREAK_RETURN` fait 21 caractères : la colonne VARCHAR(20)
-- posée par V10 tronquerait la valeur. Élargie avant tout le reste.
-- `MODIFY COLUMN` REMPLACE la définition entière : sans répéter
-- `DEFAULT 'START'`, la valeur par défaut posée par V10 disparaîtrait, et
-- toute insertion qui l'omet échouerait. Elle est donc reconduite ici.
ALTER TABLE attendance_checkpoint
    MODIFY COLUMN checkpoint_type VARCHAR(32) NOT NULL DEFAULT 'START';

ALTER TABLE attendance_checkpoint
    DROP CHECK chk_attendance_checkpoint_type;

ALTER TABLE attendance_checkpoint
    ADD CONSTRAINT chk_attendance_checkpoint_type
        CHECK (checkpoint_type IN (
            'START', 'END', 'CUSTOM',
            'MORNING_ARRIVAL', 'MORNING_BREAK_RETURN',
            'AFTERNOON_ARRIVAL', 'AFTERNOON_BREAK_RETURN'));

-- Un point de contrôle nommé est unique par séance : deux
-- MORNING_ARRIVAL sur la même séance rendraient le résultat journalier
-- indéterminé. Les types START / END / CUSTOM restent libres d'être
-- répétés — c'est leur usage depuis V10.
CREATE UNIQUE INDEX uk_attendance_checkpoint_named
    ON attendance_checkpoint (
        course_session_id,
        (CASE WHEN checkpoint_type IN ('MORNING_ARRIVAL', 'MORNING_BREAK_RETURN',
                                       'AFTERNOON_ARRIVAL', 'AFTERNOON_BREAK_RETURN')
              THEN checkpoint_type END));
