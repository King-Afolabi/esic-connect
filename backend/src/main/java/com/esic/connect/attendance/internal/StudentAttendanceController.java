package com.esic.connect.attendance.internal;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Espace « Mes présences » de l'apprenant (V10). Réservé au rôle
 * {@code STUDENT} ; le serveur résout l'apprenant à partir du seul JWT
 * (jamais d'identifiant transmis). Le dépôt d'un justificatif est une
 * métadonnée métier (catégorie, référence, commentaire) — aucune pièce
 * jointe dans cette tranche.
 */
@RestController
class StudentAttendanceController {

    private final StudentAttendanceService attendanceService;
    private final AttendanceJustificationService justificationService;

    StudentAttendanceController(StudentAttendanceService attendanceService,
                                AttendanceJustificationService justificationService) {
        this.attendanceService = attendanceService;
        this.justificationService = justificationService;
    }

    @GetMapping("/api/v1/me/attendance")
    @PreAuthorize(AttendanceManagementWeb.STUDENT_ROLE)
    PageResponse<MyAttendanceRow> listMyAttendance(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt caller) {
        return attendanceService.listOwn(AttendanceManagementWeb.subject(caller), from, to, status, page, size);
    }

    @GetMapping("/api/v1/me/attendance/{attendanceId}")
    @PreAuthorize(AttendanceManagementWeb.STUDENT_ROLE)
    MyAttendanceDetail getMyAttendance(@PathVariable String attendanceId, @AuthenticationPrincipal Jwt caller) {
        return attendanceService.getOwn(AttendanceManagementWeb.subject(caller), attendanceId);
    }

    @PostMapping("/api/v1/me/attendance/justifications")
    @PreAuthorize(AttendanceManagementWeb.STUDENT_ROLE)
    @ResponseStatus(HttpStatus.CREATED)
    JustificationResponse submit(@Valid @RequestBody JustificationRequests.Submit request,
                                 @AuthenticationPrincipal Jwt caller) {
        return justificationService.submit(request, AttendanceManagementWeb.subject(caller));
    }

    @PutMapping("/api/v1/me/attendance/justifications/{justificationId}")
    @PreAuthorize(AttendanceManagementWeb.STUDENT_ROLE)
    JustificationResponse amend(@PathVariable String justificationId,
                                @Valid @RequestBody JustificationRequests.Amend request,
                                @AuthenticationPrincipal Jwt caller) {
        return justificationService.amendOwn(justificationId, request, AttendanceManagementWeb.subject(caller));
    }

    @GetMapping("/api/v1/me/attendance/justifications")
    @PreAuthorize(AttendanceManagementWeb.STUDENT_ROLE)
    List<JustificationResponse> listMyJustifications(@AuthenticationPrincipal Jwt caller) {
        return justificationService.listOwn(AttendanceManagementWeb.subject(caller));
    }

    @GetMapping("/api/v1/me/attendance/justifications/{justificationId}")
    @PreAuthorize(AttendanceManagementWeb.STUDENT_ROLE)
    JustificationResponse getMyJustification(@PathVariable String justificationId,
                                             @AuthenticationPrincipal Jwt caller) {
        return justificationService.getOwn(justificationId, AttendanceManagementWeb.subject(caller));
    }
}
