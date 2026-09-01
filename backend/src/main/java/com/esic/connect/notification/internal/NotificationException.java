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
        INVALID_STATUS
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
