package com.esic.connect.enrollment.internal;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Suivi à distance individuel (EF-ENR-004 ; docs/02 §15.3).
 *
 * <p>Ouvert au {@code PEDAGOGICAL_MANAGER} — c'est explicitement lui qui
 * « autorise un suivi à distance » (docs/02 §5.5) — avec contrôle de
 * périmètre côté serveur : il ne peut autoriser que dans ses classes, et
 * une autorisation <em>générale</em> lui est refusée puisqu'elle
 * vaudrait aussi pour les classes d'un autre.
 *
 * <p>{@code TEACHER} n'a aucun accès : il signale une exception, il ne
 * l'accorde pas.
 */
@RestController
@RequestMapping("/api/v1/remote-attendance-authorizations")
class RemoteAttendanceController {

    private static final String DECIDE_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER')";

    private final RemoteAttendanceService service;

    RemoteAttendanceController(RemoteAttendanceService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize(DECIDE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    RemoteAttendanceResponse authorize(@Valid @RequestBody RemoteAttendanceRequests.Authorize request,
                                       @AuthenticationPrincipal Jwt caller) {
        return service.authorize(request, EnrollmentWeb.subject(caller));
    }

    @PostMapping("/{publicId}/revoke")
    @PreAuthorize(DECIDE_ROLES)
    RemoteAttendanceResponse revoke(@PathVariable String publicId,
                                    @Valid @RequestBody RemoteAttendanceRequests.Revoke request,
                                    @AuthenticationPrincipal Jwt caller) {
        return service.revoke(
                EnrollmentWeb.parseUuid(publicId, EnrollmentException.Kind.ENROLLMENT_NOT_FOUND),
                request, EnrollmentWeb.subject(caller));
    }

    /** Autorisations d'un apprenant, la plus récente en tête. */
    @GetMapping("/students/{studentUserPublicId}")
    @PreAuthorize(DECIDE_ROLES)
    List<RemoteAttendanceResponse> listForStudent(@PathVariable String studentUserPublicId) {
        return service.listForStudent(EnrollmentWeb.parseUuid(studentUserPublicId,
                EnrollmentException.Kind.STUDENT_NOT_FOUND));
    }
}
