package com.esic.connect.attendance.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Trace d'un document officiel produit (V34 ; EF-REP-006, AC-033).
 *
 * <p>Le PDF lui-même n'est pas conservé : il est reproductible depuis les
 * données d'assiduité, et le garder dupliquerait des données personnelles
 * dans un second emplacement à gouverner. Est conservée une
 * <strong>empreinte SHA-256</strong> du document remis, qui suffit à
 * répondre « ce PDF est-il bien celui qui a été émis ? ».
 */
@Entity
@Table(name = "report_document")
class ReportDocument extends BaseEntity {

    @Column(name = "document_id", nullable = false, updatable = false, length = 48)
    private String documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false, length = 32)
    private ReportDocumentType documentType;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "subject_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID subjectPublicId;

    @Column(name = "subject_snapshot", updatable = false)
    private String subjectSnapshot;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "class_group_public_id", updatable = false, columnDefinition = "BINARY(16)")
    private UUID classGroupPublicId;

    @Column(name = "period_start", updatable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", updatable = false)
    private LocalDate periodEnd;

    @Column(name = "issued_by_user_id", updatable = false)
    private Long issuedByUserId;

    @Column(name = "issued_by_snapshot", nullable = false, updatable = false)
    private String issuedBySnapshot;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "content_hash", nullable = false, updatable = false, columnDefinition = "CHAR(64)")
    private String contentHash;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason")
    private String revocationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReportDocument() {
        // JPA
    }

    ReportDocument(String documentId, ReportDocumentType documentType, UUID subjectPublicId,
                   String subjectSnapshot, UUID classGroupPublicId, LocalDate periodStart,
                   LocalDate periodEnd, Long issuedByUserId, String issuedBySnapshot,
                   Instant issuedAt, String contentHash) {
        this.documentId = documentId;
        this.documentType = documentType;
        this.subjectPublicId = subjectPublicId;
        this.subjectSnapshot = subjectSnapshot;
        this.classGroupPublicId = classGroupPublicId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.issuedByUserId = issuedByUserId;
        this.issuedBySnapshot = issuedBySnapshot;
        this.issuedAt = issuedAt;
        this.contentHash = contentHash;
        this.createdAt = issuedAt;
        this.updatedAt = issuedAt;
    }

    String getDocumentId() {
        return documentId;
    }

    ReportDocumentType getDocumentType() {
        return documentType;
    }

    UUID getSubjectPublicId() {
        return subjectPublicId;
    }

    String getSubjectSnapshot() {
        return subjectSnapshot;
    }

    LocalDate getPeriodStart() {
        return periodStart;
    }

    LocalDate getPeriodEnd() {
        return periodEnd;
    }

    String getIssuedBySnapshot() {
        return issuedBySnapshot;
    }

    Instant getIssuedAt() {
        return issuedAt;
    }

    String getContentHash() {
        return contentHash;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }

    String getRevocationReason() {
        return revocationReason;
    }

    void revoke(Instant when, String reason) {
        if (this.revokedAt == null) {
            this.revokedAt = when;
            this.revocationReason = reason;
            this.updatedAt = when;
        }
    }
}
