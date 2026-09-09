package com.esic.connect.identity.internal;

import com.esic.connect.shared.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Appareils de confiance du compte connecté (EF-AUTH-013 ;
 * docs/02 §30.2 : {@code GET /auth/devices},
 * {@code DELETE /auth/devices/{id}}).
 *
 * <p>Un compte ne voit et ne révoque que <em>ses</em> appareils : le
 * périmètre est déduit du sujet du jeton, jamais d'un paramètre fourni
 * par l'appelant.
 */
@RestController
@RequestMapping("/api/v1/auth/devices")
public class TrustedDeviceController {

    private final TrustedDeviceService trustedDeviceService;

    public TrustedDeviceController(TrustedDeviceService trustedDeviceService) {
        this.trustedDeviceService = trustedDeviceService;
    }

    @GetMapping
    public List<TrustedDeviceWeb.DeviceResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return trustedDeviceService.list(UUID.fromString(jwt.getSubject()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        trustedDeviceService.revoke(UUID.fromString(jwt.getSubject()), id);
    }

    /** Appareil inconnu ou appartenant à un tiers : indistinguable de l'extérieur. */
    @ExceptionHandler(TrustedDeviceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(
                Instant.now(), HttpStatus.NOT_FOUND.value(), "TRUSTED_DEVICE_NOT_FOUND",
                "Appareil introuvable.", request.getRequestURI(),
                UUID.randomUUID().toString(), List.of()));
    }
}
