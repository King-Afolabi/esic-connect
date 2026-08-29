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
 * Affectations historisées d'un rythme à une classe (docs/04 §14.2) et
 * résolution du contexte d'une classe à une date (section 9 du lot).
 *
 * <p>Lecture et écriture ouvertes à
 * {@code ADMIN}/{@code SUPER_ADMIN}/{@code SCHOOL_ADMINISTRATION}/{@code PEDAGOGICAL_MANAGER},
 * ce dernier étant restreint à son périmètre par le service (via
 * {@code AcademicScopeDirectory}, jamais d'après un paramètre client).
 * Identifiants exclusivement en {@code public_id}. Aucun {@code PATCH},
 * aucun {@code DELETE} : une affectation est clôturée, jamais supprimée.
 */
@RestController
class ClassWorkStudyPatternController {

    private final ClassWorkStudyPatternService service;
    private final AlternationContextService contextService;

    ClassWorkStudyPatternController(ClassWorkStudyPatternService service,
                                    AlternationContextService contextService) {
        this.service = service;
        this.contextService = contextService;
    }

    @PostMapping("/api/v1/alternation/class-assignments")
    @PreAuthorize(AlternationWeb.SCOPED_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    ClassAssignmentResponse assign(@Valid @RequestBody ClassAssignmentRequests.Assign request,
                                   @AuthenticationPrincipal Jwt caller) {
        return service.assign(request, AlternationWeb.subject(caller));
    }

    @GetMapping("/api/v1/alternation/class-assignments")
    @PreAuthorize(AlternationWeb.SCOPED_ROLES)
    PageResponse<ClassAssignmentResponse> list(
            @RequestParam(name = "class", required = false) String classGroupPublicId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(classGroupPublicId, status, page, size, sort);
    }

    @GetMapping("/api/v1/alternation/class-assignments/{publicId}")
    @PreAuthorize(AlternationWeb.SCOPED_ROLES)
    ClassAssignmentResponse get(@PathVariable String publicId) {
        return service.get(AlternationWeb.parseUuid(publicId,
                AlternationException.Kind.CLASS_ASSIGNMENT_NOT_FOUND));
    }

    @PostMapping("/api/v1/alternation/class-assignments/{publicId}/close")
    @PreAuthorize(AlternationWeb.SCOPED_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void close(@PathVariable String publicId,
               @Valid @RequestBody ClassAssignmentRequests.Close request,
               @AuthenticationPrincipal Jwt caller) {
        service.close(AlternationWeb.parseUuid(publicId, AlternationException.Kind.CLASS_ASSIGNMENT_NOT_FOUND),
                request, AlternationWeb.subject(caller));
    }

    @GetMapping("/api/v1/alternation/classes/{classPublicId}/assignments")
    @PreAuthorize(AlternationWeb.SCOPED_ROLES)
    PageResponse<ClassAssignmentResponse> listByClass(
            @PathVariable String classPublicId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listByClass(classPublicId, status, page, size, sort);
    }

    @GetMapping("/api/v1/alternation/classes/{classPublicId}/context")
    @PreAuthorize(AlternationWeb.SCOPED_ROLES)
    AlternationContextResponse classContext(
            @PathVariable String classPublicId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return contextService.resolveClassContext(classPublicId, date);
    }
}
