package com.esic.connect.outbox.internal;

/** Erreur métier du module {@code outbox}. Aucun message ne divulgue de donnée métier. */
class OutboxException extends RuntimeException {

    enum Kind {
        /** Aucun message pour cet identifiant (ou identifiant mal formé). */
        NOT_FOUND,
        /** Filtre de statut hors liste. */
        INVALID_STATUS,
        /** Rejeu demandé sur un message qui n'est pas en file d'échec. */
        NOT_REPLAYABLE
    }

    private final Kind kind;

    OutboxException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }
}
