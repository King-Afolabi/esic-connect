package com.esic.connect.planning.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.identity.CurrentUserResolver;
import com.esic.connect.planning.internal.PlanningResponses.JobResponse;
import com.esic.connect.planning.internal.PlanningResponses.RowResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Import CSV de planning (EF-PLAN-001/002 ; RG-030/031 ; DEC-G1-B) —
 * <strong>phase de simulation et de consultation</strong>. Le
 * téléversement lance une simulation qui ne persiste que
 * {@code planning_import_*} (invariant T1). La publication (transaction
 * atomique + port {@code coursesession.PlanningSessionWriter}) et le
 * versionnement relèvent d'un checkpoint ultérieur.
 *
 * <p>Routes réservées à
 * {@code ADMIN}/{@code SUPER_ADMIN}/{@code SCHOOL_ADMINISTRATION}/{@code PEDAGOGICAL_MANAGER}
 * ({@code @PreAuthorize}). Un {@code PEDAGOGICAL_MANAGER} est en plus
 * limité à son périmètre pédagogique côté serveur
 * ({@code AcademicScopeDirectory}) et à ses propres jobs.
 */
@RestController
@RequestMapping("/api/v1/planning-imports")
class PlanningImportController {

    private final PlanningSimulationService simulationService;
    private final PlanningQueryService queryService;
    private final CurrentUserResolver currentUserResolver;
    private final AcademicScopeDirectory academicScopeDirectory;

    PlanningImportController(PlanningSimulationService simulationService,
                            PlanningQueryService queryService,
                            CurrentUserResolver currentUserResolver,
                            AcademicScopeDirectory academicScopeDirectory) {
        this.simulationService = simulationService;
        this.queryService = queryService;
        this.currentUserResolver = currentUserResolver;
        this.academicScopeDirectory = academicScopeDirectory;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(PlanningWeb.MANAGE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    JobResponse simulate(@RequestPart("file") MultipartFile file,
                         @RequestParam("classGroupPublicId") String classGroupPublicId,
                         @AuthenticationPrincipal Jwt caller) throws IOException {
        Long requesterInternalId = requesterInternalId(caller);
        UUID classId;
        try {
            classId = UUID.fromString(classGroupPublicId);
        } catch (IllegalArgumentException notAUuid) {
            throw new PlanningException(PlanningException.Kind.TARGET_UNRESOLVED);
        }
        PlanningImportJob job = simulationService.simulate(new PlanningSimulationService.SimulationCommand(
                file.getOriginalFilename(), file.getContentType(), file.getBytes(),
                requesterInternalId, classId));
        return queryService.get(job.getPublicId().toString(), requesterInternalId,
                academicScopeDirectory.hasGlobalScope());
    }

    @GetMapping("/{publicId}")
    @PreAuthorize(PlanningWeb.MANAGE_ROLES)
    JobResponse get(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        return queryService.get(publicId, requesterInternalId(caller),
                academicScopeDirectory.hasGlobalScope());
    }

    @GetMapping("/{publicId}/rows")
    @PreAuthorize(PlanningWeb.MANAGE_ROLES)
    PlanningPageResponse<RowResponse> rows(@PathVariable String publicId,
                                           @RequestParam(required = false) String sort,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size,
                                           @AuthenticationPrincipal Jwt caller) {
        return queryService.rows(publicId, requesterInternalId(caller),
                academicScopeDirectory.hasGlobalScope(), page, size, sort);
    }

    /** Annule une simulation avant publication — {@code 204}. Idempotent. */
    @PostMapping("/{publicId}/cancel")
    @PreAuthorize(PlanningWeb.MANAGE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        queryService.cancel(publicId, requesterInternalId(caller),
                academicScopeDirectory.hasGlobalScope());
    }

    private Long requesterInternalId(Jwt caller) {
        return currentUserResolver.resolveInternalId(PlanningWeb.subject(caller))
                .orElseThrow(() -> new PlanningException(PlanningException.Kind.SCOPE_FORBIDDEN));
    }
}
