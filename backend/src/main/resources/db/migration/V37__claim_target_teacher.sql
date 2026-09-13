-- Ciblage facultatif d'un formateur sur une réclamation (Lot 19, 2026-09).
--
-- Le guichet TEACHER ne désignait jusqu'ici aucune personne (docs/02
-- §20.1 : « ce n'est jamais un destinataire nommé, toujours un
-- guichet »). Décision métier validée : permettre à l'auteur de
-- sélectionner facultativement un formateur précis, mémorisé durablement
-- dans le contexte de la réclamation, sans remettre en cause le principe
-- du guichet (une réclamation SANS formateur ciblé se résout toujours
-- via la séance, puis le responsable pédagogique — comportement
-- inchangé).
--
-- Colonne nullable : aucune réclamation existante n'est modifiée. Aucun
-- index dédié : ce champ n'est filtré par aucune requête (il n'entre que
-- dans la résolution de destinataires d'une réclamation déjà identifiée
-- par sa clé primaire), un index serait injustifié.
ALTER TABLE claim
    ADD COLUMN target_teacher_user_id BIGINT UNSIGNED NULL AFTER course_session_id;
