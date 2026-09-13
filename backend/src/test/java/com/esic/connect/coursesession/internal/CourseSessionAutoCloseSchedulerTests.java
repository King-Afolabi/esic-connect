package com.esic.connect.coursesession.internal;

import com.esic.connect.coursesession.SessionLifecycle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Balayage périodique (Lot 9) : {@link CourseSessionAutoCloseScheduler}.
 * Le lot lui-même ({@code properties.batchSize()} transmis au
 * repository) et la poursuite malgré l'échec d'une séance sont vérifiés
 * ici, isolément et de façon déterministe — sans dépendre d'une base de
 * données partagée qui pourrait déjà contenir des séances {@code OPEN}
 * d'autres suites (voir {@code CourseSessionAutoCloseIntegrationTests}).
 */
@ExtendWith(MockitoExtension.class)
class CourseSessionAutoCloseSchedulerTests {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");

    @Mock
    private CourseSessionRepository sessionRepository;
    @Mock
    private CourseSessionAutoCloseService autoCloseService;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private CourseSessionAutoCloseScheduler scheduler(int batchSize) {
        CourseSessionAutoCloseProperties properties =
                new CourseSessionAutoCloseProperties(true, batchSize, Duration.ofMinutes(15));
        return new CourseSessionAutoCloseScheduler(sessionRepository, autoCloseService, properties, clock);
    }

    private static CourseSession candidate(long id) {
        CourseSession session = new CourseSession(1L, "Séance", NOW.minusSeconds(7200), NOW.minusSeconds(3600),
                "Europe/Paris", null);
        ReflectionTestUtils.setField(session, "id", id);
        ReflectionTestUtils.setField(session, "publicId", UUID.randomUUID());
        return session;
    }

    @Test
    void noCandidatesMeansTheServiceIsNeverCalled() {
        when(sessionRepository.findAutoCloseCandidates(any(), any(), any())).thenReturn(List.of());

        scheduler(200).closeOverdueSessions();

        verify(autoCloseService, never()).closeIfStillEligible(anyLong());
    }

    @Test
    void theConfiguredBatchSizeIsPassedToTheRepositoryQuery() {
        when(sessionRepository.findAutoCloseCandidates(any(), any(), any())).thenReturn(List.of());

        scheduler(37).closeOverdueSessions();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(sessionRepository).findAutoCloseCandidates(eq(SessionLifecycle.OPEN), eq(NOW), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(37);
    }

    @Test
    void everyCandidateIsOffered() {
        List<CourseSession> candidates = List.of(candidate(1), candidate(2), candidate(3));
        when(sessionRepository.findAutoCloseCandidates(any(), any(), any())).thenReturn(candidates);
        when(autoCloseService.closeIfStillEligible(anyLong())).thenReturn(true);

        scheduler(200).closeOverdueSessions();

        verify(autoCloseService).closeIfStillEligible(1L);
        verify(autoCloseService).closeIfStillEligible(2L);
        verify(autoCloseService).closeIfStillEligible(3L);
    }

    @Test
    void aFailureOnOneSessionDoesNotPreventTheOthersFromBeingProcessed() {
        List<CourseSession> candidates = List.of(candidate(1), candidate(2), candidate(3));
        when(sessionRepository.findAutoCloseCandidates(any(), any(), any())).thenReturn(candidates);
        when(autoCloseService.closeIfStillEligible(1L)).thenReturn(true);
        when(autoCloseService.closeIfStillEligible(2L))
                .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("course concurrente"));
        when(autoCloseService.closeIfStillEligible(3L)).thenReturn(true);

        // Ne doit lever aucune exception : un échec individuel est
        // journalisé, jamais fatal pour le lot.
        scheduler(200).closeOverdueSessions();

        verify(autoCloseService).closeIfStillEligible(1L);
        verify(autoCloseService).closeIfStillEligible(2L);
        verify(autoCloseService).closeIfStillEligible(3L); // atteinte malgré l'échec de la 2ᵉ
    }
}
