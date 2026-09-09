package com.esic.connect.enrollment.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Vue API d'un profil apprenant — jamais d'identifiant SQL interne (ni
 * {@code id}, ni {@code userId}) ni de colonne auteur. Le compte est
 * exposé par son {@code userPublicId}.
 */
record StudentProfileResponse(
        UUID publicId,
        UUID userPublicId,
        String firstName,
        String lastName,
        String studentNumber,
        LocalDate birthDate,
        boolean workStudy,
        String companyName,
        StudentProfileStatus status,
        Instant createdAt,
        Instant updatedAt) {

    static StudentProfileResponse from(StudentProfile profile, UUID userPublicId) {
        return from(profile, userPublicId, null, null);
    }

    static StudentProfileResponse from(StudentProfile profile, UUID userPublicId,
                                       String firstName, String lastName) {
        return new StudentProfileResponse(
                profile.getPublicId(),
                userPublicId,
                firstName,
                lastName,
                profile.getStudentNumber(),
                profile.getBirthDate(),
                profile.isWorkStudy(),
                profile.getCompanyName(),
                profile.getStatus(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
