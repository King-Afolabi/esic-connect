package com.esic.connect.alternation.internal;

import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

/**
 * Fabriques de {@link Specification} pour les consultations du module. Le
 * filtre texte est déjà normalisé (trim, minuscules, longueur bornée) par
 * l'appelant ; les métacaractères {@code LIKE} sont échappés ici. Aligné
 * sur {@code enrollment.internal.EnrollmentSpecifications}.
 */
final class AlternationSpecifications {

    private static final char ESCAPE = '\\';

    private AlternationSpecifications() {
    }

    static Specification<WorkStudyPattern> patternHasStatus(WorkStudyPatternStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<WorkStudyPattern> patternHasType(WorkStudyPatternType type) {
        return (root, query, cb) -> cb.equal(root.get("patternType"), type);
    }

    static Specification<WorkStudyPattern> patternMatchesCodeOrName(String normalizedQuery) {
        String pattern = "%" + escapeLike(normalizedQuery) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("name")), pattern, ESCAPE));
    }

    static Specification<ClassWorkStudyPattern> assignmentHasClassGroup(long classGroupId) {
        return (root, query, cb) -> cb.equal(root.get("classGroupId"), classGroupId);
    }

    static Specification<ClassWorkStudyPattern> assignmentClassGroupIn(Collection<Long> classGroupIds) {
        return (root, query, cb) -> root.get("classGroupId").in(classGroupIds);
    }

    static Specification<ClassWorkStudyPattern> assignmentHasStatus(ClassPatternStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static String escapeLike(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (char c : value.toCharArray()) {
            if (c == ESCAPE || c == '%' || c == '_') {
                sb.append(ESCAPE);
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
