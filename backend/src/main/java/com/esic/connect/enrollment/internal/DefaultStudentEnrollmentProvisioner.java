package com.esic.connect.enrollment.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.enrollment.StudentEnrollmentProvisioner;
import com.esic.connect.identity.UserDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implémentation du port {@link StudentEnrollmentProvisioner}. Confinée à
 * {@code enrollment.internal}. Écrit <strong>directement</strong> via
 * {@link EnrollmentRepository} ({@code saveAndFlush}), <strong>sans</strong>
 * {@link EnrollmentPersister} (qui est {@code REQUIRES_NEW}) et
 * <strong>sans</strong> {@link EnrollmentChangePublisher} : les méthodes
 * d'application portent {@code @Transactional} en propagation
 * {@code REQUIRED} et rejoignent la transaction unique de la confirmation
 * d'import (invariants T2, T5).
 */
@Component
class DefaultStudentEnrollmentProvisioner implements StudentEnrollmentProvisioner {

    private final EnrollmentRepository enrollmentRepository;
    private final ClassGroupDirectory classGroupDirectory;
    private final UserDirectory userDirectory;

    DefaultStudentEnrollmentProvisioner(EnrollmentRepository enrollmentRepository,
                                        ClassGroupDirectory classGroupDirectory,
                                        UserDirectory userDirectory) {
        this.enrollmentRepository = enrollmentRepository;
        this.classGroupDirectory = classGroupDirectory;
        this.userDirectory = userDirectory;
    }

    // ------------------------------------------------------------------
    // Lecture seule (simulation)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Situation describeSituation(UUID userPublicId, UUID targetClassGroupPublicId) {
        if (userPublicId == null || targetClassGroupPublicId == null) {
            return Situation.none();
        }
        ClassGroupDirectory.ClassGroupRef target =
                classGroupDirectory.findByPublicId(targetClassGroupPublicId).orElse(null);
        if (target == null) {
            return Situation.none();
        }
        UserDirectory.UserRef user = userDirectory.findByPublicId(userPublicId).orElse(null);
        if (user == null) {
            return Situation.none();
        }
        List<Enrollment> active = enrollmentRepository
                .findByUserIdAndStatus(user.internalId(), EnrollmentStatus.ACTIVE);
        Optional<Enrollment> sameYear = active.stream()
                .filter(e -> e.getAcademicYearId() != null
                        && e.getAcademicYearId() == target.academicYearInternalId())
                .findFirst();
        if (sameYear.isEmpty()) {
            return Situation.none();
        }
        Enrollment enrollment = sameYear.get();
        Situation.Kind kind = enrollment.getClassGroupId() != null
                && enrollment.getClassGroupId() == target.internalId()
                ? Situation.Kind.SAME_CLASS : Situation.Kind.OTHER_CLASS_SAME_YEAR;
        return new Situation(kind, enrollment.getPublicId(), enrollment.isWorkStudy(),
                enrollment.getCompanyName());
    }

    // ------------------------------------------------------------------
    // Application (confirmation) — transaction de l'appelant (REQUIRED)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public EnrollmentView provisionEnrollment(UUID userPublicId, UUID classGroupPublicId, LocalDate startDate,
                                              boolean workStudy, String companyName, Long actorUserInternalId) {
        UserDirectory.UserRef user = userDirectory.findByPublicId(userPublicId)
                .orElseThrow(() -> new IllegalStateException("Compte introuvable pour l'inscription."));
        ClassGroupDirectory.ClassGroupRef target = requireClass(classGroupPublicId);
        Enrollment enrollment = new Enrollment(user.internalId(), target.internalId(),
                target.academicYearInternalId(), startDate, EnrollmentSource.MANUAL, null, null,
                workStudy, companyName);
        enrollment.markCreatedBy(actorUserInternalId);
        return toView(enrollmentRepository.saveAndFlush(enrollment));
    }

    @Override
    @Transactional
    public EnrollmentView provisionTransfer(UUID currentEnrollmentPublicId, UUID targetClassGroupPublicId,
                                            LocalDate effectiveDate, String reason, boolean workStudy,
                                            String companyName, Long actorUserInternalId) {
        Enrollment current = enrollmentRepository.findByPublicId(currentEnrollmentPublicId)
                .orElseThrow(() -> new IllegalStateException("Inscription courante introuvable pour le changement de classe."));
        ClassGroupDirectory.ClassGroupRef target = requireClass(targetClassGroupPublicId);

        current.close(EnrollmentStatus.TRANSFERRED, reason, effectiveDate, actorUserInternalId);
        enrollmentRepository.saveAndFlush(current); // libère le créneau d'unicité avant l'INSERT

        Enrollment next = new Enrollment(current.getUserId(), target.internalId(),
                target.academicYearInternalId(), effectiveDate.plusDays(1), EnrollmentSource.CLASS_TRANSFER,
                reason, current.getId(), workStudy, companyName);
        next.markCreatedBy(actorUserInternalId);
        return toView(enrollmentRepository.saveAndFlush(next));
    }

    @Override
    @Transactional
    public void updateEnrollmentAlternation(UUID enrollmentPublicId, boolean workStudy, String companyName,
                                            Long actorUserInternalId) {
        enrollmentRepository.findByPublicId(enrollmentPublicId)
                .ifPresent(enrollment -> enrollment.updateAlternation(workStudy, companyName, actorUserInternalId));
    }

    // ------------------------------------------------------------------

    private ClassGroupDirectory.ClassGroupRef requireClass(UUID publicId) {
        return classGroupDirectory.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalStateException("Classe introuvable pour l'inscription."));
    }

    private EnrollmentView toView(Enrollment enrollment) {
        ClassGroupDirectory.ClassGroupRef classRef =
                classGroupDirectory.findByInternalId(enrollment.getClassGroupId()).orElse(null);
        UUID userPublicId = userDirectory.findByInternalId(enrollment.getUserId())
                .map(UserDirectory.UserRef::publicId).orElse(null);
        return new EnrollmentView(
                enrollment.getPublicId(),
                userPublicId,
                classRef != null ? classRef.publicId() : null,
                enrollment.getStatus() == EnrollmentStatus.ACTIVE);
    }
}
