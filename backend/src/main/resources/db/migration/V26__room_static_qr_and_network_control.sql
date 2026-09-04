-- V26 — QR fixe de salle (EF-ORG-003, EF-ATT-010) et contrôle de plage
-- réseau (EF-ATT-008 ; docs/02 §16.6 et §16.7).
--
-- `static_qr_reference` existait depuis V4 comme texte libre saisi à la
-- création d'une salle. C'était suffisant tant que personne ne s'en
-- servait pour émarger ; ça ne l'est plus :
--
--   * un QR de salle doit être NON PRÉDICTIBLE — une valeur saisie à la
--     main sera « A101 » et n'importe qui pourra la fabriquer ;
--   * il doit être UNIQUE — deux salles portant la même référence
--     rendraient la résolution ambiguë ;
--   * il doit être RÉVOCABLE — une affiche photographiée et diffusée doit
--     pouvoir être remplacée sans recréer la salle.
--
-- La colonne devient donc un jeton généré par le serveur, unique, et
-- porte sa date d'émission pour tracer une rotation. Les valeurs déjà
-- saisies sont EFFACÉES plutôt que converties : les conserver
-- laisserait des références devinables actives sur des salles réelles.
--
-- La contrainte d'unicité tolère plusieurs NULL (comportement MySQL) :
-- une salle sans QR émis reste normale.

UPDATE room SET static_qr_reference = NULL;

ALTER TABLE room
    MODIFY COLUMN static_qr_reference VARCHAR(64) NULL,
    ADD COLUMN static_qr_issued_at TIMESTAMP(6) NULL AFTER static_qr_reference;

ALTER TABLE room
    ADD CONSTRAINT uk_room_static_qr_reference UNIQUE (static_qr_reference);

-- Canal ROOM_STATIC_QR (docs/02 §15.4, §16.6). La contrainte posée par
-- V24 énumérait les canaux d'alors ; elle est remplacée, jamais modifiée
-- sur place.
--
-- Ce canal est le seul à porter une attestation d'ORIGINE RÉSEAU : il
-- n'est accepté que depuis une plage déclarée de l'établissement. C'est
-- lui qui donne enfin au produit un contrôle de présence sur site, là où
-- le drapeau `remote` du sprint 7 n'était qu'une déclaration.

ALTER TABLE attendance_record
    DROP CHECK chk_attendance_record_source;

ALTER TABLE attendance_record
    ADD CONSTRAINT chk_attendance_record_source
        CHECK (source IN ('DYNAMIC_QR', 'SHORT_CODE', 'MANUAL', 'CORRECTION',
                          'REMOTE_QR', 'REMOTE_CODE', 'ROOM_STATIC_QR'));
