-- Lien `course_session` ↔ `planning_entry` + discriminant d'origine
-- (docs/reports/G1_ARCHITECTURE_DECISIONS.md DEC-G1-001, DEC-G1-004,
-- DEC-G1-012 ; bloc G1-B).
--
-- Modification ADDITIVE et non destructive de `course_session` (créée en
-- V9). Aucune structure du bloc G1-C (annulation, remplaçant,
-- `teacher_substitution`) n'est posée ici : elle relèvera de V14.
--
--   * `planning_entry_public_id BINARY(16) NULL UNIQUE` : identifiant
--     public de l'entrée de planning à l'origine de la séance. Sert à la
--     fois de LIEN (vers `planning_entry`) et de DISCRIMINANT D'ORIGINE :
--       - NULL      ⇒ séance exceptionnelle créée manuellement (V9) ;
--       - non NULL  ⇒ séance issue d'un planning publié (EF-SES-001,
--                     RG-016), créée / réutilisée par le port
--                     `coursesession.PlanningSessionWriter`.
--     MySQL autorise plusieurs NULL sous un index UNIQUE : les séances
--     manuelles ne se gênent pas, les UUID non nuls restent uniques
--     (idempotence de la publication par entrée — DEC-G1-001).
--   * `superseded_by_scheduling BOOLEAN NOT NULL DEFAULT FALSE` : vrai
--     quand une republication a retiré le créneau d'origine (DEC-G1-004
--     règle 4). La transition d'état `CANCELLED` correspondante n'existe
--     pas encore dans cette tranche (enum `SessionLifecycle` = PLANNED /
--     OPEN / CLOSED) ; elle arrivera avec G1-C. Une séance
--     `superseded_by_scheduling = TRUE` est filtrée de l'affichage.
--   * `exception_reason` rendue NULLABLE : une séance d'origine planning
--     n'a pas de motif d'exception (une séance manuelle en garde un —
--     contrôle applicatif inchangé, RG-017).
--
-- Conventions V1/V4..V12. Aucune donnée métier modifiée.

ALTER TABLE course_session
    ADD COLUMN planning_entry_public_id BINARY(16) NULL AFTER exception_reason,
    ADD COLUMN superseded_by_scheduling BOOLEAN NOT NULL DEFAULT FALSE AFTER planning_entry_public_id,
    MODIFY COLUMN exception_reason VARCHAR(500) NULL,
    ADD CONSTRAINT uq_course_session_planning_entry UNIQUE (planning_entry_public_id);
