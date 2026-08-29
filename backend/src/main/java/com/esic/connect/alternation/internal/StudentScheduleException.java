package com.esic.connect.alternation.internal;

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
 * Exception individuelle de calendrier rattachée à une inscription
 * (docs/04 §14.3).
 *
 * <p>{@code enrollmentId} est une simple valeur technique (clé étrangère
 * SQL vers {@code enrollment}), résolue via le port
 * {@link com.esic.connect.enrollment.EnrollmentDirectory}. {@code startAt}
 * / {@code endAt} sont des instants UTC ; {@code timeZoneId} est le fuseau
 * IANA de saisie, conservé pour la projection sur un jour civil.
 *
 * <p>Rattachement, type et période sont immuables ; seule l'annulation
 * ({@link #cancel}) fait évoluer l'entité. Aucune suppression physique :
 * l'historique est conservé.
 */
@Entity
@Table(name = "student_schedule_exception")
@EntityListeners(AuditingEntityListener.class)
class StudentScheduleException extends BaseEntity {

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private Long enrollmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false, updatable = false)
    private ScheduleExceptionType exceptionType;

    @Column(name = "start_at", nullable = false, updatable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false, updatable = false)
    private Instant endAt;

    @Column(name = "time_zone_id", nullable = false, updatable = false)
    private String timeZoneId;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ScheduleExceptionStatus status;

    @Column(name = "cancel_reason")
    private String cancelReason;

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

    protected StudentScheduleException() {
        // JPA
    }

    StudentScheduleException(Long enrollmentId, ScheduleExceptionType exceptionType, Instant startAt,
                             Instant endAt, String timeZoneId, String reason) {
        this.enrollmentId = enrollmentId;
        this.exceptionType = exceptionType;
        this.startAt = startAt;
        this.endAt = endAt;
        this.timeZoneId = timeZoneId;
        this.reason = reason;
        this.status = ScheduleExceptionStatus.ACTIVE;
    }

    void markCreatedBy(Long actorId) {
        this.createdById = actorId;
        this.updatedById = actorId;
    }

    void cancel(String reason, Long actorId) {
        this.status = ScheduleExceptionStatus.CANCELLED;
        this.cancelReason = reason;
        this.updatedById = actorId;
    }

    boolean isCancelled() {
        return status == ScheduleExceptionStatus.CANCELLED;
    }

    boolean isActive() {
        return status == ScheduleExceptionStatus.ACTIVE;
    }

    Long getEnrollmentId() {
        return enrollmentId;
    }

    ScheduleExceptionType getExceptionType() {
        return exceptionType;
    }

    Instant getStartAt() {
        return startAt;
    }

    Instant getEndAt() {
        return endAt;
    }

    String getTimeZoneId() {
        return timeZoneId;
    }

    String getReason() {
        return reason;
    }

    ScheduleExceptionStatus getStatus() {
        return status;
    }

    String getCancelReason() {
        return cancelReason;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
