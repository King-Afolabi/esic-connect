package com.esic.connect.academic.internal;

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
import java.time.LocalDate;

/**
 * Affectation d'un responsable pédagogique à une formation (RG-004,
 * RG-010, RG-011).
 *
 * <p>{@code program} est une relation intra-module. {@code managerUserId}
 * et {@code delegatedById} sont de simples valeurs techniques (clés
 * étrangères SQL vers {@code user_account}) : aucune relation JPA vers
 * {@code identity}, dont la référence passe par le port
 * {@link com.esic.connect.identity.UserDirectory} — même approche que
 * {@code ClassGroup.siteId}.
 *
 * <p>La période est en {@link LocalDate} (jour civil), borne
 * {@code validUntil} inclusive. Rattachements, rôle et {@code validFrom}
 * immuables après création ; seule la clôture ({@link #close}) fait
 * évoluer l'entité. Aucune suppression physique.
 */
@Entity
@Table(name = "pedagogical_assignment")
@EntityListeners(AuditingEntityListener.class)
class PedagogicalAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false, updatable = false)
    private Program program;

    @Column(name = "manager_user_id", nullable = false, updatable = false)
    private Long managerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_role", nullable = false, updatable = false)
    private PedagogicalAssignmentRole assignmentRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PedagogicalAssignmentStatus status;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "reason")
    private String reason;

    @Column(name = "close_reason")
    private String closeReason;

    @Column(name = "delegated_by_id", updatable = false)
    private Long delegatedById;

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

    protected PedagogicalAssignment() {
        // JPA
    }

    PedagogicalAssignment(Program program, Long managerUserId, PedagogicalAssignmentRole assignmentRole,
                          LocalDate validFrom, LocalDate validUntil, String reason, Long delegatedById) {
        this.program = program;
        this.managerUserId = managerUserId;
        this.assignmentRole = assignmentRole;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.reason = reason;
        this.delegatedById = delegatedById;
        this.status = PedagogicalAssignmentStatus.ACTIVE;
    }

    void markCreatedBy(Long actorId) {
        this.createdById = actorId;
        this.updatedById = actorId;
    }

    /**
     * Clôture explicite : libère le créneau (colonne générée
     * {@code active_primary_key}) et fixe la fin de validité au jour
     * effectif de clôture ({@code effectiveDate}), qui doit être ≥
     * {@code validFrom} (contrôlé par le service).
     */
    void close(String closeReason, Long actorId, LocalDate effectiveDate) {
        this.status = PedagogicalAssignmentStatus.CLOSED;
        this.validUntil = effectiveDate;
        this.closeReason = closeReason;
        this.updatedById = actorId;
    }

    boolean isClosed() {
        return status == PedagogicalAssignmentStatus.CLOSED;
    }

    Program getProgram() {
        return program;
    }

    Long getManagerUserId() {
        return managerUserId;
    }

    PedagogicalAssignmentRole getAssignmentRole() {
        return assignmentRole;
    }

    PedagogicalAssignmentStatus getStatus() {
        return status;
    }

    LocalDate getValidFrom() {
        return validFrom;
    }

    LocalDate getValidUntil() {
        return validUntil;
    }

    String getReason() {
        return reason;
    }

    String getCloseReason() {
        return closeReason;
    }

    Long getDelegatedById() {
        return delegatedById;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
