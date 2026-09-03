package com.esic.connect.shared.ratelimit;

import java.time.Duration;

/**
 * Levée lorsqu'un seau de limitation est épuisé. Traduite en
 * {@code 429 RATE_LIMITED} avec un en-tête {@code Retry-After} par
 * {@code GlobalExceptionHandler}.
 *
 * <p>Le message ne contient jamais l'identité limitée : il ne doit rien
 * révéler à l'appelant sur l'existence d'un compte.
 */
public class RateLimitExceededException extends RuntimeException {

    private final transient Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super("Trop de tentatives.");
        this.retryAfter = retryAfter == null || retryAfter.isNegative() ? Duration.ZERO : retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
