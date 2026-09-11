package com.esic.connect.passwordadmin.internal;

/**
 * Erreur métier de la réinitialisation de mot de passe par un tiers. Le
 * {@link Kind} détermine le code HTTP et le code d'erreur exposés
 * ({@link AdminPasswordResetExceptionHandler}).
 */
class AdminPasswordResetException extends RuntimeException {

    enum Kind {
        /** Aucun compte pour ce {@code public_id}. */
        USER_NOT_FOUND,
        /** Le compte visé est hors de portée du rôle de l'appelant (RG-009) ou hors périmètre pédagogique. */
        FORBIDDEN,
        /** Le compte visé est suspendu ou archivé : pas de réinitialisation possible. */
        NOT_ELIGIBLE
    }

    private final Kind kind;

    private AdminPasswordResetException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }

    static AdminPasswordResetException userNotFound() {
        return new AdminPasswordResetException(Kind.USER_NOT_FOUND);
    }

    static AdminPasswordResetException forbidden() {
        return new AdminPasswordResetException(Kind.FORBIDDEN);
    }

    static AdminPasswordResetException notEligible() {
        return new AdminPasswordResetException(Kind.NOT_ELIGIBLE);
    }
}
