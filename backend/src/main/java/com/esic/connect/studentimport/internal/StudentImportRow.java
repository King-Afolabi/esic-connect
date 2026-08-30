package com.esic.connect.studentimport.internal;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Une ligne de données du CSV, normalisée (V11, rapport §7.3).
 *
 * <p>Minimisation : seuls les 11 champs métier normalisés sont conservés
 * (strict nécessaire à la revue et à l'application) — jamais un duplicata
 * JSON de la ligne brute. La valeur brute d'une cellule fautive n'est
 * conservée que dans {@link StudentImportRowIssue#getReceivedValue()}.
 * Supprimée en {@code CASCADE} avec le {@link StudentImportJob}.
 */
@Entity
@Table(name = "student_import_row")
@EntityListeners(AuditingEntityListener.class)
class StudentImportRow extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_import_job_id", nullable = false, updatable = false)
    private StudentImportJob job;

    // `row_number` est un mot réservé MySQL 8 (fonction de fenêtrage) : identifiant cité.
    @Column(name = "`row_number`", nullable = false, updatable = false)
    private int rowNumber;

    @Column(name = "input_last_name", length = 120)
    private String inputLastName;

    @Column(name = "input_first_name", length = 120)
    private String inputFirstName;

    @Column(name = "input_email", length = 320)
    private String inputEmail;

    @Column(name = "input_phone", length = 32)
    private String inputPhone;

    @Column(name = "input_formation_code", length = 80)
    private String inputFormationCode;

    @Column(name = "input_class_code", length = 80)
    private String inputClassCode;

    @Column(name = "input_academic_year", length = 40)
    private String inputAcademicYear;

    @Column(name = "input_student_number", length = 60)
    private String inputStudentNumber;

    @Column(name = "input_birth_date")
    private LocalDate inputBirthDate;

    @Column(name = "input_work_study")
    private Boolean inputWorkStudy;

    @Column(name = "input_company_name", length = 191)
    private String inputCompanyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "row_status", nullable = false, length = 12)
    private StudentImportRowStatus rowStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "planned_action", nullable = false, length = 28)
    private StudentImportPlannedAction plannedAction;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "resolved_class_public_id", columnDefinition = "BINARY(16)")
    private UUID resolvedClassPublicId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "resolved_user_public_id", columnDefinition = "BINARY(16)")
    private UUID resolvedUserPublicId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "resolved_enrollment_public_id", columnDefinition = "BINARY(16)")
    private UUID resolvedEnrollmentPublicId;

    @Column(name = "student_number_generated", nullable = false)
    private boolean studentNumberGenerated;

    @Enumerated(EnumType.STRING)
    @Column(name = "applied_outcome", length = 20)
    private StudentImportRowOutcome appliedOutcome;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StudentImportRow() {
        // JPA
    }

    StudentImportRow(StudentImportJob job, int rowNumber, StudentImportRowStatus rowStatus,
                     StudentImportPlannedAction plannedAction) {
        this.job = job;
        this.rowNumber = rowNumber;
        this.rowStatus = rowStatus;
        this.plannedAction = plannedAction;
        this.studentNumberGenerated = false;
    }

    StudentImportJob getJob() {
        return job;
    }

    int getRowNumber() {
        return rowNumber;
    }

    String getInputEmail() {
        return inputEmail;
    }

    void setNormalizedIdentity(String lastName, String firstName, String email, String phone) {
        this.inputLastName = lastName;
        this.inputFirstName = firstName;
        this.inputEmail = email;
        this.inputPhone = phone;
    }

    void setNormalizedTarget(String formationCode, String classCode, String academicYear) {
        this.inputFormationCode = formationCode;
        this.inputClassCode = classCode;
        this.inputAcademicYear = academicYear;
    }

    StudentImportRowStatus getRowStatus() {
        return rowStatus;
    }

    StudentImportPlannedAction getPlannedAction() {
        return plannedAction;
    }

    boolean isStudentNumberGenerated() {
        return studentNumberGenerated;
    }

    StudentImportRowOutcome getAppliedOutcome() {
        return appliedOutcome;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
