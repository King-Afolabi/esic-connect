package com.esic.connect.audit.internal;

import com.esic.connect.outbox.OutboxHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Écrit la ligne {@code audit_event} décrite par un message d'outbox
 * (EF-AUD-003).
 *
 * <p><strong>Idempotent</strong>, comme l'exige le contrat de
 * {@link OutboxHandler} : la clé du message est reportée dans
 * {@code audit_event.outbox_key}, sous contrainte d'unicité (V32). Un
 * rejeu — reprise après échec partiel, relance manuelle depuis la file
 * d'échec — retrouve la ligne déjà écrite et rend la main sans erreur,
 * plutôt que de faire compter l'audit en double.
 *
 * <p>Aucun {@code @Transactional} ici : le diffuseur ouvre lui-même une
 * transaction neuve autour des gestionnaires
 * ({@link OutboxHandler#transactional()}). En poser un de propagation
 * ordinaire serait au mieux redondant, au pire trompeur.
 */
@Component
class AuditOutboxHandler implements OutboxHandler {

    private static final Logger log = LoggerFactory.getLogger(AuditOutboxHandler.class);

    private final AuditEventRepository repository;

    AuditOutboxHandler(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public String messageType() {
        return AuditRecorder.MESSAGE_TYPE;
    }

    @Override
    public void handle(String messageKey, Map<String, Object> payload) {
        String outboxKey = messageKey;
        if (outboxKey != null && repository.existsByOutboxKey(outboxKey)) {
            return; // déjà écrite par une tentative précédente
        }
        AuditIntent intent = AuditIntent.fromPayload(payload);
        AuditEvent event = new AuditEvent(intent.occurredAt(), intent.actorUserId(), intent.action(),
                intent.category(), intent.resourceType(), intent.result());
        event.setActorPublicIdSnapshot(intent.actorPublicIdSnapshot());
        event.setActorDisplaySnapshot(intent.actorDisplaySnapshot());
        event.setActorRole(intent.actorRole());
        event.setResourcePublicId(intent.resourcePublicId());
        event.setReason(intent.reason());
        event.setOutboxKey(outboxKey);
        try {
            repository.saveAndFlush(event);
        } catch (DataIntegrityViolationException race) {
            // Course sur `uq_audit_event_outbox_key` : une autre tentative
            // a écrit la trace entre le pré-contrôle et le flush. La trace
            // existe, c'est le seul résultat attendu.
            log.debug("Trace d'audit deja ecrite (course sur outbox_key).");
        }
    }
}
