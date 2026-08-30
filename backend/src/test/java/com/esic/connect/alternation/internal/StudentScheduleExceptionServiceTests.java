package com.esic.connect.alternation.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.alternation.AlternationChangeAction;
import com.esic.connect.alternation.AlternationResourceType;
import com.esic.connect.enrollment.EnrollmentDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validations métier des exceptions individuelles, isolées des I/O :
 * inscription inconnue / non exploitable, fuseau invalide, période
 * invalide, chevauchement de même type refusé (types différents
 * autorisés), périmètre pédagogique, annulation.
 */
@ExtendWith(MockitoExtension.class)
class StudentScheduleExceptionServiceTests {

    @Mock
    private StudentScheduleExceptionRepository exceptionRepository;
    @Mock
    private EnrollmentDirectory enrollmentDirectory;
    @Mock
    private AcademicScopeDirectory academicScope;
    @Mock
    private AlternationChangePublisher changePublisher;

    private StudentScheduleExceptionService service() {
        return new StudentScheduleExceptionService(exceptionRepository, enrollmentDirectory, academicScope,
                changePublisher);
    }

    private final UUID enrollmentPublicId = UUID.randomUUID();
    private final UUID classPublicId = UUID.randomUUID();

    private EnrollmentDirectory.EnrollmentRef enrollmentRef(boolean usable) {
        return new EnrollmentDirectory.EnrollmentRef(11L, enrollmentPublicId, UUID.randomUUID(),
                UUID.randomUUID(), classPublicId, "C1", UUID.randomUUID(), "2026-2027", usable);
    }

    private StudentExceptionRequests.Create create(String type, String zone, Instant start, Instant end) {
        return new StudentExceptionRequests.Create(enrollmentPublicId.toString(), type, start, end, zone,
                "motif");
    }

