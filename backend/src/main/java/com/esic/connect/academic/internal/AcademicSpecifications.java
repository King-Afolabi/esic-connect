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

    static Specification<PedagogicalAssignment> assignmentHasProgram(Long programId) {
        return (root, query, cb) -> cb.equal(root.get("program").get("id"), programId);
    }

    static Specification<PedagogicalAssignment> assignmentHasManager(long managerUserId) {
        return (root, query, cb) -> cb.equal(root.get("managerUserId"), managerUserId);
    }

    static Specification<PedagogicalAssignment> assignmentHasStatus(PedagogicalAssignmentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<PedagogicalAssignment> assignmentHasType(PedagogicalAssignmentRole type) {
        return (root, query, cb) -> cb.equal(root.get("assignmentRole"), type);
    }

    /**
     * Affectations effectives à la date {@code on} : {@code validFrom <=
     * on <= validUntil} (bornes inclusives, {@code validUntil} nul =
     * ouvert). Ne filtre pas sur le statut.
     */
    static Specification<PedagogicalAssignment> assignmentActiveOn(java.time.LocalDate on) {
        return (root, query, cb) -> cb.and(
                cb.lessThanOrEqualTo(root.<java.time.LocalDate>get("validFrom"), on),
                cb.or(cb.isNull(root.get("validUntil")),
                        cb.greaterThanOrEqualTo(root.<java.time.LocalDate>get("validUntil"), on)));
    }

    /** Spécification impossible à satisfaire (aucun résultat) — sans requête inutile. */
    static <T> Specification<T> matchesNothing() {
        return (root, query, cb) -> cb.disjunction();
    }

    // --- Filtres de périmètre pédagogique (ensemble de formations visibles) ---

    static Specification<Program> programIdIn(java.util.Collection<Long> programIds) {
        return (root, query, cb) -> root.get("id").in(programIds);
    }

    static Specification<ProgramLevel> levelProgramIdIn(java.util.Collection<Long> programIds) {
        return (root, query, cb) -> root.get("program").get("id").in(programIds);
    }

    static Specification<Promotion> promotionProgramIdIn(java.util.Collection<Long> programIds) {
        return (root, query, cb) -> root.get("program").get("id").in(programIds);
    }

    static Specification<ClassGroup> classGroupProgramIdIn(java.util.Collection<Long> programIds) {
        return (root, query, cb) -> root.get("promotion").get("program").get("id").in(programIds);
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
