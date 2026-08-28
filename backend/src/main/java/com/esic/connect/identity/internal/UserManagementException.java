package com.esic.connect.identity.internal;

/**
 * Erreur métier de l'administration des comptes et des rôles. Le
 * {@link Kind} détermine le code HTTP et le code d'erreur exposés
 * ({@link UserManagementExceptionHandler}). Aucun message ne contient de
 * donnée personnelle.
 */
class UserManagementException extends RuntimeException {

    enum Kind {
        /** Aucun compte pour ce {@code public_id}. */
        USER_NOT_FOUND,
        /** Transition de statut interdite (ex. suspendre un compte non actif). */
        INVALID_STATE_TRANSITION,
        /** Le rôle demandé est déjà actif pour ce compte. */
        ROLE_ALREADY_ASSIGNED,
        /** Le rôle demandé n'est pas actif pour ce compte. */
        ROLE_NOT_ASSIGNED,
        /** Retrait refusé : ce serait le dernier rôle actif du compte. */
        LAST_ACTIVE_ROLE,
        /** Action interdite sur son propre compte. */
        SELF_ACTION_FORBIDDEN,
        /** Un compte ou un rôle {@code SUPER_ADMIN} exige un appelant {@code SUPER_ADMIN}. */
        SUPER_ADMIN_PROTECTED,
        /** L'appelant n'a pas le niveau requis pour cette opération. */
        NOT_AUTHORIZED,
        /** Code de rôle inconnu (chemin ou corps de requête). */
        ROLE_UNKNOWN,
        /** Champ de tri hors liste blanche. */
        INVALID_SORT,
        /** Valeur de filtre invalide (statut ou rôle). */
        INVALID_FILTER
    }

    private final Kind kind;

    UserManagementException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }
}
