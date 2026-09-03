package com.esic.connect.shared.ratelimit;

import java.time.Duration;

/**
 * Résultat d'une tentative de consommation d'un seau de limitation.
 *
 * @param allowed          l'opération est autorisée
 * @param remaining        unités restantes sur la fenêtre courante
 *                         (jamais négatif)
 * @param retryAfter       délai avant qu'une nouvelle tentative puisse
 *                         aboutir ; {@link Duration#ZERO} lorsque
 *                         l'opération est autorisée
 * @param backendAvailable {@code false} lorsque Redis n'a pas répondu et
 *                         que la décision est un repli permissif
 *                         (voir {@link RedisRateLimiter})
 */
public record RateLimitDecision(boolean allowed, long remaining, Duration retryAfter, boolean backendAvailable) {

    public static RateLimitDecision allowed(long remaining) {
        return new RateLimitDecision(true, Math.max(0, remaining), Duration.ZERO, true);
    }

    public static RateLimitDecision denied(Duration retryAfter) {
        return new RateLimitDecision(false, 0, retryAfter, true);
    }

    /**
     * Repli appliqué lorsque Redis est indisponible : l'opération est
     * <strong>autorisée</strong>. Voir la décision d'architecture
     * {@code DEC-S2-001} dans {@code docs/03-architecture.md} §ADR.
     */
    public static RateLimitDecision backendUnavailable() {
        return new RateLimitDecision(true, 0, Duration.ZERO, false);
    }
}
