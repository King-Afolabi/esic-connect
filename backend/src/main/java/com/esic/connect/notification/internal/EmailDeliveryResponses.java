package com.esic.connect.notification.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Contrats de lecture du suivi de délivrabilité (EF-USER-008).
 *
 * <p>L'adresse n'apparaît que sous forme <strong>masquée</strong> :
 * l'écran sert à repérer une adresse erronée, pas à constituer un
 * annuaire.
 */
public final class EmailDeliveryResponses {

    private EmailDeliveryResponses() {
    }

    /**
     * @param internalStatus ce que NOUS savons de l'envoi
     * @param providerStatus ce que le FOURNISSEUR a constaté ;
     *                       {@code UNKNOWN} tant qu'il ne dit rien — ce
     *                       qui est le cas en développement avec Mailpit
     * @param lastError      catégorie technique du dernier échec, jamais
     *                       le contenu du message
     */
    public record Delivery(
            UUID id,
            String recipientMasked,
            String messageType,
            String internalStatus,
            String providerStatus,
            int attempts,
            Instant lastAttemptAt,
            String lastError,
            Instant createdAt) {

        static Delivery from(EmailDelivery delivery) {
            return new Delivery(
                    delivery.getPublicId(),
                    delivery.getRecipientMasked(),
                    delivery.getMessageType(),
                    delivery.getInternalStatus().name(),
                    delivery.getProviderStatus().name(),
                    delivery.getAttempts(),
                    delivery.getLastAttemptAt(),
                    delivery.getLastError(),
                    delivery.getCreatedAt());
        }
    }

    /** Page de résultats, au format des autres listes du produit. */
    public record Page(List<Delivery> content, int page, int size, long totalElements, int totalPages) {

        static Page of(org.springframework.data.domain.Page<EmailDelivery> source) {
            return new Page(
                    source.getContent().stream().map(Delivery::from).toList(),
                    source.getNumber(),
                    source.getSize(),
                    source.getTotalElements(),
                    source.getTotalPages());
        }
    }
}
