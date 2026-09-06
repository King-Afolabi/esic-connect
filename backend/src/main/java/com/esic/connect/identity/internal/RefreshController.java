package com.esic.connect.identity.internal;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Renouvellement silencieux de session (EF-AUTH-014, docs/02 §17.7,
 * §30.2).
 *
 * <p>Route publique : aucun jeton d'accès n'est requis — c'est le cookie
 * de renouvellement {@code HttpOnly} qui fait foi. Le corps de la requête
 * est vide ; la réponse porte un nouveau jeton d'accès <strong>dans son
 * corps</strong> (illisible depuis une origine tierce) et un cookie de
 * renouvellement <strong>rotaté</strong>.
 *
 * <p><strong>CSRF.</strong> Le cookie est {@code SameSite=Strict} : il
 * n'accompagne aucune requête déclenchée depuis un autre site. Même si un
 * appel forgé aboutissait, l'attaquant ne pourrait pas lire la réponse
 * (politique de même origine), et le jeton d'accès obtenu reste dans le
 * corps — il n'y a pas d'autorité ambiante par cookie sur les routes
 * métier, celles-ci exigeant l'en-tête {@code Authorization}. Aucun jeton
 * anti-CSRF distinct n'est donc nécessaire ici.
 */
@RestController
@RequestMapping("/api/v1/auth")
class RefreshController {

    private final RefreshService refreshService;

    RefreshController(RefreshService refreshService) {
        this.refreshService = refreshService;
    }

    @PostMapping("/refresh")
    ResponseEntity<LoginResponse> refresh(
            @CookieValue(value = RefreshCookies.COOKIE_NAME, required = false) String cookie,
            @RequestHeader(value = AuthController.DEVICE_HEADER, required = false) String deviceId) {
        RefreshService.Refreshed refreshed = refreshService.refresh(cookie, deviceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshed.cookie().toString())
                .body(refreshed.body());
    }

    /**
     * Tout échec de renouvellement — cookie absent, périmé, forgé,
     * famille révoquée, compte suspendu, magasin injoignable — répond un
     * {@code 401} nu, sans corps : aucun de ces cas n'est distinguable de
     * l'extérieur.
     */
    @ExceptionHandler(RefreshTokenException.class)
    ResponseEntity<Void> handleInvalid() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
