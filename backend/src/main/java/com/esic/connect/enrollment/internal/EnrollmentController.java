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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inscriptions historiques (docs/04 §13 ; RG-012, RG-023 ; AC-006).
 * Routes — liste, détail, création, changement de classe, clôture —
 * réservées à
 * {@code ADMIN}/{@code SUPER_ADMIN}/{@code SCHOOL_ADMINISTRATION}. Aucun
 * {@code PATCH}, aucun {@code DELETE} (une inscription est clôturée, pas
 * supprimée — docs/04 §13.4). Identifiants exclusivement en
 * {@code public_id}.
 */
@RestController
@RequestMapping("/api/v1/enrollments")
class EnrollmentController {

    private final EnrollmentService service;

    EnrollmentController(EnrollmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(EnrollmentWeb.MANAGE_ROLES)
    PageResponse<EnrollmentResponse> list(
            @RequestParam(required = false) String student,
            @RequestParam(required = false) String classGroup,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(student, classGroup, status, page, size, sort);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize(EnrollmentWeb.MANAGE_ROLES)
    EnrollmentResponse get(@PathVariable String publicId) {
        return service.get(EnrollmentWeb.parseUuid(publicId, EnrollmentException.Kind.ENROLLMENT_NOT_FOUND));
    }

    @PostMapping
    @PreAuthorize(EnrollmentWeb.MANAGE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    EnrollmentResponse enroll(@Valid @RequestBody EnrollmentRequests.Enroll request,
                              @AuthenticationPrincipal Jwt caller) {
        return service.enroll(request, EnrollmentWeb.subject(caller));
    }

    @PostMapping("/{publicId}/transfer")
    @PreAuthorize(EnrollmentWeb.MANAGE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    EnrollmentResponse transfer(@PathVariable String publicId,
                                @Valid @RequestBody EnrollmentRequests.Transfer request,
                                @AuthenticationPrincipal Jwt caller) {
        return service.transfer(EnrollmentWeb.parseUuid(publicId, EnrollmentException.Kind.ENROLLMENT_NOT_FOUND),
                request, EnrollmentWeb.subject(caller));
    }

    @PostMapping("/{publicId}/close")
    @PreAuthorize(EnrollmentWeb.MANAGE_ROLES)
    EnrollmentResponse close(@PathVariable String publicId,
                             @Valid @RequestBody EnrollmentRequests.Close request,
                             @AuthenticationPrincipal Jwt caller) {
        return service.close(EnrollmentWeb.parseUuid(publicId, EnrollmentException.Kind.ENROLLMENT_NOT_FOUND),
                request, EnrollmentWeb.subject(caller));
    }
}
