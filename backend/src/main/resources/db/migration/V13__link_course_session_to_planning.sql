-- Lien `course_session` ↔ créneau de planning + discriminant d'origine
-- (docs/reports/G1_ARCHITECTURE_DECISIONS.md DEC-G1-001, DEC-G1-002,
-- DEC-G1-004, DEC-G1-012 ; bloc G1-B ; corrigé à l'audit G1-B.1,
-- 1er septembre 2026).
--
-- Modification ADDITIVE et non destructive de `course_session` (créée en
-- V9). Aucune structure du bloc G1-C (annulation, remplaçant,
-- `teacher_substitution`) n'est posée ici : elle relèvera de V14.
--
--   * `planning_slot_public_id BINARY(16) NULL UNIQUE` : identité STABLE
--     du créneau de planning à l'origine de la séance (DEC-G1-002). Ce
--     n'est PAS `planning_entry.public_id` (qui, lui, est aléatoire et
--     propre à chaque version) : c'est le `planning_entry.slot_public_id`
--     déterministe = `UUIDv3(planning_schedule.public_id || '|' ||
--     slot_key)`, constant d'une version à la suivante. La colonne a été
--     renommée à l'audit G1-B.1 (auparavant `planning_entry_public_id`,
--     nom trompeur : elle n'a jamais contenu un `planning_entry.public_id`).
--     Sert à la fois de LIEN vers le créneau et de DISCRIMINANT D'ORIGINE :
--       - NULL      ⇒ séance exceptionnelle créée manuellement (V9) ;
--       - non NULL  ⇒ séance issue d'un planning publié (EF-SES-001,
--                     RG-016), créée / réutilisée par le port
--                     `coursesession.PlanningSessionWriter`.
--     MySQL autorise plusieurs NULL sous un index UNIQUE : les séances
--     manuelles ne se gênent pas, les UUID non nuls restent uniques
--     (idempotence de la publication par créneau — DEC-G1-001).
--   * `superseded_by_scheduling BOOLEAN NOT NULL DEFAULT FALSE` : vrai
--     quand une republication a retiré le créneau d'origine (DEC-G1-004
--     règle 4). La transition d'état `CANCELLED` correspondante n'existe
--     pas encore dans cette tranche (enum `SessionLifecycle` = PLANNED /
--     OPEN / CLOSED) ; elle arrivera avec G1-C. Une séance
--     `superseded_by_scheduling = TRUE` est traitée comme INACTIVE par
--     tous les accès métier (liste, résolution d'émargement, ouverture,
--     jeton, rapports) — garde centralisée dans `coursesession` (audit
--     G1-B.1). Seul l'historique des versions de planning continue de la
--     référencer.
--   * `exception_reason` rendue NULLABLE : une séance d'origine planning
--     n'a pas de motif d'exception (une séance manuelle en garde un —
--     contrôle applicatif inchangé, RG-017).
--
-- NOTE DE MIGRATION (audit G1-B.1) : V12 et V13 n'ont jamais été poussées
-- ni appliquées hors d'une base jetable `esic_test`. Elles ont été
-- corrigées en place (renommage `planning_entry_public_id` →
-- `planning_slot_public_id`, ajout de `planning_entry.slot_public_id`)
-- plutôt que par une migration corrective, afin de ne pas consommer le
-- numéro V14 réservé au bloc G1-C. Une base de développement locale
-- `esic_connect` déjà à V13 doit être recréée (`DROP DATABASE` +
-- `flyway`/redémarrage) ou réparée (`flyway repair`).
--
-- Conventions V1/V4..V12. Aucune donnée métier modifiée.

ALTER TABLE course_session
    ADD COLUMN planning_slot_public_id BINARY(16) NULL AFTER exception_reason,
    ADD COLUMN superseded_by_scheduling BOOLEAN NOT NULL DEFAULT FALSE AFTER planning_slot_public_id,
    MODIFY COLUMN exception_reason VARCHAR(500) NULL,
    ADD CONSTRAINT uq_course_session_planning_slot UNIQUE (planning_slot_public_id);
