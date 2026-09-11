-- Rattache une séance à une matière (docs/02 complément — création d'une
-- séance : sélection du formateur ET de la matière qu'il enseigne).
-- Colonne nullable : les séances existantes (créées avant ce lot, ou
-- issues d'un import de planning qui ne porte pas de matière) restent
-- valides sans rétro-saisie obligatoire.
ALTER TABLE course_session
    ADD COLUMN subject_id BIGINT UNSIGNED NULL AFTER teacher_user_id,
    ADD CONSTRAINT fk_course_session_subject FOREIGN KEY (subject_id) REFERENCES subject (id) ON DELETE RESTRICT;

CREATE INDEX idx_course_session_subject ON course_session (subject_id);
