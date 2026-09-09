package com.esic.connect.shared.captcha;


/**
 * Adaptateur local, actif lorsqu'aucune clé Turnstile n'est configurée
 * (docs/02 §28.1 : « le produit fonctionne intégralement sans
 * intégration externe, avec un adaptateur local »).
 *
 * <p>Il ne simule <strong>pas</strong> une protection : il déclare
 * franchement, par {@link #isEnforced()}, qu'aucun contrôle anti-robot
 * n'est appliqué. C'est ce que lit l'interface pour ne pas afficher un
 * widget décoratif, et c'est ce que doit lire toute documentation d'état :
 * sans clé, EF-AUTH-011 n'est pas actif.
 *
 * <p>Un jeton réservé permet toutefois aux tests de bout en bout de
 * traverser un parcours protégé sans appeler Cloudflare.
 */
public class LocalCaptchaVerifier implements CaptchaVerifier {

    /** Jeton accepté par cet adaptateur uniquement ; sans valeur en production. */
    public static final String TEST_TOKEN = "local-development-token";

    @Override
    public CaptchaVerdict verify(String token, String clientOrigin) {
        return CaptchaVerdict.pass();
    }

    @Override
    public boolean isEnforced() {
        return false;
    }
}
