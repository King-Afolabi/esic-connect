package com.esic.connect.organization.internal;

/**
 * Erreur métier du référentiel organisationnel. Le {@link Kind} détermine
 * le code HTTP et le code d'erreur exposés
 * ({@link OrganizationExceptionHandler}). Aucun message ne contient de
 * donnée personnelle.
 */
class OrganizationException extends RuntimeException {

    enum Kind {
        /** Aucun site pour ce {@code public_id}. */
        SITE_NOT_FOUND,
        /** Aucun bâtiment pour ce {@code public_id}. */
        BUILDING_NOT_FOUND,
        /** Aucune salle pour ce {@code public_id}. */
        ROOM_NOT_FOUND,
        /** Aucune plage réseau pour ce {@code public_id}. */
        NETWORK_RANGE_NOT_FOUND,
        /** Code déjà utilisé (site global, ou couple site + code). */
        DUPLICATE_CODE,
        /** Une plage active identique existe déjà pour ce site. */
        DUPLICATE_ACTIVE_RANGE,
        /** Fuseau horaire inconnu (non IANA). */
        INVALID_TIME_ZONE,
        /** Code pays hors ISO 3166-1 alpha-2. */
        INVALID_COUNTRY_CODE,
        /** CIDR IPv4/IPv6 invalide ou préfixe hors bornes. */
        INVALID_CIDR,
        /** Opération refusée : le parent (site ou bâtiment) est archivé. */
        ARCHIVED_PARENT,
        /** Opération refusée : l'entité visée est archivée. */
        ENTITY_ARCHIVED,
        /** Transition d'état impossible (ex. archiver une entité déjà archivée). */
        INVALID_STATE,
        /** Archivage refusé : des enfants actifs subsistent. */
        HAS_ACTIVE_CHILDREN,
        /** Le bâtiment indiqué n'appartient pas au site de la salle. */
        BUILDING_SITE_MISMATCH,
        /** Champ ou direction de tri hors liste blanche. */
        INVALID_SORT,
        /** Valeur de filtre invalide (statut, drapeau actif...). */
        INVALID_FILTER
    }

    private final Kind kind;

    OrganizationException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }
}
