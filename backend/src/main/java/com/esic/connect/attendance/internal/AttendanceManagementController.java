package com.esic.connect.attendance.internal;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.nio.charset.StandardCharsets;
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

    /**
     * Candidats à une saisie manuelle : inscriptions actives des classes
     * de la séance (correctif PR #22 §2). Contrôle fin identique à la
     * consultation des présences ({@code AccessLevel.READ} appliqué par le
     * service) : {@code ADMIN}/{@code SUPER_ADMIN} global,
     * {@code SCHOOL_ADMINISTRATION} lecture, {@code PEDAGOGICAL_MANAGER}
     * dans son périmètre, {@code TEACHER} uniquement sur ses séances.
     * {@code STUDENT} et anonyme refusés par le {@code @PreAuthorize}.
     */
    @GetMapping("/candidates")
    @PreAuthorize(AttendanceManagementWeb.MANAGE_ROLES)
    List<AttendanceCandidateResponse> candidates(@PathVariable String sessionId,
                                                 @AuthenticationPrincipal Jwt caller) {
        return service.candidates(sessionId, AttendanceManagementWeb.subject(caller));
    }

    /**
     * Export CSV des présences de <strong>cette séance</strong> (correctif
     * PR #22 §8). Même contrôle fin que la consultation de la séance : un
     * formateur affecté peut exporter sa séance. Protections CSV
     * identiques aux autres exports (UTF-8 + BOM, {@code ;}, RFC 4180,
     * neutralisation d'injection de formule) ; aucun identifiant SQL,
     * aucune adresse électronique ; nom de fichier contrôlé.
     */
    @GetMapping("/export")
    @PreAuthorize(AttendanceManagementWeb.MANAGE_ROLES)
    ResponseEntity<byte[]> export(@PathVariable String sessionId, @AuthenticationPrincipal Jwt caller) {
        AttendanceManagementService.SessionCsv csv =
                service.exportSessionCsv(sessionId, AttendanceManagementWeb.subject(caller));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + csv.fileName() + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.content().getBytes(StandardCharsets.UTF_8));
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
