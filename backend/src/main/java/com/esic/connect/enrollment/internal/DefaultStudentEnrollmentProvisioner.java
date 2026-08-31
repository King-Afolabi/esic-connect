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
 * {@link StudentProfileRepository} / {@link EnrollmentRepository}
 * ({@code saveAndFlush}), <strong>sans</strong> {@link EnrollmentPersister}
 * (qui est {@code REQUIRES_NEW}) et <strong>sans</strong>
 * {@link EnrollmentChangePublisher} : les méthodes d'application portent
 * {@code @Transactional} en propagation {@code REQUIRED} et rejoignent la
 * transaction unique de la confirmation d'import (invariants T2, T5).
 */
@Component
class DefaultStudentEnrollmentProvisioner implements StudentEnrollmentProvisioner {

    private final StudentProfileRepository profileRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassGroupDirectory classGroupDirectory;
    private final UserDirectory userDirectory;

    DefaultStudentEnrollmentProvisioner(StudentProfileRepository profileRepository,
                                        EnrollmentRepository enrollmentRepository,
                                        ClassGroupDirectory classGroupDirectory,
                                        UserDirectory userDirectory) {
        this.profileRepository = profileRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.classGroupDirectory = classGroupDirectory;
        this.userDirectory = userDirectory;
    }

    // ------------------------------------------------------------------
    // Lecture seule (simulation)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentProfileView> findProfileByUser(UUID userPublicId) {
        if (userPublicId == null) {
            return Optional.empty();
        }
        return userDirectory.findByPublicId(userPublicId)
                .flatMap(user -> profileRepository.findByUserId(user.internalId()))
                .map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentProfileView> findProfileByStudentNumber(String studentNumber) {
        if (studentNumber == null || studentNumber.isBlank()) {
            return Optional.empty();
        }
        return profileRepository.findByStudentNumberIgnoreCase(studentNumber.trim()).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean studentNumberTaken(String studentNumber) {
        return studentNumber != null && !studentNumber.isBlank()
                && profileRepository.existsByStudentNumberIgnoreCase(studentNumber.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public Situation describeSituation(UUID studentProfilePublicId, UUID targetClassGroupPublicId) {
        if (studentProfilePublicId == null || targetClassGroupPublicId == null) {
            return Situation.none();
        }
        ClassGroupDirectory.ClassGroupRef target =
                classGroupDirectory.findByPublicId(targetClassGroupPublicId).orElse(null);
        if (target == null) {
            return Situation.none();
        }
        List<Enrollment> active = enrollmentRepository
                .findByStudentProfile_PublicIdAndStatus(studentProfilePublicId, EnrollmentStatus.ACTIVE);
        Optional<Enrollment> sameYear = active.stream()
                .filter(e -> e.getAcademicYearId() != null
                        && e.getAcademicYearId() == target.academicYearInternalId())
                .findFirst();
        if (sameYear.isEmpty()) {
            return Situation.none();
        }
        Enrollment enrollment = sameYear.get();
        if (enrollment.getClassGroupId() != null && enrollment.getClassGroupId() == target.internalId()) {
            return new Situation(Situation.Kind.SAME_CLASS, null);
        }
        return new Situation(Situation.Kind.OTHER_CLASS_SAME_YEAR, enrollment.getPublicId());
    }

    // ------------------------------------------------------------------
    // Application (confirmation) — transaction de l'appelant (REQUIRED)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public StudentProfileView provisionProfile(ProvisionProfile command) {
        UserDirectory.UserRef user = userDirectory.findByPublicId(command.userPublicId())
                .orElseThrow(() -> new IllegalStateException("Compte introuvable pour la création de profil."));
        StudentProfile profile = new StudentProfile(user.internalId(), command.studentNumber(),
                command.birthDate(), command.workStudy(), command.companyName());
        profile.markCreatedBy(command.actorUserInternalId());
        return toView(profileRepository.saveAndFlush(profile));
    }

    @Override
    @Transactional
    public EnrollmentView provisionEnrollment(UUID studentProfilePublicId, UUID classGroupPublicId,
                                              LocalDate startDate, Long actorUserInternalId) {
        StudentProfile profile = requireProfile(studentProfilePublicId);
        ClassGroupDirectory.ClassGroupRef target = requireClass(classGroupPublicId);
        Enrollment enrollment = new Enrollment(profile, target.internalId(), target.academicYearInternalId(),
                startDate, EnrollmentSource.MANUAL, null, null);
        enrollment.markCreatedBy(actorUserInternalId);
        return toView(enrollmentRepository.saveAndFlush(enrollment));
    }

    @Override
    @Transactional
    public EnrollmentView provisionTransfer(UUID currentEnrollmentPublicId, UUID targetClassGroupPublicId,
                                            LocalDate effectiveDate, String reason, Long actorUserInternalId) {
        Enrollment current = enrollmentRepository.findByPublicId(currentEnrollmentPublicId)
                .orElseThrow(() -> new IllegalStateException("Inscription courante introuvable pour le changement de classe."));
        ClassGroupDirectory.ClassGroupRef target = requireClass(targetClassGroupPublicId);

        current.close(EnrollmentStatus.TRANSFERRED, reason, effectiveDate, actorUserInternalId);
        enrollmentRepository.saveAndFlush(current); // libère le créneau d'unicité avant l'INSERT

        Enrollment next = new Enrollment(current.getStudentProfile(), target.internalId(),
                target.academicYearInternalId(), effectiveDate.plusDays(1), EnrollmentSource.CLASS_TRANSFER,
                reason, current.getId());
        next.markCreatedBy(actorUserInternalId);
        return toView(enrollmentRepository.saveAndFlush(next));
    }

    @Override
    @Transactional
    public void updateProfileAlternation(UUID studentProfilePublicId, boolean workStudy, String companyName,
                                         Long actorUserInternalId) {
        profileRepository.findByPublicId(studentProfilePublicId)
                .ifPresent(profile -> profile.updateAlternation(workStudy, companyName, actorUserInternalId));
    }

    // ------------------------------------------------------------------

    private StudentProfile requireProfile(UUID publicId) {
        return profileRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalStateException("Profil apprenant introuvable."));
    }

    private ClassGroupDirectory.ClassGroupRef requireClass(UUID publicId) {
        return classGroupDirectory.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalStateException("Classe introuvable pour l'inscription."));
    }

    private StudentProfileView toView(StudentProfile profile) {
        UUID userPublicId = userDirectory.findByInternalId(profile.getUserId())
                .map(UserDirectory.UserRef::publicId).orElse(null);
        return new StudentProfileView(profile.getPublicId(), userPublicId, profile.getStudentNumber(),
                profile.isWorkStudy(), profile.getCompanyName(), profile.isArchived());
    }

    private EnrollmentView toView(Enrollment enrollment) {
        ClassGroupDirectory.ClassGroupRef classRef =
                classGroupDirectory.findByInternalId(enrollment.getClassGroupId()).orElse(null);
        return new EnrollmentView(
                enrollment.getPublicId(),
                enrollment.getStudentProfile().getPublicId(),
                classRef != null ? classRef.publicId() : null,
                enrollment.getStatus() == EnrollmentStatus.ACTIVE);
    }
}
