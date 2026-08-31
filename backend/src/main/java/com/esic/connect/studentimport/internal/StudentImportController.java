package com.esic.connect.studentimport.internal;

import com.esic.connect.identity.CurrentUserResolver;
import com.esic.connect.studentimport.internal.StudentImportResponses.JobResponse;
import com.esic.connect.studentimport.internal.StudentImportResponses.RowResponse;
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

/**
 * Import CSV contrôlé des apprenants (rapport §8 ; EF-IMP-001 ; US-050) —
 * <strong>phase de simulation et de consultation uniquement</strong>. Le
 * téléversement lance une simulation qui ne persiste que
 * {@code student_import_*} (invariant T1). La confirmation et l'annulation
 * relèvent d'un checkpoint ultérieur.
 *
 * <p>Routes réservées à
 * {@code ADMIN}/{@code SUPER_ADMIN}/{@code SCHOOL_ADMINISTRATION}/{@code PEDAGOGICAL_MANAGER}
 * ({@code @PreAuthorize}). La décision fine de périmètre est prise dans le
 * service ({@code PEDAGOGICAL_MANAGER} = ses propres jobs). DTO sans
 * identifiant SQL, sans jeton.
 */
@RestController
@RequestMapping("/api/v1/student-imports")
class StudentImportController {

    private final StudentImportSimulationService simulationService;
    private final StudentImportQueryService queryService;
    private final CurrentUserResolver currentUserResolver;

    StudentImportController(StudentImportSimulationService simulationService,
                            StudentImportQueryService queryService,
                            CurrentUserResolver currentUserResolver) {
        this.simulationService = simulationService;
        this.queryService = queryService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(StudentImportWeb.MANAGE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    JobResponse simulate(@RequestPart("file") MultipartFile file,
                         @RequestParam(required = false) String programCode,
                         @RequestParam(required = false) String classCode,
                         @AuthenticationPrincipal Jwt caller) throws IOException {
        String subject = StudentImportWeb.subject(caller);
        Long requesterInternalId = currentUserResolver.resolveInternalId(subject)
                .orElseThrow(() -> new StudentImportException(StudentImportException.Kind.JOB_FORBIDDEN));
        StudentImportJob job = simulationService.simulate(new StudentImportSimulationService.SimulationCommand(
                file.getOriginalFilename(), file.getContentType(), file.getBytes(), requesterInternalId,
                programCode, classCode));
        return queryService.get(job.getPublicId().toString(), subject);
    }

    @GetMapping
    @PreAuthorize(StudentImportWeb.MANAGE_ROLES)
    PageResponse<JobResponse> list(@RequestParam(required = false) String status,
                                   @RequestParam(required = false) String sort,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size,
                                   @AuthenticationPrincipal Jwt caller) {
        return queryService.list(status, page, size, sort, StudentImportWeb.subject(caller));
    }

    @GetMapping("/{publicId}")
    @PreAuthorize(StudentImportWeb.MANAGE_ROLES)
    JobResponse get(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        return queryService.get(publicId, StudentImportWeb.subject(caller));
    }

    @GetMapping("/{publicId}/rows")
    @PreAuthorize(StudentImportWeb.MANAGE_ROLES)
    PageResponse<RowResponse> rows(@PathVariable String publicId,
                                   @RequestParam(required = false) String rowStatus,
                                   @RequestParam(required = false) String severity,
                                   @RequestParam(required = false) String action,
                                   @RequestParam(required = false) String sort,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size,
                                   @AuthenticationPrincipal Jwt caller) {
        return queryService.rows(publicId, rowStatus, severity, action, page, size, sort,
                StudentImportWeb.subject(caller));
    }
}
