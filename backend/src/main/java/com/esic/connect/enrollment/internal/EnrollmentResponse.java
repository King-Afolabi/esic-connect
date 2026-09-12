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
 * directement un compte (refonte 2026-09, plus de {@code student_profile}
 * intermédiaire). {@code studentNumber} (porté par {@code user_account})
 * reste exposé à titre de confort d'affichage, mais facultatif :
 * {@code null} tant que le compte n'a pas de numéro. {@code workStudy} /
 * {@code companyName} décrivent la situation d'alternance
 * <strong>pendant cette inscription</strong> (ex-{@code student_profile}).
 */
record EnrollmentResponse(
        UUID publicId,
        UUID studentUserPublicId,
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
        boolean workStudy,
        String companyName,
        UUID previousEnrollmentPublicId,
        Instant createdAt,
        Instant updatedAt) {

    static EnrollmentResponse from(Enrollment enrollment, UUID studentUserPublicId, String studentNumber,
                                   ClassGroupDirectory.ClassGroupRef classRef, UUID previousEnrollmentPublicId) {
        return new EnrollmentResponse(
                enrollment.getPublicId(),
                studentUserPublicId,
                studentNumber,
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
                enrollment.isWorkStudy(),
                enrollment.getCompanyName(),
                previousEnrollmentPublicId,
                enrollment.getCreatedAt(),
                enrollment.getUpdatedAt());
    }
}
