package com.esic.connect.notification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Écrit <strong>une</strong> ligne {@code notification} dans sa propre
 * transaction {@code REQUIRES_NEW} (G1-D). Une transaction par ligne : un
 * doublon de {@code dedup_key} (course entre deux livraisons du même
 * événement) fait échouer <em>uniquement</em> cette ligne — jamais les
 * autres destinataires du même événement.
 */
@Component
class NotificationRowWriter {

    private static final Logger log = LoggerFactory.getLogger(NotificationRowWriter.class);

    private final NotificationRepository repository;

    NotificationRowWriter(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void write(long recipientUserId, NotificationType type, String title, String body,
              String resourceType, UUID resourcePublicId, String dedupKey, Instant createdAt) {
        try {
            repository.saveAndFlush(new Notification(recipientUserId, type, title, body,
                    resourceType, resourcePublicId, dedupKey, createdAt));
        } catch (DataIntegrityViolationException duplicate) {
            // Déjà notifié (dedup_key unique). Rien à faire : la
            // transaction REQUIRES_NEW de cette ligne rollbacke seule.
            log.debug("Notification deja delivree (dedup) : type={}", type);
        }
    }
}
