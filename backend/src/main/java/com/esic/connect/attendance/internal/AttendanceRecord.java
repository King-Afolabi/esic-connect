package com.esic.connect.attendance.internal;

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
 * Présence enregistrée (V9).
 *
 * <p>{@code attendanceCheckpointId}, {@code enrollmentId} et
 * {@code studentUserId} sont des valeurs techniques (clés étrangères SQL)
 * résolues via des ports publics — aucune relation JPA inter-module.
 * L'unicité {@code (attendance_checkpoint_id, enrollment_id)} est
 * garantie par la migration : c'est elle, et non un pré-contrôle
 * applicatif, qui empêche le double émargement concurrent.
 */
@Entity
@Table(name = "attendance_record")
@EntityListeners(AuditingEntityListener.class)
class AttendanceRecord extends BaseEntity {

    @Column(name = "attendance_checkpoint_id", nullable = false)
    private Long attendanceCheckpointId;

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private AttendanceRecordSource source;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AttendanceRecord() {
        // JPA
    }

    AttendanceRecord(Long attendanceCheckpointId, Long enrollmentId, Long studentUserId,
                     Instant recordedAt, AttendanceRecordSource source) {
        this.attendanceCheckpointId = attendanceCheckpointId;
        this.enrollmentId = enrollmentId;
        this.studentUserId = studentUserId;
        this.recordedAt = recordedAt;
        this.source = source;
    }

    Long getEnrollmentId() {
        return enrollmentId;
    }

    Instant getRecordedAt() {
        return recordedAt;
    }

    AttendanceRecordSource getSource() {
        return source;
    }
}
