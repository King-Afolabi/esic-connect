package com.esic.connect.enrollment.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Frontière transactionnelle dédiée aux <em>insertions</em> d'inscription
 * qui ne dépendent d'aucune écriture antérieure dans la même transaction.
 *
 * <p>{@code saveAndFlush} est isolé dans une transaction
 * {@link Propagation#REQUIRES_NEW} : si la contrainte d'unicité
 * {@code uq_enrollment_active_per_year} est violée par une course entre
 * deux requêtes, <em>cette</em> transaction est marquée rollback-only et
 * annulée sans contaminer l'appelant. Le service reçoit alors la
 * {@link org.springframework.dao.DataIntegrityViolationException}
 * <em>hors</em> de toute transaction en échec : il peut l'inspecter et la
 * retraduire en 409, ou la relancer telle quelle si elle vise une autre
 * contrainte. Même approche que
 * {@code academic.internal.AssignmentPersister}.
 *
 * <p>Le changement de classe ({@code EnrollmentService.transfer})
 * n'utilise pas ce persister : son insertion doit voir, dans la même
 * transaction, la clôture qui vient de libérer le créneau d'unicité.
 */
@Component
class EnrollmentPersister {

    private final EnrollmentRepository enrollmentRepository;

    EnrollmentPersister(EnrollmentRepository enrollmentRepository) {
        this.enrollmentRepository = enrollmentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Enrollment persist(Enrollment enrollment) {
        return enrollmentRepository.saveAndFlush(enrollment);
    }
}
