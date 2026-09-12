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
 * Inscription d'un apprenant dans une classe pour une année scolaire
 * (docs/04-modele-donnees.md §13).
 *
 * <p>{@code userId} est une simple valeur technique (clé étrangère SQL
 * vers {@code user_account}), résolue via le port
 * {@link com.esic.connect.identity.UserDirectory} — exactement comme
 * {@code classGroupId} / {@code academicYearId} le sont via
 * {@link com.esic.connect.academic.ClassGroupDirectory}. Une inscription
 * ne porte <strong>aucune</strong> relation vers {@link StudentProfile} :
 * le profil apprenant est une donnée facultative et indépendante du même
 * compte (refonte 2026-09) — son absence ne rend jamais une inscription
 * impossible ni invisible. {@code classGroupId} et {@code academicYearId}
 * sont de simples valeurs techniques (clés étrangères SQL), résolues via
 * le port {@link com.esic.connect.academic.ClassGroupDirectory}.
 * {@code previousEnrollmentId} référence l'inscription clôturée dont
 * celle-ci prend la suite lors d'un changement de classe (docs/04 §13.2).
 *
 * <p>Rattachements, {@code startDate}, {@code enrollmentSource} et
 * {@code previousEnrollmentId} sont immuables ; seule la clôture
 * ({@link #close}) fait évoluer l'entité. Aucune suppression physique
 * (docs/04 §13.4).
 */
@Entity
@Table(name = "enrollment")
@EntityListeners(AuditingEntityListener.class)
class Enrollment extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "class_group_id", nullable = false, updatable = false)
    private Long classGroupId;

    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private Long academicYearId;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EnrollmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_source", nullable = false, updatable = false)
    private EnrollmentSource enrollmentSource;

    @Column(name = "change_reason")
    private String changeReason;

    @Column(name = "previous_enrollment_id", updatable = false)
    private Long previousEnrollmentId;

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

    protected Enrollment() {
        // JPA
    }

    Enrollment(Long userId, Long classGroupId, Long academicYearId, LocalDate startDate,
               EnrollmentSource enrollmentSource, String changeReason, Long previousEnrollmentId) {
        this.userId = userId;
        this.classGroupId = classGroupId;
        this.academicYearId = academicYearId;
        this.startDate = startDate;
        this.enrollmentSource = enrollmentSource;
        this.changeReason = changeReason;
        this.previousEnrollmentId = previousEnrollmentId;
        this.status = EnrollmentStatus.ACTIVE;
    }

    void markCreatedBy(Long actorId) {
        this.createdById = actorId;
        this.updatedById = actorId;
    }

    /**
     * Clôture : libère le créneau d'unicité (colonnes générées) en
     * quittant le statut {@code ACTIVE}, fixe la date de fin (inclusive,
     * ≥ {@code startDate} — contrôlé par le service) et enregistre le
     * motif. Une clôture ne se rejoue pas (contrôle {@link #isActive}).
     */
    void close(EnrollmentStatus newStatus, String reason, LocalDate endDate, Long actorId) {
        this.status = newStatus;
        this.endDate = endDate;
        this.changeReason = reason;
        this.updatedById = actorId;
    }

    boolean isActive() {
        return status == EnrollmentStatus.ACTIVE;
    }

    Long getUserId() {
        return userId;
    }

    Long getClassGroupId() {
        return classGroupId;
    }

    Long getAcademicYearId() {
        return academicYearId;
    }

    LocalDate getStartDate() {
        return startDate;
    }

    LocalDate getEndDate() {
        return endDate;
    }

    EnrollmentStatus getStatus() {
        return status;
    }

    EnrollmentSource getEnrollmentSource() {
        return enrollmentSource;
    }

    String getChangeReason() {
        return changeReason;
    }

    Long getPreviousEnrollmentId() {
        return previousEnrollmentId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
