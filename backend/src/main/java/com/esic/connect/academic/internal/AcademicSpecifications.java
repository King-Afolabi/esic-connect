package com.esic.connect.academic.internal;

import org.springframework.data.jpa.domain.Specification;

/**
 * Fabriques de {@link Specification} pour les consultations du module. Le
 * filtre texte est déjà normalisé (trim, minuscules, longueur bornée) par
 * l'appelant ; les métacaractères {@code LIKE} sont échappés ici pour
 * éviter toute injection de motif. Aligné sur
 * {@code organization.internal.OrganizationSpecifications}.
 */
final class AcademicSpecifications {

    private static final char ESCAPE = '\\';

    private AcademicSpecifications() {
    }

    static <T> Specification<T> hasStatus(AcademicStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static <T> Specification<T> matchesCodeOrName(String normalizedQuery) {
        String pattern = likePattern(normalizedQuery);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("name")), pattern, ESCAPE));
    }

    static Specification<ProgramLevel> levelHasProgram(Long programId) {
        return (root, query, cb) -> cb.equal(root.get("program").get("id"), programId);
    }

    static Specification<Promotion> promotionHasProgram(Long programId) {
        return (root, query, cb) -> cb.equal(root.get("program").get("id"), programId);
    }

    static Specification<Promotion> promotionHasAcademicYear(Long academicYearId) {
        return (root, query, cb) -> cb.equal(root.get("academicYear").get("id"), academicYearId);
    }

    static Specification<ClassGroup> classGroupHasPromotion(Long promotionId) {
        return (root, query, cb) -> cb.equal(root.get("promotion").get("id"), promotionId);
    }

    static Specification<ClassGroup> classGroupHasProgramLevel(Long programLevelId) {
        return (root, query, cb) -> cb.equal(root.get("programLevel").get("id"), programLevelId);
    }

    static Specification<ClassGroup> classGroupHasSite(Long siteId) {
        return (root, query, cb) -> cb.equal(root.get("siteId"), siteId);
    }

    private static String likePattern(String normalizedQuery) {
        return "%" + escapeLike(normalizedQuery) + "%";
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
