package com.esic.connect.attendance.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Insère la métadonnée d'une pièce jointe au statut
 * {@code PENDING_STORAGE} dans sa <strong>propre</strong> transaction
 * courte {@code REQUIRES_NEW} (bloc G1-E ; DEC-G1-009 étape 3).
 *
 * <p>Ne rattrape <strong>aucune</strong> exception : une
 * {@code DataIntegrityViolationException} sur
 * {@code uq_justification_attachment_active} (course entre deux dépôts
 * concurrents pour le même justificatif) est <em>laissée remonter</em> —
 * la transaction {@code REQUIRES_NEW} rollbacke proprement et
 * l'orchestrateur ({@link JustificationAttachmentStore}, non
 * transactionnel) la retraduit en {@code 409}. Même idiome que
 * {@code enrollment.internal.EnrollmentPersister} /
 * {@code attendance.internal.AttendanceRecordPersister}.
 */
@Component
class JustificationAttachmentPreparer {

    /** Contrainte SQL d'unicité d'une pièce active (V16). */
    static final String ACTIVE_CONSTRAINT = "uq_justification_attachment_active";

    private final JustificationAttachmentRepository repository;

    JustificationAttachmentPreparer(JustificationAttachmentRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    JustificationAttachment insertPending(long justificationId, String safeFileName, String storageKey,
                                         String contentType, long sizeBytes, String sha256,
                                         long createdById, Instant now) {
        return repository.saveAndFlush(new JustificationAttachment(justificationId, safeFileName, storageKey,
                contentType, sizeBytes, sha256, createdById, now));
    }
}
