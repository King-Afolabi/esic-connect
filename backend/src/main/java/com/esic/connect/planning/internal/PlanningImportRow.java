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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Une ligne de données du CSV de planning, normalisée (V12). Colonnes
 * typées explicites, jamais un duplicata JSON de la ligne brute
 * (minimisation). {@code plannedAction} = résultat de la simulation
 * (comparaison avec la version publiée courante — DEC-G1-002/004).
 * Supprimée en {@code CASCADE} avec le {@link PlanningImportJob}.
 */
@Entity
@Table(name = "planning_import_row")
@EntityListeners(AuditingEntityListener.class)
class PlanningImportRow extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planning_import_job_id", nullable = false, updatable = false)
    private PlanningImportJob job;

    // `row_number` est un mot réservé MySQL 8 : identifiant cité.
    @Column(name = "`row_number`", nullable = false, updatable = false)
    private int rowNumber;

    @Column(name = "input_slot_key", length = 64)
    private String inputSlotKey;

    @Column(name = "input_session_date", length = 40)
    private String inputSessionDate;

    @Column(name = "input_start_time", length = 20)
    private String inputStartTime;

    @Column(name = "input_end_time", length = 20)
    private String inputEndTime;

    @Column(name = "input_time_zone_id", length = 64)
    private String inputTimeZoneId;

    @Column(name = "input_title", length = 191)
    private String inputTitle;

    @Column(name = "input_teacher_public_id", length = 64)
    private String inputTeacherPublicId;

    @Column(name = "input_room_code", length = 50)
    private String inputRoomCode;

    @Column(name = "resolved_teacher_user_id")
    private Long resolvedTeacherUserId;

    @Column(name = "resolved_starts_at")
    private Instant resolvedStartsAt;

    @Column(name = "resolved_ends_at")
    private Instant resolvedEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "row_status", nullable = false)
    private PlanningRowStatus rowStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "planned_action", nullable = false)
    private PlannedAction plannedAction;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PlanningImportRow() {
        // JPA
    }

    PlanningImportRow(PlanningImportJob job, int rowNumber) {
        this.job = job;
        this.rowNumber = rowNumber;
        this.rowStatus = PlanningRowStatus.VALID;
        this.plannedAction = PlannedAction.UNCHANGED;
    }

    void setInputs(String slotKey, String sessionDate, String startTime, String endTime,
                   String timeZoneId, String title, String teacherPublicId, String roomCode) {
        this.inputSlotKey = slotKey;
        this.inputSessionDate = sessionDate;
        this.inputStartTime = startTime;
        this.inputEndTime = endTime;
        this.inputTimeZoneId = timeZoneId;
        this.inputTitle = title;
        this.inputTeacherPublicId = teacherPublicId;
        this.inputRoomCode = roomCode;
    }

    void setResolution(Long teacherUserId, Instant startsAt, Instant endsAt) {
        this.resolvedTeacherUserId = teacherUserId;
        this.resolvedStartsAt = startsAt;
        this.resolvedEndsAt = endsAt;
    }

    void setOutcome(PlanningRowStatus rowStatus, PlannedAction plannedAction) {
        this.rowStatus = rowStatus;
        this.plannedAction = plannedAction;
    }

    PlanningImportJob getJob() {
        return job;
    }

    int getRowNumber() {
        return rowNumber;
    }

    String getInputSlotKey() {
        return inputSlotKey;
    }

    String getInputSessionDate() {
        return inputSessionDate;
    }

    String getInputStartTime() {
        return inputStartTime;
    }

    String getInputEndTime() {
        return inputEndTime;
    }

    String getInputTimeZoneId() {
        return inputTimeZoneId;
    }

    String getInputTitle() {
        return inputTitle;
    }

    String getInputTeacherPublicId() {
        return inputTeacherPublicId;
    }

    String getInputRoomCode() {
        return inputRoomCode;
    }

    Long getResolvedTeacherUserId() {
        return resolvedTeacherUserId;
    }

    Instant getResolvedStartsAt() {
        return resolvedStartsAt;
    }

    Instant getResolvedEndsAt() {
        return resolvedEndsAt;
    }

    PlanningRowStatus getRowStatus() {
        return rowStatus;
    }

    PlannedAction getPlannedAction() {
        return plannedAction;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
