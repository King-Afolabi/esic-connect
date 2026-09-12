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
 * qu'ils aient ou non une {@code enrollment} — il n'existe plus de
 * {@code student_profile} distinct. Les champs suivants sont des
 * <strong>décorations facultatives</strong>, jamais des conditions de
 * présence dans la liste :
 * <ul>
 *   <li>{@code studentNumber} / {@code birthDate} — portés directement par
 *       {@code user_account} ; {@code null} tant qu'ils ne sont pas
 *       renseignés ;</li>
 *   <li>{@code currentEnrollment*}, {@code workStudy}, {@code companyName}
 *       — présents uniquement si ce compte a une inscription : celle
 *       {@code ACTIVE}, ou à défaut la plus récente (l'alternance est une
 *       situation propre à cette inscription).</li>
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

        // Décoration facultative : portée par user_account, peut être absente.
        String studentNumber,
        LocalDate birthDate,

        // Décoration facultative : inscription courante (peut être absente).
        UUID currentEnrollmentPublicId,
        Boolean workStudy,
        String companyName,
        UUID classGroupPublicId,
        String classGroupCode,
        UUID academicYearPublicId,
        String academicYearCode,
        String enrollmentStatus) {

    static StudentResponse of(UserDirectory.AccountSummary account, Enrollment currentEnrollment,
                              ClassGroupDirectory.ClassGroupRef classRef) {
        return new StudentResponse(
                account.publicId(),
                account.email(),
                account.firstName(),
                account.lastName(),
                account.status(),
                account.createdAt(),
                account.lastLoginAt(),
                account.studentNumber(),
                account.birthDate(),
                currentEnrollment != null ? currentEnrollment.getPublicId() : null,
                currentEnrollment != null ? currentEnrollment.isWorkStudy() : null,
                currentEnrollment != null ? currentEnrollment.getCompanyName() : null,
                classRef != null ? classRef.publicId() : null,
                classRef != null ? classRef.code() : null,
                classRef != null ? classRef.academicYearPublicId() : null,
                classRef != null ? classRef.academicYearCode() : null,
                currentEnrollment != null ? currentEnrollment.getStatus().name() : null);
    }
}
