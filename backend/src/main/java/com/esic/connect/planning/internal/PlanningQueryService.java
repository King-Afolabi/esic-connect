package com.esic.connect.planning.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.planning.internal.PlanningResponses.IssueResponse;
import com.esic.connect.planning.internal.PlanningResponses.JobResponse;
import com.esic.connect.planning.internal.PlanningResponses.RowResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Consultation et annulation d'un job d'import de planning. Décision fine
 * de périmètre : un appelant sans accès global ne voit que ses propres
 * jobs ({@code requested_by_id}).
 */
@Service
class PlanningQueryService {

    private static final Set<String> ROW_SORTABLE = Set.of("rowNumber", "rowStatus", "createdAt");
    private static final Sort ROW_DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "rowNumber");

    private final PlanningImportJobRepository jobRepository;
    private final PlanningImportRowRepository rowRepository;
    private final PlanningImportRowIssueRepository rowIssueRepository;
    private final ClassGroupDirectory classGroupDirectory;

    PlanningQueryService(PlanningImportJobRepository jobRepository,
                         PlanningImportRowRepository rowRepository,
                         PlanningImportRowIssueRepository rowIssueRepository,
                         ClassGroupDirectory classGroupDirectory) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.rowIssueRepository = rowIssueRepository;
        this.classGroupDirectory = classGroupDirectory;
    }

    @Transactional(readOnly = true)
    JobResponse get(String publicId, Long requesterInternalId, boolean globalScope) {
        return toJobResponse(loadOwned(publicId, requesterInternalId, globalScope));
    }

    @Transactional(readOnly = true)
    PlanningPageResponse<RowResponse> rows(String publicId, Long requesterInternalId, boolean globalScope,
                                           int page, int size, String sort) {
        PlanningImportJob job = loadOwned(publicId, requesterInternalId, globalScope);
        Pageable pageable = PlanningQuerySupport.pageable(page, size, sort, ROW_SORTABLE, ROW_DEFAULT_SORT);
        Page<PlanningImportRow> rows = rowRepository.findByJob_Id(job.getId(),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort()));
        Map<Long, List<PlanningImportRowIssue>> issuesByRow = rowIssueRepository
                .findByRow_IdInOrderByIdAsc(rows.getContent().stream().map(PlanningImportRow::getId).toList())
                .stream().collect(Collectors.groupingBy(i -> i.getRow().getId()));
        return PlanningPageResponse.of(rows, row -> toRowResponse(row, issuesByRow.getOrDefault(row.getId(), List.of())));
    }

    @Transactional
    void cancel(String publicId, Long requesterInternalId, boolean globalScope) {
        PlanningImportJob job = loadOwned(publicId, requesterInternalId, globalScope);
        if (job.isPublished()) {
            throw new PlanningException(PlanningException.Kind.INVALID_JOB_STATE);
        }
        if (job.getStatus() == PlanningImportJobStatus.CANCELLED) {
            return; // idempotent
        }
        if (!job.isSimulated()) {
            throw new PlanningException(PlanningException.Kind.INVALID_JOB_STATE);
        }
        job.markCancelled();
        jobRepository.save(job);
        // Les anomalies de ligne partent en CASCADE avec les lignes.
        rowRepository.deleteByJob_Id(job.getId());
    }

    private PlanningImportJob loadOwned(String publicId, Long requesterInternalId, boolean globalScope) {
        UUID id;
        try {
            id = UUID.fromString(publicId);
        } catch (IllegalArgumentException notAUuid) {
            throw new PlanningException(PlanningException.Kind.JOB_NOT_FOUND);
        }
        PlanningImportJob job = jobRepository.findByPublicId(id)
                .orElseThrow(() -> new PlanningException(PlanningException.Kind.JOB_NOT_FOUND));
        if (!globalScope && !job.getRequestedById().equals(requesterInternalId)) {
            throw new PlanningException(PlanningException.Kind.SCOPE_FORBIDDEN);
        }
        return job;
    }

    private JobResponse toJobResponse(PlanningImportJob job) {
        UUID classPublicId = classGroupDirectory.findByInternalId(job.getClassGroupId())
                .map(ClassGroupDirectory.ClassGroupRef::publicId).orElse(null);
        UUID yearPublicId = classGroupDirectory.findByInternalId(job.getClassGroupId())
                .map(ClassGroupDirectory.ClassGroupRef::academicYearPublicId).orElse(null);
        return new JobResponse(
                job.getPublicId(), job.getStatus().name(), classPublicId, yearPublicId,
                job.getOriginalFileName(), job.getFileSizeBytes(), job.getCsvSeparator(),
                job.getTotalRows(), job.getValidRows(), job.getWarningRows(), job.getErrorRows(),
                job.getAddedRows(), job.getModifiedRows(), job.getUnchangedRows(), job.getRemovedEntries(),
                job.isConfirmable(), job.getSimulatedAt(), job.getExpiresAt(), job.getPublishedAt(),
                null, job.getFailureReason(), job.getCreatedAt());
    }

    private static RowResponse toRowResponse(PlanningImportRow row, List<PlanningImportRowIssue> issues) {
        List<IssueResponse> issueDtos = issues.stream()
                .map(i -> new IssueResponse(i.getSeverity().name(), i.getErrorCode(), i.getColumnName(),
                        i.getReceivedValue(), i.getMessage()))
                .toList();
        return new RowResponse(
                row.getPublicId(), row.getRowNumber(), row.getInputSlotKey(), row.getInputSessionDate(),
                row.getInputStartTime(), row.getInputEndTime(), row.getInputTimeZoneId(), row.getInputTitle(),
                row.getInputTeacherPublicId(), row.getInputRoomCode(), row.getRowStatus().name(),
                row.getPlannedAction().name(), row.getResolvedStartsAt(), row.getResolvedEndsAt(), issueDtos);
    }
}
