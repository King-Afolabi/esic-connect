package com.esic.connect.integration.internal;

/** Erreur métier du module {@code integration}. Aucun message ne divulgue de donnée métier. */
class IntegrationException extends RuntimeException {

    enum Kind {
        /** Abonnement inconnu, ou appartenant à quelqu'un d'autre. */
        SUBSCRIPTION_NOT_FOUND,
        /** Clé de flux inconnue, ou jeton invalide — même réponse dans les deux cas. */
        FEED_NOT_FOUND,
        /** Abonnement révoqué : distinct d'inconnu, pour que la révocation se constate. */
        FEED_REVOKED,
        /** Plafond d'abonnements actifs atteint. */
        TOO_MANY_SUBSCRIPTIONS
    }

    private final Kind kind;

    IntegrationException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }
}
