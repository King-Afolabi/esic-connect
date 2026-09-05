-- V32 — Idempotence de l'écriture d'audit produite par l'outbox
-- (EF-AUD-003 ; docs/02 §23.4).
--
-- Le contrat d'un gestionnaire d'outbox est d'être REJOUABLE : la même
-- ligne peut être traitée deux fois (reprise après un échec partiel,
-- rejeu manuel depuis la file d'échec, arrêt de la JVM entre l'effet et
-- l'enregistrement du succès). Sans clé, un rejeu produirait une SECONDE
-- ligne d'audit pour une seule action — un journal qui compte double est
-- un journal auquel on ne peut plus se fier.
--
-- La colonne est NULLABLE, et c'est délibéré : les lignes d'audit
-- antérieures à cette migration n'ont jamais transité par l'outbox et ne
-- peuvent pas se voir attribuer une clé rétroactivement. MySQL n'applique
-- pas la contrainte d'unicité aux valeurs NULL : elles coexistent sans
-- se gêner.

ALTER TABLE audit_event
    ADD COLUMN outbox_key CHAR(64) NULL AFTER correlation_id;

ALTER TABLE audit_event
    ADD CONSTRAINT uq_audit_event_outbox_key UNIQUE (outbox_key);
