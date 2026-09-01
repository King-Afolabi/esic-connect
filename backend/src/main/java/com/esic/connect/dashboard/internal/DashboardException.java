package com.esic.connect.dashboard.internal;

/**
 * Erreur du module {@code dashboard} (bloc G1-F). Aucun message ne
 * divulgue d'identifiant interne, de SQL ni de trace.
 */
class DashboardException extends RuntimeException {

    enum Kind {
        /** Aucun rôle exploitable pour construire un tableau de bord. */
        NO_ROLE
    }

    private final Kind kind;

    DashboardException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }
}
