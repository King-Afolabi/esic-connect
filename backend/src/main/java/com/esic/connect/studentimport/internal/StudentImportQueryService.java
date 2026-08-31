package com.esic.connect.studentimport.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.identity.CurrentUserResolver;
import com.esic.connect.studentimport.internal.StudentImportResponses.AppliedSummary;
import com.esic.connect.studentimport.internal.StudentImportResponses.JobIssueResponse;
import com.esic.connect.studentimport.internal.StudentImportResponses.JobResponse;
import com.esic.connect.studentimport.internal.StudentImportResponses.RowIssueResponse;
import com.esic.connect.studentimport.internal.StudentImportResponses.RowResponse;
import com.esic.connect.studentimport.internal.StudentImportResponses.Summary;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Consultation des imports (rapport §8, §9). La décision fine de
 * périmètre est prise ici, côté serveur : un appelant sans accès global
 * ({@code PEDAGOGICAL_MANAGER}) ne voit et ne manipule que <em>ses</em>
 * jobs. Aucun DTO n'expose d'identifiant SQL interne.
 */
@Service
class StudentImportQueryService {

    private final StudentImportJobRepository jobRepository;
    private final StudentImportJobIssueRepository jobIssueRepository;
    private final StudentImportRowRepository rowRepository;
    private final StudentImportRowIssueRepository rowIssueRepository;
    private final CurrentUserResolver currentUserResolver;
    private final AcademicScopeDirectory academicScopeDirectory;

    StudentImportQueryService(StudentImportJobRepository jobRepository,
                              StudentImportJobIssueRepository jobIssueRepository,
                              StudentImportRowRepository rowRepository,
                              StudentImportRowIssueRepository rowIssueRepository,
                              CurrentUserResolver currentUserResolver,
                              AcademicScopeDirectory academicScopeDirectory) {
        this.jobRepository = jobRepository;
        this.jobIssueRepository = jobIssueRepository;
        this.rowRepository = rowRepository;
        this.rowIssueRepository = rowIssueRepository;
        this.currentUserResolver = currentUserResolver;
        this.academicScopeDirectory = academicScopeDirectory;
    }

    @Transactional(readOnly = true)
    PageResponse<JobResponse> list(String status, int page, int size, String sort, String callerSubject) {
        List<Specification<StudentImportJob>> specs = new ArrayList<>();
        addIfPresent(specs, StudentImportSpecifications.jobStatus(status));
        if (!academicScopeDirectory.hasGlobalScope()) {
            specs.add(StudentImportSpecifications.jobRequestedBy(requireCaller(callerSubject)));
        }
        Page<StudentImportJob> jobs = jobRepository.findAll(Specification.allOf(specs),
                StudentImportQuerySupport.jobs(page, size, sort));
        return PageResponse.of(jobs, this::toJobResponse);
    }

    @Transactional(readOnly = true)
    JobResponse get(String publicId, String callerSubject) {
        return toJobResponse(requireVisibleJob(publicId, callerSubject));
    }

    @Transactional(readOnly = true)
    PageResponse<RowResponse> rows(String publicId, String rowStatus, String severity, String action,
                                   int page, int size, String sort, String callerSubject) {
        StudentImportJob job = requireVisibleJob(publicId, callerSubject);
        List<Specification<StudentImportRow>> specs = new ArrayList<>();
        specs.add(StudentImportSpecifications.rowInJob(job.getId()));
        addIfPresent(specs, StudentImportSpecifications.rowStatus(rowStatus));
        addIfPresent(specs, StudentImportSpecifications.rowPlannedAction(action));
        addIfPresent(specs, StudentImportSpecifications.rowHasIssueOfSeverity(severity));
        Page<StudentImportRow> rows = rowRepository.findAll(Specification.allOf(specs),
                StudentImportQuerySupport.rows(page, size, sort));

        List<Long> rowIds = rows.getContent().stream().map(StudentImportRow::getId).toList();
        Map<Long, List<StudentImportRowIssue>> issuesByRow = rowIds.isEmpty()
                ? Map.of()
                : rowIssueRepository.findByRow_IdInOrderByIdAsc(rowIds).stream()
                        .collect(Collectors.groupingBy(issue -> issue.getRow().getId()));

        return PageResponse.of(rows, row -> toRowResponse(row, issuesByRow.getOrDefault(row.getId(), List.of())));
    }

