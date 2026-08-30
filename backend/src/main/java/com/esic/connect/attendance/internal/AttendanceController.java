package com.esic.connect.attendance.internal;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Routes d'émargement :
 * <ul>
 *   <li>{@code POST /api/v1/sessions/{publicId}/attendance-token} — émet
 *       un jeton dynamique + code court (formateur / gestionnaire d'une
 *       séance ouverte) ;</li>
 *   <li>{@code GET  /api/v1/sessions/{publicId}/attendance} — présences
 *       de la séance ;</li>
 *   <li>{@code POST /api/v1/attendance/validate} — enregistre la présence
 *       de l'apprenant authentifié à partir d'un jeton ou d'un code
 *       court (réponse {@code 200} avec le récépissé).</li>
 * </ul>
 * Aucun identifiant SQL ni jeton n'apparaît dans une URL.
 */
@RestController
class AttendanceController {

    private final AttendanceService service;

    AttendanceController(AttendanceService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/sessions/{publicId}/attendance-token")
    @PreAuthorize(AttendanceWeb.MANAGE_ROLES)
    AttendanceTokenResponse issueToken(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        // Compat V9 : cible le premier point de contrôle OUVERT de la séance.
        return service.issueToken(publicId, null, AttendanceWeb.subject(caller));
    }

    @PostMapping("/api/v1/sessions/{sessionId}/checkpoints/{checkpointId}/attendance-token")
    @PreAuthorize(AttendanceWeb.MANAGE_ROLES)
    AttendanceTokenResponse issueCheckpointToken(@PathVariable String sessionId,
                                                 @PathVariable String checkpointId,
                                                 @AuthenticationPrincipal Jwt caller) {
        return service.issueToken(sessionId, checkpointId, AttendanceWeb.subject(caller));
    }

    @GetMapping("/api/v1/sessions/{publicId}/attendance")
    @PreAuthorize(AttendanceWeb.READ_ROLES)
    SessionAttendanceResponse list(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        return service.listForSession(publicId, AttendanceWeb.subject(caller));
    }

    @PostMapping("/api/v1/attendance/validate")
    @PreAuthorize(AttendanceWeb.VALIDATE_ROLE)
    AttendanceRecordResponse validate(@Valid @RequestBody AttendanceRequests.Validate request,
                                      @AuthenticationPrincipal Jwt caller) {
        return service.validate(request, AttendanceWeb.subject(caller));
    }
}
