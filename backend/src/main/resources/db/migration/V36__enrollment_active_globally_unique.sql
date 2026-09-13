-- Renforce l'unicité d'une inscription ACTIVE : elle était garantie par
-- apprenant ET par année scolaire (uq_enrollment_active_per_year, V7).
-- Décision métier (Lot 13, 2026-09) : un apprenant ne peut appartenir
-- qu'à UNE SEULE classe active, toutes années académiques confondues —
-- deux inscriptions ACTIVE simultanées (même sur des années différentes)
-- sont désormais une anomalie, plus un cas normal.
--
-- `active_student_key` (colonne générée virtuelle, IF(status='ACTIVE',
-- user_id, NULL)) porte déjà, à elle seule, exactement la sémantique
-- voulue : NULL pour toute ligne non ACTIVE, sinon la valeur du compte.
-- La contrainte composite devient donc une contrainte à une seule
-- colonne ; `active_year_key` n'a plus de rôle et est supprimée.
--
-- SÉCURITÉ DES DONNÉES EXISTANTES : cette migration NE SUPPRIME ET NE
-- CORRIGE SILENCIEUSEMENT AUCUNE LIGNE. Si un apprenant possède déjà
-- deux inscriptions ACTIVE (années différentes — le seul cas que
-- l'ancienne contrainte laissait passer), le dernier ADD CONSTRAINT
-- ci-dessous échoue explicitement (« Duplicate entry » MySQL) : la
-- migration entière échoue et reste marquée en échec par Flyway, sans
-- avoir modifié la table. Charge alors à un opérateur d'identifier les
-- comptes en cause (cf. requête de diagnostic ci-dessous, à exécuter
-- manuellement avant un nouvel essai) et de clôturer manuellement toutes
-- les inscriptions actives sauf une, par apprenant :
--
--   SELECT user_id, COUNT(*) FROM enrollment WHERE status = 'ACTIVE'
--   GROUP BY user_id HAVING COUNT(*) > 1;

ALTER TABLE enrollment DROP INDEX uq_enrollment_active_per_year;
ALTER TABLE enrollment DROP COLUMN active_year_key;
ALTER TABLE enrollment ADD CONSTRAINT uq_enrollment_active_global UNIQUE (active_student_key);
