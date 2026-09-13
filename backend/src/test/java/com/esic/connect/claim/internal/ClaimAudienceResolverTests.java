package com.esic.connect.claim.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.academic.PedagogicalResponsibilityDirectory;
import com.esic.connect.claim.ClaimAudience;
import com.esic.connect.claim.ClaimCategory;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.coursesession.SessionAttendanceMode;
import com.esic.connect.coursesession.SessionLifecycle;
import com.esic.connect.identity.UserDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Priorité de résolution du guichet TEACHER (Lot 19) : un formateur
 * explicitement ciblé l'emporte sur celui déduit de la séance, qui
 * l'emporte lui-même sur le repli responsable pédagogique.
 */
class ClaimAudienceResolverTests {

    private static final UUID AUTHOR_PUBLIC_ID = UUID.randomUUID();
    private static final UUID TARGET_TEACHER_PUBLIC_ID = UUID.randomUUID();
    private static final UUID SESSION_TEACHER_PUBLIC_ID = UUID.randomUUID();
    private static final UUID MANAGER_PUBLIC_ID = UUID.randomUUID();
    private static final UUID SESSION_PUBLIC_ID = UUID.randomUUID();

    private ClaimMessageRepository messageRepository;
    private UserDirectory userDirectory;
    private PedagogicalResponsibilityDirectory responsibilityDirectory;
    private CourseSessionDirectory courseSessionDirectory;
    private ClassGroupDirectory classGroupDirectory;
    private ClaimAudienceResolver resolver;

    @BeforeEach
    void setUp() {
        messageRepository = mock(ClaimMessageRepository.class);
        userDirectory = mock(UserDirectory.class);
        responsibilityDirectory = mock(PedagogicalResponsibilityDirectory.class);
        courseSessionDirectory = mock(CourseSessionDirectory.class);
        classGroupDirectory = mock(ClassGroupDirectory.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-13T08:00:00Z"), ZoneOffset.UTC);
        resolver = new ClaimAudienceResolver(messageRepository, userDirectory, responsibilityDirectory,
                courseSessionDirectory, classGroupDirectory, clock);

        when(messageRepository.findByClaimIdOrderByCreatedAtAscIdAsc(anyLong())).thenReturn(List.of());
        when(userDirectory.findByInternalId(1L))
                .thenReturn(Optional.of(new UserDirectory.UserRef(1L, AUTHOR_PUBLIC_ID, false, Set.of("STUDENT"))));
        when(userDirectory.findByInternalId(2L))
                .thenReturn(Optional.of(
                        new UserDirectory.UserRef(2L, TARGET_TEACHER_PUBLIC_ID, false, Set.of("TEACHER"))));
    }

    private Claim claim(Long targetTeacherUserId, Long courseSessionId) {
        return new Claim(1L, ClaimCategory.ATTENDANCE, "sujet", ClaimAudience.TEACHER,
                courseSessionId, null, null, null, targetTeacherUserId);
    }

    @Test
    void unFormateurExplicitementCibleEstPrioritaireMemeAvecUneSeance() {
        Claim claim = claim(2L, 10L);

        Set<UUID> recipients = resolver.recipientsOf(claim, null);

        assertThat(recipients).contains(TARGET_TEACHER_PUBLIC_ID);
        assertThat(recipients).doesNotContain(SESSION_TEACHER_PUBLIC_ID);
        // La séance n'a jamais été résolue : la cible explicite l'emporte
        // avant même de la consulter.
        org.mockito.Mockito.verifyNoInteractions(courseSessionDirectory);
    }

    @Test
    void sansCibleExpliciteLeFormateurDeLaSeanceEstRetenu() {
        Claim claim = claim(null, 10L);
        CourseSessionDirectory.SessionRef sessionRef = new CourseSessionDirectory.SessionRef(10L,
                SESSION_PUBLIC_ID, "Anglais", SessionLifecycle.PLANNED, 99L, List.of(), Set.of(),
                "Europe/Paris", Instant.parse("2026-09-10T06:00:00Z"), Instant.parse("2026-09-10T10:00:00Z"),
                SessionAttendanceMode.ON_SITE, null);
        when(courseSessionDirectory.findSessionByInternalId(10L)).thenReturn(Optional.of(sessionRef));
        when(courseSessionDirectory.findSessionNotificationInfo(SESSION_PUBLIC_ID)).thenReturn(Optional.of(
                new CourseSessionDirectory.SessionNotificationInfo(SESSION_PUBLIC_ID, "Anglais",
                        SESSION_TEACHER_PUBLIC_ID, Set.of(), Set.of())));

        Set<UUID> recipients = resolver.recipientsOf(claim, null);

        assertThat(recipients).contains(SESSION_TEACHER_PUBLIC_ID);
    }

    @Test
    void sansCibleNiSeanceLeResponsablePedagogiquePrendLeRelais() {
        Claim claim = claim(null, null);
        when(responsibilityDirectory.findManagersOfClasses(any(), any())).thenReturn(Set.of(MANAGER_PUBLIC_ID));

        Set<UUID> recipients = resolver.recipientsOf(claim, null);

        assertThat(recipients).contains(MANAGER_PUBLIC_ID);
    }

    @Test
    void unFormateurCibleArchiveNestPasNotifie() {
        UUID archivedTeacher = UUID.randomUUID();
        when(userDirectory.findByInternalId(3L))
                .thenReturn(Optional.of(new UserDirectory.UserRef(3L, archivedTeacher, true, Set.of("TEACHER"))));
        Claim claim = claim(3L, null);

        Set<UUID> recipients = resolver.recipientsOf(claim, null);

        assertThat(recipients).doesNotContain(archivedTeacher);
    }
}
