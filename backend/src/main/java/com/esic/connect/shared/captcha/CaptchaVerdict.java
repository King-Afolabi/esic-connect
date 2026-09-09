package com.esic.connect.shared.captcha;

/**
 * Verdict d'une vérification anti-robot.
 *
 * @param accepted la requête peut continuer
 * @param reason   motif technique, pour la supervision — jamais renvoyé
 *                 tel quel à l'appelant : il aiderait à contourner le
 *                 contrôle
 */
public record CaptchaVerdict(boolean accepted, String reason) {

    private static final CaptchaVerdict PASS = new CaptchaVerdict(true, "accepted");
    /** Le fournisseur n'est pas joignable : voir la politique de repli DEC-S2-004. */
    private static final CaptchaVerdict UNAVAILABLE = new CaptchaVerdict(true, "provider-unavailable");

    public static CaptchaVerdict pass() {
        return PASS;
    }

    public static CaptchaVerdict reject(String reason) {
        return new CaptchaVerdict(false, reason);
    }

    public static CaptchaVerdict providerUnavailable() {
        return UNAVAILABLE;
    }
}
