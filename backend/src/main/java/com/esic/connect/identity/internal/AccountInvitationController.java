package com.esic.connect.identity.internal;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API d'invitation / activation de compte (cahier §11).
 *
 * <ul>
 *   <li>{@code POST /api/v1/account-invitations} — protégé, réservé aux
 *       rôles d'administration / responsable pédagogique ;</li>
 *   <li>{@code GET  /api/v1/account-invitations/validate} — public,
 *       réponse strictement générique ;</li>
 *   <li>{@code POST /api/v1/account-invitations/activate} — public.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/account-invitations")
class AccountInvitationController {

    private final AccountInvitationService invitationService;
    private final com.esic.connect.shared.captcha.CaptchaGuard captchaGuard;

    AccountInvitationController(AccountInvitationService invitationService,
                                com.esic.connect.shared.captcha.CaptchaGuard captchaGuard) {
        this.invitationService = invitationService;
        this.captchaGuard = captchaGuard;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','PEDAGOGICAL_MANAGER','SCHOOL_ADMINISTRATION')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    IssueInvitationResponse issue(@Valid @RequestBody IssueInvitationRequest request,
                                  @AuthenticationPrincipal Jwt issuer) {
        return invitationService.issue(request.email(), request.role(),
                issuer != null ? issuer.getSubject() : null);
    }

    /** Suivi des invitations émises (EF-USER-007). */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','PEDAGOGICAL_MANAGER','SCHOOL_ADMINISTRATION')")
    PageResponse<InvitationSummaryResponse> list(@RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return invitationService.list(status, page, size);
    }

    /**
     * Réémet une invitation : l'ancien jeton est révoqué, un nouveau part
     * (EF-USER-007). C'est la seule réponse correcte après correction
     * d'une adresse — renvoyer l'ancien jeton laisserait un lien valide
     * dans une boîte qui n'est pas la bonne.
     */
    @PostMapping("/{publicId}/resend")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','PEDAGOGICAL_MANAGER','SCHOOL_ADMINISTRATION')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    IssueInvitationResponse resend(@PathVariable String publicId,
                                   @AuthenticationPrincipal Jwt issuer) {
        return invitationService.resend(parseUuid(publicId),
                issuer != null ? issuer.getSubject() : null);
    }

    private static java.util.UUID parseUuid(String value) {
        try {
            return java.util.UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            throw new InvitationException(InvitationException.Kind.TARGET_NOT_FOUND);
        }
    }

    @GetMapping("/validate")
    InvitationValidationResponse validate(@RequestParam("token") String token) {
        return invitationService.validate(token);
    }

    @PostMapping("/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void activate(@Valid @RequestBody ActivateAccountRequest request,
                  jakarta.servlet.http.HttpServletRequest httpRequest) {
        // Formulaire public exposé : contrôle anti-robot serveur
        // avant toute consommation de jeton (EF-AUTH-011, docs/02 §17.9).
        captchaGuard.require(request.captchaToken(), httpRequest.getRemoteAddr());
        invitationService.activate(request.token(), request.password());
    }
}
