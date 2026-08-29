package com.esic.connect.enrollment.internal;

import com.esic.connect.academic.ClassGroupDirectory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Vue API d'une inscription — jamais d'identifiant SQL interne (ni
 * {@code id}, ni {@code studentProfileId}, ni {@code classGroupId}, ni
 * {@code academicYearId}). La classe, la formation et l'année scolaire
 * sont exposées par leurs identifiants publics et codes (résolus via
 * {@link ClassGroupDirectory}).
 */
record EnrollmentResponse(
        UUID publicId,
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

    static EnrollmentResponse from(Enrollment enrollment, ClassGroupDirectory.ClassGroupRef classRef,
                                   UUID previousEnrollmentPublicId) {
        return new EnrollmentResponse(
                enrollment.getPublicId(),
                enrollment.getStudentProfile().getPublicId(),
                enrollment.getStudentProfile().getStudentNumber(),
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
