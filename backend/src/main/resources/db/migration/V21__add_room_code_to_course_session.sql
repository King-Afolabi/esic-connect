-- Salle d'une séance (docs/02-cahier-des-charges.md §7.2, §13.5 ;
-- EF-PLAN-009, EF-ORG-004).
--
-- POURQUOI CETTE COLONNE MANQUAIT, ET CE QU'ELLE DÉBLOQUE
--
-- Le planning transporte déjà `room_code` de bout en bout : la colonne
-- d'import, l'entrée de planning, et jusqu'à la commande de publication
-- (`PlanningSessionWriter.PlannedSession.roomCode`). Mais la séance créée
-- ne la conservait pas. Conséquence : le contrôle de conflit de salle ne
-- pouvait s'exercer qu'à l'INTÉRIEUR d'un même fichier. Deux imports
-- successifs pouvaient placer deux classes dans la même salle à la même
-- heure sans que rien ne le signale — exactement ce que le cahier demande
-- de détecter (§13.5 : « une salle occupée simultanément »).
--
-- CODE FONCTIONNEL, PAS DE CLÉ ÉTRANGÈRE
--
-- Même choix que pour `planning_entry` : la salle est identifiée par son
-- code, tel qu'écrit dans le fichier source. Une clé étrangère vers
-- `room` obligerait à refuser un planning dont une salle n'est pas encore
-- créée, alors que le cahier prévoit explicitement qu'une salle puisse
-- être « laissée provisoirement indéterminée » puis affectée plus tard
-- (§7.2, RG-044).
ALTER TABLE course_session
    ADD COLUMN room_code VARCHAR(50) NULL
        COMMENT 'Code fonctionnel de salle, repris du planning ; NULL si indeterminee (RG-044).';

-- Recherche des séances occupant une salle sur une fenêtre : c'est la
-- requête du contrôle de conflit, faite à chaque simulation de planning.
CREATE INDEX idx_course_session_room_window
    ON course_session (room_code, starts_at);
