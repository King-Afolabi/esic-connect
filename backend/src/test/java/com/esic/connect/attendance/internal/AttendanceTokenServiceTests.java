package com.esic.connect.attendance.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
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
 * précédent), la résolution par jeton et par code court en
 * <em>(séance, point de contrôle)</em> (V10), et l'<strong>invariant du
 * pointeur courant</strong> : une clé résiduelle ne doit jamais suffire à
 * valider un jeton qui n'est plus le jeton courant de la séance.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceTokenServiceTests {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final String TOKEN_PREFIX = "esic:attendance:token:";
    private static final String CODE_PREFIX = "esic:attendance:code:";
    private static final String SESSION_PREFIX = "esic:attendance:session:";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private final Map<String, String> store = new HashMap<>();

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-10T09:00:00Z"), ZoneOffset.UTC);
    private AttendanceTokenService service;

    @BeforeEach
    void setUp() {
        service = new AttendanceTokenService(redis, clock, TTL);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0, String.class)));
        when(redis.hasKey(anyString())).thenReturn(false);
    }

    private static String payload(UUID session, UUID checkpoint) {
        return session + "\n" + checkpoint;
    }

    private static String pointer(String token, String code, UUID checkpoint) {
        return token + "\n" + code + "\n" + checkpoint;
    }

    @Test
    void constructorRejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new AttendanceTokenService(redis, clock, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AttendanceTokenService(redis, clock, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void issueProducesOpaqueTokenAndUnambiguousShortCodeForACheckpoint() {
        UUID sessionId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();

        IssuedAttendanceToken issued = service.issue(sessionId, checkpointId);

        assertThat(issued.sessionPublicId()).isEqualTo(sessionId);
        assertThat(issued.checkpointPublicId()).isEqualTo(checkpointId);
        assertThat(issued.expiresAt()).isEqualTo(clock.instant().plus(TTL));
        assertThat(issued.token()).matches("[A-Za-z0-9_-]{43}");
        assertThat(issued.token()).doesNotContain(sessionId.toString());
        assertThat(issued.shortCode()).matches("[A-HJ-NP-Z2-9]{8}");
        assertThat(issued.token()).doesNotContain(issued.shortCode());

        verify(valueOps).set(eq(TOKEN_PREFIX + issued.token()),
                eq(payload(sessionId, checkpointId)), eq(TTL));
        verify(valueOps).set(eq(CODE_PREFIX + issued.shortCode()), eq(issued.token()), eq(TTL));
        verify(valueOps).set(eq(SESSION_PREFIX + sessionId),
                eq(pointer(issued.token(), issued.shortCode(), checkpointId)), eq(TTL));
    }

    @Test
    void issueRotatesAndInvalidatesThePreviousPair() {
        UUID sessionId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        store.put(SESSION_PREFIX + sessionId, "OLD_TOKEN\nOLDCODE12\n" + checkpointId);

        service.issue(sessionId, UUID.randomUUID());

        verify(redis).delete(TOKEN_PREFIX + "OLD_TOKEN");
        verify(redis).delete(CODE_PREFIX + "OLDCODE12");
    }

    @Test
    void resolveByTokenReturnsSessionAndCheckpoint() {
        UUID sessionId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        store.put(TOKEN_PREFIX + "T123", payload(sessionId, checkpointId));
        store.put(SESSION_PREFIX + sessionId, pointer("T123", "CODE1234", checkpointId));

        Optional<ResolvedAttendanceToken> resolved = service.resolve("T123", null);
        assertThat(resolved).contains(new ResolvedAttendanceToken(sessionId, checkpointId));
    }

    @Test
    void resolveByShortCodeReturnsSessionAndCheckpoint() {
        UUID sessionId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        store.put(CODE_PREFIX + "ABCD2345", "T999");
        store.put(TOKEN_PREFIX + "T999", payload(sessionId, checkpointId));
        store.put(SESSION_PREFIX + sessionId, pointer("T999", "ABCD2345", checkpointId));

        assertThat(service.resolve(null, "ABCD2345"))
                .contains(new ResolvedAttendanceToken(sessionId, checkpointId));
    }

    @Test
    void resolveReturnsEmptyForUnknownOrExpired() {
        assertThat(service.resolve("nope", null)).isEmpty();
        assertThat(service.resolve(null, "NOPE2345")).isEmpty();
    }

    @Test
    void residualOldTokenIsRejectedWhenSessionPointerHasRotated() {
        UUID sessionId = UUID.randomUUID();
        UUID cpOld = UUID.randomUUID();
        UUID cpNew = UUID.randomUUID();
        store.put(TOKEN_PREFIX + "OLD_TOKEN", payload(sessionId, cpOld));
        store.put(TOKEN_PREFIX + "NEW_TOKEN", payload(sessionId, cpNew));
        store.put(SESSION_PREFIX + sessionId, pointer("NEW_TOKEN", "NEWCODE22", cpNew));

        assertThat(service.resolve("OLD_TOKEN", null)).isEmpty();
        assertThat(service.resolve("NEW_TOKEN", null))
                .contains(new ResolvedAttendanceToken(sessionId, cpNew));
    }

    @Test
    void residualOldShortCodeIsRejectedWhenSessionPointerHasRotated() {
        UUID sessionId = UUID.randomUUID();
        UUID cp = UUID.randomUUID();
        store.put(CODE_PREFIX + "OLDCODE11", "OLD_TOKEN");
        store.put(TOKEN_PREFIX + "OLD_TOKEN", payload(sessionId, cp));
        store.put(CODE_PREFIX + "NEWCODE22", "NEW_TOKEN");
        store.put(TOKEN_PREFIX + "NEW_TOKEN", payload(sessionId, cp));
        store.put(SESSION_PREFIX + sessionId, pointer("NEW_TOKEN", "NEWCODE22", cp));

        assertThat(service.resolve(null, "OLDCODE11")).isEmpty();
        assertThat(service.resolve(null, "NEWCODE22"))
                .contains(new ResolvedAttendanceToken(sessionId, cp));
    }

    @Test
    void tokenKeyWithoutAnyCurrentSessionPointerIsRejected() {
        UUID sessionId = UUID.randomUUID();
        store.put(TOKEN_PREFIX + "ORPHAN_TOKEN", payload(sessionId, UUID.randomUUID()));

        assertThat(service.resolve("ORPHAN_TOKEN", null)).isEmpty();
    }

    @Test
    void inconsistentSessionPointerIsRejected() {
        UUID sessionId = UUID.randomUUID();
        UUID cp = UUID.randomUUID();
        store.put(TOKEN_PREFIX + "T", payload(sessionId, cp));

        // Pointeur avec seulement deux segments (format V9).
        store.put(SESSION_PREFIX + sessionId, "T\nCODE1234");
        assertThat(service.resolve("T", null)).isEmpty();

        // Pointeur avec un segment vide.
        store.put(SESSION_PREFIX + sessionId, "T\n\n" + cp);
        assertThat(service.resolve("T", null)).isEmpty();

        // Payload de jeton illisible.
        store.put(TOKEN_PREFIX + "T", "pas-un-uuid");
        store.put(SESSION_PREFIX + sessionId, pointer("T", "CODE1234", cp));
        assertThat(service.resolve("T", null)).isEmpty();
    }

    @Test
    void pointerThatDesignatesAnotherTokenIsRejected() {
        UUID sessionId = UUID.randomUUID();
        UUID cp = UUID.randomUUID();
        store.put(TOKEN_PREFIX + "PRESENTED", payload(sessionId, cp));
        store.put(SESSION_PREFIX + sessionId, pointer("SOMETHING_ELSE", "OTHERCOD", cp));

        assertThat(service.resolve("PRESENTED", null)).isEmpty();
    }

    @Test
    void normalRotationLeavesOnlyTheNewPairUsable() {
        UUID sessionId = UUID.randomUUID();
        UUID cp = UUID.randomUUID();
        IssuedAttendanceToken first = service.issue(sessionId, cp);
        store.put(TOKEN_PREFIX + first.token(), payload(sessionId, cp));
        store.put(CODE_PREFIX + first.shortCode(), first.token());
        store.put(SESSION_PREFIX + sessionId, pointer(first.token(), first.shortCode(), cp));
        assertThat(service.resolve(first.token(), null))
                .contains(new ResolvedAttendanceToken(sessionId, cp));

        IssuedAttendanceToken second = service.issue(sessionId, cp);
        store.put(TOKEN_PREFIX + second.token(), payload(sessionId, cp));
        store.put(CODE_PREFIX + second.shortCode(), second.token());
        store.put(SESSION_PREFIX + sessionId, pointer(second.token(), second.shortCode(), cp));

        assertThat(service.resolve(second.token(), null))
                .contains(new ResolvedAttendanceToken(sessionId, cp));
        assertThat(service.resolve(first.token(), null)).isEmpty();
        assertThat(service.resolve(null, first.shortCode())).isEmpty();
    }

    @Test
    void afterInvalidationEvenAResidualTokenKeyIsUnusable() {
        UUID sessionId = UUID.randomUUID();
        UUID cp = UUID.randomUUID();
        store.put(SESSION_PREFIX + sessionId, pointer("TOK", "CODE1234", cp));
        store.put(TOKEN_PREFIX + "TOK", payload(sessionId, cp));
        store.put(CODE_PREFIX + "CODE1234", "TOK");
        when(redis.delete(anyString())).thenAnswer(inv -> {
            store.remove(inv.getArgument(0, String.class));
            return true;
        });

        service.invalidateSession(sessionId);
        verify(redis).delete(SESSION_PREFIX + sessionId);

        store.put(TOKEN_PREFIX + "TOK", payload(sessionId, cp));
        assertThat(service.resolve("TOK", null)).isEmpty();
    }

    @Test
    void invalidateCheckpointOnlyPurgesWhenTheCurrentPointerMatches() {
        UUID sessionId = UUID.randomUUID();
        UUID cpA = UUID.randomUUID();
        UUID cpB = UUID.randomUUID();
        store.put(SESSION_PREFIX + sessionId, pointer("TOK", "CODE1234", cpA));

        // Le jeton courant concerne cpA : invalider cpB est sans effet.
        service.invalidateCheckpoint(sessionId, cpB);
        verify(redis, org.mockito.Mockito.never()).delete(TOKEN_PREFIX + "TOK");

        // Invalider cpA purge le couple courant et le pointeur.
        service.invalidateCheckpoint(sessionId, cpA);
        verify(redis).delete(TOKEN_PREFIX + "TOK");
        verify(redis).delete(CODE_PREFIX + "CODE1234");
        verify(redis).delete(SESSION_PREFIX + sessionId);
    }

    @Test
    void redisDownDuringIssueThrowsControlledError() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> service.issue(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(AttendanceException.class)
                .satisfies(ex -> assertThat(((AttendanceException) ex).kind())
                        .isEqualTo(AttendanceException.Kind.TOKEN_BACKEND_UNAVAILABLE));
    }

    @Test
    void redisDownDuringResolveThrowsControlledErrorWithoutLeakingTheToken() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> service.resolve("SECRET-OPAQUE-TOKEN", "SECRETCODE"))
                .isInstanceOf(AttendanceException.class)
                .satisfies(ex -> {
                    AttendanceException failure = (AttendanceException) ex;
                    assertThat(failure.kind())
                            .isEqualTo(AttendanceException.Kind.TOKEN_BACKEND_UNAVAILABLE);
                    assertThat(failure.getMessage()).doesNotContain("SECRET-OPAQUE-TOKEN");
                    assertThat(failure.getMessage()).doesNotContain("SECRETCODE");
                });
    }

    @Test
    void shortCodeCollisionIsRegenerated() {
        when(redis.hasKey(anyString())).thenReturn(true, false);

        IssuedAttendanceToken issued = service.issue(UUID.randomUUID(), UUID.randomUUID());
        assertThat(issued.shortCode()).matches("[A-HJ-NP-Z2-9]{8}");
    }

    @Test
    void invalidateSessionDeletesKnownKeysAndSwallowsRedisFailure() {
        UUID sessionId = UUID.randomUUID();
        UUID cp = UUID.randomUUID();
        store.put(SESSION_PREFIX + sessionId, pointer("TOK", "CODE1234", cp));

        service.invalidateSession(sessionId);
        verify(redis).delete(TOKEN_PREFIX + "TOK");
        verify(redis).delete(CODE_PREFIX + "CODE1234");
        verify(redis).delete(SESSION_PREFIX + sessionId);

        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        service.invalidateSession(UUID.randomUUID());
    }

    @Test
    void ttlIsExposedForClientCountdown() {
        assertThat(service.ttl()).isEqualTo(TTL);
        assertThat(Optional.of(service.ttl().toSeconds())).contains(30L);
    }
}
