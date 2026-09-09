package com.esic.connect.claim.internal;

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
 * Réclamations (EF-CLAIM-001 à 004 ; docs/02 §20 et §30.2).
 *
 * <p>Toutes les routes sont ouvertes à l'ensemble des rôles authentifiés :
 * un apprenant dépose et suit les siennes, un intervenant traite la file
 * de son guichet. La séparation ne se fait pas par {@code @PreAuthorize}
 * mais <strong>côté service</strong>, parce qu'elle dépend de la
 * réclamation visée — auteur, guichet, périmètre pédagogique — et non du
 * seul rôle. Une réclamation hors périmètre renvoie {@code 404} : son
 * existence même est une information à protéger (docs/02 §18.2).
 */
@RestController
@RequestMapping("/api/v1/claims")
class ClaimController {

    private static final String AUTHENTICATED =
            "hasAnyRole('STUDENT','TEACHER','PEDAGOGICAL_MANAGER','SCHOOL_ADMINISTRATION',"
                    + "'ADMIN','SUPER_ADMIN')";

    private final ClaimService service;

    ClaimController(ClaimService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize(AUTHENTICATED)
    @ResponseStatus(HttpStatus.CREATED)
    ClaimResponse create(@Valid @RequestBody ClaimRequests.Create request,
                         @AuthenticationPrincipal Jwt caller) {
        return service.create(request, subject(caller));
    }

    /**
     * Réclamations visibles par l'appelant : les siennes s'il est
     * apprenant, la file de son guichet s'il intervient.
     */
    @GetMapping
    @PreAuthorize(AUTHENTICATED)
    ClaimPageResponse list(@RequestParam(required = false) String audience,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size,
                           @RequestParam(required = false) String sort,
                           @AuthenticationPrincipal Jwt caller) {
        return service.list(audience, page, size, sort, subject(caller));
    }

    /** Fil complet : en-tête, messages et historique des décisions. */
    @GetMapping("/{publicId}")
    @PreAuthorize(AUTHENTICATED)
    ClaimThreadResponse get(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        return service.get(publicId, subject(caller));
    }

    @PostMapping("/{publicId}/messages")
    @PreAuthorize(AUTHENTICATED)
    @ResponseStatus(HttpStatus.CREATED)
    ClaimThreadResponse postMessage(@PathVariable String publicId,
                                    @Valid @RequestBody ClaimRequests.PostMessage request,
                                    @AuthenticationPrincipal Jwt caller) {
        return service.addMessage(publicId, request, subject(caller));
    }

    /** Transfert vers un autre guichet (EF-CLAIM-003), motivé et tracé. */
    @PostMapping("/{publicId}/transfer")
    @PreAuthorize(AUTHENTICATED)
    ClaimResponse transfer(@PathVariable String publicId,
                           @Valid @RequestBody ClaimRequests.Transfer request,
                           @AuthenticationPrincipal Jwt caller) {
        return service.transfer(publicId, request, subject(caller));
    }

    /** Décision d'un intervenant : l'apprenant expose, il ne tranche pas. */
    @PostMapping("/{publicId}/decision")
    @PreAuthorize(AUTHENTICATED)
    ClaimResponse decide(@PathVariable String publicId,
                         @Valid @RequestBody ClaimRequests.Decide request,
                         @AuthenticationPrincipal Jwt caller) {
        return service.decide(publicId, request, subject(caller));
    }

    /** Réouverture d'une réclamation close (EF-CLAIM-004). */
    @PostMapping("/{publicId}/reopen")
    @PreAuthorize(AUTHENTICATED)
    ClaimResponse reopen(@PathVariable String publicId,
                         @Valid @RequestBody ClaimRequests.Reopen request,
                         @AuthenticationPrincipal Jwt caller) {
        return service.reopen(publicId, request, subject(caller));
    }

    private static String subject(Jwt caller) {
        return caller != null ? caller.getSubject() : null;
    }
}
