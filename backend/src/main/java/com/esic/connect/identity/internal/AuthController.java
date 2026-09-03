package com.esic.connect.identity.internal;

import com.esic.connect.identity.SessionsRevokedEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.UUID;

/**
 * Parcours d'authentification : connexion, déconnexion, mot de passe
 * oublié (EF-AUTH-001, EF-AUTH-005, EF-AUTH-014).
 *
 * <p>Les routes {@code /login}, {@code /forgot-password} et
 * {@code /reset-password} sont publiques — elles sont donc toutes les
 * trois soumises à limitation de débit. Les routes de déconnexion
 * exigent un jeton valide.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final PasswordResetService passwordResetService;
    private final AccessTokenRevocationService accessTokenRevocationService;
    private final SessionRevocationService sessionRevocationService;
    private final Clock clock;

    public AuthController(AuthenticationService authenticationService,
                          PasswordResetService passwordResetService,
                          AccessTokenRevocationService accessTokenRevocationService,
                          SessionRevocationService sessionRevocationService,
                          Clock clock) {
        this.authenticationService = authenticationService;
        this.passwordResetService = passwordResetService;
        this.accessTokenRevocationService = accessTokenRevocationService;
        this.sessionRevocationService = sessionRevocationService;
        this.clock = clock;
    }

    /**
     * Connexion. L'adresse distante est transmise au service uniquement
     * pour alimenter le seau de limitation par origine : elle y est
     * réduite à une empreinte et n'est jamais conservée (docs/02 §16.7).
     *
     * <p>Derrière un proxy inverse, l'adresse vue ici est celle du proxy
     * tant que le serveur n'est pas configuré pour honorer les en-têtes
     * {@code Forwarded} / {@code X-Forwarded-For} — configuration à faire
     * au déploiement, jamais en faisant confiance à un en-tête fourni
     * par le client.
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authenticationService.login(request.email(), request.password(), httpRequest.getRemoteAddr());
    }

    /**
     * Demande un lien de réinitialisation.
     *
     * <p>Répond toujours {@code 202 Accepted} avec le même message, que
     * l'adresse soit connue ou non : le formulaire ne doit pas permettre
     * de tester l'appartenance d'une personne à l'établissement.
     */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PasswordResetTtlResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return PasswordResetTtlResponse.neutral();
    }

    /**
     * Consomme un jeton de réinitialisation et définit le nouveau mot de
     * passe. Toutes les sessions du compte sont révoquées au passage.
     */
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
    }

    /**
     * Déconnecte la session courante : le jeton présenté est inscrit sur
     * la liste de refus jusqu'à son expiration naturelle.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt) {
        accessTokenRevocationService.revoke(jwt.getId(), jwt.getExpiresAt(), clock.instant());
    }

    /**
     * Déconnecte <strong>toutes</strong> les sessions du compte, sur tous
     * les appareils. Utile après la perte d'un terminal.
     */
    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@AuthenticationPrincipal Jwt jwt) {
        sessionRevocationService.revokeAll(UUID.fromString(jwt.getSubject()),
                SessionsRevokedEvent.REASON_LOGOUT_ALL);
    }
}
