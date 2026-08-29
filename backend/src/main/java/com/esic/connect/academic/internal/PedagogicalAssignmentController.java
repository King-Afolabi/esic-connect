package com.esic.connect.academic.internal;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Affectations de responsable pédagogique à une formation (RG-004,
 * RG-010, RG-011). Routes minimales — liste, détail, création, clôture —
 * réservées à {@code ADMIN}/{@code SUPER_ADMIN}. Aucune route nichée,
 * aucun {@code PATCH}, aucun {@code DELETE}. Identifiants exclusivement en
 * {@code public_id}.
 */
@RestController
@RequestMapping("/api/v1/pedagogical-assignments")
class PedagogicalAssignmentController {

    private final PedagogicalAssignmentService service;

    PedagogicalAssignmentController(PedagogicalAssignmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(AcademicWeb.ASSIGNMENT_ROLES)
    PageResponse<PedagogicalAssignmentResponse> list(
            @RequestParam(required = false) String program,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate activeOn,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(program, user, type, status, activeOn, page, size, sort);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize(AcademicWeb.ASSIGNMENT_ROLES)
    PedagogicalAssignmentResponse get(@PathVariable String publicId) {
        return service.get(AcademicWeb.parseUuid(publicId,
                AcademicException.Kind.PEDAGOGICAL_ASSIGNMENT_NOT_FOUND));
    }

    @PostMapping
    @PreAuthorize(AcademicWeb.ASSIGNMENT_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    PedagogicalAssignmentResponse create(@Valid @RequestBody PedagogicalAssignmentRequests.Create request,
                                         @AuthenticationPrincipal Jwt caller) {
        return service.create(request, AcademicWeb.subject(caller));
    }

    @PostMapping("/{publicId}/close")
    @PreAuthorize(AcademicWeb.ASSIGNMENT_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void close(@PathVariable String publicId,
               @Valid @RequestBody PedagogicalAssignmentRequests.Close request,
               @AuthenticationPrincipal Jwt caller) {
        service.close(AcademicWeb.parseUuid(publicId, AcademicException.Kind.PEDAGOGICAL_ASSIGNMENT_NOT_FOUND),
                request.reason().trim(), request.effectiveDate(), AcademicWeb.subject(caller));
    }
}
