package com.esic.connect.alternation.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.enrollment.EnrollmentDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Résolution du contexte (sections 9 et 10) : contexte issu du rythme,
 * absence d'affectation → {@code UNKNOWN}/{@code NONE}, priorité
 * structurelle d'une exception individuelle {@code ON_SITE_REQUIRED} /
 * {@code COMPANY_PERIOD} sur le rythme.
 */
@ExtendWith(MockitoExtension.class)
class AlternationContextServiceTests {

    @Mock
    private ClassWorkStudyPatternRepository assignmentRepository;
    @Mock
    private StudentScheduleExceptionRepository exceptionRepository;
    @Mock
    private ClassGroupDirectory classGroupDirectory;
    @Mock
    private EnrollmentDirectory enrollmentDirectory;
    @Mock
    private AcademicScopeDirectory academicScope;

    private final AlternationConfigParser configParser = new AlternationConfigParser(new ObjectMapper());
    private final AlternationResolver resolver = new AlternationResolver();

    private AlternationContextService service() {
        return new AlternationContextService(assignmentRepository, exceptionRepository, configParser, resolver,
                classGroupDirectory, enrollmentDirectory, academicScope);
    }

    private static final LocalDate ANCHOR = LocalDate.of(2026, 9, 1); // Tuesday
    private final UUID classPublicId = UUID.randomUUID();
    private final UUID enrollmentPublicId = UUID.randomUUID();

    private ClassGroupDirectory.ClassGroupRef classRef() {
        return new ClassGroupDirectory.ClassGroupRef(42L, classPublicId, "C1", UUID.randomUUID(), "PRG",
                7L, UUID.randomUUID(), "2026-2027", true);
    }

    private ClassWorkStudyPattern assignment() {
        WorkStudyPattern pattern = new WorkStudyPattern("RYT-1", "Rythme", null,
                WorkStudyPatternType.THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY, 1,
                "{\"cycleLengthWeeks\":1,\"schoolWeeks\":[1],\"companyWeeks\":[],"
                        + "\"schoolDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\"],"
                        + "\"companyDays\":[\"THURSDAY\",\"FRIDAY\"]}");
        ReflectionTestUtils.setField(pattern, "publicId", UUID.randomUUID());
        ClassWorkStudyPattern assignment = new ClassWorkStudyPattern(42L, pattern, ANCHOR, ANCHOR, null);
        ReflectionTestUtils.setField(assignment, "publicId", UUID.randomUUID());
        return assignment;
    }

    @Test
    void classContextIsUnknownWithNoAssignment() {
        when(classGroupDirectory.findByPublicId(classPublicId)).thenReturn(Optional.of(classRef()));
        when(academicScope.hasGlobalScope()).thenReturn(true);
        when(assignmentRepository.findActiveCovering(eq(42L), any())).thenReturn(List.of());

        AlternationContextResponse response = service().resolveClassContext(classPublicId.toString(),
                LocalDate.of(2026, 9, 9));

        assertThat(response.context()).isEqualTo(AlternationContext.UNKNOWN);
        assertThat(response.source()).isEqualTo(ContextSource.NONE);
        assertThat(response.cycleWeekIndex()).isNull();
    }

    @Test
    void classContextResolvesFromPattern() {
        when(classGroupDirectory.findByPublicId(classPublicId)).thenReturn(Optional.of(classRef()));
        when(academicScope.hasGlobalScope()).thenReturn(true);
        when(assignmentRepository.findActiveCovering(eq(42L), any())).thenReturn(List.of(assignment()));

        // 2026-09-10 is a Thursday -> company day of the 3/2 pattern
        AlternationContextResponse response = service().resolveClassContext(classPublicId.toString(),
                LocalDate.of(2026, 9, 10));

        assertThat(response.context()).isEqualTo(AlternationContext.COMPANY);
        assertThat(response.source()).isEqualTo(ContextSource.PATTERN);
        assertThat(response.workStudyPatternCode()).isEqualTo("RYT-1");
        assertThat(response.dayOfWeek()).isEqualTo("THURSDAY");
        assertThat(response.cycleWeekIndex()).isEqualTo(1);
    }

    @Test
    void enrollmentContextFallsBackToPatternWhenNoException() {
        stubEnrollmentWithPattern();
        when(exceptionRepository.findActiveOverlapping(eq(11L), any(), any())).thenReturn(List.of());

        // Monday -> school day
        EnrollmentContextResponse response = service().resolveEnrollmentContext(enrollmentPublicId.toString(),
                LocalDate.of(2026, 9, 7));

        assertThat(response.patternContext()).isEqualTo(AlternationContext.SCHOOL);
        assertThat(response.effectiveContext()).isEqualTo(AlternationContext.SCHOOL);
        assertThat(response.source()).isEqualTo(ContextSource.PATTERN);
        assertThat(response.coveringExceptionTypes()).isEmpty();
    }

