package com.esic.connect.identity.internal;

import java.util.List;

/**
 * Erreur métier des parcours d'authentification hors connexion
 * (réinitialisation de mot de passe, déconnexion). Le {@link Kind}
 * détermine le code HTTP et le code d'erreur exposés
 * ({@link AuthExceptionHandler}).
 *
 * <p>Aucun message ne révèle l'existence d'un compte : le parcours
 * « mot de passe oublié » répond de façon identique que l'adresse soit
 * connue ou non (docs/02 §17.8).
 */
class AuthException extends RuntimeException {

    enum Kind {
        /** Jeton de réinitialisation inconnu, déjà utilisé, révoqué ou expiré. */
        INVALID_RESET_TOKEN,
        /** Le nouveau mot de passe ne respecte pas la politique. */
        WEAK_PASSWORD,
        /** Le compte n'est pas dans un état permettant cette opération. */
        ACCOUNT_NOT_ELIGIBLE,
        /** Redis indisponible : la révocation demandée n'a pas pu être appliquée. */
        REVOCATION_BACKEND_UNAVAILABLE
    }

    private final Kind kind;
    private final transient List<String> details;

    AuthException(Kind kind) {
        this(kind, List.of());
    }

    AuthException(Kind kind, List<String> details) {
        super(kind.name());
        this.kind = kind;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    Kind kind() {
        return kind;
    }

    List<String> details() {
        return details;
    }

    static AuthException invalidResetToken() {
        return new AuthException(Kind.INVALID_RESET_TOKEN);
    }

    static AuthException weakPassword(List<String> violations) {
        return new AuthException(Kind.WEAK_PASSWORD, violations);
    }

    static AuthException accountNotEligible() {
        return new AuthException(Kind.ACCOUNT_NOT_ELIGIBLE);
    }

    static AuthException revocationBackendUnavailable() {
        return new AuthException(Kind.REVOCATION_BACKEND_UNAVAILABLE);
    }
}
