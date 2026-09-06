package com.esic.connect.identity.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fabrique du cookie de renouvellement de session (docs/02 §17.7,
 * docs/03 §15.2).
 *
 * <ul>
 *   <li>{@code HttpOnly} — jamais lisible par un script, donc jamais dans
 *       {@code localStorage} (RG-093) ;</li>
 *   <li>{@code SameSite=Strict} — n'accompagne aucune navigation venue
 *       d'un autre site ; c'est la protection CSRF de ce cookie, sans
 *       jeton anti-CSRF distinct ;</li>
 *   <li>{@code Path=/api/v1/auth} — n'est envoyé qu'aux routes
 *       d'authentification, jamais aux routes métier ;</li>
 *   <li>{@code Secure} — piloté par {@code app.security.cookies.secure}
 *       (vrai par défaut). Désactivé pour les seuls profils servis en
 *       clair (local, démonstration, test).</li>
 * </ul>
 */
@Component
class RefreshCookies {

    static final String COOKIE_NAME = "refresh_token";
    private static final String PATH = "/api/v1/auth";

    private final boolean secure;

    RefreshCookies(@Value("${app.security.cookies.secure:true}") boolean secure) {
        this.secure = secure;
    }

    ResponseCookie build(String value, Duration maxAge) {
        return base(value).maxAge(maxAge).build();
    }

    /** Cookie vide, expiré immédiatement : efface celui du navigateur à la déconnexion. */
    ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }
}
