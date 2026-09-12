package com.esic.connect.enrollment.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.identity.UserDirectory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Vue API d'un <strong>apprenant</strong> — écran « Apprenants »
 * ({@code GET /api/v1/students}, {@code GET /api/v1/students/{userPublicId}}).
 *
 * <p>Refonte 2026-09 : le rôle {@code STUDENT} (module {@code identity})
 * est l'unique source de vérité du statut apprenant. Cette vue liste donc
 * <strong>tous les comptes porteurs d'un rôle actif {@code STUDENT}</strong>,
 * qu'ils aient ou non un {@code student_profile} ou une {@code enrollment}.
 * Les champs suivants sont des <strong>décorations facultatives</strong>,
 * jamais des conditions de présence dans la liste :
 * <ul>
 *   <li>{@code studentProfilePublicId} à {@code companyName} — présents
 *       uniquement si un {@code student_profile} existe pour ce compte ;</li>
 *   <li>{@code currentEnrollment*} — présents uniquement si ce compte a une
 *       inscription : celle {@code ACTIVE}, ou à défaut la plus récente.</li>
 * </ul>
 * Aucun de ces deux groupes de champs n'est requis pour qu'un apprenant
 * apparaisse dans cette liste, et plusieurs inscriptions pour un même
 * compte ne produisent jamais plusieurs lignes (une ligne par compte).
 */
record StudentResponse(
        UUID userPublicId,
        String email,
        String firstName,
        String lastName,
        String accountStatus,
        Instant createdAt,
        Instant lastLoginAt,

        // Décoration facultative : student_profile (peut être absente).
        UUID studentProfilePublicId,
        String studentNumber,
        LocalDate birthDate,
        Boolean workStudy,
        String companyName,
        String profileStatus,

        // Décoration facultative : inscription courante (peut être absente).
        UUID currentEnrollmentPublicId,
        UUID classGroupPublicId,
        String classGroupCode,
        UUID academicYearPublicId,
        String academicYearCode,
        String enrollmentStatus) {

    static StudentResponse of(UserDirectory.AccountSummary account, StudentProfile profile,
                              Enrollment currentEnrollment, ClassGroupDirectory.ClassGroupRef classRef) {
        return new StudentResponse(
                account.publicId(),
                account.email(),
                account.firstName(),
                account.lastName(),
                account.status(),
                account.createdAt(),
                account.lastLoginAt(),
                profile != null ? profile.getPublicId() : null,
                profile != null ? profile.getStudentNumber() : null,
                profile != null ? profile.getBirthDate() : null,
                profile != null ? profile.isWorkStudy() : null,
                profile != null ? profile.getCompanyName() : null,
                profile != null ? profile.getStatus().name() : null,
                currentEnrollment != null ? currentEnrollment.getPublicId() : null,
                classRef != null ? classRef.publicId() : null,
                classRef != null ? classRef.code() : null,
                classRef != null ? classRef.academicYearPublicId() : null,
                classRef != null ? classRef.academicYearCode() : null,
                currentEnrollment != null ? currentEnrollment.getStatus().name() : null);
    }
}
