package com.esic.connect.outbox.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Exploitation de la file d'effets de bord (EF-OPS-005 ; docs/02 §25.2 :
 * « relance manuelle possible depuis l'interface d'administration »).
 *
 * <p>Le rejeu ne produit pas l'effet lui-même : il remet la ligne en
 * attente, et le diffuseur la reprend. C'est ce qui garantit qu'un rejeu
 * manuel suit exactement le même chemin — mêmes contrôles, même
 * idempotence, même comptabilité des tentatives — qu'une reprise
 * automatique.
 */
@Service
class OutboxAdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private final OutboxMessageRepository repository;
    private final OutboxStore store;
    private final OutboxDispatcher dispatcher;

    OutboxAdminService(OutboxMessageRepository repository, OutboxStore store, OutboxDispatcher dispatcher) {
        this.repository = repository;
        this.store = store;
        this.dispatcher = dispatcher;
    }

    @Transactional(readOnly = true)
    OutboxResponses.MessagePage list(String status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        Page<OutboxMessage> found = (status == null || status.isBlank())
                ? repository.findAllByOrderByCreatedAtDesc(pageable)
                : repository.findByStatusOrderByCreatedAtDesc(parseStatus(status), pageable);
        List<OutboxResponses.Message> content = found.getContent().stream().map(OutboxAdminService::toView).toList();
        return new OutboxResponses.MessagePage(content, found.getNumber(), found.getSize(),
                found.getTotalElements(), found.getTotalPages());
    }

    @Transactional(readOnly = true)
    OutboxResponses.Summary summary() {
        return new OutboxResponses.Summary(
                repository.countByStatus(OutboxStatus.PENDING),
                repository.countByStatus(OutboxStatus.FAILED),
                repository.countByStatus(OutboxStatus.DEAD),
                repository.countByStatus(OutboxStatus.SENT));
    }

    /**
     * Remet en attente un message de la file d'échec, puis déclenche un
     * drain immédiat pour que l'exploitant voie le résultat sans attendre
     * le prochain passage planifié.
     *
     * <p>Refusé sur un message déjà traité ou encore en cours de reprise :
     * rejouer un {@code SENT} produirait un second effet de bord — la
     * seule protection restante serait l'idempotence du gestionnaire, et
     * s'appuyer dessus pour une action déclenchée à la main serait une
     * imprudence.
     */
    void replay(String publicId) {
        // La remise en file est portée par OutboxStore, et non par une
        // méthode @Transactional de ce bean : un appel interne ne passe
        // pas par le proxy Spring, l'annotation y serait sans effet et la
        // remise se ferait hors transaction.
        store.requeue(parseUuid(publicId));
        dispatcher.drainQuietly();
    }

    private static OutboxStatus parseStatus(String raw) {
        try {
            return OutboxStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new OutboxException(OutboxException.Kind.INVALID_STATUS);
        }
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException malformed) {
            // Un identifiant mal formé et un identifiant inconnu donnent la
            // même réponse : rien ne doit permettre de sonder l'existence.
            throw new OutboxException(OutboxException.Kind.NOT_FOUND);
        }
    }

    private static OutboxResponses.Message toView(OutboxMessage message) {
        return new OutboxResponses.Message(message.getPublicId(), message.getMessageType(),
                message.getStatus().name(), message.getAttempts(), message.getLastError(),
                message.getNextAttemptAt(), message.getCreatedAt(), message.getProcessedAt());
    }
}
