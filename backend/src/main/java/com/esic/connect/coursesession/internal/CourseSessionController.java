package com.esic.connect.coursesession.internal;

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

import java.time.Instant;
import java.util.List;

/**
 * Séances exceptionnelles et cycle de vie {@code PLANNED → OPEN → CLOSED}.
 * Identifiants exclusivement en {@code public_id}. Le contrôle fin de
 * périmètre est appliqué par {@link CourseSessionService} /
 * {@link CourseSessionAccessGuard}.
 */
@RestController
@RequestMapping("/api/v1/sessions")
class CourseSessionController {

    private final CourseSessionService service;
    private final SubstitutionService substitutionService;
    private final SessionLifecycleExtensionService lifecycleService;

    CourseSessionController(CourseSessionService service, SubstitutionService substitutionService,
                            SessionLifecycleExtensionService lifecycleService) {
        this.lifecycleService = lifecycleService;
        this.service = service;
        this.substitutionService = substitutionService;
    }

    @GetMapping
    @PreAuthorize(CourseSessionWeb.READ_ROLES)
    PageResponse<CourseSessionResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String teacher,
            @RequestParam(required = false) String classGroup,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal Jwt caller) {
        return service.list(status, teacher, classGroup, from, to, page, size, sort,
                CourseSessionWeb.subject(caller));
    }

    @GetMapping("/teachers")
    @PreAuthorize(CourseSessionWeb.CREATE_ROLES)
    List<TeacherOptionResponse> eligibleTeachers() {
        return service.listEligibleTeachers();
    }

    @GetMapping("/{publicId}")
    @PreAuthorize(CourseSessionWeb.READ_ROLES)
    CourseSessionResponse get(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        return service.get(publicId, CourseSessionWeb.subject(caller));
    }

    @PostMapping
    @PreAuthorize(CourseSessionWeb.CREATE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    CourseSessionResponse create(@Valid @RequestBody CourseSessionRequests.Create request,
                                 @AuthenticationPrincipal Jwt caller) {
        return service.create(request, CourseSessionWeb.subject(caller));
    }

    @PostMapping("/{publicId}/open")
    @PreAuthorize(CourseSessionWeb.MANAGE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void open(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        service.open(publicId, CourseSessionWeb.subject(caller));
    }

    @PostMapping("/{publicId}/close")
    @PreAuthorize(CourseSessionWeb.MANAGE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void close(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        service.close(publicId, CourseSessionWeb.subject(caller));
    }

    /**
     * Annule une séance {@code PLANNED} / {@code OPEN} avec un motif
     * obligatoire (G1-C ; EF-SES-004 ; CDC §15.4). {@code 204}.
     * {@code CLOSED} / déjà {@code CANCELLED} → {@code 409}.
     */
    /**
     * Reporte une séance annulée (EF-SES-007 ; docs/02 §14.4).
     *
     * <p>Crée une séance de <strong>remplacement</strong> liée à
     * l'originale, qui reste annulée et consultable. Le cahier interdit un
     * report automatique : la nouvelle date est une décision, prise par
     * le responsable.
     */
    @PostMapping("/{publicId}/postpone")
    // CREATE_ROLES et non MANAGE_ROLES : reporter crée une séance, et le
    // formateur « ne crée pas librement une séance de planning »
    // (docs/02 §5.6). Il demande l'annulation, le responsable décide de
    // la nouvelle date.
    @PreAuthorize(CourseSessionWeb.CREATE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    CourseSessionResponse postpone(@PathVariable String publicId,
                                   @Valid @RequestBody CourseSessionRequests.Postpone request,
                                   @AuthenticationPrincipal Jwt caller) {
        return lifecycleService.postpone(publicId, request, CourseSessionWeb.subject(caller));
    }

    /**
     * Le formateur demande l'annulation de sa séance (EF-SES-008).
     *
     * <p>Il demande, il ne décide pas : la validation revient au
     * responsable pédagogique (RG-024). Ouvert au rôle {@code TEACHER},
     * contrairement à l'annulation elle-même.
     */
    @PostMapping("/{publicId}/cancellation-requests")
    @PreAuthorize("hasAnyRole('TEACHER','PEDAGOGICAL_MANAGER','ADMIN','SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    CancellationRequestResponse requestCancellation(
            @PathVariable String publicId,
            @Valid @RequestBody CourseSessionRequests.RequestCancellation request,
            @AuthenticationPrincipal Jwt caller) {
        return lifecycleService.requestCancellation(publicId, request,
                CourseSessionWeb.subject(caller));
    }

    /** Demandes d'annulation d'une séance, la plus récente en tête. */
    @GetMapping("/{publicId}/cancellation-requests")
    @PreAuthorize("hasAnyRole('TEACHER','PEDAGOGICAL_MANAGER','SCHOOL_ADMINISTRATION','ADMIN','SUPER_ADMIN')")
    java.util.List<CancellationRequestResponse> cancellationRequests(
            @PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        return lifecycleService.listForSession(publicId, CourseSessionWeb.subject(caller));
    }

    /**
     * Décide d'une demande d'annulation (EF-SES-008). Une acceptation
     * annule la séance dans la foulée.
     */
    @PostMapping("/cancellation-requests/{requestId}/decision")
    // CREATE_ROLES : « le formateur ne valide jamais lui-même » (RG-024).
    // MANAGE_ROLES inclut TEACHER — correct pour ouvrir/clore une séance,
    // faux pour décider d'une annulation.
    @PreAuthorize(CourseSessionWeb.CREATE_ROLES)
    CancellationRequestResponse decideCancellation(
            @PathVariable String requestId,
            @Valid @RequestBody CourseSessionRequests.DecideCancellation decision,
            @AuthenticationPrincipal Jwt caller) {
        return lifecycleService.decide(requestId, decision, CourseSessionWeb.subject(caller));
    }

    /** Le demandeur retire sa demande tant qu'elle n'est pas décidée. */
    @PostMapping("/cancellation-requests/{requestId}/withdraw")
    @PreAuthorize("hasAnyRole('TEACHER','PEDAGOGICAL_MANAGER','ADMIN','SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void withdrawCancellation(@PathVariable String requestId, @AuthenticationPrincipal Jwt caller) {
        lifecycleService.withdraw(requestId, CourseSessionWeb.subject(caller));
    }

    @PostMapping("/{publicId}/cancel")
    @PreAuthorize(CourseSessionWeb.MANAGE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable String publicId,
                @Valid @RequestBody CourseSessionRequests.Cancel request,
                @AuthenticationPrincipal Jwt caller) {
        service.cancel(publicId, request.reason(), CourseSessionWeb.subject(caller));
    }

    // ------------------------------------------------------------------
    // Remplacements de formateur (G1-C.2 ; EF-SES-005)
    // ------------------------------------------------------------------

    @GetMapping("/{publicId}/substitutions")
    @PreAuthorize(CourseSessionWeb.READ_ROLES)
    List<SubstitutionResponse> listSubstitutions(@PathVariable String publicId,
                                                 @AuthenticationPrincipal Jwt caller) {
        return substitutionService.list(publicId, CourseSessionWeb.subject(caller));
    }

    /**
     * Affecte un remplaçant sur une séance {@code PLANNED} / {@code OPEN}
     * (G1-C.2). Réservé aux rôles de gestion pédagogique
     * ({@code CREATE_ROLES}, {@code TEACHER} exclu : « ne valide pas
     * lui-même son remplacement », CDC §12.4). {@code 201}.
     */
    @PostMapping("/{publicId}/substitutions")
    @PreAuthorize(CourseSessionWeb.CREATE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    SubstitutionResponse addSubstitution(@PathVariable String publicId,
                                         @Valid @RequestBody CourseSessionRequests.CreateSubstitution request,
                                         @AuthenticationPrincipal Jwt caller) {
        return substitutionService.create(publicId, request, CourseSessionWeb.subject(caller));
    }

    @PostMapping("/{publicId}/substitutions/{substitutionId}/end")
    @PreAuthorize(CourseSessionWeb.CREATE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void endSubstitution(@PathVariable String publicId,
                         @PathVariable String substitutionId,
                         @AuthenticationPrincipal Jwt caller) {
        substitutionService.end(publicId, substitutionId, CourseSessionWeb.subject(caller));
    }
}
