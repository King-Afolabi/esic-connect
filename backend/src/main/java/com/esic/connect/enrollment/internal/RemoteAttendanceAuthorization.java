package com.esic.connect.enrollment.internal;

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
import java.time.LocalDate;

/**
 * Autorisation de suivi à distance individuel (EF-ENR-004 ; docs/02
 * §15.3) : un apprenant est autorisé à suivre à distance alors que sa
 * classe est en présentiel.
 *
 * <p>La portée « une séance / une période / l'année » du cahier est
 * exprimée par un <strong>intervalle de dates</strong> plutôt que par une
 * énumération. Une autorisation d'une seule séance est un intervalle d'un
 * jour ; le calcul de couverture reste alors unique, là où trois portées
 * distinctes auraient produit trois chemins à maintenir — et à faire
 * diverger.
 *
 * <p>{@code classGroupId} est facultatif : une autorisation peut être
 * générale (l'apprenant a un motif durable) ou restreinte à une classe.
 * Aucune relation JPA vers {@code academic} ni {@code identity} — les
 * colonnes sont des clés étrangères SQL, les références passent par les
 * ports publics.
 */
@Entity
@Table(name = "remote_attendance_authorization")
@EntityListeners(AuditingEntityListener.class)
class RemoteAttendanceAuthorization extends BaseEntity {

    @Column(name = "student_user_id", nullable = false, updatable = false)
    private Long studentUserId;

    @Column(name = "class_group_id", updatable = false)
    private Long classGroupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RemoteAuthorizationStatus status;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "decided_by_id", nullable = false, updatable = false)
    private Long decidedById;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    @Column(name = "revoked_by_id")
    private Long revokedById;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", length = 500)
    private String revocationReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RemoteAttendanceAuthorization() {
        // JPA
    }

    RemoteAttendanceAuthorization(Long studentUserId, Long classGroupId, String reason,
                                  LocalDate validFrom, LocalDate validUntil, Long decidedById,
                                  Instant decidedAt) {
        this.studentUserId = studentUserId;
        this.classGroupId = classGroupId;
        this.reason = reason;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.decidedById = decidedById;
        this.decidedAt = decidedAt;
        this.status = RemoteAuthorizationStatus.ACTIVE;
    }

    /**
     * Révocation : l'autorisation n'est jamais supprimée. Le cahier veut
     * une décision traçable — savoir qu'une autorisation a existé puis a
     * été retirée, et pourquoi, fait partie du dossier de l'apprenant.
     */
    void revoke(String motive, Long actorId, Instant at) {
        this.status = RemoteAuthorizationStatus.REVOKED;
        this.revocationReason = motive;
        this.revokedById = actorId;
        this.revokedAt = at;
    }

    /** Couvre {@code date} : active, et la date tombe dans l'intervalle. */
    boolean covers(LocalDate date) {
        return status == RemoteAuthorizationStatus.ACTIVE
                && !date.isBefore(validFrom)
                && (validUntil == null || !date.isAfter(validUntil));
    }

    boolean isActive() {
        return status == RemoteAuthorizationStatus.ACTIVE;
    }

    Long getStudentUserId() {
        return studentUserId;
    }

    Long getClassGroupId() {
        return classGroupId;
    }

    RemoteAuthorizationStatus getStatus() {
        return status;
    }

    String getReason() {
        return reason;
    }

    LocalDate getValidFrom() {
        return validFrom;
    }

    LocalDate getValidUntil() {
        return validUntil;
    }

    Instant getDecidedAt() {
        return decidedAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }

    String getRevocationReason() {
        return revocationReason;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
