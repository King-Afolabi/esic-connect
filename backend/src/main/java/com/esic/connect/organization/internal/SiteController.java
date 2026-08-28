package com.esic.connect.organization.internal;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Administration des sites (docs/04 §9.1). Lecture ouverte aux rôles de
 * gestion pédagogique/administrative ; écriture réservée à
 * {@code ADMIN}/{@code SUPER_ADMIN}. Les règles fines (unicité, fuseau,
 * pays, enfants actifs, immuabilité du code) sont dans {@link SiteService}.
 * Toutes les routes utilisent exclusivement {@code public_id}.
 */
@RestController
@RequestMapping("/api/v1/sites")
class SiteController {

    static final String READ_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER')";
    static final String WRITE_ROLES = "hasAnyRole('ADMIN','SUPER_ADMIN')";

    private final SiteService siteService;

    SiteController(SiteService siteService) {
        this.siteService = siteService;
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    PageResponse<SiteResponse> list(@RequestParam(required = false) String status,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) String sort,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return siteService.list(status, q, page, size, sort);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize(READ_ROLES)
    SiteResponse get(@PathVariable String publicId) {
        return siteService.get(parseUuid(publicId));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    SiteResponse create(@Valid @RequestBody CreateSiteRequest request, @AuthenticationPrincipal Jwt caller) {
        return siteService.create(request, subject(caller));
    }

    @PatchMapping("/{publicId}")
    @PreAuthorize(WRITE_ROLES)
    SiteResponse update(@PathVariable String publicId,
                        @Valid @RequestBody UpdateSiteRequest request,
                        @AuthenticationPrincipal Jwt caller) {
        return siteService.update(parseUuid(publicId), request, subject(caller));
    }

    @PostMapping("/{publicId}/archive")
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable String publicId,
                 @Valid @RequestBody ArchiveRequest request,
                 @AuthenticationPrincipal Jwt caller) {
        siteService.archive(parseUuid(publicId), request.reason().trim(), subject(caller));
    }

    @PostMapping("/{publicId}/restore")
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void restore(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        siteService.restore(parseUuid(publicId), subject(caller));
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            throw new OrganizationException(OrganizationException.Kind.SITE_NOT_FOUND);
        }
    }

    private static String subject(Jwt caller) {
        return caller != null ? caller.getSubject() : null;
    }
}
