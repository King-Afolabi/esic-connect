package com.esic.connect.outbox.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Frontières transactionnelles du diffuseur.
 *
 * <p><strong>Pourquoi une classe séparée de {@link OutboxDispatcher}.</strong>
 * Les trois étapes — réclamer, exécuter, enregistrer le résultat — doivent
 * tomber dans des transactions distinctes. Si elles vivaient dans le même
 * bean, l'appel d'une méthode {@code @Transactional} depuis une autre
 * méthode du même bean ne passerait pas par le proxy Spring : les
 * annotations seraient sans effet, tout se déroulerait dans une seule
 * transaction — exactement ce que le découpage cherche à éviter — et
 * l'anomalie serait invisible jusqu'au premier échec de gestionnaire.
 */
@Component
class OutboxStore {

    private static final Logger log = LoggerFactory.getLogger(OutboxStore.class);
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final OutboxMessageRepository repository;
    private final ObjectMapper objectMapper;
    private final OutboxProperties properties;
    private final Clock clock;

    OutboxStore(OutboxMessageRepository repository, ObjectMapper objectMapper,
                OutboxProperties properties, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Réclame la prochaine ligne traitable en repoussant sa visibilité.
     * Transaction courte : le verrou de ligne n'est jamais tenu pendant
     * l'exécution d'un gestionnaire.
     *
     * @return l'instantané détaché, ou {@code null} si la file est vide
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Claim claimNext() {
        Instant now = clock.instant();
        List<OutboxMessage> batch = repository.claimBatch(now, PageRequest.of(0, 1));
        if (batch.isEmpty()) {
            return null;
        }
        OutboxMessage message = batch.get(0);
        message.reserveUntil(now.plus(properties.visibility()));
        repository.save(message);
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(message.getPayload(), PAYLOAD_TYPE);
        } catch (Exception unreadable) {
            // Une ligne au payload illisible ne sera jamais traitable :
            // elle est comptée comme un échec plutôt que de bloquer la
            // tête de file à chaque passage.
            payload = null;
        }
        return new Claim(message.getId(), message.getMessageType(), message.getDedupKey(), payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordSuccess(long id) {
        repository.findById(id).ifPresent(message -> {
            message.markSent(clock.instant());
            repository.save(message);
        });
    }

    /** @param cause libellé NON sensible (nom de classe, code) */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordFailure(long id, String cause) {
        Optional<OutboxMessage> found = repository.findById(id);
        if (found.isEmpty()) {
            return;
        }
        OutboxMessage message = found.get();
        Instant now = clock.instant();
        Instant retryAt = now.plus(properties.backoffAfter(message.getAttempts() + 1));
        message.markFailed(now, cause, properties.maxAttempts(), retryAt);
        repository.save(message);
        if (message.getStatus() == OutboxStatus.DEAD) {
            log.error("Effet de bord abandonne apres {} tentatives (file d'echec, rejouable) : type={}, cause={}",
                    message.getAttempts(), message.getMessageType(), cause);
        } else {
            log.warn("Effet de bord en echec, reprise planifiee : type={}, tentative={}, cause={}",
                    message.getMessageType(), message.getAttempts(), cause);
        }
    }

    /**
     * Rejeu manuel depuis la file d'échec (EF-OPS-005).
     *
     * <p>Refusé sur un message qui n'est pas {@link OutboxStatus#DEAD} :
     * rejouer un {@code SENT} produirait un second effet de bord, et la
     * seule protection restante serait l'idempotence du gestionnaire —
     * s'y fier pour une action déclenchée à la main serait imprudent.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void requeue(java.util.UUID publicId) {
        OutboxMessage message = repository.findByPublicId(publicId)
                .orElseThrow(() -> new OutboxException(OutboxException.Kind.NOT_FOUND));
        if (message.getStatus() != OutboxStatus.DEAD) {
            throw new OutboxException(OutboxException.Kind.NOT_REPLAYABLE);
        }
        message.requeue(clock.instant());
        repository.save(message);
    }

    /** Instantané d'une ligne réclamée, détaché de la session JPA. */
    record Claim(long id, String messageType, String dedupKey, Map<String, Object> payload) {
    }
}
