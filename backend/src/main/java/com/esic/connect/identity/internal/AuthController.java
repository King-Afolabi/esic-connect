package com.esic.connect.identity.internal;

import com.esic.connect.identity.SessionsRevokedEvent;
import com.esic.connect.shared.captcha.CaptchaGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
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

    /**
     * Identifiant d'appareil conservé par le client (EF-AUTH-010,
     * EF-AUTH-013). Purement indicatif : le serveur n'en garde qu'une
     * empreinte, et un client qui n'en envoie pas est simplement traité
     * comme un appareil inconnu — jamais comme un appareil de confiance.
     */
    static final String DEVICE_HEADER = "X-Device-Id";

    private final AuthenticationService authenticationService;
    private final PasswordResetService passwordResetService;
    private final AccessTokenRevocationService accessTokenRevocationService;
    private final SessionRevocationService sessionRevocationService;
    private final RefreshService refreshService;
    private final UserAccountRepository userAccountRepository;
    private final CaptchaGuard captchaGuard;
    private final String captchaSiteKey;
    private final Clock clock;

    public AuthController(AuthenticationService authenticationService,
                          PasswordResetService passwordResetService,
                          AccessTokenRevocationService accessTokenRevocationService,
                          SessionRevocationService sessionRevocationService,
                          RefreshService refreshService,
                          UserAccountRepository userAccountRepository,
                          CaptchaGuard captchaGuard,
                          @org.springframework.beans.factory.annotation.Value(
                                  "${app.security.captcha.site-key:}") String captchaSiteKey,
                          Clock clock) {
        this.authenticationService = authenticationService;
        this.passwordResetService = passwordResetService;
        this.accessTokenRevocationService = accessTokenRevocationService;
        this.sessionRevocationService = sessionRevocationService;
        this.refreshService = refreshService;
        this.userAccountRepository = userAccountRepository;
        this.captchaGuard = captchaGuard;
        this.captchaSiteKey = captchaSiteKey == null ? "" : captchaSiteKey.trim();
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
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = DEVICE_HEADER, required = false) String deviceId,
            HttpServletRequest httpRequest) {
        LoginResponse response = authenticationService.login(request.email(), request.password(),
                httpRequest.getRemoteAddr(), deviceId, request.captchaToken());
        // Un succès sans second facteur porte un jeton d'accès : on ouvre
        // alors une session de renouvellement. Un défi MFA n'en porte pas
        // et ne pose aucun cookie — la connexion n'est pas terminée.
        return refreshService.onAuthenticated(response, deviceId)
                .map(cookie -> ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE, cookie.toString())
                        .body(response))
                .orElseGet(() -> ResponseEntity.ok(response));
    }

    /**
     * Identité du compte connecté (docs/02 §30.2). Sert à l'interface
     * après un renouvellement silencieux : le jeton d'accès seul ne porte
     * pas l'adresse électronique. Route protégée — un jeton valide est
     * requis.
     */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        UserAccount account = userAccountRepository.findByPublicId(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new MeResponse(account.getPublicId().toString(), account.getEmail(),
                roles == null ? List.of() : roles);
    }

    /** @param subject identifiant public du compte (jamais l'id SQL) */
    public record MeResponse(String subject, String email, List<String> roles) {
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
    public PasswordResetTtlResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                   HttpServletRequest httpRequest) {
        // Formulaire public : contrôle anti-robot systématique lorsqu'un
        // fournisseur est configuré (EF-AUTH-011, docs/02 §17.9). Le refus
        // survient AVANT toute lecture de compte, il ne révèle donc rien.
        captchaGuard.require(request.captchaToken(), httpRequest.getRemoteAddr());
        passwordResetService.requestReset(request.email());
        return PasswordResetTtlResponse.neutral();
    }

    /**
     * Configuration publique du contrôle anti-robot : la clé de site est
     * publique par construction (elle s'affiche dans le HTML du widget),
     * la clé secrète ne sort jamais du serveur.
     *
     * <p>Permet à l'interface de n'afficher le widget que lorsqu'il sert
     * réellement, plutôt qu'un ornement sans effet.
     */
    @GetMapping("/captcha")
    public CaptchaConfigResponse captchaConfiguration() {
        return new CaptchaConfigResponse(captchaGuard.isEnforced(), captchaSiteKey);
    }

    /** @param enforced faux si aucun fournisseur n'est configuré : le widget est alors inutile */
    public record CaptchaConfigResponse(boolean enforced, String siteKey) {
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
     * Déconnecte la session courante : le jeton d'accès présenté est
     * inscrit sur la liste de refus jusqu'à son expiration naturelle, et
     * la famille de renouvellement portée par le cookie est supprimée —
     * sans quoi un rechargement rouvrirait aussitôt la session. Le cookie
     * du navigateur est vidé dans la réponse.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Jwt jwt,
            @CookieValue(value = RefreshCookies.COOKIE_NAME, required = false) String refreshCookie) {
        accessTokenRevocationService.revoke(jwt.getId(), jwt.getExpiresAt(), clock.instant());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshService.revoke(refreshCookie).toString())
                .build();
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
