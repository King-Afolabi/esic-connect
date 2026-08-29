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
 * Profils apprenants (docs/04 §11.1). Routes minimales — liste, détail,
 * création — réservées à
 * {@code ADMIN}/{@code SUPER_ADMIN}/{@code SCHOOL_ADMINISTRATION}. Aucun
 * {@code PATCH}, aucun {@code DELETE}. Identifiants exclusivement en
 * {@code public_id}.
 */
@RestController
@RequestMapping("/api/v1/student-profiles")
class StudentProfileController {

    private final StudentProfileService service;

    StudentProfileController(StudentProfileService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(EnrollmentWeb.MANAGE_ROLES)
    PageResponse<StudentProfileResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(q, status, user, page, size, sort);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize(EnrollmentWeb.MANAGE_ROLES)
    StudentProfileResponse get(@PathVariable String publicId) {
        return service.get(EnrollmentWeb.parseUuid(publicId,
                EnrollmentException.Kind.STUDENT_PROFILE_NOT_FOUND));
    }

    @PostMapping
    @PreAuthorize(EnrollmentWeb.MANAGE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    StudentProfileResponse create(@Valid @RequestBody StudentProfileRequests.Create request,
                                  @AuthenticationPrincipal Jwt caller) {
        return service.create(request, EnrollmentWeb.subject(caller));
    }
}
