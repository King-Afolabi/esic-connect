package com.esic.connect.identity.internal;

import com.esic.connect.shared.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Passkeys WebAuthn (EF-AUTH-006, EF-AUTH-007 ; docs/02 §30.2).
 *
 * <p>Les deux routes de connexion sont publiques par nécessité : une
 * connexion sans mot de passe précède, par définition, toute session.
 * Elles restent inexploitables sans un défi valide, à usage unique, et
 * sans une signature produite par une clé privée qui ne quitte jamais le
 * terminal de l'utilisateur.
 */
@RestController
@RequestMapping("/api/v1/auth/webauthn")
public class WebAuthnController {

    private final WebAuthnService webAuthnService;
    private final RefreshService refreshService;

    public WebAuthnController(WebAuthnService webAuthnService, RefreshService refreshService) {
        this.webAuthnService = webAuthnService;
        this.refreshService = refreshService;
    }

    @PostMapping("/register/options")
    public WebAuthnWeb.RegistrationOptions registrationOptions(@AuthenticationPrincipal Jwt jwt) {
        return webAuthnService.registrationOptions(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/register")
    public WebAuthnWeb.CredentialResponse register(
            @Valid @RequestBody WebAuthnWeb.RegistrationRequestBody body,
            @AuthenticationPrincipal Jwt jwt) {
        return webAuthnService.register(UUID.fromString(jwt.getSubject()), body);
    }

    @PostMapping("/login/options")
    public WebAuthnWeb.AuthenticationOptions authenticationOptions() {
        return webAuthnService.authenticationOptions();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody WebAuthnWeb.AuthenticationRequestBody body,
            @RequestHeader(value = AuthController.DEVICE_HEADER, required = false) String deviceId) {
        LoginResponse response = webAuthnService.authenticate(body, deviceId);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        refreshService.onAuthenticated(response, deviceId)
                .ifPresent(cookie -> builder.header(HttpHeaders.SET_COOKIE, cookie.toString()));
        return builder.body(response);
    }

    /** Passkeys du compte connecté. */
    @GetMapping("/credentials")
    public List<WebAuthnWeb.CredentialResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return webAuthnService.list(UUID.fromString(jwt.getSubject()));
    }

    @DeleteMapping("/credentials/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        webAuthnService.revoke(UUID.fromString(jwt.getSubject()), id);
    }

    @ExceptionHandler(WebAuthnException.class)
    ResponseEntity<ApiError> handle(WebAuthnException exception, HttpServletRequest request) {
        HttpStatus status = exception.code().status();
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(), status.value(), exception.code().name(), exception.getMessage(),
                request.getRequestURI(), UUID.randomUUID().toString(), List.of()));
    }

    @ExceptionHandler(MfaBackendUnavailableException.class)
    ResponseEntity<ApiError> handleUnavailable(MfaBackendUnavailableException exception,
                                               HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(
                Instant.now(), HttpStatus.SERVICE_UNAVAILABLE.value(),
                "WEBAUTHN_BACKEND_UNAVAILABLE",
                "La vérification par clé d'accès est momentanément indisponible.",
                request.getRequestURI(), UUID.randomUUID().toString(), List.of()));
    }
}
