package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttendanceStatus;
import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Entrée <strong>append-only</strong> de l'historique d'une présence
 * (docs/04 §19.4 : « Une correction est ajoutée. Elle ne remplace jamais
 * les anciennes corrections. »).
 *
 * <p>Aucune mise à jour ni suppression : chaque évolution d'une
 * {@code attendance_record} (création manuelle, correction de statut,
 * annulation, cycle de vie d'un justificatif) ajoute une ligne. Ne
 * stocke ni jeton, ni code court, ni JWT, ni empreinte.
 * {@code attendanceRecordId} et {@code actorUserId} sont des valeurs
 * techniques (FK SQL).
 */
@Entity
@Table(name = "attendance_correction")
@EntityListeners(AuditingEntityListener.class)
class AttendanceCorrection extends BaseEntity {

    @Column(name = "attendance_record_id", nullable = false, updatable = false)
    private Long attendanceRecordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false)
    private AttendanceCorrectionAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", updatable = false)
    private AttendanceStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", updatable = false)
    private AttendanceStatus newStatus;

    @Column(name = "previous_late_minutes", updatable = false)
    private Integer previousLateMinutes;

    @Column(name = "new_late_minutes", updatable = false)
    private Integer newLateMinutes;

    @Column(name = "previous_comment", updatable = false)
    private String previousComment;

    @Column(name = "new_comment", updatable = false)
    private String newComment;

    @Column(name = "reason", nullable = false, updatable = false)
    private String reason;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AttendanceCorrection() {
        // JPA
    }

    private AttendanceCorrection(Long attendanceRecordId, AttendanceCorrectionAction action,
                                AttendanceStatus previousStatus, AttendanceStatus newStatus,
                                Integer previousLateMinutes, Integer newLateMinutes,
                                String previousComment, String newComment, String reason,
                                Long actorUserId, Instant occurredAt) {
        this.attendanceRecordId = attendanceRecordId;
        this.action = action;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.previousLateMinutes = previousLateMinutes;
        this.newLateMinutes = newLateMinutes;
        this.previousComment = previousComment;
        this.newComment = newComment;
        this.reason = reason;
        this.actorUserId = actorUserId;
        this.occurredAt = occurredAt;
    }

    static AttendanceCorrection created(Long recordId, AttendanceStatus status, Integer lateMinutes,
                                       String comment, String reason, Long actorId, Instant at) {
        return new AttendanceCorrection(recordId, AttendanceCorrectionAction.CREATED_MANUALLY,
                null, status, null, lateMinutes, null, comment, reason, actorId, at);
    }

    static AttendanceCorrection statusCorrected(Long recordId, AttendanceStatus fromStatus,
                                                AttendanceStatus toStatus, Integer fromLate, Integer toLate,
                                                String fromComment, String toComment, String reason,
                                                Long actorId, Instant at) {
        return new AttendanceCorrection(recordId, AttendanceCorrectionAction.STATUS_CORRECTED,
                fromStatus, toStatus, fromLate, toLate, fromComment, toComment, reason, actorId, at);
    }

    static AttendanceCorrection cancelled(Long recordId, AttendanceStatus fromStatus, String reason,
                                          Long actorId, Instant at) {
        return new AttendanceCorrection(recordId, AttendanceCorrectionAction.CANCELLED,
                fromStatus, AttendanceStatus.CANCELLED, null, null, null, null, reason, actorId, at);
    }

    static AttendanceCorrection justificationEvent(Long recordId, AttendanceCorrectionAction action,
                                                   AttendanceStatus fromStatus, AttendanceStatus toStatus,
                                                   String reason, Long actorId, Instant at) {
        return new AttendanceCorrection(recordId, action, fromStatus, toStatus,
                null, null, null, null, reason, actorId, at);
    }

    Long getAttendanceRecordId() {
        return attendanceRecordId;
    }

    AttendanceCorrectionAction getAction() {
        return action;
    }

    AttendanceStatus getPreviousStatus() {
        return previousStatus;
    }

    AttendanceStatus getNewStatus() {
        return newStatus;
    }

    Integer getPreviousLateMinutes() {
        return previousLateMinutes;
    }

    Integer getNewLateMinutes() {
        return newLateMinutes;
    }

    String getPreviousComment() {
        return previousComment;
    }

    String getNewComment() {
        return newComment;
    }

    String getReason() {
        return reason;
    }

    Long getActorUserId() {
        return actorUserId;
    }

    Instant getOccurredAt() {
        return occurredAt;
    }
}
