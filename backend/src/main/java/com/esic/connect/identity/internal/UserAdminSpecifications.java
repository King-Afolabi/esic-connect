package com.esic.connect.identity.internal;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * Fabriques de {@link Specification} pour la consultation administrative
 * des comptes. Le filtre texte est déjà normalisé (trim, minuscules,
 * longueur bornée) par le service ; les métacaractères {@code LIKE} sont
 * échappés ici pour éviter toute injection de motif.
 */
final class UserAdminSpecifications {

    private static final char ESCAPE = '\\';

    private UserAdminSpecifications() {
    }

    static Specification<UserAccount> hasStatus(AccountStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** Vrai si le compte possède une affectation active pour ce rôle. */
    static Specification<UserAccount> hasActiveRole(RoleCode role) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<UserRole> userRole = sub.from(UserRole.class);
            sub.select(userRole.get("user").get("id"));
            sub.where(cb.and(
                    cb.equal(userRole.get("user").get("id"), root.get("id")),
                    cb.isTrue(userRole.get("active")),
                    cb.equal(userRole.get("role").get("code"), role)));
            return cb.exists(sub);
        };
    }

    /** Recherche insensible à la casse sur l'email, le prénom ou le nom. */
    static Specification<UserAccount> matchesText(String normalizedQuery) {
        String pattern = "%" + escapeLike(normalizedQuery) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("email")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("firstName")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("lastName")), pattern, ESCAPE));
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
