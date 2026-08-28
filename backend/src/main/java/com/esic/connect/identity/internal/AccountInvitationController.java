package com.esic.connect.identity.internal;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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

    AccountInvitationController(AccountInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','PEDAGOGICAL_MANAGER','SCHOOL_ADMINISTRATION')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    IssueInvitationResponse issue(@Valid @RequestBody IssueInvitationRequest request,
                                  @AuthenticationPrincipal Jwt issuer) {
        return invitationService.issue(request.email(), request.role(),
                issuer != null ? issuer.getSubject() : null);
    }

    @GetMapping("/validate")
    InvitationValidationResponse validate(@RequestParam("token") String token) {
        return invitationService.validate(token);
    }

    @PostMapping("/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void activate(@Valid @RequestBody ActivateAccountRequest request) {
        invitationService.activate(request.token(), request.password());
    }
}
