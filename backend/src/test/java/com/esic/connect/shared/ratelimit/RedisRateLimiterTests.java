package com.esic.connect.shared.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compteur de limitation de débit, contre un vrai Redis (EF-AUTH-012).
 *
 * <p>Chaque test utilise une identité neuve : les seaux ne doivent pas
 * fuir d'un test à l'autre, et l'isolation ne repose sur aucun nettoyage
 * global.
 */
@SpringBootTest
@ActiveProfiles("test")
class RedisRateLimiterTests {

    private static final Duration WINDOW = Duration.ofMinutes(5);

    @Autowired
    private RateLimiter rateLimiter;

    @Test
    void allowsUpToTheLimitThenDenies() {
        String identity = newIdentity();

        for (int attempt = 1; attempt <= 3; attempt++) {
            RateLimitDecision decision = rateLimiter.consume("test-bucket", identity, 3, WINDOW);
            assertThat(decision.allowed()).as("tentative %d", attempt).isTrue();
            assertThat(decision.remaining()).isEqualTo(3 - attempt);
        }

        RateLimitDecision denied = rateLimiter.consume("test-bucket", identity, 3, WINDOW);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.remaining()).isZero();
        assertThat(denied.retryAfter()).isPositive().isLessThanOrEqualTo(WINDOW);
    }

    @Test
    void bucketsAreIsolatedFromEachOther() {
        String identity = newIdentity();
        rateLimiter.consume("bucket-a", identity, 1, WINDOW);

        assertThat(rateLimiter.consume("bucket-a", identity, 1, WINDOW).allowed()).isFalse();
        assertThat(rateLimiter.consume("bucket-b", identity, 1, WINDOW).allowed()).isTrue();
    }

    @Test
    void identitiesAreIsolatedFromEachOther() {
        String saturated = newIdentity();
        rateLimiter.consume("shared-bucket", saturated, 1, WINDOW);
        assertThat(rateLimiter.consume("shared-bucket", saturated, 1, WINDOW).allowed()).isFalse();

        assertThat(rateLimiter.consume("shared-bucket", newIdentity(), 1, WINDOW).allowed()).isTrue();
    }

    @Test
    void resetClearsTheCounter() {
        String identity = newIdentity();
        rateLimiter.consume("resettable", identity, 1, WINDOW);
        assertThat(rateLimiter.consume("resettable", identity, 1, WINDOW).allowed()).isFalse();

        rateLimiter.reset("resettable", identity);

        assertThat(rateLimiter.consume("resettable", identity, 1, WINDOW).allowed()).isTrue();
    }

    @Test
    void theWindowExpiresOnItsOwn() throws InterruptedException {
        String identity = newIdentity();
        Duration shortWindow = Duration.ofSeconds(1);

        assertThat(rateLimiter.consume("expiring", identity, 1, shortWindow).allowed()).isTrue();
        assertThat(rateLimiter.consume("expiring", identity, 1, shortWindow).allowed()).isFalse();

        // La fenêtre est portée par le TTL de la clé : aucune tâche de
        // purge n'existe, et il ne doit pas en falloir.
        Thread.sleep(1_300);

        assertThat(rateLimiter.consume("expiring", identity, 1, shortWindow).allowed()).isTrue();
    }

    @Test
    void invalidParametersAreRejected() {
        String identity = newIdentity();
        assertThatThrownBy(() -> rateLimiter.consume("bad", identity, 0, WINDOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rateLimiter.consume("bad", identity, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rateLimiter.consume("bad", identity, 1, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String newIdentity() {
        return IdentityHashing.of("rate-limit-test-" + UUID.randomUUID());
    }
}
