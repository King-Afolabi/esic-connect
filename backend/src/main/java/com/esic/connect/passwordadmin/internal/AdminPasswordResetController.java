package com.esic.connect.passwordadmin.internal;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Réinitialisation du mot de passe d'un tiers par une personne habilitée
 * (module {@code passwordadmin}, docs/02 §17.8 complété).
 *
 * <p>Le {@code @PreAuthorize} filtre grossièrement par rôle ; la hiérarchie
 * fine (protection {@code SUPER_ADMIN}/{@code ADMIN}, périmètre du
 * {@code PEDAGOGICAL_MANAGER}) est appliquée dans
 * {@link AdminPasswordResetService}.
 */
@RestController
class AdminPasswordResetController {

    private static final String RESET_ROLES =
            "hasAnyRole('SUPER_ADMIN','ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER')";

    private final AdminPasswordResetService service;

    AdminPasswordResetController(AdminPasswordResetService service) {
        this.service = service;
    }

    /**
     * Déclenche l'envoi d'un lien de réinitialisation au compte visé — le
     * nouveau mot de passe reste choisi par la personne elle-même, jamais
     * par l'appelant.
     */
    @PostMapping("/api/v1/users/{publicId}/password-reset")
    @PreAuthorize(RESET_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void triggerReset(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        service.triggerReset(parseUuid(publicId), subject(caller), roles(caller));
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            throw AdminPasswordResetException.userNotFound();
        }
    }

    private static String subject(Jwt caller) {
        return caller != null ? caller.getSubject() : null;
    }

    private static List<String> roles(Jwt caller) {
        if (caller == null) {
            return List.of();
        }
        List<String> claim = caller.getClaimAsStringList("roles");
        return claim != null ? claim : List.of();
    }
}
