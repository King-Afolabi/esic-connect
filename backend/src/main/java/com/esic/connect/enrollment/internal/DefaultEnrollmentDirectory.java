package com.esic.connect.enrollment.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.enrollment.EnrollmentDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

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

    DefaultEnrollmentDirectory(EnrollmentRepository enrollmentRepository,
                               ClassGroupDirectory classGroupDirectory) {
        this.enrollmentRepository = enrollmentRepository;
        this.classGroupDirectory = classGroupDirectory;
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

    private EnrollmentRef toRef(Enrollment enrollment) {
        ClassGroupDirectory.ClassGroupRef classRef =
                classGroupDirectory.findByInternalId(enrollment.getClassGroupId()).orElse(null);
        return new EnrollmentRef(
                enrollment.getId(),
                enrollment.getPublicId(),
                enrollment.getStudentProfile().getPublicId(),
                classRef != null ? classRef.publicId() : null,
                classRef != null ? classRef.code() : null,
                classRef != null ? classRef.academicYearPublicId() : null,
                classRef != null ? classRef.academicYearCode() : null,
                enrollment.getStatus() == EnrollmentStatus.ACTIVE);
    }
}
