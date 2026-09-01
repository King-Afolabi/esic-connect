package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.internal.JustificationAttachmentResponses.Meta;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * File des justificatifs d'absence pour la gestion (V10). Lecture
 * ouverte à {@code ADMIN}/{@code SUPER_ADMIN}/{@code SCHOOL_ADMINISTRATION}/
 * {@code PEDAGOGICAL_MANAGER} (périmètre) — {@code TEACHER} n'obtient
 * rien via cette route (il consulte les justificatifs de ses séances via
 * l'historique des présences). L'examen exclut {@code TEACHER}. Le
 * périmètre pédagogique est appliqué par le service
 * ({@code AcademicScopeDirectory}).
 */
@RestController
@RequestMapping("/api/v1/attendance/justifications")
class AttendanceJustificationController {

    private final AttendanceJustificationService service;

    AttendanceJustificationController(AttendanceJustificationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(AttendanceManagementWeb.REVIEW_LIST_ROLES)
    List<JustificationResponse> list(@RequestParam(required = false) String status,
                                     @AuthenticationPrincipal Jwt caller) {
        return service.listForReview(status, AttendanceManagementWeb.subject(caller));
    }

    @GetMapping("/{justificationId}")
    @PreAuthorize(AttendanceManagementWeb.REVIEW_LIST_ROLES)
    JustificationResponse get(@PathVariable String justificationId, @AuthenticationPrincipal Jwt caller) {
        return service.getForReview(justificationId, AttendanceManagementWeb.subject(caller));
    }

    @PostMapping("/{justificationId}/review")
    @PreAuthorize(AttendanceManagementWeb.REVIEW_ROLES)
    JustificationResponse review(@PathVariable String justificationId,
                                 @Valid @RequestBody JustificationRequests.Review request,
                                 @AuthenticationPrincipal Jwt caller) {
        return service.review(justificationId, request, AttendanceManagementWeb.subject(caller));
    }

    // --- Pièce jointe : consultation / téléchargement par l'examinateur ---

    @GetMapping("/{justificationId}/attachment")
    @PreAuthorize(AttendanceManagementWeb.REVIEW_LIST_ROLES)
    Meta attachment(@PathVariable String justificationId, @AuthenticationPrincipal Jwt caller) {
        return service.getReviewAttachment(justificationId, AttendanceManagementWeb.subject(caller));
    }

    @GetMapping("/{justificationId}/attachment/download")
    @PreAuthorize(AttendanceManagementWeb.REVIEW_LIST_ROLES)
    ResponseEntity<InputStreamResource> downloadAttachment(@PathVariable String justificationId,
                                                           @AuthenticationPrincipal Jwt caller) {
        return JustificationAttachmentResponses.download(
                service.openReviewAttachment(justificationId, AttendanceManagementWeb.subject(caller)));
    }
}