    @Test
    void individualCompanyPeriodExceptionOverridesSchoolPattern() {
        stubEnrollmentWithPattern();
        StudentScheduleException companyException = new StudentScheduleException(11L,
                ScheduleExceptionType.COMPANY_PERIOD, Instant.parse("2026-09-07T00:00:00Z"),
                Instant.parse("2026-09-08T23:59:00Z"), "Europe/Paris", "stage");
        when(exceptionRepository.findActiveOverlapping(eq(11L), any(), any()))
                .thenReturn(List.of(companyException));

        // Monday 2026-09-07: pattern says SCHOOL, exception forces COMPANY
        EnrollmentContextResponse response = service().resolveEnrollmentContext(enrollmentPublicId.toString(),
                LocalDate.of(2026, 9, 7));

        assertThat(response.patternContext()).isEqualTo(AlternationContext.SCHOOL);
        assertThat(response.effectiveContext()).isEqualTo(AlternationContext.COMPANY);
        assertThat(response.source()).isEqualTo(ContextSource.INDIVIDUAL_EXCEPTION);
        assertThat(response.coveringExceptionTypes()).containsExactly(ScheduleExceptionType.COMPANY_PERIOD);
    }

    @Test
    void remoteAllowedExceptionIsReportedButDoesNotChangeContext() {
        stubEnrollmentWithPattern();
        StudentScheduleException remote = new StudentScheduleException(11L,
                ScheduleExceptionType.REMOTE_ALLOWED, Instant.parse("2026-09-07T00:00:00Z"),
                Instant.parse("2026-09-08T23:59:00Z"), "Europe/Paris", "santé");
        when(exceptionRepository.findActiveOverlapping(eq(11L), any(), any())).thenReturn(List.of(remote));

        EnrollmentContextResponse response = service().resolveEnrollmentContext(enrollmentPublicId.toString(),
                LocalDate.of(2026, 9, 7));

        assertThat(response.effectiveContext()).isEqualTo(AlternationContext.SCHOOL);
        assertThat(response.source()).isEqualTo(ContextSource.PATTERN);
        assertThat(response.coveringExceptionTypes()).containsExactly(ScheduleExceptionType.REMOTE_ALLOWED);
    }

    @Test
    void contradictoryExceptionsYieldUnknown() {
        stubEnrollmentWithPattern();
        StudentScheduleException onSite = new StudentScheduleException(11L,
                ScheduleExceptionType.ON_SITE_REQUIRED, Instant.parse("2026-09-07T00:00:00Z"),
                Instant.parse("2026-09-08T00:00:00Z"), "Europe/Paris", "réunion");
        StudentScheduleException company = new StudentScheduleException(11L,
                ScheduleExceptionType.COMPANY_PERIOD, Instant.parse("2026-09-07T00:00:00Z"),
                Instant.parse("2026-09-08T00:00:00Z"), "Europe/Paris", "stage");
        when(exceptionRepository.findActiveOverlapping(eq(11L), any(), any()))
                .thenReturn(List.of(onSite, company));

        EnrollmentContextResponse response = service().resolveEnrollmentContext(enrollmentPublicId.toString(),
                LocalDate.of(2026, 9, 7));

        assertThat(response.effectiveContext()).isEqualTo(AlternationContext.UNKNOWN);
        assertThat(response.source()).isEqualTo(ContextSource.INDIVIDUAL_EXCEPTION);
    }

    private void stubEnrollmentWithPattern() {
        UUID classPub = classPublicId;
        EnrollmentDirectory.EnrollmentRef ref = new EnrollmentDirectory.EnrollmentRef(11L, enrollmentPublicId,
                UUID.randomUUID(), classPub, "C1", UUID.randomUUID(), "2026-2027", true);
        when(enrollmentDirectory.findByPublicId(enrollmentPublicId)).thenReturn(Optional.of(ref));
        lenient().when(academicScope.hasGlobalScope()).thenReturn(true);
        when(classGroupDirectory.findByPublicId(classPub)).thenReturn(Optional.of(classRef()));
        when(assignmentRepository.findActiveCovering(anyLong(), any())).thenReturn(List.of(assignment()));
    }
}
