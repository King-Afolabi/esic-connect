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
 * Administration des promotions (docs/04 §12.4). Rattachements formation
 * et année scolaire fournis dans le corps de création, par
 * {@code public_id}. Mêmes rôles que {@link AcademicYearController}.
 */
@RestController
@RequestMapping("/api/v1/promotions")
class PromotionController {

    private final PromotionService service;

    PromotionController(PromotionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(AcademicWeb.READ_ROLES)
    PageResponse<PromotionResponse> list(@RequestParam(required = false) String program,
                                         @RequestParam(required = false) String academicYear,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) String sort,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.list(program, academicYear, status, q, page, size, sort);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize(AcademicWeb.READ_ROLES)
    PromotionResponse get(@PathVariable String publicId) {
        return service.get(AcademicWeb.parseUuid(publicId, AcademicException.Kind.PROMOTION_NOT_FOUND));
    }

    @PostMapping
    @PreAuthorize(AcademicWeb.SCOPED_WRITE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    PromotionResponse create(@Valid @RequestBody PromotionRequests.Create request,
                             @AuthenticationPrincipal Jwt caller) {
        return service.create(request, AcademicWeb.subject(caller));
    }

    @PatchMapping("/{publicId}")
    @PreAuthorize(AcademicWeb.SCOPED_WRITE_ROLES)
    PromotionResponse update(@PathVariable String publicId,
                             @Valid @RequestBody PromotionRequests.Update request,
                             @AuthenticationPrincipal Jwt caller) {
        return service.update(AcademicWeb.parseUuid(publicId, AcademicException.Kind.PROMOTION_NOT_FOUND),
                request, AcademicWeb.subject(caller));
    }

    @PostMapping("/{publicId}/archive")
    @PreAuthorize(AcademicWeb.SCOPED_WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable String publicId,
                 @Valid @RequestBody ArchiveRequest request,
                 @AuthenticationPrincipal Jwt caller) {
        service.archive(AcademicWeb.parseUuid(publicId, AcademicException.Kind.PROMOTION_NOT_FOUND),
                request.reason().trim(), AcademicWeb.subject(caller));
    }

    @PostMapping("/{publicId}/restore")
    @PreAuthorize(AcademicWeb.SCOPED_WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void restore(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        service.restore(AcademicWeb.parseUuid(publicId, AcademicException.Kind.PROMOTION_NOT_FOUND),
                AcademicWeb.subject(caller));
    }
}
