package com.esic.connect.claim.internal;

/** Erreurs métier du module {@code claim}, traduites en {@code CLAIM_*}. */
class ClaimException extends RuntimeException {

    enum Kind {
        /** Réclamation inexistante, ou hors du périmètre de l'appelant. */
        NOT_FOUND,
        /** L'appelant n'a pas qualité pour cette opération. */
        FORBIDDEN,
        /** Séance visée inexistante. */
        SESSION_NOT_FOUND,
        /** Opération incompatible avec le statut courant. */
        INVALID_STATE,
        /** Requête mal formée : statut inconnu, période inversée, guichet identique. */
        INVALID_SUBMISSION,
        /** Champ ou direction de tri hors liste blanche. */
        INVALID_SORT,
        /**
         * Guichet périmétré visé alors que l'auteur n'a aucune classe
         * active : la réclamation ne serait visible de personne. Le
         * cahier veut un destinataire compétent, pas un dépôt sans
         * lecteur (docs/02 §20.1).
         */
        NO_SCOPE_FOR_AUDIENCE
    }

    private final Kind kind;

    ClaimException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }
}
