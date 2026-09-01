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

/**
 * Pièce jointe d'un justificatif d'absence (V16 ; bloc G1-E ; EF-JUS-001 ;
 * CDC §21.5). <strong>Métadonnées uniquement</strong> — le contenu du
 * fichier n'est jamais en base : il est stocké hors webroot par le port
 * {@link com.esic.connect.attendance.JustificationFileStorage} sous la
 * clé opaque {@code storageKey} (jamais dérivée du nom client).
 *
 * <p>{@code justificationId} et {@code createdById} sont des valeurs
 * techniques (FK SQL). {@code contentType} est <strong>re-dérivé</strong>
 * des magic bytes à la validation, jamais celui déclaré par le client.
 * {@code status} suit la séquence avec compensation de DEC-G1-009.
 */
@Entity
@Table(name = "justification_attachment")
class JustificationAttachment extends BaseEntity {

    @Column(name = "justification_id", nullable = false, updatable = false)
    private Long justificationId;

    @Column(name = "original_file_name", nullable = false, updatable = false, length = 255)
    private String originalFileName;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 180)
    private String storageKey;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sha256", nullable = false, updatable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JustificationAttachmentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by_id", nullable = false, updatable = false)
    private Long createdById;

    @Column(name = "stored_at")
    private Instant storedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected JustificationAttachment() {
        // JPA
    }

    JustificationAttachment(Long justificationId, String originalFileName, String storageKey,
                            String contentType, long sizeBytes, String sha256, Long createdById,
                            Instant createdAt) {
        this.justificationId = justificationId;
        this.originalFileName = originalFileName;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.createdById = createdById;
        this.createdAt = createdAt;
        this.status = JustificationAttachmentStatus.PENDING_STORAGE;
    }

    /** Le fichier a été déplacé avec succès dans sa zone définitive. */
    void markStored(Instant at) {
        this.status = JustificationAttachmentStatus.STORED;
        this.storedAt = at;
    }

    /** Suppression logique (le fichier sera retiré par la réconciliation ou en best effort). */
    void markDeleted(Instant at) {
        this.status = JustificationAttachmentStatus.DELETED;
        this.deletedAt = at;
    }

    boolean isStored() {
        return status == JustificationAttachmentStatus.STORED;
    }

    boolean isPendingStorage() {
        return status == JustificationAttachmentStatus.PENDING_STORAGE;
    }

    Long getJustificationId() {
        return justificationId;
    }

    String getOriginalFileName() {
        return originalFileName;
    }

    String getStorageKey() {
        return storageKey;
    }

    String getContentType() {
        return contentType;
    }

    long getSizeBytes() {
        return sizeBytes;
    }

    String getSha256() {
        return sha256;
    }

    JustificationAttachmentStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Long getCreatedById() {
        return createdById;
    }

    Instant getStoredAt() {
        return storedAt;
    }

    Instant getDeletedAt() {
        return deletedAt;
    }
}
