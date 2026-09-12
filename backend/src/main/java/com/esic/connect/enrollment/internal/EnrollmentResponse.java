package com.esic.connect.enrollment.internal;

import com.esic.connect.academic.ClassGroupDirectory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Vue API d'une inscription — jamais d'identifiant SQL interne (ni
 * {@code id}, ni {@code userId}, ni {@code classGroupId}, ni
 * {@code academicYearId}). La classe, la formation et l'année scolaire
 * sont exposées par leurs identifiants publics et codes (résolus via
 * {@link ClassGroupDirectory}).
 *
 * <p>{@code studentUserPublicId} est <strong>l'identifiant du compte
 * apprenant</strong> — toujours renseigné, une inscription rattachant
 * directement un compte (refonte 2026-09). {@code studentProfilePublicId}
 * et {@code studentNumber} restent exposés à titre de confort d'affichage
 * (numéro étudiant, lien vers le profil), mais sont
 * <strong>facultatifs</strong> : {@code null} lorsque le compte apprenant
 * n'a pas (encore) de {@code student_profile} — ce qui ne remet jamais en
 * cause la validité de l'inscription elle-même.
 */
record EnrollmentResponse(
        UUID publicId,
        UUID studentUserPublicId,
        UUID studentProfilePublicId,
        String studentNumber,
        UUID classGroupPublicId,
        String classGroupCode,
        UUID programPublicId,
        String programCode,
        UUID academicYearPublicId,
        String academicYearCode,
        LocalDate startDate,
        LocalDate endDate,
        EnrollmentStatus status,
        EnrollmentSource enrollmentSource,
        String changeReason,
        UUID previousEnrollmentPublicId,
        Instant createdAt,
        Instant updatedAt) {

    static EnrollmentResponse from(Enrollment enrollment, UUID studentUserPublicId, StudentProfile profile,
                                   ClassGroupDirectory.ClassGroupRef classRef, UUID previousEnrollmentPublicId) {
        return new EnrollmentResponse(
                enrollment.getPublicId(),
                studentUserPublicId,
                profile != null ? profile.getPublicId() : null,
                profile != null ? profile.getStudentNumber() : null,
                classRef.publicId(),
                classRef.code(),
                classRef.programPublicId(),
                classRef.programCode(),
                classRef.academicYearPublicId(),
                classRef.academicYearCode(),
                enrollment.getStartDate(),
                enrollment.getEndDate(),
                enrollment.getStatus(),
                enrollment.getEnrollmentSource(),
                enrollment.getChangeReason(),
                previousEnrollmentPublicId,
                enrollment.getCreatedAt(),
                enrollment.getUpdatedAt());
    }
}
