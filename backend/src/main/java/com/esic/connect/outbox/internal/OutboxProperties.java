package com.esic.connect.outbox.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Réglages du diffuseur (docs/02 §25.2 : « plusieurs tentatives, attente
 * croissante, passage en file d'échec après épuisement »).
 *
 * <p>Toute valeur incohérente fait <strong>échouer le démarrage</strong>
 * plutôt que de dégrader silencieusement la garantie de reprise : un
 * {@code max-attempts} à zéro enverrait chaque effet de bord directement
 * en file d'échec, et personne ne s'en apercevrait avant de chercher une
 * notification qui n'est jamais arrivée.
 *
 * @param maxAttempts   nombre de tentatives avant la file d'échec (> 0)
 * @param batchSize     lignes réclamées par passage (> 0)
 * @param retryInitial  attente avant la première reprise (> 0)
 * @param retryMax      plafond de l'attente croissante (>= retryInitial)
 * @param pollInterval  période du passage de reprise, en millisecondes (> 0)
 * @param visibility    durée pendant laquelle une ligne réclamée reste
 *                      invisible des autres diffuseurs (> 0)
 * @param dispatchAfterCommit draine immédiatement après le commit métier
 */
@ConfigurationProperties(prefix = "app.outbox")
record OutboxProperties(Integer maxAttempts,
                        Integer batchSize,
                        Duration retryInitial,
                        Duration retryMax,
                        Long pollInterval,
                        Duration visibility,
                        Boolean dispatchAfterCommit) {

    OutboxProperties {
        maxAttempts = maxAttempts != null ? maxAttempts : 5;
        batchSize = batchSize != null ? batchSize : 100;
        retryInitial = retryInitial != null ? retryInitial : Duration.ofSeconds(30);
        retryMax = retryMax != null ? retryMax : Duration.ofMinutes(30);
        pollInterval = pollInterval != null ? pollInterval : 60_000L;
        visibility = visibility != null ? visibility : Duration.ofMinutes(2);
        dispatchAfterCommit = dispatchAfterCommit == null || dispatchAfterCommit;

        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("app.outbox.max-attempts doit etre > 0");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("app.outbox.batch-size doit etre > 0");
        }
        if (retryInitial.isNegative() || retryInitial.isZero()) {
            throw new IllegalArgumentException("app.outbox.retry-initial doit etre > 0");
        }
        if (retryMax.compareTo(retryInitial) < 0) {
            throw new IllegalArgumentException("app.outbox.retry-max doit etre >= retry-initial");
        }
        if (pollInterval <= 0) {
            throw new IllegalArgumentException("app.outbox.poll-interval doit etre > 0");
        }
        if (visibility.isNegative() || visibility.isZero()) {
            throw new IllegalArgumentException("app.outbox.visibility doit etre > 0");
        }
    }

    /**
     * Attente croissante : doublement à chaque échec, plafonné.
     * Le plafond compte autant que la croissance — sans lui, un
     * fournisseur indisponible une nuit repousserait la reprise à
     * plusieurs jours.
     */
    Duration backoffAfter(int attempts) {
        Duration delay = retryInitial;
        for (int i = 1; i < attempts && delay.compareTo(retryMax) < 0; i++) {
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(retryMax) > 0 ? retryMax : delay;
    }
}
