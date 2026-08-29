package com.esic.connect.alternation.internal;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Exceptions individuelles de calendrier (docs/04 §14.3) et résolution du
 * contexte <em>effectif</em> d'une inscription à une date (section 10 du
 * lot).
 *
 * <p>Lecture et écriture ouvertes à
 * {@code ADMIN}/{@code SUPER_ADMIN}/{@code SCHOOL_ADMINISTRATION}/{@code PEDAGOGICAL_MANAGER},
 * ce dernier restreint aux inscriptions dont la classe relève de son
 * périmètre (via {@code EnrollmentDirectory} + {@code AcademicScopeDirectory}).
 * Identifiants exclusivement en {@code public_id}. Aucun {@code PATCH},
 * aucun {@code DELETE} : une exception est annulée, jamais supprimée.
 */
@RestController
class StudentScheduleExceptionController {

    private final StudentScheduleExceptionService service;
    private final AlternationContextService contextService;

    StudentScheduleExceptionController(StudentScheduleExceptionService service,
                                       AlternationContextService contextService) {
        this.service = service;
        this.contextService = contextService;
    }

    @PostMapping("/api/v1/alternation/student-exceptions")
    @PreAuthorize(AlternationWeb.SCOPED_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    StudentExceptionResponse create(@Valid @RequestBody StudentExceptionRequests.Create request,
                                    @AuthenticationPrincipal Jwt caller) {
        return service.create(request, AlternationWeb.subject(caller));
    }

    @GetMapping("/api/v1/alternation/student-exceptions/{publicId}")
    @PreAuthorize(AlternationWeb.SCOPED_ROLES)
    StudentExceptionResponse get(@PathVariable String publicId) {
        return service.get(AlternationWeb.parseUuid(publicId, AlternationException.Kind.EXCEPTION_NOT_FOUND));
    }

    @PostMapping("/api/v1/alternation/student-exceptions/{publicId}/cancel")
    @PreAuthorize(AlternationWeb.SCOPED_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable String publicId,
                @Valid @RequestBody StudentExceptionRequests.Cancel request,
                @AuthenticationPrincipal Jwt caller) {
        service.cancel(AlternationWeb.parseUuid(publicId, AlternationException.Kind.EXCEPTION_NOT_FOUND),
                request, AlternationWeb.subject(caller));
    }

    @GetMapping("/api/v1/alternation/enrollments/{enrollmentPublicId}/exceptions")
    @PreAuthorize(AlternationWeb.SCOPED_ROLES)
    PageResponse<StudentExceptionResponse> listByEnrollment(
            @PathVariable String enrollmentPublicId,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listByEnrollment(enrollmentPublicId, page, size, sort);
    }

    @GetMapping("/api/v1/alternation/enrollments/{enrollmentPublicId}/context")
    @PreAuthorize(AlternationWeb.SCOPED_ROLES)
    EnrollmentContextResponse enrollmentContext(
            @PathVariable String enrollmentPublicId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return contextService.resolveEnrollmentContext(enrollmentPublicId, date);
    }
}
