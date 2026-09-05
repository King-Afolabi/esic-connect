package com.esic.connect.outbox.internal;

import com.esic.connect.outbox.OutboxHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Diffuseur de la file transactionnelle (docs/02 §25.1, §25.2).
 *
 * <p><strong>Deux déclencheurs, un seul mécanisme.</strong> Le drain
 * immédiat après commit produit l'effet de bord sans latence perceptible ;
 * le passage planifié reprend ce que l'immédiat a manqué — échec
 * temporaire du fournisseur, arrêt de la JVM entre le commit et le drain,
 * ligne écrite par une autre instance. C'est la combinaison des deux qui
 * donne la garantie du cahier (§25.2) : rien n'est perdu, et rien
 * n'attend inutilement.
 *
 * <p>Ce bean n'ouvre <strong>aucune</strong> transaction : il orchestre
 * {@link OutboxStore}, qui les porte toutes. Un gestionnaire s'exécute
 * donc hors de toute transaction du diffuseur, libre d'ouvrir la sienne.
 */
@Component
class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxStore store;
    private final OutboxHandlerInvoker invoker;
    private final OutboxProperties properties;
    private final Map<String, OutboxHandler> handlers;

    OutboxDispatcher(OutboxStore store, OutboxHandlerInvoker invoker,
                     OutboxProperties properties, List<OutboxHandler> handlers) {
        this.store = store;
        this.invoker = invoker;
        this.properties = properties;
        this.handlers = index(handlers);
    }

    private static Map<String, OutboxHandler> index(List<OutboxHandler> handlers) {
        Map<String, OutboxHandler> byType = new HashMap<>();
        for (OutboxHandler handler : handlers) {
            String type = handler.messageType();
            if (type == null || type.isBlank()) {
                throw new IllegalStateException(
                        "OutboxHandler sans messageType : " + handler.getClass().getName());
            }
            OutboxHandler previous = byType.put(type, handler);
            if (previous != null) {
                // Deux gestionnaires du même type : l'un des deux ne
                // s'exécuterait jamais, et lequel dépendrait de l'ordre
                // d'injection. Mieux vaut refuser de démarrer.
                throw new IllegalStateException("Deux OutboxHandler declarent le type " + type
                        + " : " + previous.getClass().getName() + " et " + handler.getClass().getName());
            }
        }
        return Map.copyOf(byType);
    }

    /**
     * Traite au plus {@code batch-size} messages. Ne lève jamais : appelé
     * depuis un {@code afterCommit}, une exception y masquerait le succès
     * d'une opération métier déjà acquise.
     */
    void drainQuietly() {
        try {
            drain();
        } catch (RuntimeException failure) {
            log.warn("Drain d'outbox interrompu : {}", failure.getClass().getSimpleName());
        }
    }

    /** Reprise planifiée : ce que le drain immédiat n'a pas produit. */
    @Scheduled(initialDelayString = "${app.outbox.poll-interval:60000}",
            fixedDelayString = "${app.outbox.poll-interval:60000}")
    void scheduledDrain() {
        int processed = drainQuietlyCounting();
        if (processed > 0) {
            log.info("Reprise d'outbox : {} effet(s) de bord traite(s).", processed);
        }
    }

    private int drainQuietlyCounting() {
        try {
            return drain();
        } catch (RuntimeException failure) {
            log.warn("Reprise d'outbox interrompue : {}", failure.getClass().getSimpleName());
            return 0;
        }
    }

    /** @return le nombre de messages effectivement réclamés et traités */
    int drain() {
        int processed = 0;
        for (int i = 0; i < properties.batchSize(); i++) {
            if (!processNext()) {
                break;
            }
            processed++;
        }
        return processed;
    }

    /** @return {@code true} si un message a été réclamé */
    private boolean processNext() {
        OutboxStore.Claim claim = store.claimNext();
        if (claim == null) {
            return false;
        }
        if (claim.payload() == null) {
            store.recordFailure(claim.id(), "UNREADABLE_PAYLOAD");
            return true;
        }
        OutboxHandler handler = handlers.get(claim.messageType());
        if (handler == null) {
            // Un type sans gestionnaire n'est pas un incident passager :
            // il est enregistré comme échec pour finir en file d'échec,
            // visible, plutôt que d'être ignoré en silence.
            store.recordFailure(claim.id(), "NO_HANDLER:" + claim.messageType());
            return true;
        }
        try {
            if (handler.transactional()) {
                invoker.invoke(handler, claim.dedupKey(), claim.payload());
            } else {
                handler.handle(claim.dedupKey(), claim.payload());
            }
        } catch (RuntimeException failure) {
            // Jamais le message d'exception dans la BASE : il peut porter
            // une valeur métier. La trace technique complète reste
            // disponible dans les journaux applicatifs, dont l'accès et la
            // durée sont bornés (docs/02 §33) — sans elle, un effet de
            // bord en échec serait indiagnosticable.
            log.debug("Echec du gestionnaire d'outbox (type={})", claim.messageType(), failure);
            store.recordFailure(claim.id(), failure.getClass().getSimpleName());
            return true;
        }
        store.recordSuccess(claim.id());
        return true;
    }
}
