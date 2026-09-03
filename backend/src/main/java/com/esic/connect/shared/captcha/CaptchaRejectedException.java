package com.esic.connect.shared.captcha;

/**
 * Le contrôle anti-robot a refusé la requête.
 *
 * <p>Se traduit par un {@code 400} portant un code stable, sans révéler
 * le motif exact : « jeton déjà consommé » et « jeton absent » doivent
 * être indistinguables pour qui cherche à contourner le contrôle.
 */
public class CaptchaRejectedException extends RuntimeException {

    public CaptchaRejectedException() {
        super("La vérification anti-robot a échoué. Rechargez la page et réessayez.");
    }
}
