package com.esic.connect.notification.internal;

import com.esic.connect.identity.UserDirectory;
import com.esic.connect.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Écrit la notification d'<strong>un</strong> destinataire et met en file
 * ses canaux externes, dans une transaction qui lui est propre
 * ({@code REQUIRES_NEW}).
 *
 * <p><strong>Pourquoi une transaction par destinataire.</strong> Le cahier
 * exige que « l'échec d'un destinataire n'interrompe jamais les autres »
 * (§21.3). Si les trente apprenants d'une classe partageaient une
 * transaction, une seule ligne fautive les priverait tous de la
 * notification, et la reprise buterait indéfiniment sur la même ligne.
 * Isolés, les vingt-neuf autres sont prévenus ; seul le destinataire
 * fautif est retenté.
 *
 * <p><strong>Pourquoi la ligne et ses envois sont écrits ensemble.</strong>
 * Le centre de notifications, l'intention de courriel et l'intention de
 * poussée d'un même destinataire commitent dans cette transaction. Écrire
 * la ligne dans l'une et les envois dans une autre ouvrirait la porte au
 * cas le plus désagréable : la notification apparaît à l'écran, le
 * courriel n'est jamais parti, et la reprise passe son chemin parce que
 * la ligne existe déjà.
 */
@Component
class NotificationRecipientWriter {

    private final NotificationRepository repository;
    private final NotificationPreferenceService preferences;
    private final PushSubscriptionRepository pushSubscriptions;
    private final OutboxPublisher outboxPublisher;

    NotificationRecipientWriter(NotificationRepository repository,
                                NotificationPreferenceService preferences,
                                PushSubscriptionRepository pushSubscriptions,
                                OutboxPublisher outboxPublisher) {
        this.repository = repository;
        this.preferences = preferences;
        this.pushSubscriptions = pushSubscriptions;
        this.outboxPublisher = outboxPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(NotificationRequest request, UserDirectory.UserRef recipient,
                        String dedupKey, Instant now) {
        repository.saveAndFlush(new Notification(recipient.internalId(), request.type(),
                request.title(), request.body(), request.resourceType(), request.resourcePublicId(),
                dedupKey, now));
        queueExternalChannels(request, recipient, dedupKey);
    }

    private void queueExternalChannels(NotificationRequest request, UserDirectory.UserRef recipient,
                                       String dedupKey) {
        NotificationCategory category = request.type().category();
        if (preferences.accepts(recipient.internalId(), category, NotificationChannel.EMAIL)) {
            outboxPublisher.enqueue(NotificationEmailOutboxHandler.MESSAGE_TYPE,
                    NotificationChannel.EMAIL.name() + '|' + dedupKey,
                    channelPayload(request, recipient));
        }
        // Inutile de mettre une poussée en file pour un compte sans
        // appareil abonné : elle échouerait, serait reprise, puis
        // encombrerait la file d'échec — un bruit permanent pour une
        // situation parfaitement normale.
        if (preferences.accepts(recipient.internalId(), category, NotificationChannel.PUSH)
                && pushSubscriptions.countByUserIdAndRevokedAtIsNull(recipient.internalId()) > 0) {
            outboxPublisher.enqueue(WebPushOutboxHandler.MESSAGE_TYPE,
                    NotificationChannel.PUSH.name() + '|' + dedupKey,
                    channelPayload(request, recipient));
        }
    }

    /**
     * Contenu transmis aux canaux externes. Le destinataire y figure par
     * son identifiant public : son adresse est relue au moment de l'envoi,
     * afin qu'une adresse corrigée entre-temps soit celle utilisée — et
     * pour qu'aucune adresse ne dorme en clair dans la file.
     */
    private static Map<String, Object> channelPayload(NotificationRequest request,
                                                      UserDirectory.UserRef recipient) {
        return Map.of(
                "recipientPublicId", recipient.publicId().toString(),
                "category", request.type().category().name(),
                "type", request.type().name(),
                "title", request.title(),
                "body", request.body());
    }
}
