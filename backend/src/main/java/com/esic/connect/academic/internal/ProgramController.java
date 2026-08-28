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
 * Administration des formations (docs/04 §12.2). Mêmes rôles que
 * {@link AcademicYearController}. Toutes les routes utilisent
 * exclusivement {@code public_id}.
 */
@RestController
@RequestMapping("/api/v1/programs")
class ProgramController {

    private final ProgramService service;

    ProgramController(ProgramService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(AcademicWeb.READ_ROLES)
    PageResponse<ProgramResponse> list(@RequestParam(required = false) String status,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(required = false) String sort,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return service.list(status, q, page, size, sort);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize(AcademicWeb.READ_ROLES)
    ProgramResponse get(@PathVariable String publicId) {
        return service.get(AcademicWeb.parseUuid(publicId, AcademicException.Kind.PROGRAM_NOT_FOUND));
    }

    @PostMapping
    @PreAuthorize(AcademicWeb.WRITE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    ProgramResponse create(@Valid @RequestBody ProgramRequests.Create request,
                           @AuthenticationPrincipal Jwt caller) {
        return service.create(request, AcademicWeb.subject(caller));
    }

    @PatchMapping("/{publicId}")
    @PreAuthorize(AcademicWeb.WRITE_ROLES)
    ProgramResponse update(@PathVariable String publicId,
                           @Valid @RequestBody ProgramRequests.Update request,
                           @AuthenticationPrincipal Jwt caller) {
        return service.update(AcademicWeb.parseUuid(publicId, AcademicException.Kind.PROGRAM_NOT_FOUND),
                request, AcademicWeb.subject(caller));
    }

    @PostMapping("/{publicId}/archive")
    @PreAuthorize(AcademicWeb.WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable String publicId,
                 @Valid @RequestBody ArchiveRequest request,
                 @AuthenticationPrincipal Jwt caller) {
        service.archive(AcademicWeb.parseUuid(publicId, AcademicException.Kind.PROGRAM_NOT_FOUND),
                request.reason().trim(), AcademicWeb.subject(caller));
    }

    @PostMapping("/{publicId}/restore")
    @PreAuthorize(AcademicWeb.WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void restore(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        service.restore(AcademicWeb.parseUuid(publicId, AcademicException.Kind.PROGRAM_NOT_FOUND),
                AcademicWeb.subject(caller));
    }
}
