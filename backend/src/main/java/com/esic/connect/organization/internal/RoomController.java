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
 * Administration des salles (docs/04 §9.3). Création et liste nichées sous
 * un site ; opérations unitaires par {@code public_id} de la salle. Mêmes
 * rôles que {@link SiteController}.
 */
@RestController
@RequestMapping("/api/v1")
class RoomController {

    private final RoomService roomService;

    RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping("/sites/{sitePublicId}/rooms")
    @PreAuthorize(SiteController.READ_ROLES)
    PageResponse<RoomResponse> list(@PathVariable String sitePublicId,
                                    @RequestParam(required = false) String building,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) String sort,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return roomService.listForSite(parseSiteUuid(sitePublicId), building, status, q, page, size, sort);
    }

    @PostMapping("/sites/{sitePublicId}/rooms")
    @PreAuthorize(SiteController.WRITE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    RoomResponse create(@PathVariable String sitePublicId,
                        @Valid @RequestBody CreateRoomRequest request,
                        @AuthenticationPrincipal Jwt caller) {
        return roomService.create(parseSiteUuid(sitePublicId), request, subject(caller));
    }

    @GetMapping("/rooms/{publicId}")
    @PreAuthorize(SiteController.READ_ROLES)
    RoomResponse get(@PathVariable String publicId) {
        return roomService.get(parseRoomUuid(publicId));
    }

    @PatchMapping("/rooms/{publicId}")
    @PreAuthorize(SiteController.WRITE_ROLES)
    RoomResponse update(@PathVariable String publicId,
                        @Valid @RequestBody UpdateRoomRequest request,
                        @AuthenticationPrincipal Jwt caller) {
        return roomService.update(parseRoomUuid(publicId), request, subject(caller));
    }

    @PostMapping("/rooms/{publicId}/archive")
    @PreAuthorize(SiteController.WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable String publicId,
                 @Valid @RequestBody ArchiveRequest request,
                 @AuthenticationPrincipal Jwt caller) {
        roomService.archive(parseRoomUuid(publicId), request.reason().trim(), subject(caller));
    }

    @PostMapping("/rooms/{publicId}/restore")
    @PreAuthorize(SiteController.WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void restore(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        roomService.restore(parseRoomUuid(publicId), subject(caller));
    }

    private static UUID parseSiteUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            throw new OrganizationException(OrganizationException.Kind.SITE_NOT_FOUND);
        }
    }

    private static UUID parseRoomUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            throw new OrganizationException(OrganizationException.Kind.ROOM_NOT_FOUND);
        }
    }

    private static String subject(Jwt caller) {
        return caller != null ? caller.getSubject() : null;
    }
}
