package com.esic.connect.alternation.internal;

import com.esic.connect.enrollment.EnrollmentDirectory;

import java.time.Instant;
import java.util.UUID;

/**
 * Vue API d'une exception individuelle de calendrier — jamais
 * d'identifiant SQL interne. L'inscription et sa classe sont exposées par
 * leurs identifiants publics (résolus via {@link EnrollmentDirectory}) ;
 * aucun nom, numéro étudiant ni adresse.
 */
record StudentExceptionResponse(
        UUID publicId,
        UUID enrollmentPublicId,
        UUID studentProfilePublicId,
        UUID classGroupPublicId,
        ScheduleExceptionType type,
        Instant startAt,
        Instant endAt,
        String timeZoneId,
        String reason,
        ScheduleExceptionStatus status,
        String cancelReason,
        Instant createdAt,
        Instant updatedAt) {

    static StudentExceptionResponse from(StudentScheduleException exception,
                                         EnrollmentDirectory.EnrollmentRef enrollmentRef) {
        return new StudentExceptionResponse(
                exception.getPublicId(),
                enrollmentRef != null ? enrollmentRef.publicId() : null,
                enrollmentRef != null ? enrollmentRef.studentProfilePublicId() : null,
                enrollmentRef != null ? enrollmentRef.classGroupPublicId() : null,
                exception.getExceptionType(),
                exception.getStartAt(),
                exception.getEndAt(),
                exception.getTimeZoneId(),
                exception.getReason(),
                exception.getStatus(),
                exception.getCancelReason(),
                exception.getCreatedAt(),
                exception.getUpdatedAt());
    }
}
