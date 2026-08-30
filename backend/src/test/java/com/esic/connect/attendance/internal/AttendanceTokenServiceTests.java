package com.esic.connect.attendance.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AttendanceTokenService} sans Redis externe : le
 * {@link StringRedisTemplate} est mocké. Vérifie l'entropie / le format
 * du jeton et du code court, la rotation (invalidation du couple
 * précédent), la résolution par jeton et par code court, l'erreur
 * contrôlée si Redis est indisponible, et le refus d'un TTL non positif.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceTokenServiceTests {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final String TOKEN_PREFIX = "esic:attendance:token:";
    private static final String CODE_PREFIX = "esic:attendance:code:";
    private static final String SESSION_PREFIX = "esic:attendance:session:";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-10T09:00:00Z"), ZoneOffset.UTC);
    private AttendanceTokenService service;

    @BeforeEach
    void setUp() {
        service = new AttendanceTokenService(redis, clock, TTL);
    }

    @Test
    void constructorRejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new AttendanceTokenService(redis, clock, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AttendanceTokenService(redis, clock, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void issueProducesOpaqueTokenAndUnambiguousShortCode() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(redis.hasKey(anyString())).thenReturn(false);
        UUID sessionId = UUID.randomUUID();

        IssuedAttendanceToken issued = service.issue(sessionId);

        assertThat(issued.sessionPublicId()).isEqualTo(sessionId);
        assertThat(issued.expiresAt()).isEqualTo(clock.instant().plus(TTL));
        // Jeton opaque : URL-safe sans padding, longueur d'un tirage de 32 octets.
        assertThat(issued.token()).matches("[A-Za-z0-9_-]{43}");
        assertThat(issued.token()).doesNotContain(issued.sessionPublicId().toString());
        // Code court : 8 caractères d'un alphabet sans 0/O/1/I/L.
        assertThat(issued.shortCode()).matches("[A-HJ-NP-Z2-9]{8}");
        assertThat(issued.token()).doesNotContain(issued.shortCode());

        verify(valueOps).set(eq(TOKEN_PREFIX + issued.token()), eq(sessionId.toString()), eq(TTL));
        verify(valueOps).set(eq(CODE_PREFIX + issued.shortCode()), eq(issued.token()), eq(TTL));
        verify(valueOps).set(eq(SESSION_PREFIX + sessionId),
                eq(issued.token() + "\n" + issued.shortCode()), eq(TTL));
    }

    @Test
    void issueRotatesAndInvalidatesThePreviousPair() {
        when(redis.opsForValue()).thenReturn(valueOps);
        UUID sessionId = UUID.randomUUID();
        // issue() ne lit que la clé de session courante.
        when(valueOps.get(SESSION_PREFIX + sessionId)).thenReturn("OLD_TOKEN\nOLDCODE12");
        when(redis.hasKey(anyString())).thenReturn(false);

        service.issue(sessionId);

        verify(redis).delete(TOKEN_PREFIX + "OLD_TOKEN");
        verify(redis).delete(CODE_PREFIX + "OLDCODE12");
    }

    @Test
    void resolveSessionByToken() {
        when(redis.opsForValue()).thenReturn(valueOps);
        UUID sessionId = UUID.randomUUID();
        when(valueOps.get(TOKEN_PREFIX + "T123")).thenReturn(sessionId.toString());

        assertThat(service.resolveSession("T123", null)).contains(sessionId);
    }

    @Test
    void resolveSessionByShortCode() {
        when(redis.opsForValue()).thenReturn(valueOps);
        UUID sessionId = UUID.randomUUID();
        when(valueOps.get(CODE_PREFIX + "ABCD2345")).thenReturn("T999");
        when(valueOps.get(TOKEN_PREFIX + "T999")).thenReturn(sessionId.toString());

        assertThat(service.resolveSession(null, "ABCD2345")).contains(sessionId);
    }

    @Test
    void resolveSessionReturnsEmptyForUnknownOrExpired() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        assertThat(service.resolveSession("nope", null)).isEmpty();
        assertThat(service.resolveSession(null, "NOPE2345")).isEmpty();
    }

    @Test
    void redisDownDuringIssueThrowsControlledError() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> service.issue(UUID.randomUUID()))
                .isInstanceOf(AttendanceException.class)
                .satisfies(ex -> assertThat(((AttendanceException) ex).kind())
                        .isEqualTo(AttendanceException.Kind.TOKEN_BACKEND_UNAVAILABLE));
    }

    @Test
    void redisDownDuringResolveThrowsControlledError() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> service.resolveSession("T", null))
                .isInstanceOf(AttendanceException.class)
                .satisfies(ex -> assertThat(((AttendanceException) ex).kind())
                        .isEqualTo(AttendanceException.Kind.TOKEN_BACKEND_UNAVAILABLE));
    }

    @Test
    void shortCodeCollisionIsRegenerated() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        // Première tentative en collision, seconde libre.
        when(redis.hasKey(anyString())).thenReturn(true, false);

        IssuedAttendanceToken issued = service.issue(UUID.randomUUID());
        assertThat(issued.shortCode()).matches("[A-HJ-NP-Z2-9]{8}");
    }

    @Test
    void invalidateSessionDeletesKnownKeysAndSwallowsRedisFailure() {
        when(redis.opsForValue()).thenReturn(valueOps);
        UUID sessionId = UUID.randomUUID();
        when(valueOps.get(SESSION_PREFIX + sessionId)).thenReturn("TOK\nCODE1234");

        service.invalidateSession(sessionId);
        verify(redis).delete(TOKEN_PREFIX + "TOK");
        verify(redis).delete(CODE_PREFIX + "CODE1234");
        verify(redis).delete(SESSION_PREFIX + sessionId);

        // Redis KO : l'invalidation ne propage pas (les clés expireront par TTL).
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        service.invalidateSession(UUID.randomUUID());
    }

    @Test
    void ttlIsExposedForClientCountdown() {
        assertThat(service.ttl()).isEqualTo(TTL);
        assertThat(Optional.of(service.ttl().toSeconds())).contains(30L);
    }
}
