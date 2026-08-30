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
 * Anomalie portée par une ligne (V11, rapport §7.4).
 *
 * <p>{@code receivedValue} conserve la valeur reçue <strong>tronquée</strong>
 * d'une cellule fautive — jamais reprise dans l'audit (rapport §10).
 * Supprimée en {@code CASCADE} avec le {@link StudentImportRow}.
 */
@Entity
@Table(name = "student_import_row_issue")
@EntityListeners(AuditingEntityListener.class)
class StudentImportRowIssue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_import_row_id", nullable = false, updatable = false)
    private StudentImportRow row;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private StudentImportIssueSeverity severity;

    @Column(name = "column_name", length = 64)
    private String columnName;

    @Column(name = "received_value", length = 200)
    private String receivedValue;

    @Column(name = "error_code", nullable = false, length = 80)
    private String errorCode;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "suggested_value", length = 200)
    private String suggestedValue;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StudentImportRowIssue() {
        // JPA
    }

    StudentImportRowIssue(StudentImportRow row, StudentImportIssueSeverity severity, String errorCode,
                          String message, String columnName, String receivedValue, String suggestedValue) {
        this.row = row;
        this.severity = severity;
        this.errorCode = errorCode;
        this.message = message;
        this.columnName = columnName;
        this.receivedValue = receivedValue;
        this.suggestedValue = suggestedValue;
    }

    StudentImportRow getRow() {
        return row;
    }

    StudentImportIssueSeverity getSeverity() {
        return severity;
    }

    String getColumnName() {
        return columnName;
    }

    String getReceivedValue() {
        return receivedValue;
    }

    String getErrorCode() {
        return errorCode;
    }

    String getMessage() {
        return message;
    }

    String getSuggestedValue() {
        return suggestedValue;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
