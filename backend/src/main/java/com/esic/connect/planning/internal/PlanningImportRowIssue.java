package com.esic.connect.planning.internal;

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
 * Anomalie portée par une ligne de CSV de planning. {@code receivedValue}
 * conserve la valeur reçue TRONQUÉE d'une cellule fautive — jamais reprise
 * dans l'audit. Supprimée en {@code CASCADE} avec la {@link PlanningImportRow}.
 */
@Entity
@Table(name = "planning_import_row_issue")
@EntityListeners(AuditingEntityListener.class)
class PlanningImportRowIssue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planning_import_row_id", nullable = false, updatable = false)
    private PlanningImportRow row;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private PlanningIssueSeverity severity;

    @Column(name = "column_name", length = 64)
    private String columnName;

    @Column(name = "received_value", length = 200)
    private String receivedValue;

    @Column(name = "error_code", nullable = false, length = 80)
    private String errorCode;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PlanningImportRowIssue() {
        // JPA
    }

    PlanningImportRowIssue(PlanningImportRow row, PlanningIssueSeverity severity, String columnName,
                           String receivedValue, String errorCode, String message) {
        this.row = row;
        this.severity = severity;
        this.columnName = columnName;
        this.receivedValue = receivedValue;
        this.errorCode = errorCode;
        this.message = message;
    }

    PlanningImportRow getRow() {
        return row;
    }

    PlanningIssueSeverity getSeverity() {
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

    Instant getCreatedAt() {
        return createdAt;
    }
}
