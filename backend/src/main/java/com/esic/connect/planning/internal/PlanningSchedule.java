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
 * Planning d'une classe pour une année scolaire (V12). Un seul par
 * {@code (class_group_id, academic_year_id)}. {@code currentVersionNumber}
 * = numéro de la version {@code PUBLISHED} courante (0 tant qu'aucune
 * publication).
 *
 * <p>{@code classGroupId} / {@code academicYearId} sont des valeurs
 * techniques (clés étrangères SQL) : aucune relation JPA vers
 * {@code academic}, la résolution passe par
 * {@link com.esic.connect.academic.ClassGroupDirectory}.
 */
@Entity
@Table(name = "planning_schedule")
@EntityListeners(AuditingEntityListener.class)
class PlanningSchedule extends BaseEntity {

    @Column(name = "class_group_id", nullable = false, updatable = false)
    private Long classGroupId;

    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private Long academicYearId;

    @Column(name = "current_version_number", nullable = false)
    private int currentVersionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PlanningScheduleStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by_id")
    private Long createdById;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by_id")
    private Long updatedById;

    protected PlanningSchedule() {
        // JPA
    }

    PlanningSchedule(Long classGroupId, Long academicYearId, Long actorId) {
        this.classGroupId = classGroupId;
        this.academicYearId = academicYearId;
        this.currentVersionNumber = 0;
        this.status = PlanningScheduleStatus.DRAFT;
        this.createdById = actorId;
        this.updatedById = actorId;
    }

    void markPublished(int versionNumber, Long actorId) {
        this.currentVersionNumber = versionNumber;
        this.status = PlanningScheduleStatus.ACTIVE;
        this.updatedById = actorId;
    }

    Long getClassGroupId() {
        return classGroupId;
    }

    Long getAcademicYearId() {
        return academicYearId;
    }

    int getCurrentVersionNumber() {
        return currentVersionNumber;
    }

    PlanningScheduleStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
