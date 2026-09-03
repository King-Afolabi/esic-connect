package com.esic.connect.identity;

import java.util.UUID;

/**
 * Publié lorsqu'un mot de passe vient d'être modifié, quelle qu'en soit
 * l'origine (réinitialisation, changement volontaire).
 *
 * <p>Sert à l'audit et à la notification de sécurité de la personne
 * concernée. Ne porte jamais le mot de passe, ni ancien ni nouveau.
 *
 * @param origin d'où vient le changement, pour le libellé d'audit
 */
public record PasswordChangedEvent(Long userId,
                                   UUID userPublicId,
                                   String email,
                                   String firstName,
                                   String displaySnapshot,
                                   String origin) {

    public static final String ORIGIN_RESET = "PASSWORD_RESET";
    public static final String ORIGIN_SELF_SERVICE = "SELF_SERVICE";
}
