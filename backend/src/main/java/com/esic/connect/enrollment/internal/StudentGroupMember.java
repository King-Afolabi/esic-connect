package com.esic.connect.enrollment.internal;

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
 * Appartenance d'une inscription à un groupe temporaire (migration V19).
 *
 * <p>La cible est l'<strong>inscription</strong>, non le profil
 * apprenant : un apprenant qui change de classe en cours d'année garde
 * ainsi la trace de son appartenance au groupe pour la période concernée,
 * sans qu'aucun historique ne soit écrasé (RG-006, RG-033).
 *
 * <p>Un retrait est <em>logique</em> : la ligne passe en {@code REMOVED},
 * elle n'est jamais supprimée.
 */
@Entity
@Table(name = "student_group_member")
@EntityListeners(AuditingEntityListener.class)
public class StudentGroupMember extends BaseEntity {

    @Column(name = "student_group_id", nullable = false)
    private Long studentGroupId;

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StudentGroupMemberStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by_id")
    private Long createdById;

    protected StudentGroupMember() {
        // JPA
    }

    public StudentGroupMember(Long studentGroupId, Long enrollmentId, Instant joinedAt, Long actorId) {
        this.studentGroupId = studentGroupId;
        this.enrollmentId = enrollmentId;
        this.status = StudentGroupMemberStatus.ACTIVE;
        this.joinedAt = joinedAt;
        this.createdById = actorId;
    }

    public void remove(Instant at) {
        this.status = StudentGroupMemberStatus.REMOVED;
        this.leftAt = at;
    }

    public Long getStudentGroupId() {
        return studentGroupId;
    }

    public Long getEnrollmentId() {
        return enrollmentId;
    }

    public StudentGroupMemberStatus getStatus() {
        return status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
