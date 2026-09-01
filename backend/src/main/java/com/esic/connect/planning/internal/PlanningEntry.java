package com.esic.connect.planning.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Un créneau d'UNE version de planning (V12).
 *
 * <p><strong>Identité</strong> (DEC-G1-002, précisée à l'audit G1-B.1) :
 * <ul>
 *   <li>{@code publicId} (hérité de {@code BaseEntity}) = identifiant
 *       public de CETTE ligne de version, <em>aléatoire</em> : deux
 *       versions successives d'un même créneau ont deux {@code publicId}
 *       différents ;</li>
 *   <li>{@code slotKey} = libellé fourni dans le CSV, unique
 *       {@code (planning_version_id, slot_key)} ;</li>
 *   <li>{@code slotPublicId} = identité <em>stable</em> du créneau à
 *       travers les versions, déterministe
 *       ({@code UUIDv3(schedule.publicId || '|' || slotKey)}). C'est
 *       cette valeur — et jamais {@code publicId} — qui est transmise au
 *       port {@code coursesession.PlanningSessionWriter} et portée par
 *       {@code course_session.planning_slot_public_id}.</li>
 * </ul>
 *
 * <p>{@code teacherUserId} est une valeur technique INTERNE au module
 * (jamais exposée par le port {@code PlanningSessionWriter}, DEC-G1-001).
 * {@code roomCode} reste un code fonctionnel (pas de FK, RG-035).
 * {@code sessionPublicId} référence la séance {@code course_session}
 * créée / réutilisée à la publication.
 */
@Entity
@Table(name = "planning_entry")
@EntityListeners(AuditingEntityListener.class)
class PlanningEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planning_version_id", nullable = false, updatable = false)
    private PlanningVersion planningVersion;

    @Column(name = "planning_schedule_id", nullable = false, updatable = false)
    private Long planningScheduleId;

    @Column(name = "slot_key", nullable = false, updatable = false, length = 64)
    private String slotKey;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "slot_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID slotPublicId;

    @Column(name = "class_group_id", nullable = false, updatable = false)
    private Long classGroupId;

    @Column(name = "teacher_user_id", nullable = false)
    private Long teacherUserId;

    @Column(name = "room_code", length = 50)
    private String roomCode;

    @Column(name = "title", nullable = false, length = 191)
    private String title;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "time_zone_id", nullable = false, length = 64)
    private String timeZoneId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "session_public_id", columnDefinition = "BINARY(16)")
    private UUID sessionPublicId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PlanningEntry() {
        // JPA
    }

    PlanningEntry(PlanningVersion planningVersion, Long planningScheduleId, String slotKey,
                  UUID slotPublicId, Long classGroupId, Long teacherUserId, String roomCode, String title,
                  Instant startsAt, Instant endsAt, String timeZoneId) {
        this.planningVersion = planningVersion;
        this.planningScheduleId = planningScheduleId;
        this.slotKey = slotKey;
        this.slotPublicId = slotPublicId;
        this.classGroupId = classGroupId;
        this.teacherUserId = teacherUserId;
        this.roomCode = roomCode;
        this.title = title;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.timeZoneId = timeZoneId;
    }

    void linkSession(UUID sessionPublicId) {
        this.sessionPublicId = sessionPublicId;
    }

    PlanningVersion getPlanningVersion() {
        return planningVersion;
    }

    Long getPlanningScheduleId() {
        return planningScheduleId;
    }

    String getSlotKey() {
        return slotKey;
    }

    UUID getSlotPublicId() {
        return slotPublicId;
    }

    Long getClassGroupId() {
        return classGroupId;
    }

    Long getTeacherUserId() {
        return teacherUserId;
    }

    String getRoomCode() {
        return roomCode;
    }

    String getTitle() {
        return title;
    }

    Instant getStartsAt() {
        return startsAt;
    }

    Instant getEndsAt() {
        return endsAt;
    }

    String getTimeZoneId() {
        return timeZoneId;
    }

    UUID getSessionPublicId() {
        return sessionPublicId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
