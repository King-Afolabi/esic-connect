package com.esic.connect.outbox.internal;

import com.esic.connect.outbox.OutboxPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.Map;

/**
 * Implémentation du port {@link OutboxPublisher} (docs/02 §25.1).
 *
 * <p><strong>{@code Propagation.SUPPORTS} et non {@code REQUIRED}.</strong>
 * C'est le point central du mécanisme : l'écriture doit rejoindre la
 * transaction métier en cours pour commiter — ou disparaître — avec elle
 * (RG-097, AC-027). {@code REQUIRED} aurait le même effet <em>quand</em>
 * une transaction existe, mais en ouvrirait une nouvelle sinon ;
 * {@code SUPPORTS} rend visible que ce bean ne définit aucune frontière
 * transactionnelle : il écrit là où on l'appelle. Un appel hors
 * transaction reste possible (l'INSERT est alors auto-commit), au prix de
 * la garantie d'atomicité — c'est à l'appelant de savoir ce qu'il fait.
 *
 * <p><strong>Drain après commit.</strong> À la première écriture d'une
 * transaction, une synchronisation est enregistrée : dès le commit, le
 * diffuseur est appelé sur le même fil. L'effet de bord est donc produit
 * immédiatement — l'utilisateur ne voit aucune latence supplémentaire —
 * tout en restant repris par le passage planifié si ce drain immédiat
 * échoue ou n'a jamais lieu (arrêt de la JVM entre le commit et le
 * drain).
 */
@Component
class DefaultOutboxPublisher implements OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(DefaultOutboxPublisher.class);

    private final OutboxMessageRepository repository;
    private final ObjectMapper objectMapper;
    /**
     * <strong>Résolution différée, et c'est nécessaire.</strong> Le
     * diffuseur dépend de tous les {@link com.esic.connect.outbox.OutboxHandler},
     * et un gestionnaire peut légitimement enregistrer à son tour un
     * message — c'est ainsi qu'une notification met en file son courriel
     * et sa poussée. Une injection directe formerait donc un cycle
     * publisher → dispatcher → handler → publisher, et le contexte
     * refuserait de démarrer. Le diffuseur n'est requis qu'au moment de
     * drainer, jamais à la construction : le résoudre à ce moment-là
     * supprime le cycle sans rien masquer.
     */
    private final ObjectProvider<OutboxDispatcher> dispatcher;
    private final OutboxProperties properties;
    private final Clock clock;

    DefaultOutboxPublisher(OutboxMessageRepository repository, ObjectMapper objectMapper,
                           ObjectProvider<OutboxDispatcher> dispatcher, OutboxProperties properties,
                           Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public boolean enqueue(String messageType, String dedupKey, Map<String, Object> payload) {
        if (messageType == null || messageType.isBlank()) {
            throw new IllegalArgumentException("messageType obligatoire");
        }
        if (dedupKey == null || dedupKey.isBlank()) {
            throw new IllegalArgumentException("dedupKey obligatoire");
        }
        String hashed = OutboxDedup.key(messageType, dedupKey);
        if (repository.existsByDedupKey(hashed)) {
            return false; // rejeu de l'événement source : déjà enregistré
        }
        String json = serialize(payload);
        try {
            repository.save(new OutboxMessage(messageType, hashed, json, clock.instant()));
        } catch (DataIntegrityViolationException race) {
            // Course sur `uq_outbox_message_dedup` entre le pré-contrôle et
            // l'INSERT : l'intention est enregistrée, c'est ce qui compte.
            log.debug("Message d'outbox deja enregistre (course) : type={}", messageType);
            return false;
        }
        scheduleDrain();
        return true;
    }

    /**
     * Enregistre — une seule fois par transaction — la demande de drain.
     * En l'absence de transaction, le drain a lieu tout de suite : la
     * ligne est déjà committée.
     *
     * <p><strong>{@code afterCompletion} et non {@code afterCommit}.</strong>
     * Spring parcourt une copie de la liste des synchronisations pour
     * déclencher {@code afterCommit} : une synchronisation enregistrée
     * <em>pendant</em> cette phase — cas d'un module qui publie lui-même
     * son événement après commit — ne verrait jamais son
     * {@code afterCommit} appelé, et le drain n'aurait pas lieu. La ligne
     * ne serait alors traitée qu'à la reprise planifiée, une minute plus
     * tard : correct, mais inutilement tardif, et difficile à diagnostiquer.
     * {@code afterCompletion} est déclenché sur la liste rafraîchie et
     * couvre les deux cas.
     */
    private void scheduleDrain() {
        if (!properties.dispatchAfterCommit()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatcher.getObject().drainQuietly();
            return;
        }
        if (Boolean.TRUE.equals(TransactionSynchronizationManager.getResource(DRAIN_MARKER))) {
            return;
        }
        TransactionSynchronizationManager.bindResource(DRAIN_MARKER, Boolean.TRUE);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(DRAIN_MARKER);
                if (status == STATUS_COMMITTED) {
                    dispatcher.getObject().drainQuietly();
                }
                // Sur rollback, la ligne d'outbox a disparu avec la
                // transaction : il n'y a rien à drainer (AC-027).
            }
        });
    }

    /** Clé de ressource marquant qu'un drain est déjà planifié pour la transaction courante. */
    private static final Object DRAIN_MARKER = new Object();

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException ex) {
            // Un payload non sérialisable est un défaut de programmation,
            // pas un incident d'exploitation : il doit casser tôt et fort.
            throw new IllegalArgumentException("Payload d'outbox non serialisable", ex);
        }
    }
}
