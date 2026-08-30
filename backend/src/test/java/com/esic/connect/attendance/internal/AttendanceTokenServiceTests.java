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
 * précédent), la résolution par jeton et par code court, et surtout
 * l'<strong>invariant du pointeur courant</strong> : une clé
 * {@code token -> session} résiduelle ne doit jamais suffire à valider un
 * jeton qui n'est plus le jeton courant de la séance.
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

    /** Faux magasin clé -> valeur pour les scénarios de résolution. */
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

    @Test
    void constructorRejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new AttendanceTokenService(redis, clock, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AttendanceTokenService(redis, clock, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void issueProducesOpaqueTokenAndUnambiguousShortCode() {
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
        UUID sessionId = UUID.randomUUID();
        store.put(SESSION_PREFIX + sessionId, "OLD_TOKEN\nOLDCODE12");

        service.issue(sessionId);

        verify(redis).delete(TOKEN_PREFIX + "OLD_TOKEN");
        verify(redis).delete(CODE_PREFIX + "OLDCODE12");
    }

    @Test
    void resolveSessionByToken() {
        UUID sessionId = UUID.randomUUID();
        store.put(TOKEN_PREFIX + "T123", sessionId.toString());
        store.put(SESSION_PREFIX + sessionId, "T123\nCODE1234");

        assertThat(service.resolveSession("T123", null)).contains(sessionId);
    }

    @Test
    void resolveSessionByShortCode() {
        UUID sessionId = UUID.randomUUID();
        store.put(CODE_PREFIX + "ABCD2345", "T999");
        store.put(TOKEN_PREFIX + "T999", sessionId.toString());
        store.put(SESSION_PREFIX + sessionId, "T999\nABCD2345");

        assertThat(service.resolveSession(null, "ABCD2345")).contains(sessionId);
    }

    @Test
    void resolveSessionReturnsEmptyForUnknownOrExpired() {
        assertThat(service.resolveSession("nope", null)).isEmpty();
        assertThat(service.resolveSession(null, "NOPE2345")).isEmpty();
    }

    // ------------------------------------------------------------------
    // Invariant du pointeur courant
    // ------------------------------------------------------------------

    @Test
    void residualOldTokenIsRejectedWhenSessionPointerHasRotated() {
        UUID sessionId = UUID.randomUUID();
        // L'ancienne clé token -> session a survécu à la rotation…
        store.put(TOKEN_PREFIX + "OLD_TOKEN", sessionId.toString());
        store.put(TOKEN_PREFIX + "NEW_TOKEN", sessionId.toString());
        // …mais le pointeur courant désigne le nouveau couple.
        store.put(SESSION_PREFIX + sessionId, "NEW_TOKEN\nNEWCODE22");

        assertThat(service.resolveSession("OLD_TOKEN", null)).isEmpty();
        assertThat(service.resolveSession("NEW_TOKEN", null)).contains(sessionId);
    }

    @Test
    void residualOldShortCodeIsRejectedWhenSessionPointerHasRotated() {
        UUID sessionId = UUID.randomUUID();
        store.put(CODE_PREFIX + "OLDCODE11", "OLD_TOKEN");
        store.put(TOKEN_PREFIX + "OLD_TOKEN", sessionId.toString());
        store.put(CODE_PREFIX + "NEWCODE22", "NEW_TOKEN");
        store.put(TOKEN_PREFIX + "NEW_TOKEN", sessionId.toString());
        store.put(SESSION_PREFIX + sessionId, "NEW_TOKEN\nNEWCODE22");

        assertThat(service.resolveSession(null, "OLDCODE11")).isEmpty();
        assertThat(service.resolveSession(null, "NEWCODE22")).contains(sessionId);
    }

    @Test
    void tokenKeyWithoutAnyCurrentSessionPointerIsRejected() {
        UUID sessionId = UUID.randomUUID();
        store.put(TOKEN_PREFIX + "ORPHAN_TOKEN", sessionId.toString());
        // Aucun esic:attendance:session:{id} : preuve invalide.

        assertThat(service.resolveSession("ORPHAN_TOKEN", null)).isEmpty();
    }

    @Test
    void inconsistentSessionPointerIsRejected() {
        UUID sessionId = UUID.randomUUID();
        store.put(TOKEN_PREFIX + "T", sessionId.toString());

        // Pointeur sans séparateur.
        store.put(SESSION_PREFIX + sessionId, "TokenWithoutSeparator");
        assertThat(service.resolveSession("T", null)).isEmpty();

        // Pointeur avec un segment vide.
        store.put(SESSION_PREFIX + sessionId, "T\n");
        assertThat(service.resolveSession("T", null)).isEmpty();

        // Payload de jeton illisible.
        store.put(TOKEN_PREFIX + "T", "pas-un-uuid");
        store.put(SESSION_PREFIX + sessionId, "T\nCODE1234");
        assertThat(service.resolveSession("T", null)).isEmpty();
    }

    @Test
    void pointerThatDesignatesAnotherTokenIsRejected() {
        UUID sessionId = UUID.randomUUID();
        // Le jeton présenté existe et pointe la bonne séance…
        store.put(TOKEN_PREFIX + "PRESENTED", sessionId.toString());
        // …mais le pointeur courant désigne un tout autre jeton.
        store.put(SESSION_PREFIX + sessionId, "SOMETHING_ELSE\nOTHERCOD");

        assertThat(service.resolveSession("PRESENTED", null)).isEmpty();
    }

    @Test
    void normalRotationLeavesOnlyTheNewPairUsable() {
        UUID sessionId = UUID.randomUUID();
        IssuedAttendanceToken first = service.issue(sessionId);
        // Reflète l'écriture faite par issue() dans le faux magasin.
        store.put(TOKEN_PREFIX + first.token(), sessionId.toString());
        store.put(CODE_PREFIX + first.shortCode(), first.token());
        store.put(SESSION_PREFIX + sessionId, first.token() + "\n" + first.shortCode());
        assertThat(service.resolveSession(first.token(), null)).contains(sessionId);

        IssuedAttendanceToken second = service.issue(sessionId);
        store.put(TOKEN_PREFIX + second.token(), sessionId.toString());
        store.put(CODE_PREFIX + second.shortCode(), second.token());
        store.put(SESSION_PREFIX + sessionId, second.token() + "\n" + second.shortCode());

        assertThat(service.resolveSession(second.token(), null)).contains(sessionId);
        // L'ancien jeton reste éventuellement en clé (TTL) mais n'est plus courant.
        assertThat(service.resolveSession(first.token(), null)).isEmpty();
        assertThat(service.resolveSession(null, first.shortCode())).isEmpty();
    }

    @Test
    void afterInvalidationEvenAResidualTokenKeyIsUnusable() {
        UUID sessionId = UUID.randomUUID();
        store.put(SESSION_PREFIX + sessionId, "TOK\nCODE1234");
        store.put(TOKEN_PREFIX + "TOK", sessionId.toString());
        store.put(CODE_PREFIX + "CODE1234", "TOK");
        // invalidateSession supprime les clés connues -> on retire du magasin.
        when(redis.delete(anyString())).thenAnswer(inv -> {
            store.remove(inv.getArgument(0, String.class));
            return true;
        });

        service.invalidateSession(sessionId);
        verify(redis).delete(SESSION_PREFIX + sessionId);

        // Même si une clé token avait survécu, l'absence de pointeur la rend inutilisable.
        store.put(TOKEN_PREFIX + "TOK", sessionId.toString());
        assertThat(service.resolveSession("TOK", null)).isEmpty();
    }

    // ------------------------------------------------------------------

    @Test
    void redisDownDuringIssueThrowsControlledError() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> service.issue(UUID.randomUUID()))
                .isInstanceOf(AttendanceException.class)
                .satisfies(ex -> assertThat(((AttendanceException) ex).kind())
                        .isEqualTo(AttendanceException.Kind.TOKEN_BACKEND_UNAVAILABLE));
    }

    @Test
    void redisDownDuringResolveThrowsControlledErrorWithoutLeakingTheToken() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> service.resolveSession("SECRET-OPAQUE-TOKEN", "SECRETCODE"))
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
        // Première tentative en collision, seconde libre.
        when(redis.hasKey(anyString())).thenReturn(true, false);

        IssuedAttendanceToken issued = service.issue(UUID.randomUUID());
        assertThat(issued.shortCode()).matches("[A-HJ-NP-Z2-9]{8}");
    }

    @Test
    void invalidateSessionDeletesKnownKeysAndSwallowsRedisFailure() {
        UUID sessionId = UUID.randomUUID();
        store.put(SESSION_PREFIX + sessionId, "TOK\nCODE1234");

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