    @Test
    void createRejectsUnknownEnrollment() {
        when(enrollmentDirectory.findByPublicId(enrollmentPublicId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().create(create("REMOTE_ALLOWED", "Europe/Paris",
                Instant.parse("2026-09-10T08:00:00Z"), Instant.parse("2026-09-10T18:00:00Z")), "sub"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.ENROLLMENT_NOT_FOUND);
    }

    @Test
    void createRejectsNonUsableEnrollment() {
        when(enrollmentDirectory.findByPublicId(enrollmentPublicId)).thenReturn(Optional.of(enrollmentRef(false)));
        assertThatThrownBy(() -> service().create(create("REMOTE_ALLOWED", "Europe/Paris",
                Instant.parse("2026-09-10T08:00:00Z"), Instant.parse("2026-09-10T18:00:00Z")), "sub"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.ENROLLMENT_NOT_USABLE);
    }

    @Test
    void createRejectsOutOfScopeCaller() {
        when(enrollmentDirectory.findByPublicId(enrollmentPublicId)).thenReturn(Optional.of(enrollmentRef(true)));
        when(academicScope.hasGlobalScope()).thenReturn(false);
        when(academicScope.isClassInScope(classPublicId)).thenReturn(false);
        assertThatThrownBy(() -> service().create(create("REMOTE_ALLOWED", "Europe/Paris",
                Instant.parse("2026-09-10T08:00:00Z"), Instant.parse("2026-09-10T18:00:00Z")), "sub"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.OUT_OF_SCOPE);
    }

    @Test
    void createRejectsInvalidTimeZone() {
        when(enrollmentDirectory.findByPublicId(enrollmentPublicId)).thenReturn(Optional.of(enrollmentRef(true)));
        when(academicScope.hasGlobalScope()).thenReturn(true);
        assertThatThrownBy(() -> service().create(create("REMOTE_ALLOWED", "Mars/Olympus",
                Instant.parse("2026-09-10T08:00:00Z"), Instant.parse("2026-09-10T18:00:00Z")), "sub"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.INVALID_TIME_ZONE);
    }

    @Test
    void createRejectsEndBeforeStart() {
        when(enrollmentDirectory.findByPublicId(enrollmentPublicId)).thenReturn(Optional.of(enrollmentRef(true)));
        when(academicScope.hasGlobalScope()).thenReturn(true);
        assertThatThrownBy(() -> service().create(create("REMOTE_ALLOWED", "Europe/Paris",
                Instant.parse("2026-09-10T18:00:00Z"), Instant.parse("2026-09-10T08:00:00Z")), "sub"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.INVALID_PERIOD);
    }

    @Test
    void createRejectsOverlapOfSameType() {
        when(enrollmentDirectory.findByPublicId(enrollmentPublicId)).thenReturn(Optional.of(enrollmentRef(true)));
        when(academicScope.hasGlobalScope()).thenReturn(true);
        StudentScheduleException existing = new StudentScheduleException(11L,
                ScheduleExceptionType.REMOTE_ALLOWED, Instant.parse("2026-09-10T00:00:00Z"),
                Instant.parse("2026-09-12T00:00:00Z"), "Europe/Paris", "motif");
        when(exceptionRepository.findActiveOverlapping(eq(11L), any(), any())).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service().create(create("REMOTE_ALLOWED", "Europe/Paris",
                Instant.parse("2026-09-11T08:00:00Z"), Instant.parse("2026-09-11T18:00:00Z")), "sub"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.EXCEPTION_OVERLAP);
        verify(exceptionRepository, never()).save(any());
    }

    @Test
    void createAllowsOverlapOfDifferentType() {
        when(enrollmentDirectory.findByPublicId(enrollmentPublicId)).thenReturn(Optional.of(enrollmentRef(true)));
        when(academicScope.hasGlobalScope()).thenReturn(true);
        StudentScheduleException existing = new StudentScheduleException(11L,
                ScheduleExceptionType.REMOTE_ALLOWED, Instant.parse("2026-09-10T00:00:00Z"),
                Instant.parse("2026-09-12T00:00:00Z"), "Europe/Paris", "motif");
        when(exceptionRepository.findActiveOverlapping(eq(11L), any(), any())).thenReturn(List.of(existing));
        when(exceptionRepository.save(any())).thenAnswer(inv -> {
            StudentScheduleException e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "publicId", UUID.randomUUID());
            return e;
        });
        when(changePublisher.actorId("sub")).thenReturn(4L);

        StudentExceptionResponse response = service().create(create("ON_SITE_REQUIRED", "Europe/Paris",
                Instant.parse("2026-09-11T08:00:00Z"), Instant.parse("2026-09-11T18:00:00Z")), "sub");

        assertThat(response.type()).isEqualTo(ScheduleExceptionType.ON_SITE_REQUIRED);
        verify(changePublisher).publish(eq(AlternationResourceType.STUDENT_SCHEDULE_EXCEPTION), any(),
                eq(AlternationChangeAction.CREATED), eq(4L), any());
    }

    @Test
    void cancelRejectsAlreadyCancelledException() {
        StudentScheduleException exception = new StudentScheduleException(11L,
                ScheduleExceptionType.REMOTE_ALLOWED, Instant.parse("2026-09-10T08:00:00Z"),
                Instant.parse("2026-09-10T18:00:00Z"), "Europe/Paris", "motif");
        exception.cancel("déjà", null);
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(exception, "publicId", id);
        when(exceptionRepository.findByPublicId(id)).thenReturn(Optional.of(exception));

        assertThatThrownBy(() -> service().cancel(id, new StudentExceptionRequests.Cancel("x"), "sub"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.EXCEPTION_ALREADY_CANCELLED);
    }

    @Test
    void cancelMarksCancelledAndPublishesEvent() {
        StudentScheduleException exception = new StudentScheduleException(11L,
                ScheduleExceptionType.COMPANY_PERIOD, Instant.parse("2026-09-10T08:00:00Z"),
                Instant.parse("2026-09-10T18:00:00Z"), "Europe/Paris", "motif");
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(exception, "publicId", id);
        when(exceptionRepository.findByPublicId(id)).thenReturn(Optional.of(exception));
        when(enrollmentDirectory.findByInternalId(11L)).thenReturn(Optional.of(enrollmentRef(true)));
        when(academicScope.hasGlobalScope()).thenReturn(true);
        when(changePublisher.actorId("sub")).thenReturn(2L);

        service().cancel(id, new StudentExceptionRequests.Cancel("erreur de saisie"), "sub");

        assertThat(exception.getStatus()).isEqualTo(ScheduleExceptionStatus.CANCELLED);
        verify(changePublisher).publish(eq(AlternationResourceType.STUDENT_SCHEDULE_EXCEPTION), eq(id),
                eq(AlternationChangeAction.CANCELLED), eq(2L), any());
    }

    @Test
    void listByEnrollmentRejectsSortOutsideWhitelist() {
        when(enrollmentDirectory.findByPublicId(enrollmentPublicId)).thenReturn(Optional.of(enrollmentRef(true)));
        when(academicScope.hasGlobalScope()).thenReturn(true);
        assertThatThrownBy(() -> service().listByEnrollment(enrollmentPublicId.toString(), 0, 20, "reason,asc"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.INVALID_SORT);
    }
}
