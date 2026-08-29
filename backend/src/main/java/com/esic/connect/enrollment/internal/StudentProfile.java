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
 * Profil apprenant (docs/04-modele-donnees.md §11.1).
 *
 * <p>{@code userId} est une simple valeur technique (clé étrangère SQL
 * vers {@code user_account}, unique) : aucune relation JPA vers
 * {@code identity}, dont la référence passe par le port
 * {@link com.esic.connect.identity.UserDirectory} — même approche que
 * {@code ClassGroup.siteId}.
 *
 * <p>{@code userId} et {@code studentNumber} sont immuables après
 * création. Aucune suppression physique : l'entité passe en
 * {@link StudentProfileStatus#ARCHIVED} (hors périmètre de ce lot).
 */
@Entity
@Table(name = "student_profile")
@EntityListeners(AuditingEntityListener.class)
class StudentProfile extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "student_number", nullable = false, updatable = false)
    private String studentNumber;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "work_study", nullable = false)
    private boolean workStudy;

    @Column(name = "company_name")
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StudentProfileStatus status;

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

    protected StudentProfile() {
        // JPA
    }

    StudentProfile(Long userId, String studentNumber, LocalDate birthDate, boolean workStudy, String companyName) {
        this.userId = userId;
        this.studentNumber = studentNumber;
        this.birthDate = birthDate;
        this.workStudy = workStudy;
        this.companyName = companyName;
        this.status = StudentProfileStatus.ACTIVE;
    }

    void markCreatedBy(Long actorId) {
        this.createdById = actorId;
        this.updatedById = actorId;
    }

    boolean isArchived() {
        return status == StudentProfileStatus.ARCHIVED;
    }

    Long getUserId() {
        return userId;
    }

    String getStudentNumber() {
        return studentNumber;
    }

    LocalDate getBirthDate() {
        return birthDate;
    }

    boolean isWorkStudy() {
        return workStudy;
    }

    String getCompanyName() {
        return companyName;
    }

    StudentProfileStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
