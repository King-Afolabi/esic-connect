package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttendanceStatus;
import com.esic.connect.coursesession.AttendanceCheckpointStatus;
import com.esic.connect.coursesession.AttendanceCheckpointType;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory.CheckpointRef;
import com.esic.connect.coursesession.CourseSessionDirectory.SessionRef;
import com.esic.connect.coursesession.SessionLifecycle;
import com.esic.connect.enrollment.EnrollmentDirectory;
import com.esic.connect.identity.UserDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Correctif G1-0.1 — la couverture d'une inscription lors d'une validation
 * de présence est décidée à la <strong>date civile de la séance</strong>
 * ({@code startsAt} projeté dans son fuseau IANA persisté), jamais à
 * « aujourd'hui en UTC ». Tests <strong>déterministes</strong> : horloge
 * figée, aucune dépendance à l'heure réelle d'exécution.
 *
 * <p>Défaut d'origine : {@code AttendanceService.validate} résolvait les
 * inscriptions actives avec {@code LocalDate.ofInstant(clock.instant(),
 * ZoneOffset.UTC)}. Dans la fenêtre où la date locale (Europe/Paris) diffère
 * de la date UTC, une inscription tout juste créée pour le jour local était
 * écartée → {@code ATT_NOT_ENROLLED} (409) sporadique, uniquement la nuit.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceServiceSessionDateTests {

    /** Séance saisie à Paris, débutant juste après minuit local (30/03/2026 00:30 CEST). */
    private static final String SESSION_ZONE = "Europe/Paris";
    private static final Instant SESSION_START = Instant.parse("2026-03-29T22:30:00Z");
    private static final Instant SESSION_END = Instant.parse("2026-03-29T23:30:00Z");
    /** Date civile de la séance dans son fuseau : 30/03/2026 (et non le 29/03 en UTC). */
    private static final LocalDate SESSION_CIVIL_DATE = LocalDate.of(2026, 3, 30);
    private static final LocalDate SESSION_UTC_DATE = LocalDate.of(2026, 3, 29);

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID CHECKPOINT_ID = UUID.randomUUID();
    private static final UUID CLASS_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID ENROLLMENT_PUBLIC_ID = UUID.randomUUID();

    @Mock
    private AttendanceTokenService tokenService;
    @Mock
    private AttendanceRecordRepository recordRepository;
    @Mock
    private AttendanceRecordPersister recordPersister;
    @Mock
    private CourseSessionDirectory courseSessionDirectory;
    @Mock
    private EnrollmentDirectory enrollmentDirectory;
    @Mock
    private UserDirectory userDirectory;
    @Mock
    private AttendanceChangePublisher changePublisher;

    private AttendanceService serviceWithClock(Clock clock) {
        AttendanceService service = new AttendanceService(tokenService, recordRepository, recordPersister,
                courseSessionDirectory, enrollmentDirectory, userDirectory, changePublisher, clock,
                Duration.ofMinutes(10));

        lenient().when(tokenService.resolve(eq("tok"), any()))
                .thenReturn(Optional.of(new ResolvedAttendanceToken(SESSION_ID, CHECKPOINT_ID)));
        lenient().when(courseSessionDirectory.findForAttendance(SESSION_ID))
                .thenReturn(Optional.of(session()));
        lenient().when(userDirectory.findByPublicId(STUDENT_ID))
                .thenReturn(Optional.of(new UserDirectory.UserRef(7L, STUDENT_ID, false, Set.of("ROLE_STUDENT"))));
        lenient().when(recordRepository.existsByAttendanceCheckpointIdAndEnrollmentId(42L, 99L)).thenReturn(false);
        // Le persister renvoie l'entité que le service vient de construire
        // (recordedAt = instant de l'horloge figée ; public_id nul, seulement
        // transmis au publisher mocké).
        lenient().when(recordPersister.persist(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(changePublisher.actorId(any())).thenReturn(7L);
        return service;
    }

    private static SessionRef session() {
        CheckpointRef cp = new CheckpointRef(42L, CHECKPOINT_ID, "Arrivée", AttendanceCheckpointType.START,
                AttendanceCheckpointStatus.OPEN, true, 0, SESSION_START, null);
        return new SessionRef(1L, SESSION_ID, "Séance", SessionLifecycle.OPEN, 5L, List.of(cp),
                Set.of(CLASS_ID), SESSION_ZONE, SESSION_START, SESSION_END);
    }

    private static EnrollmentDirectory.EnrollmentRef enrollment() {
        return new EnrollmentDirectory.EnrollmentRef(99L, ENROLLMENT_PUBLIC_ID, UUID.randomUUID(), STUDENT_ID,
                CLASS_ID, "C1", UUID.randomUUID(), "2025-2026", true);
    }

    private static AttendanceRequests.Validate validateRequest() {
        return new AttendanceRequests.Validate("tok", null);
    }

    // ------------------------------------------------------------------
    // 1 + 2 + 5 — la date de décision est la date civile de la séance,
    // quelle que soit l'heure réelle (horloge figée, plusieurs instants
    // dont un dans la fenêtre nuit Paris ≠ UTC).
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "horloge = {0}")
    @ValueSource(strings = {
            "2026-03-29T22:15:00Z", // pile avant le début, Paris = 30/03 00:15, UTC = 29/03
            "2026-03-29T23:45:00Z", // fenêtre nuit : UTC = 29/03, Paris = 30/03 01:45
            "2026-08-31T23:30:00Z", // fenêtre de la panne d'origine : UTC = 31/08, Paris = 01/09
            "2026-06-15T09:00:00Z", // très loin de la séance : la valeur de l'horloge n'entre pas en jeu
            "2027-01-01T12:00:00Z"  // année suivante : idem
    })
    void enrollmentCoverageIsCheckedAtTheSessionCivilDateWhateverTheWallClock(String nowIso) {
        AttendanceService service = serviceWithClock(
                Clock.fixed(Instant.parse(nowIso), ZoneId.of(SESSION_ZONE)));
        ArgumentCaptor<LocalDate> dateArg = ArgumentCaptor.forClass(LocalDate.class);
        when(enrollmentDirectory.findActiveEnrollmentsForUserOn(eq(STUDENT_ID), dateArg.capture()))
                .thenReturn(List.of(enrollment()));

        service.validate(validateRequest(), STUDENT_ID.toString());

        assertThat(dateArg.getValue())
                .as("date de décision = date civile de la séance dans son fuseau persisté")
                .isEqualTo(SESSION_CIVIL_DATE)
                .isNotEqualTo(SESSION_UTC_DATE)
                .isNotEqualTo(LocalDate.ofInstant(Instant.parse(nowIso), ZoneOffset.UTC));
    }

    // ------------------------------------------------------------------
    // 3 — la date métier de la séance est couverte par l'inscription.
    // ------------------------------------------------------------------

    @Test
    void validatesWhenTheEnrollmentCoversTheSessionCivilDate() {
        AttendanceService service = serviceWithClock(
                Clock.fixed(Instant.parse("2026-08-31T23:30:00Z"), ZoneId.of(SESSION_ZONE)));
        when(enrollmentDirectory.findActiveEnrollmentsForUserOn(STUDENT_ID, SESSION_CIVIL_DATE))
                .thenReturn(List.of(enrollment()));

        AttendanceRecordResponse response = service.validate(validateRequest(), STUDENT_ID.toString());

        assertThat(response).isNotNull();
        assertThat(response.status()).isIn(AttendanceStatus.PRESENT, AttendanceStatus.LATE);
    }

    // ------------------------------------------------------------------
    // 4 — la date métier de la séance n'est pas couverte : refus net,
    //     jamais un faux positif dû à la date UTC.
    // ------------------------------------------------------------------

    @Test
    void rejectsWhenNoEnrollmentCoversTheSessionCivilDate() {
        AttendanceService service = serviceWithClock(
                Clock.fixed(Instant.parse("2026-08-31T23:30:00Z"), ZoneId.of(SESSION_ZONE)));
        // Rien pour la date civile de la séance ; une couverture existerait
        // pour la date UTC — elle ne doit jamais être consultée.
        when(enrollmentDirectory.findActiveEnrollmentsForUserOn(STUDENT_ID, SESSION_CIVIL_DATE))
                .thenReturn(List.of());
        lenient().when(enrollmentDirectory.findActiveEnrollmentsForUserOn(STUDENT_ID, SESSION_UTC_DATE))
                .thenReturn(List.of(enrollment()));

        assertThatThrownBy(() -> service.validate(validateRequest(), STUDENT_ID.toString()))
                .isInstanceOf(AttendanceException.class)
                .extracting(ex -> ((AttendanceException) ex).kind())
                .isEqualTo(AttendanceException.Kind.NOT_ENROLLED);
    }
}
