package com.esic.connect.outbox.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vues de l'écran d'exploitation de la file (EF-OPS-005 ; docs/02 §25.2 :
 * « destinataire, type, nombre de tentatives, dernière erreur, prochaine
 * tentative, statut, dates de création et de traitement »).
 *
 * <p>Le <strong>payload n'est jamais renvoyé</strong>. Il décrit un effet
 * de bord et peut contenir des identifiants de personnes ; l'exploitant a
 * besoin de savoir <em>quoi</em> a échoué et <em>pourquoi</em>, pas de
 * lire le contenu. Le destinataire, lui, reste visible dans le module qui
 * produit l'effet (journal de délivrabilité, centre de notifications).
 */
final class OutboxResponses {

    private OutboxResponses() {
    }

    record Message(UUID publicId,
                   String messageType,
                   String status,
                   int attempts,
                   String lastError,
                   Instant nextAttemptAt,
                   Instant createdAt,
                   Instant processedAt) {
    }

    record MessagePage(List<Message> content, int page, int size, long totalElements, int totalPages) {
    }

    /**
     * Compteurs d'en-tête de l'écran. {@code dead} est le seul chiffre qui
     * appelle une action humaine : les autres se résorbent seuls.
     */
    record Summary(long pending, long failed, long dead, long sent) {
    }
}
