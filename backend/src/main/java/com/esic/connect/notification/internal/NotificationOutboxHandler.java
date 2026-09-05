package com.esic.connect.notification.internal;

import com.esic.connect.identity.UserDirectory;
import com.esic.connect.outbox.OutboxHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Produit une notification : résolution de l'audience côté serveur, puis
 * écriture isolée par destinataire (EF-NOTIF-001 à 003).
 *
 * <p><strong>Ce gestionnaire n'ouvre pas de transaction</strong>
 * ({@link #transactional()} renvoie {@code false}) : chaque destinataire
 * est traité dans la sienne par {@link NotificationRecipientWriter}. Une
 * transaction englobante annulerait le travail des destinataires servis
 * dès qu'un seul échoue — exactement ce que §21.3 interdit.
 *
 * <p><strong>Un échec partiel est un échec.</strong> Les destinataires
 * servis le restent ; mais le gestionnaire relance l'exception pour que
 * le message soit repris, et que ceux qui n'ont pas été servis aient une
 * nouvelle chance. La clé d'idempotence évite qu'une reprise ne notifie
 * deux fois ceux qui l'étaient déjà.
 */
@Component
class NotificationOutboxHandler implements OutboxHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxHandler.class);

    private final NotificationAudienceResolver audienceResolver;
    private final NotificationRecipientWriter recipientWriter;
    private final NotificationRepository repository;
    private final UserDirectory userDirectory;
    private final Clock clock;

    NotificationOutboxHandler(NotificationAudienceResolver audienceResolver,
                              NotificationRecipientWriter recipientWriter,
                              NotificationRepository repository,
                              UserDirectory userDirectory,
                              Clock clock) {
        this.audienceResolver = audienceResolver;
        this.recipientWriter = recipientWriter;
        this.repository = repository;
        this.userDirectory = userDirectory;
        this.clock = clock;
    }

    @Override
    public String messageType() {
        return NotificationRequest.MESSAGE_TYPE;
    }

    @Override
    public boolean transactional() {
        return false;
    }

    @Override
    public void handle(String messageKey, Map<String, Object> payload) {
        NotificationRequest request = NotificationRequest.fromPayload(payload);
        Set<UUID> recipients = audienceResolver.resolve(request);
        var now = clock.instant();
        int failures = 0;

        for (UUID recipientPublicId : recipients) {
            UserDirectory.UserRef recipient = userDirectory.findByPublicId(recipientPublicId).orElse(null);
            // Un compte inconnu ou archivé n'est jamais destinataire :
            // écrire dans une boîte que personne ne relève n'informe
            // personne, et le réessayer indéfiniment encore moins.
            if (recipient == null || recipient.archived()) {
                continue;
            }
            String dedupKey = NotificationDedup.key(request.type(), request.resourcePublicId(),
                    recipient.internalId(), request.eventKey());
            if (repository.existsByDedupKey(dedupKey)) {
                continue; // déjà notifié : rejeu du message ou de l'événement source
            }
            try {
                recipientWriter.deliver(request, recipient, dedupKey, now);
            } catch (RuntimeException failure) {
                if (NotificationErrorClassifier.isDedupKeyCollision(failure)) {
                    // Course réellement attribuée à `uq_notification_dedup` :
                    // une autre livraison a inséré la ligne entre le
                    // pré-contrôle et le flush. Succès idempotent.
                    continue;
                }
                failures++;
                log.warn("Echec de notification d'un destinataire : type={}, cause={}",
                        request.type(), NotificationErrorClassifier.rootCauseName(failure));
            }
        }

        if (failures > 0) {
            // Signale l'échec au diffuseur : la reprise servira ceux qui
            // ne l'ont pas été, sans redoubler ceux qui l'ont été.
            throw new NotificationDeliveryIncompleteException(failures);
        }
    }
}
