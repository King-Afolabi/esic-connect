package com.esic.connect.enrollment.internal;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;

/**
 * Reconnaissance <em>ciblée</em> d'une violation d'une contrainte
 * d'unicité connue du module (V7), pour retraduire une collision
 * concurrente en 409 plutôt qu'en 500 générique.
 *
 * <p>{@link #matchesConstraint} n'est vrai que si la violation d'intégrité
 * concerne précisément la contrainte nommée — jamais une autre FK,
 * {@code CHECK}, {@code NOT NULL} ou unicité (dont
 * {@code uq_enrollment_public_id} / {@code uq_student_profile_public_id}).
 * Recherche à la fois le nom de contrainte structuré (Hibernate) et le
 * message SQL brut, en exigeant une sémantique de doublon. Même approche
 * que {@code PedagogicalAssignmentService.isActivePrimaryUniqueViolation}.
 */
final class EnrollmentPersistence {

    static final String ACTIVE_ENROLLMENT_CONSTRAINT = "uq_enrollment_active_per_year";
    static final String PROFILE_USER_CONSTRAINT = "uq_student_profile_user";
    static final String PROFILE_STUDENT_NUMBER_CONSTRAINT = "uq_student_profile_student_number";

    private EnrollmentPersistence() {
    }

    static boolean isActiveEnrollmentUniqueViolation(DataIntegrityViolationException violation) {
        return matchesConstraint(violation, ACTIVE_ENROLLMENT_CONSTRAINT);
    }

    static boolean matchesConstraint(DataIntegrityViolationException violation, String constraintLowercase) {
        for (Throwable cause = violation; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint) {
                String name = constraint.getConstraintName();
                if (name != null && name.toLowerCase(Locale.ROOT).contains(constraintLowercase)) {
                    return true;
                }
            }
            String message = cause.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains(constraintLowercase)
                        && (lower.contains("duplicate entry") || lower.contains("unique"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
