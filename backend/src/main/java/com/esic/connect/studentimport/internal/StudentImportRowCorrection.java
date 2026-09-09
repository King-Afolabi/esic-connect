package com.esic.connect.studentimport.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Correction d'une valeur de ligne avant confirmation (EF-IMP-006 ;
 * docs/02 §13.6 ; migration V20).
 *
 * <p>Append-only : chaque correction est une ligne. Corriger deux fois le
 * même champ produit deux enregistrements, l'un après l'autre — écraser
 * effacerait la trace de ce que l'utilisateur a réellement modifié, ce
 * que le cahier interdit explicitement (« journalisation de la correction
 * avec son auteur »).
 */
@Entity
@Table(name = "student_import_row_correction")
@EntityListeners(AuditingEntityListener.class)
public class StudentImportRowCorrection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_import_row_id", nullable = false, updatable = false)
    private StudentImportRow row;

    @Column(name = "field_name", nullable = false, length = 64, updatable = false)
    private String fieldName;

    @Column(name = "previous_value", length = 320, updatable = false)
    private String previousValue;

    @Column(name = "new_value", length = 320, updatable = false)
    private String newValue;

    @Column(name = "corrected_by_id", nullable = false, updatable = false)
    private Long correctedById;

    @Column(name = "corrected_at", nullable = false, updatable = false)
    private Instant correctedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StudentImportRowCorrection() {
        // JPA
    }

    StudentImportRowCorrection(StudentImportRow row, String fieldName, String previousValue,
                               String newValue, Long correctedById, Instant correctedAt) {
        this.row = row;
        this.fieldName = fieldName;
        this.previousValue = truncate(previousValue);
        this.newValue = truncate(newValue);
        this.correctedById = correctedById;
        this.correctedAt = correctedAt;
    }

    private static String truncate(String value) {
        return value == null ? null : value.substring(0, Math.min(value.length(), 320));
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public Instant getCorrectedAt() {
        return correctedAt;
    }
}
