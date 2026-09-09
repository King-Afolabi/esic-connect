package com.esic.connect.identity;

import java.util.UUID;

/**
 * Publié lorsque l'ensemble des jetons d'accès d'un compte est révoqué
 * (déconnexion globale, réinitialisation de mot de passe, suspension,
 * incident) — EF-AUTH-014, RG-010.
 */
public record SessionsRevokedEvent(Long userId,
                                   UUID userPublicId,
                                   String displaySnapshot,
                                   String reason) {

    public static final String REASON_PASSWORD_RESET = "PASSWORD_RESET";
    public static final String REASON_LOGOUT_ALL = "LOGOUT_ALL";
    public static final String REASON_ADMIN_REVOCATION = "ADMIN_REVOCATION";
}
