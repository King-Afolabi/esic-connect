package com.esic.connect.academic.internal;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Référentiel des matières (EF-ACA-006 ; docs/02 §6.4 et §30.2).
 *
 * <p>Lecture ouverte aux rôles de gestion <em>et</em> aux formateurs :
 * un formateur doit pouvoir désigner la matière d'une séance. L'écriture
 * reste aux rôles de gestion, avec contrôle de périmètre sur les
 * formations rattachées (`AcademicScopeGuard`).
 */
@RestController
@RequestMapping("/api/v1/subjects")
class SubjectController {

    /**
     * Le catalogue des matières n'est pas une donnée sensible et sert à
     * toute l'application : un formateur le lit pour qualifier une
     * séance. Le masquer produirait des doublons, pas de la sécurité.
     */
    private static final String SUBJECT_READ_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER','TEACHER')";

    private final SubjectService service;

    SubjectController(SubjectService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SUBJECT_READ_ROLES)
    PageResponse<SubjectResponse> list(@RequestParam(required = false) String status,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(required = false) String programId,
                                       @RequestParam(required = false) String sort,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return service.list(status, q, programId, page, size, sort);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize(SUBJECT_READ_ROLES)
    SubjectResponse get(@PathVariable String publicId) {
        return service.get(AcademicWeb.parseUuid(publicId, AcademicException.Kind.SUBJECT_NOT_FOUND));
    }

    @PostMapping
    @PreAuthorize(AcademicWeb.SCOPED_WRITE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    SubjectResponse create(@Valid @RequestBody SubjectRequests.Create request,
                           @AuthenticationPrincipal Jwt caller) {
        return service.create(request, AcademicWeb.subject(caller));
    }

    @PatchMapping("/{publicId}")
    @PreAuthorize(AcademicWeb.SCOPED_WRITE_ROLES)
    SubjectResponse update(@PathVariable String publicId,
                           @Valid @RequestBody SubjectRequests.Update request,
                           @AuthenticationPrincipal Jwt caller) {
        return service.update(AcademicWeb.parseUuid(publicId, AcademicException.Kind.SUBJECT_NOT_FOUND),
                request, AcademicWeb.subject(caller));
    }

    @PostMapping("/{publicId}/archive")
    @PreAuthorize(AcademicWeb.SCOPED_WRITE_ROLES)
    SubjectResponse archive(@PathVariable String publicId,
                            @Valid @RequestBody ArchiveRequest request,
                            @AuthenticationPrincipal Jwt caller) {
        return service.archive(AcademicWeb.parseUuid(publicId, AcademicException.Kind.SUBJECT_NOT_FOUND),
                request.reason(), AcademicWeb.subject(caller));
    }

    @PostMapping("/{publicId}/restore")
    @PreAuthorize(AcademicWeb.SCOPED_WRITE_ROLES)
    SubjectResponse restore(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        return service.restore(AcademicWeb.parseUuid(publicId, AcademicException.Kind.SUBJECT_NOT_FOUND),
                AcademicWeb.subject(caller));
    }
}
