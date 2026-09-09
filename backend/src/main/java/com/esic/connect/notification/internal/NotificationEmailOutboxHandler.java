package com.esic.connect.notification.internal;

import com.esic.connect.identity.UserDirectory;
import com.esic.connect.outbox.OutboxHandler;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Envoie le courriel d'une notification et en trace la tentative
 * (EF-NOTIF-004 ; docs/02 §11.3, §25.1).
 *
 * <p><strong>Quatre états, jamais confondus.</strong> Ce gestionnaire
 * distingue ce que le produit a fait de ce que le fournisseur a constaté :
 *
 * <ul>
 *   <li><em>accepté par l'adaptateur</em> — la ligne d'outbox existe, rien
 *       n'est encore parti ;</li>
 *   <li><em>remis au serveur de messagerie</em> ({@code SENT_TO_PROVIDER})
 *       — l'adaptateur SMTP a rendu la main sans erreur ;</li>
 *   <li><em>en échec</em> ({@code PROCESSING_FAILED}) — l'envoi a échoué,
 *       la reprise est planifiée ;</li>
 *   <li><em>délivré</em> — <strong>jamais affirmé ici</strong> : seul le
 *       fournisseur peut le constater, et l'état par défaut reste
 *       {@code UNKNOWN}.</li>
 * </ul>
 *
 * <p><strong>Hors transaction</strong> ({@link #transactional()} renvoie
 * {@code false}) : tenir une connexion de base ouverte pendant l'attente
 * d'un serveur SMTP épuiserait le pool au premier incident réseau. Les
 * deux écritures de traçabilité portent leurs propres transactions
 * courtes.
 */
@Component
class NotificationEmailOutboxHandler implements OutboxHandler {

    /** Type de message d'outbox routant vers ce gestionnaire. */
    static final String MESSAGE_TYPE = "NOTIFICATION_EMAIL";
    /** Catégorie de la trace de délivrabilité, pour filtrer le journal. */
    static final String DELIVERY_MESSAGE_TYPE = "NOTIFICATION";

    private final NotificationMailer mailer;
    private final EmailDeliveryService deliveryService;
    private final UserDirectory userDirectory;

    NotificationEmailOutboxHandler(NotificationMailer mailer, EmailDeliveryService deliveryService,
                                   UserDirectory userDirectory) {
        this.mailer = mailer;
        this.deliveryService = deliveryService;
        this.userDirectory = userDirectory;
    }

    @Override
    public String messageType() {
        return MESSAGE_TYPE;
    }

    @Override
    public boolean transactional() {
        return false;
    }

    @Override
    public void handle(String messageKey, Map<String, Object> payload) {
        UUID recipientPublicId = UUID.fromString(String.valueOf(payload.get("recipientPublicId")));
        UserDirectory.UserRef recipient = userDirectory.findByPublicId(recipientPublicId).orElse(null);
        if (recipient == null || recipient.archived()) {
            // Le compte a disparu ou a été archivé entre la mise en file
            // et l'envoi. Rendre la main sans erreur : réessayer ne le
            // ferait pas réapparaître, et encombrerait la file d'échec.
            return;
        }
        Optional<String> address = userDirectory.findEmailForDelivery(recipient.internalId());
        if (address.isEmpty()) {
            return;
        }
        String email = address.get();
        String title = String.valueOf(payload.get("title"));
        String body = String.valueOf(payload.get("body"));

        EmailDelivery delivery = deliveryService.open(email, recipient.internalId(), DELIVERY_MESSAGE_TYPE);
        try {
            mailer.sendNotification(email, title, body);
        } catch (RuntimeException failure) {
            deliveryService.recordFailure(delivery.getId(), failure.getClass().getSimpleName());
            // Relancée : le diffuseur replanifie, et la file d'échec
            // finira par montrer ce qui ne part jamais.
            throw failure;
        }
        // « Remis au serveur de messagerie » — jamais « délivré ».
        deliveryService.recordSent(delivery.getId());
    }
}
