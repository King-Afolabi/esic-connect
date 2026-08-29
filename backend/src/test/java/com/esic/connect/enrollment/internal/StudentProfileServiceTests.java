package com.esic.connect.enrollment.internal;

import com.esic.connect.enrollment.EnrollmentChangeAction;
import com.esic.connect.enrollment.EnrollmentResourceType;
import com.esic.connect.identity.UserDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validations métier des profils apprenants, isolées des I/O : compte
 * cible inconnu / archivé / sans rôle {@code STUDENT}, numéro étudiant
 * dupliqué, profil déjà existant, création publiée, tri hors liste
 * blanche.
 */
@ExtendWith(MockitoExtension.class)
class StudentProfileServiceTests {

    @Mock
    private StudentProfileRepository profileRepository;
    @Mock
    private UserDirectory userDirectory;
    @Mock
    private EnrollmentChangePublisher changePublisher;

    private StudentProfileService service() {
        return new StudentProfileService(profileRepository, userDirectory, changePublisher);
    }

    private static StudentProfileRequests.Create create(UUID userPublicId, String studentNumber) {
        return new StudentProfileRequests.Create(userPublicId.toString(), studentNumber,
                LocalDate.of(2004, 5, 1), Boolean.TRUE, "  ACME  ");
    }

    private static UserDirectory.UserRef student(long id) {
        return new UserDirectory.UserRef(id, UUID.randomUUID(), false, Set.of("STUDENT"));
    }

    @Test
    void createRejectsUnknownUser() {
        UUID user = UUID.randomUUID();
        when(userDirectory.findByPublicId(user)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().create(create(user, "ESIC-2026-000001"), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.USER_NOT_ELIGIBLE);
        verify(changePublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void createRejectsArchivedUser() {
        UUID user = UUID.randomUUID();
        when(userDirectory.findByPublicId(user))
                .thenReturn(Optional.of(new UserDirectory.UserRef(7L, user, true, Set.of("STUDENT"))));
        assertThatThrownBy(() -> service().create(create(user, "ESIC-2026-000001"), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.USER_NOT_ELIGIBLE);
    }

    @Test
    void createRejectsUserWithoutStudentRole() {
        UUID user = UUID.randomUUID();
        when(userDirectory.findByPublicId(user))
                .thenReturn(Optional.of(new UserDirectory.UserRef(7L, user, false, Set.of("TEACHER"))));
        assertThatThrownBy(() -> service().create(create(user, "ESIC-2026-000001"), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.USER_NOT_ELIGIBLE);
    }

    @Test
    void createRejectsDuplicateStudentNumber() {
        UUID user = UUID.randomUUID();
        when(userDirectory.findByPublicId(user)).thenReturn(Optional.of(student(7L)));
        when(profileRepository.existsByStudentNumberIgnoreCase("ESIC-2026-000001")).thenReturn(true);
        assertThatThrownBy(() -> service().create(create(user, "ESIC-2026-000001"), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.DUPLICATE_STUDENT_NUMBER);
    }

    @Test
    void createRejectsExistingProfileForUser() {
        UUID user = UUID.randomUUID();
        when(userDirectory.findByPublicId(user)).thenReturn(Optional.of(student(7L)));
        when(profileRepository.existsByStudentNumberIgnoreCase("ESIC-2026-000001")).thenReturn(false);
        when(profileRepository.existsByUserId(7L)).thenReturn(true);
        assertThatThrownBy(() -> service().create(create(user, "ESIC-2026-000001"), null))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.PROFILE_ALREADY_EXISTS);
    }

    @Test
    void createPersistsTrimsCompanyAndPublishesEvent() {
        UUID user = UUID.randomUUID();
        UserDirectory.UserRef ref = student(7L);
        when(userDirectory.findByPublicId(user)).thenReturn(Optional.of(ref));
        when(profileRepository.existsByStudentNumberIgnoreCase("ESIC-2026-000001")).thenReturn(false);
        when(profileRepository.existsByUserId(7L)).thenReturn(false);
        when(changePublisher.actorId("caller")).thenReturn(99L);
        when(profileRepository.saveAndFlush(any(StudentProfile.class))).thenAnswer(inv -> {
            StudentProfile p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "publicId", UUID.randomUUID());
            return p;
        });

        StudentProfileResponse response = service().create(create(user, "ESIC-2026-000001"), "caller");

        assertThat(response.userPublicId()).isEqualTo(ref.publicId());
        assertThat(response.studentNumber()).isEqualTo("ESIC-2026-000001");
        assertThat(response.companyName()).isEqualTo("ACME");
        assertThat(response.workStudy()).isTrue();
        assertThat(response.status()).isEqualTo(StudentProfileStatus.ACTIVE);
        verify(changePublisher).publish(eq(EnrollmentResourceType.STUDENT_PROFILE), any(),
                eq(EnrollmentChangeAction.CREATED), eq(99L), isNull());
    }

    @Test
    void listRejectsSortOutsideWhitelist() {
        assertThatThrownBy(() -> service().list(null, null, null, 0, 20, "userId,asc"))
                .extracting(ex -> ((EnrollmentException) ex).kind())
                .isEqualTo(EnrollmentException.Kind.INVALID_SORT);
    }
}
