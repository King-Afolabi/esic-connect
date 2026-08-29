package com.esic.connect.enrollment.internal;

import org.springframework.data.jpa.domain.Specification;

/**
 * Fabriques de {@link Specification} pour les consultations du module. Le
 * filtre texte est déjà normalisé (trim, minuscules, longueur bornée) par
 * l'appelant ; les métacaractères {@code LIKE} sont échappés ici. Aligné
 * sur {@code academic.internal.AcademicSpecifications}.
 */
final class EnrollmentSpecifications {

    private static final char ESCAPE = '\\';

    private EnrollmentSpecifications() {
    }

    static Specification<StudentProfile> profileHasStatus(StudentProfileStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<StudentProfile> profileHasUser(long userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    static Specification<StudentProfile> profileMatchesStudentNumber(String normalizedQuery) {
        String pattern = "%" + escapeLike(normalizedQuery) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("studentNumber")), pattern, ESCAPE);
    }

    static Specification<Enrollment> enrollmentHasStudentProfile(long studentProfileId) {
        return (root, query, cb) -> cb.equal(root.get("studentProfile").get("id"), studentProfileId);
    }

    static Specification<Enrollment> enrollmentHasClassGroup(long classGroupId) {
        return (root, query, cb) -> cb.equal(root.get("classGroupId"), classGroupId);
    }

    static Specification<Enrollment> enrollmentHasAcademicYear(long academicYearId) {
        return (root, query, cb) -> cb.equal(root.get("academicYearId"), academicYearId);
    }

    static Specification<Enrollment> enrollmentHasStatus(EnrollmentStatus status) {
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
