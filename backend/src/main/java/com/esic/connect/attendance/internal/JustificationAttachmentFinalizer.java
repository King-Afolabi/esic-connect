package com.esic.connect.attendance.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Transitions d'état courtes d'une pièce jointe ({@code PENDING_STORAGE →
 * STORED} ou {@code → DELETED}) — chacune dans sa <strong>propre</strong>
 * transaction {@code REQUIRES_NEW} (bloc G1-E ; DEC-G1-009 étapes 7-8 et
 * compensation).
 *
 * <p>Le verrou optimiste ({@code @Version} de {@link JustificationAttachment})
 * est l'autorité contre la concurrence : deux réconciliateurs qui
 * finalisent la même ligne — l'un gagne, l'autre reçoit une
 * {@code OptimisticLockingFailureException} <em>laissée remonter</em>,
 * que la boucle appelante journalise et ignore (l'autre a fait le
 * travail).
 */
@Component
class JustificationAttachmentFinalizer {

    private final JustificationAttachmentRepository repository;

    JustificationAttachmentFinalizer(JustificationAttachmentRepository repository) {
        this.repository = repository;
    }

    /** {@code PENDING_STORAGE → STORED}. Sans effet si la ligne n'est plus en attente. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markStored(long attachmentId, Instant at) {
        Optional<JustificationAttachment> found = repository.findById(attachmentId);
        if (found.isEmpty() || !found.get().isPendingStorage()) {
            return;
        }
        JustificationAttachment attachment = found.get();
        attachment.markStored(at);
        repository.saveAndFlush(attachment);
    }

    /** {@code * → DELETED} (compensation / fichier absent / incohérence). Idempotent. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markDeleted(long attachmentId, Instant at) {
        Optional<JustificationAttachment> found = repository.findById(attachmentId);
        if (found.isEmpty() || found.get().getStatus() == JustificationAttachmentStatus.DELETED) {
            return;
        }
        JustificationAttachment attachment = found.get();
        attachment.markDeleted(at);
        repository.saveAndFlush(attachment);
    }
}
