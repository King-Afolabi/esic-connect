-- Domaine organisationnel (docs/04-modele-donnees.md §9).
--
-- Ce module `organization` élargit et remplace le module `room` prévu
-- initialement (docs/03-architecture.md §7.6) : il couvre la hiérarchie
-- site -> bâtiment -> salle ainsi que les plages réseau autorisées par
-- site (cahier §17.9, réservées au SUPER_ADMIN).
--
-- Conventions identiques à V1 : PK BIGINT UNSIGNED AUTO_INCREMENT,
-- identifiant public UUID (BINARY(16)), suppression RESTRICT, horodatage
-- UTC (TIMESTAMP(6)), verrouillage optimiste (`version`). Colonnes auteur
-- (`*_by_id`) en FK RESTRICT vers `user_account`. Aucune donnée métier
-- n'est insérée ici.

CREATE TABLE site (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id      BINARY(16)      NOT NULL,
    code           VARCHAR(50)     NOT NULL,
    name           VARCHAR(150)    NOT NULL,
    address_line1  VARCHAR(255)    NULL,
    address_line2  VARCHAR(255)    NULL,
    postal_code    VARCHAR(20)     NULL,
    city           VARCHAR(100)    NULL,
    country_code   CHAR(2)         NULL,
    time_zone_id   VARCHAR(64)     NOT NULL,
    status         VARCHAR(30)     NOT NULL,
    archived_at    TIMESTAMP(6)    NULL,
    archived_by_id BIGINT UNSIGNED NULL,
    archive_reason VARCHAR(500)    NULL,
    created_at     TIMESTAMP(6)    NOT NULL,
    created_by_id  BIGINT UNSIGNED NULL,
    updated_at     TIMESTAMP(6)    NOT NULL,
    updated_by_id  BIGINT UNSIGNED NULL,
    version        BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_site_public_id UNIQUE (public_id),
    CONSTRAINT uq_site_code UNIQUE (code),

    CONSTRAINT fk_site_archived_by FOREIGN KEY (archived_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_site_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_site_updated_by FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_site_status ON site (status);

CREATE TABLE building (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id      BINARY(16)      NOT NULL,
    site_id        BIGINT UNSIGNED NOT NULL,
    code           VARCHAR(50)     NOT NULL,
    name           VARCHAR(150)    NOT NULL,
    status         VARCHAR(30)     NOT NULL,
    archived_at    TIMESTAMP(6)    NULL,
    archived_by_id BIGINT UNSIGNED NULL,
    archive_reason VARCHAR(500)    NULL,
    created_at     TIMESTAMP(6)    NOT NULL,
    created_by_id  BIGINT UNSIGNED NULL,
    updated_at     TIMESTAMP(6)    NOT NULL,
    updated_by_id  BIGINT UNSIGNED NULL,
    version        BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_building_public_id UNIQUE (public_id),
    -- Code unique dans le périmètre d'un site (docs/04 §9.2).
    CONSTRAINT uq_building_site_code UNIQUE (site_id, code),

    CONSTRAINT fk_building_site FOREIGN KEY (site_id) REFERENCES site (id) ON DELETE RESTRICT,
    CONSTRAINT fk_building_archived_by FOREIGN KEY (archived_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_building_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_building_updated_by FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_building_site ON building (site_id);
CREATE INDEX idx_building_status ON building (status);

CREATE TABLE room (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id           BINARY(16)      NOT NULL,
    site_id             BIGINT UNSIGNED NOT NULL,
    building_id         BIGINT UNSIGNED NULL,
    code                VARCHAR(50)     NOT NULL,
    name                VARCHAR(150)    NOT NULL,
    capacity            INT             NULL,
    floor_label         VARCHAR(50)     NULL,
    static_qr_reference VARCHAR(255)    NULL,
    status              VARCHAR(30)     NOT NULL,
    archived_at         TIMESTAMP(6)    NULL,
    archived_by_id      BIGINT UNSIGNED NULL,
    archive_reason      VARCHAR(500)    NULL,
    created_at          TIMESTAMP(6)    NOT NULL,
    created_by_id       BIGINT UNSIGNED NULL,
    updated_at          TIMESTAMP(6)    NOT NULL,
    updated_by_id       BIGINT UNSIGNED NULL,
    version             BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_room_public_id UNIQUE (public_id),
    -- Code unique par site, indépendamment du bâtiment (docs/04 §9.3, §46.2).
    CONSTRAINT uq_room_site_code UNIQUE (site_id, code),
    CONSTRAINT chk_room_capacity CHECK (capacity IS NULL OR capacity > 0),

    CONSTRAINT fk_room_site FOREIGN KEY (site_id) REFERENCES site (id) ON DELETE RESTRICT,
    CONSTRAINT fk_room_building FOREIGN KEY (building_id) REFERENCES building (id) ON DELETE RESTRICT,
    CONSTRAINT fk_room_archived_by FOREIGN KEY (archived_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_room_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_room_updated_by FOREIGN KEY (updated_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_room_site ON room (site_id);
CREATE INDEX idx_room_building ON room (building_id);
CREATE INDEX idx_room_status ON room (status);

-- Plages réseau autorisées par site (docs/04 §9.4, cahier §17.9).
-- Modèle « ajout + désactivation » : jamais de suppression physique.
-- `public_id`, `updated_at` et `version` ajoutés au modèle initial pour
-- le routage par identifiant public et le verrouillage optimiste.
-- L'adresse IP d'un utilisateur n'est jamais stockée ici.
CREATE TABLE site_network_range (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id     BINARY(16)      NOT NULL,
    site_id       BIGINT UNSIGNED NOT NULL,
    cidr          VARCHAR(50)     NOT NULL,
    label         VARCHAR(100)    NOT NULL,
    active        BOOLEAN         NOT NULL DEFAULT TRUE,
    valid_from    TIMESTAMP(6)    NULL,
    valid_until   TIMESTAMP(6)    NULL,
    created_at    TIMESTAMP(6)    NOT NULL,
    created_by_id BIGINT UNSIGNED NULL,
    updated_at    TIMESTAMP(6)    NOT NULL,
    version       BIGINT UNSIGNED NOT NULL DEFAULT 0,

    -- Colonne générée : vaut 1 tant que la plage est active, NULL sinon.
    -- MySQL autorisant plusieurs NULL dans un index UNIQUE, la contrainte
    -- ci-dessous n'interdit qu'une seule plage ACTIVE par couple
    -- (site, cidr) et laisse l'historique des plages désactivées libre.
    active_range_key TINYINT UNSIGNED GENERATED ALWAYS AS (IF(active, 1, NULL)) VIRTUAL,

    CONSTRAINT uq_site_network_range_public_id UNIQUE (public_id),
    CONSTRAINT uq_site_network_range_active UNIQUE (site_id, cidr, active_range_key),

    CONSTRAINT fk_site_network_range_site FOREIGN KEY (site_id) REFERENCES site (id) ON DELETE RESTRICT,
    CONSTRAINT fk_site_network_range_created_by FOREIGN KEY (created_by_id) REFERENCES user_account (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_site_network_range_site ON site_network_range (site_id);
CREATE INDEX idx_site_network_range_active ON site_network_range (active);
