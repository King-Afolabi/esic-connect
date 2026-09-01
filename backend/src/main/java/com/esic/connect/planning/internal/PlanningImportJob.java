package com.esic.connect.planning.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * En-tête d'un import de planning : téléversement + simulation (V12,
 * DEC-G1-003). Le fichier n'est jamais conservé — nom assaini, empreinte
 * SHA-256 et taille uniquement. {@code classGroupId} / {@code academicYearId}
 * = cible visée (valeurs techniques). {@code planningScheduleId} /
 * {@code publishedVersionId} ne sont renseignés qu'à la publication.
 */
@Entity
@Table(name = "planning_import_job")
@EntityListeners(AuditingEntityListener.class)
class PlanningImportJob extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PlanningImportJobStatus status;

    @Column(name = "class_group_id", nullable = false, updatable = false)
    private Long classGroupId;

    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private Long academicYearId;

    @Column(name = "planning_schedule_id")
    private Long planningScheduleId;

    @Column(name = "published_version_id")
    private Long publishedVersionId;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    @Column(name = "file_size_bytes", nullable = false)
    private int fileSizeBytes;

    @Column(name = "csv_separator", nullable = false)
    private char csvSeparator;

    @Column(name = "requested_by_id", nullable = false, updatable = false)
    private Long requestedById;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "valid_rows", nullable = false)
    private int validRows;

    @Column(name = "warning_rows", nullable = false)
    private int warningRows;

    @Column(name = "error_rows", nullable = false)
    private int errorRows;

    @Column(name = "blocking_issue_count", nullable = false)
    private int blockingIssueCount;

    @Column(name = "added_rows", nullable = false)
    private int addedRows;

    @Column(name = "modified_rows", nullable = false)
    private int modifiedRows;

    @Column(name = "unchanged_rows", nullable = false)
    private int unchangedRows;

    @Column(name = "removed_entries", nullable = false)
    private int removedEntries;

    @Column(name = "confirmable", nullable = false)
    private boolean confirmable;

    @Column(name = "simulated_at", nullable = false)
    private Instant simulatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by_id")
    private Long publishedById;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlanningImportJob() {
        // JPA
    }

    PlanningImportJob(Long classGroupId, Long academicYearId, String originalFileName,
                      String fileSha256, int fileSizeBytes, char csvSeparator, Long requestedById,
                      Instant simulatedAt, Instant expiresAt) {
        this.status = PlanningImportJobStatus.SIMULATED;
        this.classGroupId = classGroupId;
        this.academicYearId = academicYearId;
        this.originalFileName = originalFileName;
        this.fileSha256 = fileSha256;
        this.fileSizeBytes = fileSizeBytes;
        this.csvSeparator = csvSeparator;
        this.requestedById = requestedById;
        this.simulatedAt = simulatedAt;
        this.expiresAt = expiresAt;
    }

    void recordSimulationCounts(int totalRows, int validRows, int warningRows, int errorRows,
                                int blockingIssueCount, int addedRows, int modifiedRows,
                                int unchangedRows, int removedEntries, boolean confirmable) {
        this.totalRows = totalRows;
        this.validRows = validRows;
        this.warningRows = warningRows;
        this.errorRows = errorRows;
        this.blockingIssueCount = blockingIssueCount;
        this.addedRows = addedRows;
        this.modifiedRows = modifiedRows;
        this.unchangedRows = unchangedRows;
        this.removedEntries = removedEntries;
        this.confirmable = confirmable;
    }

    void markPublished(Long planningScheduleId, Long publishedVersionId, Instant at, Long actorId) {
        this.status = PlanningImportJobStatus.PUBLISHED;
        this.planningScheduleId = planningScheduleId;
        this.publishedVersionId = publishedVersionId;
        this.publishedAt = at;
        this.publishedById = actorId;
    }

    void markCancelled() {
        this.status = PlanningImportJobStatus.CANCELLED;
    }

    void markExpired() {
        this.status = PlanningImportJobStatus.EXPIRED;
    }

    void markFailed(String failureReason) {
        this.status = PlanningImportJobStatus.FAILED;
        this.failureReason = failureReason;
    }

    boolean isSimulated() {
        return status == PlanningImportJobStatus.SIMULATED;
    }

    boolean isPublished() {
        return status == PlanningImportJobStatus.PUBLISHED;
    }

    PlanningImportJobStatus getStatus() {
        return status;
    }

    Long getClassGroupId() {
        return classGroupId;
    }

    Long getAcademicYearId() {
        return academicYearId;
    }

    Long getPlanningScheduleId() {
        return planningScheduleId;
    }

    Long getPublishedVersionId() {
        return publishedVersionId;
    }

    String getOriginalFileName() {
        return originalFileName;
    }

    String getFileSha256() {
        return fileSha256;
    }

    int getFileSizeBytes() {
        return fileSizeBytes;
    }

    char getCsvSeparator() {
        return csvSeparator;
    }

    Long getRequestedById() {
        return requestedById;
    }

    int getTotalRows() {
        return totalRows;
    }

    int getValidRows() {
        return validRows;
    }

    int getWarningRows() {
        return warningRows;
    }

    int getErrorRows() {
        return errorRows;
    }

    int getBlockingIssueCount() {
        return blockingIssueCount;
    }

    int getAddedRows() {
        return addedRows;
    }

    int getModifiedRows() {
        return modifiedRows;
    }

    int getUnchangedRows() {
        return unchangedRows;
    }

    int getRemovedEntries() {
        return removedEntries;
    }

    boolean isConfirmable() {
        return confirmable;
    }

    Instant getSimulatedAt() {
        return simulatedAt;
    }

    Instant getPublishedAt() {
        return publishedAt;
    }

    String getFailureReason() {
        return failureReason;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
