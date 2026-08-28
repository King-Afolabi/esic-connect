package com.esic.connect.identity.internal;

/**
 * Erreur métier du parcours d'invitation / activation. Le {@link Kind}
 * détermine le code HTTP et le code d'erreur exposés
 * ({@link InvitationExceptionHandler}).
 *
 * <p>Toutes les causes rendant un jeton inutilisable (inconnu, expiré,
 * révoqué, déjà accepté) sont représentées par {@link Kind#INVALID_TOKEN}
 * afin que la réponse publique reste strictement identique.
 */
class InvitationException extends RuntimeException {

    enum Kind {
        /** Jeton inconnu, expiré, révoqué ou déjà utilisé — réponse publique uniforme. */
        INVALID_TOKEN,
        /** Compte ciblé introuvable (émission, route protégée). */
        TARGET_NOT_FOUND,
        /** Compte ciblé pas en attente d'activation (émission, route protégée). */
        TARGET_NOT_PENDING,
        /** Rôle demandé inconnu ou inactif (émission, route protégée). */
        ROLE_INVALID
    }

    private final Kind kind;

    InvitationException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }
}
