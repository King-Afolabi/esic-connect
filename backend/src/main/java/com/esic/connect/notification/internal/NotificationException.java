package com.esic.connect.notification.internal;

/**
 * Erreur métier du module {@code notification} (G1-D). Aucun message ne
 * divulgue de donnée personnelle.
 */
class NotificationException extends RuntimeException {

    enum Kind {
        /** Aucune notification pour ce {@code public_id} chez l'appelant (ou identifiant mal formé). */
        NOT_FOUND,
        /** Appelant non résolu (JWT sans compte correspondant). */
        UNAUTHENTICATED,
        /** Paramètre de statut hors liste. */
        INVALID_STATUS,
        /** Catégorie, canal ou valeur de préférence hors liste. */
        INVALID_PREFERENCE,
        /** Réglage non modifiable : canal `IN_APP` ou catégorie `SECURITY`. */
        PREFERENCE_LOCKED,
        /** Abonnement de poussée mal formé (terminaison ou clés). */
        INVALID_SUBSCRIPTION
    }

    private final Kind kind;

    NotificationException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }
}
