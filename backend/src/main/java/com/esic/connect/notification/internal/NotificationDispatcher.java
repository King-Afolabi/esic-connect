package com.esic.connect.notification.internal;

import com.esic.connect.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;

/**
 * Enregistre une demande de notification dans l'outbox, depuis la
 * transaction métier (EF-NOTIF-002 ; RG-096, RG-097).
 *
 * <p>Remplace l'écriture directe « après commit, au mieux » : une panne
 * entre le commit et l'écriture perdait définitivement la notification
 * (dette T-01). L'intention commite désormais avec l'action, et le
 * diffuseur garantit la reprise.
 */
@Component
class NotificationDispatcher {

    private final OutboxPublisher outboxPublisher;

    NotificationDispatcher(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    /**
     * <p>La clé d'occurrence combine le type et l'identifiant d'événement
     * porté par la source — {@code eventId} d'un changement de séance,
     * {@code versionPublicId} d'une publication. Deux livraisons du même
     * événement produisent donc la même clé, et une seule ligne.
     */
    void dispatch(NotificationRequest request) {
        String key = request.type().name() + '|' + request.resourcePublicId() + '|' + request.eventKey();
        outboxPublisher.enqueue(NotificationRequest.MESSAGE_TYPE, key, request.toPayload());
    }
}
