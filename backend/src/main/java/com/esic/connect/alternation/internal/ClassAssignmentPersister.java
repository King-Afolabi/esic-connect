package com.esic.connect.alternation.internal;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Frontière transactionnelle dédiée à l'insertion d'une affectation de
 * rythme à une classe.
 *
 * <p>{@code saveAndFlush} est isolé dans une transaction
 * {@link Propagation#REQUIRES_NEW} : si la contrainte d'unicité
 * {@code uq_class_work_study_pattern_active_open} est violée (course
 * entre deux créations d'une affectation « ouverte » pour la même
 * classe), <em>cette</em> transaction est marquée rollback-only et
 * annulée sans contaminer l'appelant. Le service
 * ({@link ClassWorkStudyPatternService}) reçoit alors la
 * {@link DataIntegrityViolationException} <em>hors</em> de toute
 * transaction en échec : il la retraduit en 409
 * ({@code ALT_OPEN_ASSIGNMENT_EXISTS}) si c'est bien cette contrainte,
 * sinon la relance telle quelle. Même approche que
 * {@code academic.internal.AssignmentPersister} et
 * {@code enrollment.internal.EnrollmentPersister}.
 */
@Component
class ClassAssignmentPersister {

    static final String ACTIVE_OPEN_CONSTRAINT = "uq_class_work_study_pattern_active_open";

    private final ClassWorkStudyPatternRepository repository;

    ClassAssignmentPersister(ClassWorkStudyPatternRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    ClassWorkStudyPattern persist(ClassWorkStudyPattern assignment) {
        return repository.saveAndFlush(assignment);
    }

    /**
     * Vrai uniquement si la violation d'intégrité concerne la contrainte
     * d'unicité de l'affectation ouverte — jamais une autre FK,
     * {@code CHECK}, {@code NOT NULL} ou l'unicité de {@code public_id}.
     * Recherche le nom de contrainte structuré (Hibernate) et le message
     * SQL brut, en exigeant une sémantique de doublon.
     */
    static boolean isOpenAssignmentUniqueViolation(DataIntegrityViolationException violation) {
        for (Throwable cause = violation; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint) {
                String name = constraint.getConstraintName();
                if (name != null && name.toLowerCase(Locale.ROOT).contains(ACTIVE_OPEN_CONSTRAINT)) {
                    return true;
                }
            }
            String message = cause.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains(ACTIVE_OPEN_CONSTRAINT)
                        && (lower.contains("duplicate entry") || lower.contains("unique"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
