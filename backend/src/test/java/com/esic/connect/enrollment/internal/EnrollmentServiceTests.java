package com.esic.connect.enrollment.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.enrollment.EnrollmentChangeAction;
import com.esic.connect.enrollment.EnrollmentResourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * Validations métier des inscriptions, isolées des I/O (horloge figée) :
 * profil inconnu / archivé, classe inconnue / archivée, unicité d'une
 * inscription active par année (RG-012), changement de classe
 * (clôture {@code TRANSFERRED} + nouvelle inscription liée), clôture,
 * garde-fous de dates.
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTests {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-15T09:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private StudentProfileRepository profileRepository;
    @Mock
    private EnrollmentPersister persister;
    @Mock
    private ClassGroupDirectory classGroupDirectory;
    @Mock
    private EnrollmentChangePublisher changePublisher;

    private EnrollmentService service() {
        return new EnrollmentService(enrollmentRepository, profileRepository, persister, classGroupDirectory,
                changePublisher, FIXED_CLOCK);
    }

    // ------------------------------------------------------------------
    // Fabriques
    // ------------------------------------------------------------------

    private static StudentProfile profile(long id) {
        StudentProfile p = new StudentProfile(1_000L, "ESIC-2026-000001", null, false, null);
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "publicId", UUID.randomUUID());
        return p;
    }

    private static Enrollment enrollment(StudentProfile p, long id, long classId, long yearId, LocalDate start) {
        Enrollment e = new Enrollment(p, classId, yearId, start, EnrollmentSource.MANUAL, null, null);
        ReflectionTestUtils.setField(e, "id", id);
        ReflectionTestUtils.setField(e, "publicId", UUID.randomUUID());
        return e;
    }

    private static ClassGroupDirectory.ClassGroupRef classRef(long internalId, long yearId, boolean open) {
        return new ClassGroupDirectory.ClassGroupRef(internalId, UUID.randomUUID(), "BTS-SIO-1",
                UUID.randomUUID(), "BTS-SIO", yearId, UUID.randomUUID(), "2026-2027", open);
    }

    private static EnrollmentRequests.Enroll enrollRequest(UUID profilePublicId, UUID classPublicId, LocalDate start) {
        return new EnrollmentRequests.Enroll(profilePublicId.toString(), classPublicId.toString(), start);
    }

    // ------------------------------------------------------------------
    // enroll
    // ------------------------------------------------------------------

    @Test
    void enrollRejectsUnknownProfile() {
        UUID profileId = UUID.randomUUID();
        when(profileRepository.findByPublicId(profileId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().enroll(enrollRequest(profileId, UUID.randomUUID(), null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.STUDENT_PROFILE_NOT_FOUND);
    }

    @Test
    void enrollRejectsArchivedProfile() {
        StudentProfile p = profile(1L);
        ReflectionTestUtils.setField(p, "status", StudentProfileStatus.ARCHIVED);
        when(profileRepository.findByPublicId(p.getPublicId())).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service().enroll(enrollRequest(p.getPublicId(), UUID.randomUUID(), null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.STUDENT_PROFILE_ARCHIVED);
    }

    @Test
    void enrollRejectsUnknownClass() {
        StudentProfile p = profile(1L);
        UUID classId = UUID.randomUUID();
        when(profileRepository.findByPublicId(p.getPublicId())).thenReturn(Optional.of(p));
        when(classGroupDirectory.findByPublicId(classId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().enroll(enrollRequest(p.getPublicId(), classId, null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.CLASS_GROUP_NOT_FOUND);
    }

    @Test
    void enrollRejectsArchivedClassChain() {
        StudentProfile p = profile(1L);
        UUID classId = UUID.randomUUID();
        when(profileRepository.findByPublicId(p.getPublicId())).thenReturn(Optional.of(p));
        when(classGroupDirectory.findByPublicId(classId)).thenReturn(Optional.of(classRef(10L, 50L, false)));
        assertThatThrownBy(() -> service().enroll(enrollRequest(p.getPublicId(), classId, null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.ARCHIVED_PARENT);
    }

    @Test
    void enrollRejectsWhenActiveEnrollmentAlreadyExistsForYear() {
        StudentProfile p = profile(1L);
        UUID classId = UUID.randomUUID();
        when(profileRepository.findByPublicId(p.getPublicId())).thenReturn(Optional.of(p));
        when(classGroupDirectory.findByPublicId(classId)).thenReturn(Optional.of(classRef(10L, 50L, true)));
        when(enrollmentRepository.existsByStudentProfileIdAndAcademicYearIdAndStatus(1L, 50L, EnrollmentStatus.ACTIVE))
                .thenReturn(true);
        assertThatThrownBy(() -> service().enroll(enrollRequest(p.getPublicId(), classId, null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.ACTIVE_ENROLLMENT_EXISTS);
        verify(changePublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void enrollDefaultsStartDateToClockAndPublishesEvent() {
        StudentProfile p = profile(1L);
        UUID classId = UUID.randomUUID();
        when(profileRepository.findByPublicId(p.getPublicId())).thenReturn(Optional.of(p));
        when(classGroupDirectory.findByPublicId(classId)).thenReturn(Optional.of(classRef(10L, 50L, true)));
        when(enrollmentRepository.existsByStudentProfileIdAndAcademicYearIdAndStatus(1L, 50L, EnrollmentStatus.ACTIVE))
                .thenReturn(false);
        when(changePublisher.actorId("caller")).thenReturn(42L);
        when(persister.persist(any(Enrollment.class))).thenAnswer(inv -> withPublicId(inv.getArgument(0)));

        EnrollmentResponse response = service().enroll(enrollRequest(p.getPublicId(), classId, null), "caller");

        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(persister).persist(captor.capture());
        assertThat(captor.getValue().getStartDate()).isEqualTo(TODAY);
        assertThat(captor.getValue().getEnrollmentSource()).isEqualTo(EnrollmentSource.MANUAL);
        assertThat(captor.getValue().getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(response.status()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(response.previousEnrollmentPublicId()).isNull();
        verify(changePublisher).publish(eq(EnrollmentResourceType.ENROLLMENT), any(),
                eq(EnrollmentChangeAction.CREATED), eq(42L), any());
    }

    @Test
    void enrollUsesProvidedStartDate() {
        StudentProfile p = profile(1L);
        UUID classId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 9, 1);
        when(profileRepository.findByPublicId(p.getPublicId())).thenReturn(Optional.of(p));
        when(classGroupDirectory.findByPublicId(classId)).thenReturn(Optional.of(classRef(10L, 50L, true)));
        when(enrollmentRepository.existsByStudentProfileIdAndAcademicYearIdAndStatus(1L, 50L, EnrollmentStatus.ACTIVE))
                .thenReturn(false);
        when(persister.persist(any(Enrollment.class))).thenAnswer(inv -> withPublicId(inv.getArgument(0)));

        service().enroll(enrollRequest(p.getPublicId(), classId, start), null);

        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(persister).persist(captor.capture());
        assertThat(captor.getValue().getStartDate()).isEqualTo(start);
    }

    @Test
    void enrollTranslatesActivePerYearCollisionInto409() {
        StudentProfile p = profile(1L);
        UUID classId = UUID.randomUUID();
        when(profileRepository.findByPublicId(p.getPublicId())).thenReturn(Optional.of(p));
        when(classGroupDirectory.findByPublicId(classId)).thenReturn(Optional.of(classRef(10L, 50L, true)));
        when(enrollmentRepository.existsByStudentProfileIdAndAcademicYearIdAndStatus(1L, 50L, EnrollmentStatus.ACTIVE))
                .thenReturn(false);
        when(persister.persist(any(Enrollment.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement; Duplicate entry '1-50' for key 'uq_enrollment_active_per_year'"));

        assertThatThrownBy(() -> service().enroll(enrollRequest(p.getPublicId(), classId, null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.ACTIVE_ENROLLMENT_EXISTS);
        verify(changePublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void enrollRethrowsUnrelatedIntegrityViolationUnchanged() {
        StudentProfile p = profile(1L);
        UUID classId = UUID.randomUUID();
        DataIntegrityViolationException unrelated = new DataIntegrityViolationException(
                "Duplicate entry 'x' for key 'uq_enrollment_public_id'");
        when(profileRepository.findByPublicId(p.getPublicId())).thenReturn(Optional.of(p));
        when(classGroupDirectory.findByPublicId(classId)).thenReturn(Optional.of(classRef(10L, 50L, true)));
        when(enrollmentRepository.existsByStudentProfileIdAndAcademicYearIdAndStatus(1L, 50L, EnrollmentStatus.ACTIVE))
                .thenReturn(false);
        when(persister.persist(any(Enrollment.class))).thenThrow(unrelated);

        assertThatThrownBy(() -> service().enroll(enrollRequest(p.getPublicId(), classId, null), null))
                .isSameAs(unrelated);
    }

    // ------------------------------------------------------------------
    // transfer
    // ------------------------------------------------------------------

    @Test
    void transferRejectsNonActiveEnrollment() {
        StudentProfile p = profile(1L);
        Enrollment current = enrollment(p, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
        ReflectionTestUtils.setField(current, "status", EnrollmentStatus.TRANSFERRED);
        when(enrollmentRepository.findByPublicId(current.getPublicId())).thenReturn(Optional.of(current));
        assertThatThrownBy(() -> service().transfer(current.getPublicId(),
                new EnrollmentRequests.Transfer(UUID.randomUUID().toString(), "changement", null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.ENROLLMENT_NOT_ACTIVE);
    }

    @Test
    void transferRejectsSameClass() {
        StudentProfile p = profile(1L);
        Enrollment current = enrollment(p, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
        UUID targetPublicId = UUID.randomUUID();
        when(enrollmentRepository.findByPublicId(current.getPublicId())).thenReturn(Optional.of(current));
        when(classGroupDirectory.findByPublicId(targetPublicId)).thenReturn(Optional.of(classRef(10L, 50L, true)));
        assertThatThrownBy(() -> service().transfer(current.getPublicId(),
                new EnrollmentRequests.Transfer(targetPublicId.toString(), "changement", null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.SAME_CLASS);
    }

    @Test
    void transferRejectsEffectiveDateBeforeStart() {
        StudentProfile p = profile(1L);
        Enrollment current = enrollment(p, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
        UUID targetPublicId = UUID.randomUUID();
        when(enrollmentRepository.findByPublicId(current.getPublicId())).thenReturn(Optional.of(current));
        when(classGroupDirectory.findByPublicId(targetPublicId)).thenReturn(Optional.of(classRef(20L, 50L, true)));
        assertThatThrownBy(() -> service().transfer(current.getPublicId(),
                new EnrollmentRequests.Transfer(targetPublicId.toString(), "changement", LocalDate.of(2026, 5, 1)),
                null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.DATE_INVALID);
    }

    @Test
    void transferRejectsActiveEnrollmentInTargetYear() {
        StudentProfile p = profile(1L);
        Enrollment current = enrollment(p, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
        UUID targetPublicId = UUID.randomUUID();
        when(enrollmentRepository.findByPublicId(current.getPublicId())).thenReturn(Optional.of(current));
        when(classGroupDirectory.findByPublicId(targetPublicId)).thenReturn(Optional.of(classRef(20L, 60L, true)));
        when(enrollmentRepository.existsByStudentProfileIdAndAcademicYearIdAndStatus(1L, 60L, EnrollmentStatus.ACTIVE))
                .thenReturn(true);
        assertThatThrownBy(() -> service().transfer(current.getPublicId(),
                new EnrollmentRequests.Transfer(targetPublicId.toString(), "changement", null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.ACTIVE_ENROLLMENT_EXISTS);
    }

    @Test
    void transferClosesCurrentAndCreatesLinkedEnrollment() {
        StudentProfile p = profile(1L);
        Enrollment current = enrollment(p, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
        UUID targetPublicId = UUID.randomUUID();
        ClassGroupDirectory.ClassGroupRef targetRef = classRef(20L, 50L, true);
        when(enrollmentRepository.findByPublicId(current.getPublicId())).thenReturn(Optional.of(current));
        when(classGroupDirectory.findByPublicId(targetPublicId)).thenReturn(Optional.of(targetRef));
        when(classGroupDirectory.findByInternalId(10L)).thenReturn(Optional.of(classRef(10L, 50L, true)));
        when(changePublisher.actorId("caller")).thenReturn(7L);
        when(enrollmentRepository.saveAndFlush(any(Enrollment.class))).thenAnswer(inv -> withPublicId(inv.getArgument(0)));

        EnrollmentResponse response = service().transfer(current.getPublicId(),
                new EnrollmentRequests.Transfer(targetPublicId.toString(), "  mutation  ", null), "caller");

        assertThat(current.getStatus()).isEqualTo(EnrollmentStatus.TRANSFERRED);
        assertThat(current.getEndDate()).isEqualTo(TODAY);
        assertThat(current.getChangeReason()).isEqualTo("mutation");

        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        Enrollment next = captor.getAllValues().get(1);
        assertThat(next.getEnrollmentSource()).isEqualTo(EnrollmentSource.CLASS_TRANSFER);
        assertThat(next.getPreviousEnrollmentId()).isEqualTo(500L);
        assertThat(next.getClassGroupId()).isEqualTo(20L);
        // `end_date` de l'ancienne inscription est inclusif : la nouvelle
        // débute le lendemain, sans chevauchement de période.
        assertThat(next.getStartDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(next.getStartDate()).isAfter(current.getEndDate());

        assertThat(response.enrollmentSource()).isEqualTo(EnrollmentSource.CLASS_TRANSFER);
        assertThat(response.previousEnrollmentPublicId()).isEqualTo(current.getPublicId());
        verify(changePublisher).publish(eq(EnrollmentResourceType.ENROLLMENT), eq(current.getPublicId()),
                eq(EnrollmentChangeAction.TRANSFERRED), eq(7L), any());
        verify(changePublisher).publish(eq(EnrollmentResourceType.ENROLLMENT), eq(next.getPublicId()),
                eq(EnrollmentChangeAction.CREATED), eq(7L), any());
    }

    @Test
    void transferStartsNewEnrollmentTheDayAfterInclusiveEndDate() {
        StudentProfile p = profile(1L);
        Enrollment current = enrollment(p, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
        UUID targetPublicId = UUID.randomUUID();
        ClassGroupDirectory.ClassGroupRef targetRef = classRef(20L, 50L, true);
        LocalDate effectiveDate = LocalDate.of(2026, 9, 30);
        when(enrollmentRepository.findByPublicId(current.getPublicId())).thenReturn(Optional.of(current));
        when(classGroupDirectory.findByPublicId(targetPublicId)).thenReturn(Optional.of(targetRef));
        when(classGroupDirectory.findByInternalId(10L)).thenReturn(Optional.of(classRef(10L, 50L, true)));
        when(enrollmentRepository.saveAndFlush(any(Enrollment.class))).thenAnswer(inv -> withPublicId(inv.getArgument(0)));

        EnrollmentResponse response = service().transfer(current.getPublicId(),
                new EnrollmentRequests.Transfer(targetPublicId.toString(), "mutation", effectiveDate), null);

        assertThat(current.getEndDate()).isEqualTo(effectiveDate);

        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        Enrollment next = captor.getAllValues().get(1);
        assertThat(next.getStartDate()).isEqualTo(effectiveDate.plusDays(1));
        // Aucun jour commun entre l'ancienne (…→ end_date inclus) et la
        // nouvelle (start_date →…).
        assertThat(next.getStartDate()).isAfter(current.getEndDate());
        assertThat(response.startDate()).isEqualTo(effectiveDate.plusDays(1));
    }

    // ------------------------------------------------------------------
    // close
    // ------------------------------------------------------------------

    @Test
    void closeRejectsNonActiveEnrollment() {
        StudentProfile p = profile(1L);
        Enrollment current = enrollment(p, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
        ReflectionTestUtils.setField(current, "status", EnrollmentStatus.COMPLETED);
        when(enrollmentRepository.findByPublicId(current.getPublicId())).thenReturn(Optional.of(current));
        assertThatThrownBy(() -> service().close(current.getPublicId(),
                new EnrollmentRequests.Close("WITHDRAWN", "fin", null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.ENROLLMENT_NOT_ACTIVE);
    }

    @Test
    void closeRejectsInvalidStatus() {
        StudentProfile p = profile(1L);
        Enrollment current = enrollment(p, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
        when(enrollmentRepository.findByPublicId(current.getPublicId())).thenReturn(Optional.of(current));
        assertThatThrownBy(() -> service().close(current.getPublicId(),
                new EnrollmentRequests.Close("SUSPENDED", "fin", null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.INVALID_CLOSE_STATUS);
    }

    @Test
    void closeRejectsEffectiveDateBeforeStart() {
        StudentProfile p = profile(1L);
        Enrollment current = enrollment(p, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
        when(enrollmentRepository.findByPublicId(current.getPublicId())).thenReturn(Optional.of(current));
        assertThatThrownBy(() -> service().close(current.getPublicId(),
                new EnrollmentRequests.Close("COMPLETED", "fin", LocalDate.of(2026, 5, 1)), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.DATE_INVALID);
    }

    @Test
    void closeCompletesEnrollmentAndPublishesEvent() {
        StudentProfile p = profile(1L);
        Enrollment current = enrollment(p, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
        when(enrollmentRepository.findByPublicId(current.getPublicId())).thenReturn(Optional.of(current));
        when(classGroupDirectory.findByInternalId(10L)).thenReturn(Optional.of(classRef(10L, 50L, true)));
        when(changePublisher.actorId("caller")).thenReturn(7L);

        EnrollmentResponse response = service().close(current.getPublicId(),
                new EnrollmentRequests.Close("COMPLETED", "diplômé", null), "caller");

        assertThat(current.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(current.getEndDate()).isEqualTo(TODAY);
        assertThat(response.status()).isEqualTo(EnrollmentStatus.COMPLETED);
        verify(changePublisher).publish(eq(EnrollmentResourceType.ENROLLMENT), eq(current.getPublicId()),
                eq(EnrollmentChangeAction.CLOSED), eq(7L), any());
    }

    @Test
    void listRejectsSortOutsideWhitelist() {
        assertThatThrownBy(() -> service().list(null, null, null, 0, 20, "classGroupId,asc"))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.INVALID_SORT);
    }

    // ------------------------------------------------------------------

    private static Enrollment withPublicId(Enrollment e) {
        if (e.getPublicId() == null) {
            ReflectionTestUtils.setField(e, "publicId", UUID.randomUUID());
        }
        return e;
    }
}
