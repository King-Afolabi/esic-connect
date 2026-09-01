package com.esic.connect.notification.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Écrit <strong>une</strong> ligne {@code notification} dans sa propre
 * transaction {@code REQUIRES_NEW} (G1-D).
 *
 * <p><strong>G1-D.1 — frontière transactionnelle.</strong> Cette méthode
 * ne « rattrape » plus l'exception de persistance : une
 * {@code DataIntegrityViolationException} (course sur {@code dedup_key})
 * ou toute autre erreur est <em>laissée remonter</em>. La transaction
 * {@code REQUIRES_NEW} de cette ligne rollbacke alors <em>proprement</em>
 * et l'exception d'origine est propagée (jamais une
 * {@code UnexpectedRollbackException} : rien n'est avalé ici, donc le
 * proxy ne tente pas de committer une transaction marquée
 * {@code rollback-only}). C'est {@link NotificationWriter}, bean
 * <em>non transactionnel</em>, qui décide destinataire par destinataire
 * s'il faut ignorer (doublon = idempotent) ou seulement journaliser
 * (échec best-effort) — sans jamais interrompre les autres destinataires
 * du même événement.
 */
@Component
class NotificationRowWriter {

    private final NotificationRepository repository;

    NotificationRowWriter(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void write(long recipientUserId, NotificationType type, String title, String body,
              String resourceType, UUID resourcePublicId, String dedupKey, Instant createdAt) {
        repository.saveAndFlush(new Notification(recipientUserId, type, title, body,
                resourceType, resourcePublicId, dedupKey, createdAt));
    }
}
