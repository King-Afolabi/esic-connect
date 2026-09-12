package com.esic.connect.enrollment.internal;

import org.springframework.data.jpa.domain.Specification;

/**
 * Fabriques de {@link Specification} pour les consultations du module.
 * Recherche textuelle (nom, numéro étudiant) déportée sur le port
 * {@code identity.UserDirectory} depuis la refonte 2026-09 : ces
 * spécifications ne portent plus que des égalités et appartenances sur
 * {@link Enrollment}.
 */
final class EnrollmentSpecifications {

    private EnrollmentSpecifications() {
    }

    static Specification<Enrollment> enrollmentHasUser(long userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    static Specification<Enrollment> enrollmentHasClassGroup(long classGroupId) {
        return (root, query, cb) -> cb.equal(root.get("classGroupId"), classGroupId);
    }

    /**
     * Restreint la liste des inscriptions à celles rattachées à une classe
     * de {@code classGroupIds} — filtre de périmètre pédagogique. Ensemble
     * vide ⇒ prédicat toujours faux.
     */
    static Specification<Enrollment> enrollmentClassGroupIn(java.util.Collection<Long> classGroupIds) {
        return (root, query, cb) -> (classGroupIds == null || classGroupIds.isEmpty())
                ? cb.disjunction()
                : root.get("classGroupId").in(classGroupIds);
    }

    static Specification<Enrollment> enrollmentHasAcademicYear(long academicYearId) {
        return (root, query, cb) -> cb.equal(root.get("academicYearId"), academicYearId);
    }

    static Specification<Enrollment> enrollmentHasStatus(EnrollmentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
