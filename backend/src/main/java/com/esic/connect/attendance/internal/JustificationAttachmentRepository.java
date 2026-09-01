package com.esic.connect.attendance.internal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface JustificationAttachmentRepository extends JpaRepository<JustificationAttachment, Long> {

    Optional<JustificationAttachment> findByPublicId(UUID publicId);

    /** Pièce active (non {@code DELETED}) d'un justificatif, s'il en existe une. */
    Optional<JustificationAttachment> findByJustificationIdAndStatusNot(Long justificationId,
            JustificationAttachmentStatus status);

    List<JustificationAttachment> findByJustificationIdOrderByCreatedAtDesc(Long justificationId);

    /**
     * Lignes {@code PENDING_STORAGE} plus vieilles que {@code cutoff} :
     * candidates à la réconciliation (crash entre le commit métier et le
     * déplacement du fichier — DEC-G1-009).
     */
    List<JustificationAttachment> findByStatusAndCreatedAtBefore(JustificationAttachmentStatus status,
            Instant cutoff);

    /** Même chose, <strong>borné</strong> (lot de réconciliation), du plus ancien au plus récent. */
    List<JustificationAttachment> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            JustificationAttachmentStatus status, Instant cutoff, Pageable pageable);
}
