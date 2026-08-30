package com.esic.connect.attendance.internal;

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
 * Gestion manuelle des présences d'une séance (V10) : ajout manuel,
 * correction, annulation logique, historique. Identifiants exclusivement
 * en {@code public_id}. Le contrôle fin de périmètre est appliqué par
 * {@link AttendanceManagementService} (via
 * {@code CourseSessionDirectory}).
 */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/attendance")
class AttendanceManagementController {

    private final AttendanceManagementService service;

    AttendanceManagementController(AttendanceManagementService service) {
        this.service = service;
    }

    @PostMapping("/manual")
    @PreAuthorize(AttendanceManagementWeb.MANAGE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    AttendanceRecordResponse recordManual(@PathVariable String sessionId,
                                          @Valid @RequestBody AttendanceManagementRequests.ManualRecord request,
                                          @AuthenticationPrincipal Jwt caller) {
        return service.recordManual(sessionId, request, AttendanceManagementWeb.subject(caller));
    }

    @PostMapping("/{attendanceId}/correct")
    @PreAuthorize(AttendanceManagementWeb.MANAGE_ROLES)
    AttendanceRecordResponse correct(@PathVariable String sessionId, @PathVariable String attendanceId,
                                     @Valid @RequestBody AttendanceManagementRequests.Correct request,
                                     @AuthenticationPrincipal Jwt caller) {
        return service.correct(sessionId, attendanceId, request, AttendanceManagementWeb.subject(caller));
    }

    @PostMapping("/{attendanceId}/cancel")
    @PreAuthorize(AttendanceManagementWeb.MANAGE_ROLES)
    AttendanceRecordResponse cancel(@PathVariable String sessionId, @PathVariable String attendanceId,
                                    @Valid @RequestBody AttendanceManagementRequests.Cancel request,
                                    @AuthenticationPrincipal Jwt caller) {
        return service.cancel(sessionId, attendanceId, request, AttendanceManagementWeb.subject(caller));
    }

    @GetMapping("/{attendanceId}/history")
    @PreAuthorize(AttendanceManagementWeb.MANAGE_ROLES)
    List<AttendanceCorrectionResponse> history(@PathVariable String sessionId,
                                               @PathVariable String attendanceId,
                                               @AuthenticationPrincipal Jwt caller) {
        return service.history(sessionId, attendanceId, AttendanceManagementWeb.subject(caller));
    }
}
