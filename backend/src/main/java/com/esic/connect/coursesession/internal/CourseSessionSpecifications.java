package com.esic.connect.coursesession.internal;

import com.esic.connect.coursesession.SessionLifecycle;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;

/** Fragments de {@link Specification} pour la consultation des séances. */
final class CourseSessionSpecifications {

    private CourseSessionSpecifications() {
    }

    static Specification<CourseSession> hasStatus(SessionLifecycle status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<CourseSession> taughtBy(long teacherUserId) {
        return (root, query, cb) -> cb.equal(root.get("teacherUserId"), teacherUserId);
    }

    static Specification<CourseSession> startsFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startsAt"), from);
    }

    static Specification<CourseSession> startsUntil(Instant to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("startsAt"), to);
    }

    /** Séances dont le début est strictement avant {@code to} (chevauchement d'intervalle). */
    static Specification<CourseSession> startsBefore(Instant to) {
        return (root, query, cb) -> cb.lessThan(root.get("startsAt"), to);
    }

    /** Séances dont la fin est strictement après {@code from} (chevauchement d'intervalle). */
    static Specification<CourseSession> endsAfter(Instant from) {
        return (root, query, cb) -> cb.greaterThan(root.get("endsAt"), from);
    }

    /**
     * Exclut les séances d'origine planning retirées par une republication
     * ({@code superseded_by_scheduling = true} — DEC-G1-004 règle 4) : elles
     * ne sont plus affichées dans les listes de séances.
     */
    static Specification<CourseSession> notSupersededByScheduling() {
        return (root, query, cb) -> cb.isFalse(root.get("supersededByScheduling"));
    }

    /**
     * Séances <strong>opérationnelles</strong> uniquement (garde
     * centralisée, audit G1-B.1) : exclut les séances retirées par une
     * republication de planning. Point d'extension pour l'état
     * {@code CANCELLED} du bloc G1-C.
     */
    static Specification<CourseSession> operational() {
        return notSupersededByScheduling();
    }

    /**
     * Séances possédant au moins une classe rattachée dont l'identifiant
     * interne figure dans {@code classGroupInternalIds}. {@code distinct}
     * évite les doublons dus au {@code join}.
     */
    static Specification<CourseSession> hasAnyClassIn(Collection<Long> classGroupInternalIds) {
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            Join<Object, Object> join = root.join("classes");
            return join.get("classGroupId").in(classGroupInternalIds);
        };
    }
}
