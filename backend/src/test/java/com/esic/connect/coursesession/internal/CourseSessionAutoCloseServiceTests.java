package com.esic.connect.coursesession.internal;

import com.esic.connect.coursesession.CourseSessionChangeAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Revérification de l'éligibilité d'UNE séance, dans sa propre
 * transaction (Lot 9) : {@link CourseSessionAutoCloseService}. Aucune
 * horloge réelle ni attente : {@link Clock#fixed}.
 */
@ExtendWith(MockitoExtension.class)
class CourseSessionAutoCloseServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");
    private static final Duration GRACE = Duration.ofMinutes(15);

    @Mock
    private CourseSessionRepository sessionRepository;
    @Mock
    private CourseSessionService sessionService;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final CourseSessionAutoCloseProperties properties =
            new CourseSessionAutoCloseProperties(true, 200, GRACE);

    private CourseSessionAutoCloseService service() {
        return new CourseSessionAutoCloseService(sessionRepository, sessionService, properties, clock);
    }

    private CourseSession openSessionEndingAt(Instant endsAt) {
        CourseSession session = new CourseSession(1L, "Séance", endsAt.minusSeconds(3600), endsAt,
                "Europe/Paris", null);
        session.open(endsAt.minusSeconds(1800), null);
        ReflectionTestUtils.setField(session, "id", 42L);
        ReflectionTestUtils.setField(session, "publicId", UUID.randomUUID());
        return session;
    }

    @Test
    void aSessionNotFoundIsIgnored() {
        when(sessionRepository.findById(99L)).thenReturn(Optional.empty());

        boolean closed = service().closeIfStillEligible(99L);

        assertThat(closed).isFalse();
        verify(sessionService, never()).closeCore(any(), any(), any(), any());
    }

    @Test
    void aSessionNoLongerOpenIsIgnored() {
        CourseSession session = openSessionEndingAt(NOW.minusSeconds(20 * 60));
        session.close(NOW.minusSeconds(600), 7L); // fermée manuellement entre-temps
        when(sessionRepository.findById(42L)).thenReturn(Optional.of(session));

        boolean closed = service().closeIfStillEligible(42L);

        assertThat(closed).isFalse();
        verify(sessionService, never()).closeCore(any(), any(), any(), any());
    }

    @Test
    void aSessionStillWithinItsGracePeriodIsNotClosed() {
        // Finie il y a 10 min < 15 min de grâce.
        CourseSession session = openSessionEndingAt(NOW.minusSeconds(10 * 60));
        when(sessionRepository.findById(42L)).thenReturn(Optional.of(session));

        boolean closed = service().closeIfStillEligible(42L);

        assertThat(closed).isFalse();
        verify(sessionService, never()).closeCore(any(), any(), any(), any());
    }

    @Test
    void aSessionExactlyAtTheGracePeriodBoundaryIsClosed() {
        // endsAt + 15 min == NOW pile : borne inclusive.
        CourseSession session = openSessionEndingAt(NOW.minusSeconds(15 * 60));
        when(sessionRepository.findById(42L)).thenReturn(Optional.of(session));

        boolean closed = service().closeIfStillEligible(42L);

        assertThat(closed).isTrue();
        verify(sessionService).closeCore(session, NOW, null, CourseSessionChangeAction.AUTO_CLOSED);
    }

    @Test
    void aSessionPastItsGracePeriodIsClosedWithNoHumanActor() {
        CourseSession session = openSessionEndingAt(NOW.minusSeconds(30 * 60));
        when(sessionRepository.findById(42L)).thenReturn(Optional.of(session));

        boolean closed = service().closeIfStillEligible(42L);

        assertThat(closed).isTrue();
        // Jamais d'acteur humain usurpé : actorId toujours null.
        verify(sessionService).closeCore(eq(session), eq(NOW), eq(null), eq(CourseSessionChangeAction.AUTO_CLOSED));
    }
}
