package com.esic.connect.organization.internal;

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

import java.util.UUID;

/**
 * Plages réseau autorisées par site (docs/04 §9.4, cahier §17.9).
 *
 * <p><strong>Toutes</strong> les opérations, consultation comprise, sont
 * réservées à {@code SUPER_ADMIN} : {@code @PreAuthorize} au niveau de la
 * classe. Création et liste nichées sous un site ; activation /
 * désactivation par {@code public_id} de la plage. Aucune suppression.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('SUPER_ADMIN')")
class SiteNetworkRangeController {

    private final SiteNetworkRangeService rangeService;

    SiteNetworkRangeController(SiteNetworkRangeService rangeService) {
        this.rangeService = rangeService;
    }

    @GetMapping("/sites/{sitePublicId}/network-ranges")
    PageResponse<SiteNetworkRangeResponse> list(@PathVariable String sitePublicId,
                                                @RequestParam(required = false) String active,
                                                @RequestParam(required = false) String sort,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return rangeService.listForSite(parseSiteUuid(sitePublicId), active, page, size, sort);
    }

    @PostMapping("/sites/{sitePublicId}/network-ranges")
    @ResponseStatus(HttpStatus.CREATED)
    SiteNetworkRangeResponse create(@PathVariable String sitePublicId,
                                    @Valid @RequestBody CreateNetworkRangeRequest request,
                                    @AuthenticationPrincipal Jwt caller) {
        return rangeService.create(parseSiteUuid(sitePublicId), request, subject(caller));
    }

    @GetMapping("/network-ranges/{publicId}")
    SiteNetworkRangeResponse get(@PathVariable String publicId) {
        return rangeService.get(parseRangeUuid(publicId));
    }

    @PostMapping("/network-ranges/{publicId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        rangeService.deactivate(parseRangeUuid(publicId), subject(caller));
    }

    @PostMapping("/network-ranges/{publicId}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void activate(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        rangeService.activate(parseRangeUuid(publicId), subject(caller));
    }

    private static UUID parseSiteUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            throw new OrganizationException(OrganizationException.Kind.SITE_NOT_FOUND);
        }
    }

    private static UUID parseRangeUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            throw new OrganizationException(OrganizationException.Kind.NETWORK_RANGE_NOT_FOUND);
        }
    }

    private static String subject(Jwt caller) {
        return caller != null ? caller.getSubject() : null;
    }
}
