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
 * Administration des bâtiments (docs/04 §9.2). Création et liste nichées
 * sous un site ; opérations unitaires par {@code public_id} du bâtiment.
 * Mêmes rôles que {@link SiteController}.
 */
@RestController
@RequestMapping("/api/v1")
class BuildingController {

    private final BuildingService buildingService;

    BuildingController(BuildingService buildingService) {
        this.buildingService = buildingService;
    }

    @GetMapping("/sites/{sitePublicId}/buildings")
    @PreAuthorize(SiteController.READ_ROLES)
    PageResponse<BuildingResponse> list(@PathVariable String sitePublicId,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(required = false) String sort,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return buildingService.listForSite(parseSiteUuid(sitePublicId), status, q, page, size, sort);
    }

    @PostMapping("/sites/{sitePublicId}/buildings")
    @PreAuthorize(SiteController.WRITE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    BuildingResponse create(@PathVariable String sitePublicId,
                            @Valid @RequestBody CreateBuildingRequest request,
                            @AuthenticationPrincipal Jwt caller) {
        return buildingService.create(parseSiteUuid(sitePublicId), request, subject(caller));
    }

    @GetMapping("/buildings/{publicId}")
    @PreAuthorize(SiteController.READ_ROLES)
    BuildingResponse get(@PathVariable String publicId) {
        return buildingService.get(parseBuildingUuid(publicId));
    }

    @PatchMapping("/buildings/{publicId}")
    @PreAuthorize(SiteController.WRITE_ROLES)
    BuildingResponse update(@PathVariable String publicId,
                            @Valid @RequestBody UpdateBuildingRequest request,
                            @AuthenticationPrincipal Jwt caller) {
        return buildingService.update(parseBuildingUuid(publicId), request, subject(caller));
    }

    @PostMapping("/buildings/{publicId}/archive")
    @PreAuthorize(SiteController.WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable String publicId,
                 @Valid @RequestBody ArchiveRequest request,
                 @AuthenticationPrincipal Jwt caller) {
        buildingService.archive(parseBuildingUuid(publicId), request.reason().trim(), subject(caller));
    }

    @PostMapping("/buildings/{publicId}/restore")
    @PreAuthorize(SiteController.WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void restore(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        buildingService.restore(parseBuildingUuid(publicId), subject(caller));
    }

    private static UUID parseSiteUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            throw new OrganizationException(OrganizationException.Kind.SITE_NOT_FOUND);
        }
    }

    private static UUID parseBuildingUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            throw new OrganizationException(OrganizationException.Kind.BUILDING_NOT_FOUND);
        }
    }

    private static String subject(Jwt caller) {
        return caller != null ? caller.getSubject() : null;
    }
}
