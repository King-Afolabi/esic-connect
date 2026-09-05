package com.esic.connect.audit.internal;

/** Erreur d'appel de la consultation d'audit. Aucun message ne divulgue de donnée. */
class AuditException extends RuntimeException {

    enum Kind {
        /** Format d'export hors liste ({@code csv}, {@code xlsx}, {@code pdf}). */
        INVALID_FORMAT,
        /** Filtre mal formé (identifiant qui n'est pas un UUID). */
        INVALID_FILTER
    }

    private final Kind kind;

    AuditException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }
}
