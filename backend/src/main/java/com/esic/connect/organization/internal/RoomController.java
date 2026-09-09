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

    /**
     * Consultation et impression du QR fixe (EF-ORG-003). Volontairement
     * plus large que {@link SiteController#WRITE_ROLES} : l'administration
     * scolaire doit pouvoir réimprimer une affiche décollée sans pouvoir
     * la renouveler.
     */
    static final String STATIC_QR_VIEW_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION')";
    /**
     * Renouvellement / révocation du QR fixe (EF-ORG-003) — action
     * exceptionnelle qui invalide toutes les affiches en salle. Réservée
     * à {@code ADMIN} : {@code SUPER_ADMIN} garde la lecture et
     * l'impression, mais ne renouvelle pas dans le parcours normal, et
     * {@code SCHOOL_ADMINISTRATION} non plus.
     */
    static final String STATIC_QR_ROTATE_ROLES = "hasRole('ADMIN')";

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

    /**
     * Vue administrative du QR fixe de la salle (EF-ORG-003 ; docs/02
     * §7.1, §16.6) — <strong>réimpression</strong>. Ne modifie rien : même
     * jeton, même date d'émission, les affiches posées restent valides.
     * Renvoie {@code issued == false} tant qu'aucun QR n'a été émis.
     *
     * <p>Ouvert à {@code SCHOOL_ADMINISTRATION} en plus de
     * {@code ADMIN} / {@code SUPER_ADMIN} : réimprimer une affiche est un
     * geste d'exploitation courant. La référence complète n'est renvoyée
     * qu'ici — elle a disparu de {@link RoomResponse}.
     */
    @GetMapping("/rooms/{publicId}/static-qr")
    @PreAuthorize(STATIC_QR_VIEW_ROLES)
    RoomStaticQrView getStaticQr(@PathVariable String publicId) {
        return roomService.getStaticQr(parseRoomUuid(publicId));
    }

    /**
     * Émet ou <strong>renouvelle</strong> le QR fixe de la salle
     * (EF-ORG-003 ; docs/02 §7.1). Le jeton est <strong>généré par le
     * serveur</strong> : une référence saisie à la main serait devinable,
     * et un QR devinable n'est pas un contrôle.
     *
     * <p>Réservé à {@code ADMIN} : le renouvellement invalide
     * immédiatement toutes les affiches en salle, il n'est pas dans le
     * parcours normal d'un {@code SUPER_ADMIN} ni d'un
     * {@code SCHOOL_ADMINISTRATION}. Audité via l'outbox transactionnelle.
     */
    @PostMapping({"/rooms/{publicId}/static-qr", "/rooms/{publicId}/static-qr/rotate"})
    @PreAuthorize(STATIC_QR_ROTATE_ROLES)
    RoomStaticQrView rotateStaticQr(@PathVariable String publicId,
                                    @AuthenticationPrincipal Jwt caller) {
        return roomService.rotateStaticQr(parseRoomUuid(publicId), subject(caller));
    }

    /**
     * Retire le QR : la salle n'accepte plus d'émargement par affiche.
     * Même restriction que le renouvellement ({@code ADMIN} seul).
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/rooms/{publicId}/static-qr")
    @PreAuthorize(STATIC_QR_ROTATE_ROLES)
    RoomStaticQrView revokeStaticQr(@PathVariable String publicId,
                                    @AuthenticationPrincipal Jwt caller) {
        return roomService.revokeStaticQr(parseRoomUuid(publicId), subject(caller));
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
