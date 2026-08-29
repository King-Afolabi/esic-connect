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
 * Administration des niveaux (docs/04 §12.3). Liste et création nichées
 * sous une formation ; opérations unitaires par {@code public_id} du
 * niveau. Mêmes rôles que {@link AcademicYearController}.
 */
@RestController
@RequestMapping("/api/v1")
class ProgramLevelController {

    private final ProgramLevelService service;

    ProgramLevelController(ProgramLevelService service) {
        this.service = service;
    }

    @GetMapping("/programs/{programPublicId}/levels")
    @PreAuthorize(AcademicWeb.READ_ROLES)
    PageResponse<ProgramLevelResponse> list(@PathVariable String programPublicId,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) String sort,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.listForProgram(
                AcademicWeb.parseUuid(programPublicId, AcademicException.Kind.PROGRAM_NOT_FOUND),
                status, q, page, size, sort);
    }

    @PostMapping("/programs/{programPublicId}/levels")
    @PreAuthorize(AcademicWeb.SCOPED_WRITE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    ProgramLevelResponse create(@PathVariable String programPublicId,
                                @Valid @RequestBody ProgramLevelRequests.Create request,
                                @AuthenticationPrincipal Jwt caller) {
        return service.create(AcademicWeb.parseUuid(programPublicId, AcademicException.Kind.PROGRAM_NOT_FOUND),
                request, AcademicWeb.subject(caller));
    }

    @GetMapping("/program-levels/{publicId}")
    @PreAuthorize(AcademicWeb.READ_ROLES)
    ProgramLevelResponse get(@PathVariable String publicId) {
        return service.get(AcademicWeb.parseUuid(publicId, AcademicException.Kind.PROGRAM_LEVEL_NOT_FOUND));
    }

    @PatchMapping("/program-levels/{publicId}")
    @PreAuthorize(AcademicWeb.SCOPED_WRITE_ROLES)
    ProgramLevelResponse update(@PathVariable String publicId,
                                @Valid @RequestBody ProgramLevelRequests.Update request,
                                @AuthenticationPrincipal Jwt caller) {
        return service.update(AcademicWeb.parseUuid(publicId, AcademicException.Kind.PROGRAM_LEVEL_NOT_FOUND),
                request, AcademicWeb.subject(caller));
    }

    @PostMapping("/program-levels/{publicId}/archive")
    @PreAuthorize(AcademicWeb.SCOPED_WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable String publicId,
                 @Valid @RequestBody ArchiveRequest request,
                 @AuthenticationPrincipal Jwt caller) {
        service.archive(AcademicWeb.parseUuid(publicId, AcademicException.Kind.PROGRAM_LEVEL_NOT_FOUND),
                request.reason().trim(), AcademicWeb.subject(caller));
    }

    @PostMapping("/program-levels/{publicId}/restore")
    @PreAuthorize(AcademicWeb.SCOPED_WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void restore(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        service.restore(AcademicWeb.parseUuid(publicId, AcademicException.Kind.PROGRAM_LEVEL_NOT_FOUND),
                AcademicWeb.subject(caller));
    }
}
