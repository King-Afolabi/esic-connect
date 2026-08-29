package com.esic.connect.alternation.internal;

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
 * Modèles réutilisables de rythme d'alternance (docs/04 §14.1).
 * Consultation ouverte à
 * {@code ADMIN}/{@code SUPER_ADMIN}/{@code SCHOOL_ADMINISTRATION}/{@code PEDAGOGICAL_MANAGER} ;
 * écriture réservée à
 * {@code ADMIN}/{@code SUPER_ADMIN}/{@code SCHOOL_ADMINISTRATION}.
 * Identifiants exclusivement en {@code public_id}.
 */
@RestController
@RequestMapping("/api/v1/alternation/patterns")
class WorkStudyPatternController {

    private final WorkStudyPatternService service;

    WorkStudyPatternController(WorkStudyPatternService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(AlternationWeb.PATTERN_READ_ROLES)
    PageResponse<WorkStudyPatternResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(status, type, q, page, size, sort);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize(AlternationWeb.PATTERN_READ_ROLES)
    WorkStudyPatternResponse get(@PathVariable String publicId) {
        return service.get(AlternationWeb.parseUuid(publicId, AlternationException.Kind.PATTERN_NOT_FOUND));
    }

    @PostMapping
    @PreAuthorize(AlternationWeb.PATTERN_WRITE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    WorkStudyPatternResponse create(@Valid @RequestBody WorkStudyPatternRequests.Create request,
                                    @AuthenticationPrincipal Jwt caller) {
        return service.create(request, AlternationWeb.subject(caller));
    }

    @PatchMapping("/{publicId}")
    @PreAuthorize(AlternationWeb.PATTERN_WRITE_ROLES)
    WorkStudyPatternResponse update(@PathVariable String publicId,
                                    @Valid @RequestBody WorkStudyPatternRequests.Update request,
                                    @AuthenticationPrincipal Jwt caller) {
        return service.update(AlternationWeb.parseUuid(publicId, AlternationException.Kind.PATTERN_NOT_FOUND),
                request, AlternationWeb.subject(caller));
    }

    @PostMapping("/{publicId}/archive")
    @PreAuthorize(AlternationWeb.PATTERN_WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable String publicId,
                 @Valid @RequestBody WorkStudyPatternRequests.Archive request,
                 @AuthenticationPrincipal Jwt caller) {
        service.archive(AlternationWeb.parseUuid(publicId, AlternationException.Kind.PATTERN_NOT_FOUND),
                request.reason().trim(), AlternationWeb.subject(caller));
    }

    @PostMapping("/{publicId}/restore")
    @PreAuthorize(AlternationWeb.PATTERN_WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void restore(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        service.restore(AlternationWeb.parseUuid(publicId, AlternationException.Kind.PATTERN_NOT_FOUND),
                AlternationWeb.subject(caller));
    }
}
