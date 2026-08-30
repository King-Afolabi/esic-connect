package com.esic.connect.enrollment.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.enrollment.EnrollmentDirectory;
import com.esic.connect.identity.UserDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implémentation du port {@link EnrollmentDirectory}. Reste confinée à
 * {@code enrollment.internal} : les autres modules ne connaissent que
 * l'interface publique et le {@link EnrollmentDirectory.EnrollmentRef}.
 *
 * <p>La classe et l'année scolaire sont résolues via le port
 * {@link ClassGroupDirectory} (déjà consommé par ce module) à partir de
 * la valeur technique {@code enrollment.class_group_id} — aucun partage
 * d'entité JPA avec {@code academic}.
 */
@Component
class DefaultEnrollmentDirectory implements EnrollmentDirectory {

    private final EnrollmentRepository enrollmentRepository;
    private final ClassGroupDirectory classGroupDirectory;
    private final UserDirectory userDirectory;

    DefaultEnrollmentDirectory(EnrollmentRepository enrollmentRepository,
                               ClassGroupDirectory classGroupDirectory,
                               UserDirectory userDirectory) {
        this.enrollmentRepository = enrollmentRepository;
        this.classGroupDirectory = classGroupDirectory;
        this.userDirectory = userDirectory;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EnrollmentRef> findByPublicId(UUID enrollmentPublicId) {
        if (enrollmentPublicId == null) {
            return Optional.empty();
        }
        return enrollmentRepository.findByPublicId(enrollmentPublicId).map(this::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EnrollmentRef> findByInternalId(long enrollmentInternalId) {
        return enrollmentRepository.findById(enrollmentInternalId).map(this::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentRef> findActiveEnrollmentsForUserOn(UUID userPublicId, LocalDate date) {
        return userDirectory.findByPublicId(userPublicId)
                .map(user -> enrollmentRepository
                        .findByStudentProfile_UserIdAndStatus(user.internalId(), EnrollmentStatus.ACTIVE))
                .orElseGet(List::of)
                .stream()
                .filter(enrollment -> coversDate(enrollment, date))
                .map(this::toRef)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentRef> findEnrollmentsForUser(UUID userPublicId) {
        return userDirectory.findByPublicId(userPublicId)
                .map(user -> enrollmentRepository.findByStudentProfile_UserId(user.internalId()))
                .orElseGet(List::of)
                .stream()
                .filter(enrollment -> enrollment.getStatus() != EnrollmentStatus.ARCHIVED)
                .map(this::toRef)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RosterEntry> findActiveRosterForClasses(Collection<UUID> classGroupPublicIds) {
        if (classGroupPublicIds == null || classGroupPublicIds.isEmpty()) {
            return List.of();
        }
        Set<Long> classInternalIds = classGroupPublicIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(classGroupDirectory::findByPublicId)
                .filter(Optional::isPresent)
                .map(ref -> ref.get().internalId())
                .collect(Collectors.toUnmodifiableSet());
        if (classInternalIds.isEmpty()) {
            return List.of();
        }
        return enrollmentRepository
                .findByClassGroupIdInAndStatus(classInternalIds, EnrollmentStatus.ACTIVE).stream()
                .map(enrollment -> {
                    StudentProfile profile = enrollment.getStudentProfile();
                    UserDirectory.PersonName name = userDirectory.findName(profile.getUserId()).orElse(null);
                    ClassGroupDirectory.ClassGroupRef classRef =
                            classGroupDirectory.findByInternalId(enrollment.getClassGroupId()).orElse(null);
                    return new RosterEntry(
                            enrollment.getId(),
                            enrollment.getPublicId(),
                            profile.getPublicId(),
                            profile.getStudentNumber(),
                            name != null ? name.firstName() : null,
                            name != null ? name.lastName() : null,
                            classRef != null ? classRef.publicId() : null,
                            classRef != null ? classRef.code() : null);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttendeeRef> describeAttendee(long enrollmentInternalId) {
        return enrollmentRepository.findById(enrollmentInternalId).map(enrollment -> {
            StudentProfile profile = enrollment.getStudentProfile();
            UserDirectory.PersonName name = userDirectory.findName(profile.getUserId()).orElse(null);
            return new AttendeeRef(
                    profile.getPublicId(),
                    enrollment.getPublicId(),
                    profile.getStudentNumber(),
                    name != null ? name.firstName() : null,
                    name != null ? name.lastName() : null);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveEnrollmentsInClasses(Collection<UUID> classGroupPublicIds) {
        if (classGroupPublicIds == null || classGroupPublicIds.isEmpty()) {
            return 0;
        }
        Set<Long> classInternalIds = classGroupPublicIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(classGroupDirectory::findByPublicId)
                .filter(Optional::isPresent)
                .map(ref -> ref.get().internalId())
                .collect(Collectors.toUnmodifiableSet());
        if (classInternalIds.isEmpty()) {
            return 0;
        }
        return enrollmentRepository.countByClassGroupIdInAndStatus(classInternalIds, EnrollmentStatus.ACTIVE);
    }

    private static boolean coversDate(Enrollment enrollment, LocalDate date) {
        if (date == null) {
            return true;
        }
        boolean startedByDate = !enrollment.getStartDate().isAfter(date);
        boolean notYetEnded = enrollment.getEndDate() == null || !enrollment.getEndDate().isBefore(date);
        return startedByDate && notYetEnded;
    }

    private EnrollmentRef toRef(Enrollment enrollment) {
        ClassGroupDirectory.ClassGroupRef classRef =
                classGroupDirectory.findByInternalId(enrollment.getClassGroupId()).orElse(null);
        UUID studentUserPublicId = userDirectory.findByInternalId(enrollment.getStudentProfile().getUserId())
                .map(UserDirectory.UserRef::publicId).orElse(null);
        return new EnrollmentRef(
                enrollment.getId(),
                enrollment.getPublicId(),
                enrollment.getStudentProfile().getPublicId(),
                studentUserPublicId,
                classRef != null ? classRef.publicId() : null,
                classRef != null ? classRef.code() : null,
                classRef != null ? classRef.academicYearPublicId() : null,
                classRef != null ? classRef.academicYearCode() : null,
                enrollment.getStatus() == EnrollmentStatus.ACTIVE);
    }
}
