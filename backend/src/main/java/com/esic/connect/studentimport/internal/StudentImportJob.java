package com.esic.connect.studentimport.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * En-tête d'un import CSV d'apprenants (V11, rapport §7.1).
 *
 * <p>Le fichier téléversé n'est pas conservé : seuls le nom assaini
 * ({@code originalFileName}), l'empreinte SHA-256 du contenu
 * ({@code fileSha256}) et la taille ({@code fileSizeBytes}) sont
 * persistés. {@code requestedById} / {@code confirmedById} sont de
 * simples valeurs techniques (FK SQL vers {@code user_account}) : aucune
 * relation JPA vers {@code identity}.
 *
 * <p>Au checkpoint CP1, l'entité n'est écrite que par les tests de
 * contraintes : les compteurs et le passage à {@code APPLIED} seront
 * pilotés par les services de simulation / confirmation des checkpoints
 * suivants.
 */
@Entity
@Table(name = "student_import_job")
@EntityListeners(AuditingEntityListener.class)
class StudentImportJob extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private StudentImportJobStatus status;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    @Column(name = "file_size_bytes", nullable = false)
    private int fileSizeBytes;

    @Column(name = "csv_separator", nullable = false)
    private char csvSeparator;

    @Column(name = "requested_by_id", nullable = false, updatable = false)
    private Long requestedById;

    @Column(name = "scope_program_code", length = 80)
    private String scopeProgramCode;

    @Column(name = "scope_class_code", length = 80)
    private String scopeClassCode;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "valid_rows", nullable = false)
    private int validRows;

    @Column(name = "warning_rows", nullable = false)
    private int warningRows;

    @Column(name = "error_rows", nullable = false)
    private int errorRows;

    @Column(name = "blocking_issue_count", nullable = false)
    private int blockingIssueCount;

    @Column(name = "planned_create_rows", nullable = false)
    private int plannedCreateRows;

    @Column(name = "planned_update_rows", nullable = false)
    private int plannedUpdateRows;

    @Column(name = "planned_transfer_rows", nullable = false)
    private int plannedTransferRows;

    @Column(name = "planned_noop_rows", nullable = false)
    private int plannedNoopRows;

    @Column(name = "applied_created")
    private Integer appliedCreated;

    @Column(name = "applied_updated")
    private Integer appliedUpdated;

    @Column(name = "applied_transferred")
    private Integer appliedTransferred;

    @Column(name = "applied_invited")
    private Integer appliedInvited;

    @Column(name = "applied_ignored")
    private Integer appliedIgnored;

    @Column(name = "confirmable", nullable = false)
    private boolean confirmable;

    @Column(name = "simulated_at", nullable = false)
    private Instant simulatedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "confirmed_by_id")
    private Long confirmedById;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudentImportJob() {
        // JPA
    }

    StudentImportJob(String originalFileName, String fileSha256, int fileSizeBytes, char csvSeparator,
                     Long requestedById, Instant simulatedAt, Instant expiresAt) {
        this.status = StudentImportJobStatus.SIMULATED;
        this.originalFileName = originalFileName;
        this.fileSha256 = fileSha256;
        this.fileSizeBytes = fileSizeBytes;
        this.csvSeparator = csvSeparator;
        this.requestedById = requestedById;
        this.simulatedAt = simulatedAt;
        this.expiresAt = expiresAt;
        this.confirmable = false;
    }

    StudentImportJobStatus getStatus() {
        return status;
    }

    String getOriginalFileName() {
        return originalFileName;
    }

    String getFileSha256() {
        return fileSha256;
    }

    int getFileSizeBytes() {
        return fileSizeBytes;
    }

    char getCsvSeparator() {
        return csvSeparator;
    }

    Long getRequestedById() {
        return requestedById;
    }

    String getScopeProgramCode() {
        return scopeProgramCode;
    }

    String getScopeClassCode() {
        return scopeClassCode;
    }

    int getTotalRows() {
        return totalRows;
    }

    boolean isConfirmable() {
        return confirmable;
    }

    Instant getSimulatedAt() {
        return simulatedAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
