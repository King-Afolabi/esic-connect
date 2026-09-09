package com.esic.connect.identity.internal;

/**
 * Cycle de vie d'une demande de réinitialisation (docs/02 §17.8).
 * Reflète la contrainte {@code ck_password_reset_token_status} de V17.
 */
enum PasswordResetStatus {
    /** Émise, ni utilisée ni révoquée, et non encore expirée. */
    PENDING,
    /** Utilisée pour définir un nouveau mot de passe. Usage unique. */
    CONSUMED,
    /** Annulée par l'émission d'une demande plus récente. */
    REVOKED,
    /** Périmée sans avoir été utilisée. */
    EXPIRED
}
