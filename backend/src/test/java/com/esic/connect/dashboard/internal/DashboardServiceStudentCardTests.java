package com.esic.connect.dashboard.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.attendance.AttendanceDashboardDirectory;
import com.esic.connect.audit.AuditDashboardDirectory;
import com.esic.connect.claim.ClaimDashboardDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.enrollment.EnrollmentDirectory;
import com.esic.connect.identity.AccountStatsDirectory;
import com.esic.connect.studentimport.StudentImportDashboardDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * « Ma classe » (Lot 13) : {@link DashboardService} dérive la classe
 * active de l'apprenant de son inscription {@code ACTIVE} elle-même, pas
 * des séances de la semaine. Le cas « plusieurs inscriptions actives
 * simultanées » — une anomalie que la contrainte SQL globale
 * ({@code uq_enrollment_active_global}, V36) empêche désormais via
 * l'API — est vérifié ici, au niveau service, plutôt qu'en intégration :
 * on ne peut plus la provoquer via une vraie base sans contourner cette
 * contrainte.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceStudentCardTests {

    private static final UUID USER_PUBLIC_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");

    @Mock
    private EnrollmentDirectory enrollmentDirectory;
    @Mock
    private CourseSessionDirectory courseSessionDirectory;
    @Mock
    private AttendanceDashboardDirectory attendanceDashboard;
    @Mock
    private AcademicScopeDirectory academicScope;
    @Mock
    private ClassGroupDirectory classGroupDirectory;
    @Mock
    private AccountStatsDirectory accountStats;
    @Mock
    private StudentImportDashboardDirectory studentImportDashboard;
    @Mock
    private ClaimDashboardDirectory claimDashboard;
    @Mock
    private AuditDashboardDirectory auditDashboard;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private DashboardService service() {
        return new DashboardService(enrollmentDirectory, courseSessionDirectory, attendanceDashboard,
                academicScope, classGroupDirectory, accountStats, studentImportDashboard, claimDashboard,
                auditDashboard, clock);
    }

    private void stubNoSessionsAndEmptyDigest() {
        when(courseSessionDirectory.findSessionsForClasses(any(), any(), any())).thenReturn(List.of());
        when(attendanceDashboard.studentDigest(any()))
                .thenReturn(new AttendanceDashboardDirectory.StudentAttendanceDigest(0, 0, 0, 0, 0, 0));
    }

    private static EnrollmentDirectory.EnrollmentRef activeRef(UUID classPublicId) {
        return new EnrollmentDirectory.EnrollmentRef(1L, UUID.randomUUID(), USER_PUBLIC_ID, classPublicId,
                "C1", UUID.randomUUID(), "2026-2027", true);
    }

    @Test
    void noActiveEnrollmentMeansNoActiveClass() {
        stubNoSessionsAndEmptyDigest();
        when(enrollmentDirectory.findActiveEnrollmentsForUserOn(any(), any())).thenReturn(List.of());

        DashboardResponses.Dashboard dashboard =
                service().forCaller(USER_PUBLIC_ID.toString(), List.of("STUDENT"), null);

        assertThat(dashboard.student().activeClass()).isNull();
        assertThat(dashboard.notes()).isEmpty();
    }

    @Test
    void oneActiveEnrollmentExposesItsClassRegardlessOfSessions() {
        stubNoSessionsAndEmptyDigest();
        UUID classPublicId = UUID.randomUUID();
        when(enrollmentDirectory.findActiveEnrollmentsForUserOn(any(), any()))
                .thenReturn(List.of(activeRef(classPublicId)));
        when(classGroupDirectory.findByPublicId(classPublicId)).thenReturn(java.util.Optional.of(
                new ClassGroupDirectory.ClassGroupRef(1L, classPublicId, "C1", "Classe 1",
                        UUID.randomUUID(), "BTS-SIO", 1L, UUID.randomUUID(), "2026-2027", true)));

        DashboardResponses.Dashboard dashboard =
                service().forCaller(USER_PUBLIC_ID.toString(), List.of("STUDENT"), null);

        DashboardResponses.ClassRef activeClass = dashboard.student().activeClass();
        assertThat(activeClass).isNotNull();
        assertThat(activeClass.name()).isEqualTo("Classe 1");
        assertThat(activeClass.code()).isEqualTo("C1");
        assertThat(activeClass.academicYearCode()).isEqualTo("2026-2027");
        assertThat(dashboard.notes()).isEmpty();
    }

    @Test
    void severalSimultaneousActiveEnrollmentsAreAnAnomalyNeverAnArbitraryChoice() {
        stubNoSessionsAndEmptyDigest();
        when(enrollmentDirectory.findActiveEnrollmentsForUserOn(any(), any()))
                .thenReturn(List.of(activeRef(UUID.randomUUID()), activeRef(UUID.randomUUID())));

        DashboardResponses.Dashboard dashboard =
                service().forCaller(USER_PUBLIC_ID.toString(), List.of("STUDENT"), null);

        // Ne jamais choisir arbitrairement une classe parmi plusieurs.
        assertThat(dashboard.student().activeClass()).isNull();
        // Le dashboard reste utilisable (pas d'exception), l'anomalie est signalée.
        assertThat(dashboard.notes()).anySatisfy(n -> assertThat(n).contains("Anomalie"));
        // ClassGroupDirectory n'est jamais interrogé pour choisir : aucun appel.
        org.mockito.Mockito.verify(classGroupDirectory, org.mockito.Mockito.never()).findByPublicId(any());
    }
}
