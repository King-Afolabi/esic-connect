-- V31 — Outbox transactionnelle (EF-AUD-003, EF-OPS-005 ; docs/02 §23.4,
-- §25.1, §25.2 ; RG-096, RG-097 ; AC-027, AC-028).
--
-- CE QUE CETTE TABLE CORRIGE. Jusqu'ici, deux mécanismes coexistaient et
-- perdaient l'un comme l'autre l'effet de bord :
--
--   * l'audit s'écrivait dans une transaction SÉPARÉE (`REQUIRES_NEW`)
--     ouverte AVANT le commit métier — une trace pouvait donc être
--     committée pour une action ensuite annulée (RG-097 violé), et
--     inversement un incident d'écriture perdait la trace sans que
--     personne ne l'apprenne (dette T-02) ;
--   * les notifications s'écrivaient APRÈS commit, sans file ni reprise :
--     un arrêt de la JVM entre le commit et l'écriture perdait
--     définitivement la notification (dette T-01).
--
-- Le principe de l'outbox lève les deux d'un coup : la ligne d'intention
-- est écrite DANS LA TRANSACTION MÉTIER. Elle commite avec elle, ou
-- disparaît avec elle. Un diffuseur la reprend ensuite — immédiatement
-- après le commit, puis périodiquement pour ce que l'immédiat a manqué.
--
-- POURQUOI UNE SEULE TABLE POUR TOUS LES EFFETS DE BORD. Le cahier
-- (§25.1) décrit UN flux : action -> outbox -> diffuseur -> fournisseur.
-- `message_type` route vers le gestionnaire du module concerné ; le
-- module `outbox` ne connaît aucun de ces modules (il ne fait que router),
-- et aucun d'eux ne connaît les autres.
--
-- Conventions V1/V15/V28 : PK BIGINT UNSIGNED AUTO_INCREMENT, `public_id`
-- BINARY(16) unique, TIMESTAMP(6) UTC, verrou optimiste (`version`),
-- ENGINE=InnoDB, utf8mb4_0900_ai_ci. Aucune donnée métier insérée.

CREATE TABLE outbox_message (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id       BINARY(16)      NOT NULL,

    -- Route vers le gestionnaire : AUDIT | NOTIFICATION. Volontairement
    -- une chaîne et non un ENUM SQL : ajouter un type d'effet de bord au
    -- sprint suivant ne doit pas exiger un ALTER TABLE sur une table qui
    -- peut être volumineuse.
    message_type    VARCHAR(48)     NOT NULL,

    -- Idempotence de l'INTENTION. Deux livraisons du même événement
    -- métier produisent la même clé et donc une seule ligne : le
    -- diffuseur n'a pas à savoir si son gestionnaire est rejouable.
    dedup_key       CHAR(64)        NOT NULL,

    -- Description de l'effet à produire, jamais le résultat. Ne contient
    -- ni jeton, ni mot de passe, ni adresse en clair, ni adresse IP
    -- (RG-094) : uniquement des identifiants publics et des libellés
    -- neutres, que le gestionnaire re-résout au moment du traitement.
    payload         JSON            NOT NULL,

    -- PENDING  : à traiter (jamais tenté, ou replanifié après un échec)
    -- SENT     : le gestionnaire a rendu la main sans erreur
    -- FAILED   : dernière tentative en échec, une reprise est planifiée
    -- DEAD     : tentatives épuisées — FILE D'ÉCHEC, rejouable à la main
    status          VARCHAR(16)     NOT NULL,

    attempts        INT UNSIGNED    NOT NULL DEFAULT 0,

    -- Date à partir de laquelle une reprise est autorisée (attente
    -- croissante entre deux tentatives, cahier §25.2).
    next_attempt_at TIMESTAMP(6)    NOT NULL,

    -- Cause de l'échec sous forme NON SENSIBLE (nom de classe, code) :
    -- un message d'exception peut contenir une valeur métier.
    last_error      VARCHAR(255)    NULL,

    created_at      TIMESTAMP(6)    NOT NULL,
    processed_at    TIMESTAMP(6)    NULL,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_outbox_message_public_id UNIQUE (public_id),
    CONSTRAINT uq_outbox_message_dedup UNIQUE (dedup_key),
    CONSTRAINT chk_outbox_message_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'DEAD')),
    -- Une ligne terminée porte forcément sa date de traitement, et une
    -- ligne en attente n'en porte jamais : sans cela « jamais traité » et
    -- « traité » se confondraient dans la file d'échec.
    CONSTRAINT chk_outbox_message_processed_at
        CHECK ((status IN ('PENDING', 'FAILED')) = (processed_at IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Réclamation du prochain lot par le diffuseur :
-- WHERE status IN ('PENDING','FAILED') AND next_attempt_at <= now ORDER BY id.
CREATE INDEX idx_outbox_message_claim
    ON outbox_message (status, next_attempt_at, id);

-- Écran d'exploitation : file d'échec et journal, les plus récents d'abord.
CREATE INDEX idx_outbox_message_status_created
    ON outbox_message (status, created_at);
