package com.esic.connect.enrollment.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.enrollment.EnrollmentChangeAction;
import com.esic.connect.enrollment.EnrollmentResourceType;
import com.esic.connect.identity.UserDirectory;
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
import java.util.Set;
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
 * compte cible inconnu / archivé / sans rôle {@code STUDENT}, classe
 * inconnue / archivée, unicité d'une inscription active par année
 * (RG-012), changement de classe (clôture {@code TRANSFERRED} + nouvelle
 * inscription liée), clôture, garde-fous de dates.
 *
 * <p>Une inscription rattache directement un <strong>compte</strong>
 * (refonte 2026-09), résolu via {@link UserDirectory} — jamais un profil
 * apprenant : aucun de ces tests ne dépend de {@link StudentProfile}.
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTests {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-15T09:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final long USER_INTERNAL_ID = 1_000L;

    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private StudentProfileRepository profileRepository;
    @Mock
    private EnrollmentPersister persister;
    @Mock
    private ClassGroupDirectory classGroupDirectory;
    @Mock
    private UserDirectory userDirectory;
    @Mock
    private EnrollmentChangePublisher changePublisher;
    @Mock
    private RosterScopeResolver rosterScope;

    private EnrollmentService service() {
        return new EnrollmentService(enrollmentRepository, profileRepository, persister, classGroupDirectory,
                userDirectory, changePublisher, rosterScope, FIXED_CLOCK);
    }

    // ------------------------------------------------------------------
    // Fabriques
    // ------------------------------------------------------------------

    private static UserDirectory.UserRef studentRef(UUID publicId) {
        return new UserDirectory.UserRef(USER_INTERNAL_ID, publicId, false, Set.of("STUDENT"));
    }

    private static Enrollment enrollment(long userId, long id, long classId, long yearId, LocalDate start) {
        Enrollment e = new Enrollment(userId, classId, yearId, start, EnrollmentSource.MANUAL, null, null);
        ReflectionTestUtils.setField(e, "id", id);
        ReflectionTestUtils.setField(e, "publicId", UUID.randomUUID());
        return e;
    }

    private static ClassGroupDirectory.ClassGroupRef classRef(long internalId, long yearId, boolean open) {
        return new ClassGroupDirectory.ClassGroupRef(internalId, UUID.randomUUID(), "BTS-SIO-1",
                UUID.randomUUID(), "BTS-SIO", yearId, UUID.randomUUID(), "2026-2027", open);
    }

    private static EnrollmentRequests.Enroll enrollRequest(UUID userPublicId, UUID classPublicId, LocalDate start) {
        return new EnrollmentRequests.Enroll(userPublicId.toString(), classPublicId.toString(), start);
    }

    // ------------------------------------------------------------------
    // enroll
    // ------------------------------------------------------------------

    @Test
    void enrollRejectsUnknownUser() {
        UUID userId = UUID.randomUUID();
        when(userDirectory.findByPublicId(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().enroll(enrollRequest(userId, UUID.randomUUID(), null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.USER_NOT_ELIGIBLE);
    }

    @Test
    void enrollRejectsArchivedUser() {
        UUID userId = UUID.randomUUID();
        UserDirectory.UserRef archived = new UserDirectory.UserRef(USER_INTERNAL_ID, userId, true, Set.of("STUDENT"));
        when(userDirectory.findByPublicId(userId)).thenReturn(Optional.of(archived));
        assertThatThrownBy(() -> service().enroll(enrollRequest(userId, UUID.randomUUID(), null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.USER_NOT_ELIGIBLE);
    }

    @Test
    void enrollRejectsUserWithoutActiveStudentRole() {
        UUID userId = UUID.randomUUID();
        UserDirectory.UserRef teacher = new UserDirectory.UserRef(USER_INTERNAL_ID, userId, false, Set.of("TEACHER"));
        when(userDirectory.findByPublicId(userId)).thenReturn(Optional.of(teacher));
        assertThatThrownBy(() -> service().enroll(enrollRequest(userId, UUID.randomUUID(), null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.USER_NOT_ELIGIBLE);
    }

    @Test
    void enrollRejectsUnknownClass() {
        UUID userId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        when(userDirectory.findByPublicId(userId)).thenReturn(Optional.of(studentRef(userId)));
        when(classGroupDirectory.findByPublicId(classId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().enroll(enrollRequest(userId, classId, null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.CLASS_GROUP_NOT_FOUND);
    }

    @Test
    void enrollRejectsArchivedClassChain() {
        UUID userId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        when(userDirectory.findByPublicId(userId)).thenReturn(Optional.of(studentRef(userId)));
        when(classGroupDirectory.findByPublicId(classId)).thenReturn(Optional.of(classRef(10L, 50L, false)));
        assertThatThrownBy(() -> service().enroll(enrollRequest(userId, classId, null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.ARCHIVED_PARENT);
    }

    @Test
    void enrollRejectsWhenActiveEnrollmentAlreadyExistsForYear() {
        UUID userId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        when(userDirectory.findByPublicId(userId)).thenReturn(Optional.of(studentRef(userId)));
        when(classGroupDirectory.findByPublicId(classId)).thenReturn(Optional.of(classRef(10L, 50L, true)));
        when(enrollmentRepository.existsByUserIdAndAcademicYearIdAndStatus(USER_INTERNAL_ID, 50L,
                EnrollmentStatus.ACTIVE)).thenReturn(true);
        assertThatThrownBy(() -> service().enroll(enrollRequest(userId, classId, null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.ACTIVE_ENROLLMENT_EXISTS);
        verify(changePublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void enrollDefaultsStartDateToClockAndPublishesEvent() {
        UUID userId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        when(userDirectory.findByPublicId(userId)).thenReturn(Optional.of(studentRef(userId)));
        when(classGroupDirectory.findByPublicId(classId)).thenReturn(Optional.of(classRef(10L, 50L, true)));
        when(enrollmentRepository.existsByUserIdAndAcademicYearIdAndStatus(USER_INTERNAL_ID, 50L,
                EnrollmentStatus.ACTIVE)).thenReturn(false);
        when(changePublisher.actorId("caller")).thenReturn(42L);
        when(persister.persist(any(Enrollment.class))).thenAnswer(inv -> withPublicId(inv.getArgument(0)));

        EnrollmentResponse response = service().enroll(enrollRequest(userId, classId, null), "caller");

        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(persister).persist(captor.capture());
        assertThat(captor.getValue().getStartDate()).isEqualTo(TODAY);
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_INTERNAL_ID);
        assertThat(captor.getValue().getEnrollmentSource()).isEqualTo(EnrollmentSource.MANUAL);
        assertThat(captor.getValue().getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(response.status()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(response.studentUserPublicId()).isEqualTo(userId);
        assertThat(response.studentProfilePublicId()).isNull();
        assertThat(response.previousEnrollmentPublicId()).isNull();
        verify(changePublisher).publish(eq(EnrollmentResourceType.ENROLLMENT), any(),
                eq(EnrollmentChangeAction.CREATED), eq(42L), any());
    }

    @Test
    void enrollUsesProvidedStartDate() {
        UUID userId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 9, 1);
        when(userDirectory.findByPublicId(userId)).thenReturn(Optional.of(studentRef(userId)));
        when(classGroupDirectory.findByPublicId(classId)).thenReturn(Optional.of(classRef(10L, 50L, true)));
        when(enrollmentRepository.existsByUserIdAndAcademicYearIdAndStatus(USER_INTERNAL_ID, 50L,
                EnrollmentStatus.ACTIVE)).thenReturn(false);
        when(persister.persist(any(Enrollment.class))).thenAnswer(inv -> withPublicId(inv.getArgument(0)));

        service().enroll(enrollRequest(userId, classId, start), null);

        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(persister).persist(captor.capture());
        assertThat(captor.getValue().getStartDate()).isEqualTo(start);
    }

    @Test
    void enrollTranslatesActivePerYearCollisionInto409() {
        UUID userId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        when(userDirectory.findByPublicId(userId)).thenReturn(Optional.of(studentRef(userId)));
        when(classGroupDirectory.findByPublicId(classId)).thenReturn(Optional.of(classRef(10L, 50L, true)));
        when(enrollmentRepository.existsByUserIdAndAcademicYearIdAndStatus(USER_INTERNAL_ID, 50L,
                EnrollmentStatus.ACTIVE)).thenReturn(false);
        when(persister.persist(any(Enrollment.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement; Duplicate entry '1-50' for key 'uq_enrollment_active_per_year'"));

        assertThatThrownBy(() -> service().enroll(enrollRequest(userId, classId, null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.ACTIVE_ENROLLMENT_EXISTS);
        verify(changePublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void enrollRethrowsUnrelatedIntegrityViolationUnchanged() {
        UUID userId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        DataIntegrityViolationException unrelated = new DataIntegrityViolationException(
                "Duplicate entry 'x' for key 'uq_enrollment_public_id'");
        when(userDirectory.findByPublicId(userId)).thenReturn(Optional.of(studentRef(userId)));
        when(classGroupDirectory.findByPublicId(classId)).thenReturn(Optional.of(classRef(10L, 50L, true)));
        when(enrollmentRepository.existsByUserIdAndAcademicYearIdAndStatus(USER_INTERNAL_ID, 50L,
                EnrollmentStatus.ACTIVE)).thenReturn(false);
        when(persister.persist(any(Enrollment.class))).thenThrow(unrelated);

        assertThatThrownBy(() -> service().enroll(enrollRequest(userId, classId, null), null))
                .isSameAs(unrelated);
    }

    // ------------------------------------------------------------------
    // transfer
    // ------------------------------------------------------------------

    @Test
    void transferRejectsNonActiveEnrollment() {
        Enrollment current = enrollment(USER_INTERNAL_ID, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
        ReflectionTestUtils.setField(current, "status", EnrollmentStatus.TRANSFERRED);
        when(enrollmentRepository.findByPublicId(current.getPublicId())).thenReturn(Optional.of(current));
        assertThatThrownBy(() -> service().transfer(current.getPublicId(),
                new EnrollmentRequests.Transfer(UUID.randomUUID().toString(), "changement", null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.ENROLLMENT_NOT_ACTIVE);
    }

    @Test
    void transferRejectsSameClass() {
        Enrollment current = enrollment(USER_INTERNAL_ID, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
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
        Enrollment current = enrollment(USER_INTERNAL_ID, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
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
        Enrollment current = enrollment(USER_INTERNAL_ID, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
        UUID targetPublicId = UUID.randomUUID();
        when(enrollmentRepository.findByPublicId(current.getPublicId())).thenReturn(Optional.of(current));
        when(classGroupDirectory.findByPublicId(targetPublicId)).thenReturn(Optional.of(classRef(20L, 60L, true)));
        when(enrollmentRepository.existsByUserIdAndAcademicYearIdAndStatus(USER_INTERNAL_ID, 60L,
                EnrollmentStatus.ACTIVE)).thenReturn(true);
        assertThatThrownBy(() -> service().transfer(current.getPublicId(),
                new EnrollmentRequests.Transfer(targetPublicId.toString(), "changement", null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.ACTIVE_ENROLLMENT_EXISTS);
    }

    @Test
    void transferClosesCurrentAndCreatesLinkedEnrollment() {
        Enrollment current = enrollment(USER_INTERNAL_ID, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
        UUID targetPublicId = UUID.randomUUID();
        UUID userPublicId = UUID.randomUUID();
        ClassGroupDirectory.ClassGroupRef targetRef = classRef(20L, 50L, true);
        when(enrollmentRepository.findByPublicId(current.getPublicId())).thenReturn(Optional.of(current));
        when(classGroupDirectory.findByPublicId(targetPublicId)).thenReturn(Optional.of(targetRef));
        when(classGroupDirectory.findByInternalId(10L)).thenReturn(Optional.of(classRef(10L, 50L, true)));
        when(userDirectory.findByInternalId(USER_INTERNAL_ID)).thenReturn(Optional.of(studentRef(userPublicId)));
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
        assertThat(next.getUserId()).isEqualTo(USER_INTERNAL_ID);
        assertThat(next.getPreviousEnrollmentId()).isEqualTo(500L);
        assertThat(next.getClassGroupId()).isEqualTo(20L);
        // `end_date` de l'ancienne inscription est inclusif : la nouvelle
        // débute le lendemain, sans chevauchement de période.
        assertThat(next.getStartDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(next.getStartDate()).isAfter(current.getEndDate());

        assertThat(response.enrollmentSource()).isEqualTo(EnrollmentSource.CLASS_TRANSFER);
        assertThat(response.studentUserPublicId()).isEqualTo(userPublicId);
        assertThat(response.previousEnrollmentPublicId()).isEqualTo(current.getPublicId());
        verify(changePublisher).publish(eq(EnrollmentResourceType.ENROLLMENT), eq(current.getPublicId()),
                eq(EnrollmentChangeAction.TRANSFERRED), eq(7L), any());
        verify(changePublisher).publish(eq(EnrollmentResourceType.ENROLLMENT), eq(next.getPublicId()),
                eq(EnrollmentChangeAction.CREATED), eq(7L), any());
    }

    @Test
    void transferStartsNewEnrollmentTheDayAfterInclusiveEndDate() {
        Enrollment current = enrollment(USER_INTERNAL_ID, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
        UUID targetPublicId = UUID.randomUUID();
        UUID userPublicId = UUID.randomUUID();
        ClassGroupDirectory.ClassGroupRef targetRef = classRef(20L, 50L, true);
        LocalDate effectiveDate = LocalDate.of(2026, 9, 30);
        when(enrollmentRepository.findByPublicId(current.getPublicId())).thenReturn(Optional.of(current));
        when(classGroupDirectory.findByPublicId(targetPublicId)).thenReturn(Optional.of(targetRef));
        when(classGroupDirectory.findByInternalId(10L)).thenReturn(Optional.of(classRef(10L, 50L, true)));
        when(userDirectory.findByInternalId(USER_INTERNAL_ID)).thenReturn(Optional.of(studentRef(userPublicId)));
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
        Enrollment current = enrollment(USER_INTERNAL_ID, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
        ReflectionTestUtils.setField(current, "status", EnrollmentStatus.COMPLETED);
        when(enrollmentRepository.findByPublicId(current.getPublicId())).thenReturn(Optional.of(current));
        assertThatThrownBy(() -> service().close(current.getPublicId(),
                new EnrollmentRequests.Close("WITHDRAWN", "fin", null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.ENROLLMENT_NOT_ACTIVE);
    }

    @Test
    void closeRejectsInvalidStatus() {
        Enrollment current = enrollment(USER_INTERNAL_ID, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
        when(enrollmentRepository.findByPublicId(current.getPublicId())).thenReturn(Optional.of(current));
        assertThatThrownBy(() -> service().close(current.getPublicId(),
                new EnrollmentRequests.Close("SUSPENDED", "fin", null), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.INVALID_CLOSE_STATUS);
    }

    @Test
    void closeRejectsEffectiveDateBeforeStart() {
        Enrollment current = enrollment(USER_INTERNAL_ID, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
        when(enrollmentRepository.findByPublicId(current.getPublicId())).thenReturn(Optional.of(current));
        assertThatThrownBy(() -> service().close(current.getPublicId(),
                new EnrollmentRequests.Close("COMPLETED", "fin", LocalDate.of(2026, 5, 1)), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.DATE_INVALID);
    }

    @Test
    void closeCompletesEnrollmentAndPublishesEvent() {
        Enrollment current = enrollment(USER_INTERNAL_ID, 500L, 10L, 50L, LocalDate.of(2026, 6, 1));
        UUID userPublicId = UUID.randomUUID();
        when(enrollmentRepository.findByPublicId(current.getPublicId())).thenReturn(Optional.of(current));
        when(classGroupDirectory.findByInternalId(10L)).thenReturn(Optional.of(classRef(10L, 50L, true)));
        when(userDirectory.findByInternalId(USER_INTERNAL_ID)).thenReturn(Optional.of(studentRef(userPublicId)));
        when(changePublisher.actorId("caller")).thenReturn(7L);

        EnrollmentResponse response = service().close(current.getPublicId(),
                new EnrollmentRequests.Close("COMPLETED", "diplômé", null), "caller");

        assertThat(current.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(current.getEndDate()).isEqualTo(TODAY);
        assertThat(response.status()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(response.studentUserPublicId()).isEqualTo(userPublicId);
        verify(changePublisher).publish(eq(EnrollmentResourceType.ENROLLMENT), eq(current.getPublicId()),
                eq(EnrollmentChangeAction.CLOSED), eq(7L), any());
    }

    @Test
    void listRejectsSortOutsideWhitelist() {
        assertThatThrownBy(() -> service().list(null, null, null, 0, 20, "classGroupId,asc", null))
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
