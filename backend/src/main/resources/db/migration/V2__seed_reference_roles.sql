-- Rôles système de référence (docs/02-cahier-des-charges.md §7 / §10.2).
-- Aucun compte utilisateur n'est créé ici.
INSERT INTO role (public_id, code, name, description, system_role, active, created_at, updated_at, version) VALUES
    (UUID_TO_BIN(UUID()), 'SUPER_ADMIN', 'Super administrateur', 'Contrôle technique global, usage exceptionnel et fortement audité.', TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
    (UUID_TO_BIN(UUID()), 'ADMIN', 'Administrateur', 'Administration fonctionnelle globale.', TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
    (UUID_TO_BIN(UUID()), 'SCHOOL_ADMINISTRATION', 'Administration scolaire', 'Suivi de l''assiduité et des justificatifs à l''échelle de l''établissement.', TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
    (UUID_TO_BIN(UUID()), 'PEDAGOGICAL_MANAGER', 'Responsable pédagogique', 'Gestion d''une ou plusieurs formations.', TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
    (UUID_TO_BIN(UUID()), 'TEACHER', 'Formateur', 'Animation des séances et émargement.', TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
    (UUID_TO_BIN(UUID()), 'STUDENT', 'Apprenant', 'Émargement et consultation de son assiduité.', TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0);
