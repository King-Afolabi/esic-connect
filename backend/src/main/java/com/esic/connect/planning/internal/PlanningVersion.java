package com.esic.connect.planning.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Une version d'un planning (V12). Créée à chaque publication (RG-032,
 * EF-PLAN-005/007) ; jamais supprimée. {@code replacedByVersion} pointe la
 * version suivante quand celle-ci passe {@link PlanningVersionStatus#SUPERSEDED}.
 */
@Entity
@Table(name = "planning_version")
@EntityListeners(AuditingEntityListener.class)
class PlanningVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planning_schedule_id", nullable = false, updatable = false)
    private PlanningSchedule schedule;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PlanningVersionStatus status;

    @Column(name = "source_import_job_id")
    private Long sourceImportJobId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_version_id")
    private PlanningVersion replacedByVersion;

    @Column(name = "entry_count", nullable = false)
    private int entryCount;

    @Column(name = "change_summary", length = 500)
    private String changeSummary;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by_id")
    private Long publishedById;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlanningVersion() {
        // JPA
    }

    PlanningVersion(PlanningSchedule schedule, int versionNumber, Long sourceImportJobId) {
        this.schedule = schedule;
        this.versionNumber = versionNumber;
        this.sourceImportJobId = sourceImportJobId;
        this.status = PlanningVersionStatus.DRAFT;
        this.entryCount = 0;
    }

    void publish(int entryCount, String changeSummary, Instant at, Long actorId) {
        this.status = PlanningVersionStatus.PUBLISHED;
        this.entryCount = entryCount;
        this.changeSummary = changeSummary;
        this.publishedAt = at;
        this.publishedById = actorId;
    }

    void supersede(PlanningVersion replacement) {
        this.status = PlanningVersionStatus.SUPERSEDED;
        this.replacedByVersion = replacement;
    }

    PlanningSchedule getSchedule() {
        return schedule;
    }

    int getVersionNumber() {
        return versionNumber;
    }

    PlanningVersionStatus getStatus() {
        return status;
    }

    Long getSourceImportJobId() {
        return sourceImportJobId;
    }

    PlanningVersion getReplacedByVersion() {
        return replacedByVersion;
    }

    int getEntryCount() {
        return entryCount;
    }

    String getChangeSummary() {
        return changeSummary;
    }

    Instant getPublishedAt() {
        return publishedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