    // ------------------------------------------------------------------

    StudentImportJob requireVisibleJob(String publicId, String callerSubject) {
        StudentImportJob job = jobRepository
                .findByPublicId(StudentImportWeb.parseUuid(publicId, StudentImportException.Kind.JOB_NOT_FOUND))
                .orElseThrow(() -> new StudentImportException(StudentImportException.Kind.JOB_NOT_FOUND));
        if (!academicScopeDirectory.hasGlobalScope()
                && !job.getRequestedById().equals(requireCaller(callerSubject))) {
            throw new StudentImportException(StudentImportException.Kind.JOB_FORBIDDEN);
        }
        return job;
    }

    private Long requireCaller(String callerSubject) {
        return currentUserResolver.resolveInternalId(callerSubject)
                .orElseThrow(() -> new StudentImportException(StudentImportException.Kind.JOB_FORBIDDEN));
    }

    private static <T> void addIfPresent(List<Specification<T>> specs, Specification<T> spec) {
        if (spec != null) {
            specs.add(spec);
        }
    }

    JobResponse toJobResponse(StudentImportJob job) {
        List<JobIssueResponse> issues = jobIssueRepository.findByJobIdOrderByIdAsc(job.getId()).stream()
                .map(issue -> new JobIssueResponse(issue.getSeverity().name(), issue.getErrorCode(),
                        issue.getMessage(), issue.getColumnName()))
                .toList();
        Summary summary = new Summary(job.getTotalRows(), job.getValidRows(), job.getWarningRows(),
                job.getErrorRows(), job.getBlockingIssueCount(), job.getPlannedCreateRows(),
                job.getPlannedUpdateRows(), job.getPlannedTransferRows(), job.getPlannedNoopRows());
        AppliedSummary applied = job.getStatus() == StudentImportJobStatus.APPLIED
                ? new AppliedSummary(job.getAppliedCreated(), job.getAppliedUpdated(), job.getAppliedTransferred(),
                        job.getAppliedInvited(), job.getAppliedIgnored())
                : null;
        return new JobResponse(
                job.getPublicId(),
                job.getStatus().name(),
                job.getOriginalFileName(),
                job.getFileSha256(),
                job.getFileSizeBytes(),
                String.valueOf(job.getCsvSeparator()),
                job.getScopeProgramCode(),
                job.getScopeClassCode(),
                job.isConfirmable(),
                summary,
                issues,
                job.getSimulatedAt(),
                job.getExpiresAt(),
                job.getConfirmedAt(),
                applied,
                job.getCreatedAt());
    }

    private static RowResponse toRowResponse(StudentImportRow row, List<StudentImportRowIssue> issues) {
        return new RowResponse(
                row.getPublicId(),
                row.getRowNumber(),
                row.getRowStatus().name(),
                row.getPlannedAction().name(),
                row.getInputLastName(),
                row.getInputFirstName(),
                row.getInputEmail(),
                row.getInputPhone(),
                row.getInputFormationCode(),
                row.getInputClassCode(),
                row.getInputAcademicYear(),
                row.getInputStudentNumber(),
                row.getInputBirthDate(),
                row.getInputWorkStudy(),
                row.getInputCompanyName(),
                row.getResolvedClassPublicId(),
                row.getResolvedUserPublicId(),
                row.getResolvedEnrollmentPublicId(),
                row.isStudentNumberGenerated(),
                row.getAppliedOutcome() != null ? row.getAppliedOutcome().name() : null,
                issues.stream()
                        .map(issue -> new RowIssueResponse(issue.getSeverity().name(), issue.getErrorCode(),
                                issue.getMessage(), issue.getColumnName(), issue.getReceivedValue(),
                                issue.getSuggestedValue()))
                        .toList());
    }
}
