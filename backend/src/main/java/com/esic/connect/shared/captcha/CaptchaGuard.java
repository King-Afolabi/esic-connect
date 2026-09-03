package com.esic.connect.shared.captcha;

import org.springframework.stereotype.Component;

/**
 * Applique le contrôle anti-robot sur un parcours exposé et refuse
 * l'appel s'il échoue (EF-AUTH-011).
 *
 * <p>Un seul point d'application : les contrôleurs se contentent de
 * l'appeler, ce qui évite qu'un parcours oublie la vérification serveur
 * tout en affichant le widget.
 */
@Component
public class CaptchaGuard {

    private final CaptchaVerifier verifier;

    public CaptchaGuard(CaptchaVerifier verifier) {
        this.verifier = verifier;
    }

    /**
     * @throws CaptchaRejectedException si le jeton est refusé ; jamais si
     *                                  le fournisseur est simplement
     *                                  injoignable (DEC-S2-004)
     */
    public void require(String token, String clientOrigin) {
        CaptchaVerdict verdict = verifier.verify(token, clientOrigin);
        if (!verdict.accepted()) {
            throw new CaptchaRejectedException();
        }
    }

    public boolean isEnforced() {
        return verifier.isEnforced();
    }
}
