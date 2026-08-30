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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Anomalie globale d'un import (en-tête, colonne obligatoire manquante,
 * trop de lignes, encodage...) — V11, rapport §7.2. Supprimée en
 * {@code CASCADE} avec le {@link StudentImportJob}.
 */
@Entity
@Table(name = "student_import_job_issue")
@EntityListeners(AuditingEntityListener.class)
class StudentImportJobIssue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_import_job_id", nullable = false, updatable = false)
    private StudentImportJob job;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private StudentImportIssueSeverity severity;

    @Column(name = "error_code", nullable = false, length = 80)
    private String errorCode;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "column_name", length = 64)
    private String columnName;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StudentImportJobIssue() {
        // JPA
    }

    StudentImportJobIssue(StudentImportJob job, StudentImportIssueSeverity severity, String errorCode,
                          String message, String columnName) {
        this.job = job;
        this.severity = severity;
        this.errorCode = errorCode;
        this.message = message;
        this.columnName = columnName;
    }

    StudentImportJob getJob() {
        return job;
    }

    StudentImportIssueSeverity getSeverity() {
        return severity;
    }

    String getErrorCode() {
        return errorCode;
    }

    String getMessage() {
        return message;
    }

    String getColumnName() {
        return columnName;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
