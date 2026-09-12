package com.esic.connect.enrollment.internal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Écran « Apprenants » (refonte 2026-09). Liste et détail de tous les
 * comptes porteurs d'un rôle actif {@code STUDENT} — le rôle est l'unique
 * source de vérité du statut apprenant, jamais l'existence d'un
 * {@code student_profile} ni d'une {@code enrollment} (voir
 * {@link StudentDirectoryService}). Mêmes rôles et même périmètre que
 * {@code GET /api/v1/student-profiles} / {@code GET /api/v1/enrollments}
 * ({@link EnrollmentWeb#READ_ROLES}).
 */
@RestController
@RequestMapping("/api/v1/students")
class StudentController {

    private final StudentDirectoryService service;

    StudentController(StudentDirectoryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(EnrollmentWeb.READ_ROLES)
    PageResponse<StudentResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt caller) {
        return service.list(q, status, page, size, sort, EnrollmentWeb.subject(caller));
    }

    @GetMapping("/{userPublicId}")
    @PreAuthorize(EnrollmentWeb.READ_ROLES)
    StudentResponse get(@PathVariable String userPublicId, @AuthenticationPrincipal Jwt caller) {
        return service.get(EnrollmentWeb.parseUuid(userPublicId, EnrollmentException.Kind.STUDENT_NOT_FOUND),
                EnrollmentWeb.subject(caller));
    }
}
