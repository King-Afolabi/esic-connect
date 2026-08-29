package com.esic.connect.academic.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Frontière transactionnelle dédiée à l'insertion d'une affectation de
 * responsable pédagogique.
 *
 * <p>{@code saveAndFlush} est isolé dans une transaction
 * {@link Propagation#REQUIRES_NEW} : si la contrainte d'unicité
 * {@code uq_pedagogical_assignment_active_primary} est violée (course
 * entre deux créations d'un {@code PRIMARY_MANAGER}), <em>cette</em>
 * transaction est marquée rollback-only et annulée, sans contaminer la
 * transaction appelante. L'appelant ({@link PedagogicalAssignmentService})
 * reçoit alors une {@link org.springframework.dao.DataIntegrityViolationException}
 * hors de toute transaction en échec, qu'il peut inspecter et retraduire.
 */
@Component
class AssignmentPersister {

    private final PedagogicalAssignmentRepository assignmentRepository;

    AssignmentPersister(PedagogicalAssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    PedagogicalAssignment persist(PedagogicalAssignment assignment) {
        return assignmentRepository.saveAndFlush(assignment);
    }
}
