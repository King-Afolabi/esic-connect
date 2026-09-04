-- V23 — Planning construit directement dans le calendrier (EF-PLAN-006 ;
-- docs/02 §13.7).
--
-- Un planning saisi à la main emprunte le MÊME modèle qu'un import :
-- même table de travail, mêmes lignes, mêmes contrôles de conflit, même
-- publication atomique versionnée. C'est le choix structurant du sprint 6 :
-- dupliquer le moteur de conflits pour le calendrier garantirait qu'un
-- jour les deux divergent.
--
-- Une seule conséquence de schéma : ce travail n'a pas de fichier. La
-- contrainte V12 exigeait `file_size_bytes > 0`, ce qui n'a de sens que
-- pour un téléversement. Elle est remplacée par `>= 0` — un fichier réel
-- reste impossible à vide côté applicatif (les gardes CSV et classeur
-- refusent un contenu vide bien avant la base).
--
-- V12 n'est PAS modifiée : sa somme de contrôle resterait valide mais
-- toute base déjà migrée divergerait.

ALTER TABLE planning_import_job
    DROP CHECK chk_planning_import_job_file_size;

ALTER TABLE planning_import_job
    ADD CONSTRAINT chk_planning_import_job_file_size CHECK (file_size_bytes >= 0);
