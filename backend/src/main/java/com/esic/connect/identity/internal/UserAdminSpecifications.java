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

    /**
     * Recherche insensible à la casse sur l'email, le prénom, le nom ou le
     * numéro étudiant (refonte 2026-09, ex-{@code student_profile.student_number} —
     * l'écran « Apprenants » doit rester cherchable par ce numéro).
     * {@code LOWER(NULL) LIKE …} vaut {@code NULL} (donc jamais vrai) : un
     * compte sans numéro n'est simplement jamais retenu par cette clause.
     */
    static Specification<UserAccount> matchesText(String normalizedQuery) {
        String pattern = "%" + escapeLike(normalizedQuery) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("email")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("firstName")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("lastName")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("studentNumber")), pattern, ESCAPE));
    }

    /**
     * Restreint le résultat aux comptes dont l'identifiant interne figure
     * dans {@code ids} — périmètre calculé par un autre module (par
     * exemple {@code enrollment}, pour restreindre la liste des apprenants
     * au périmètre pédagogique de l'appelant). {@code ids} vide ⇒
     * prédicat toujours faux (aucune fuite) : l'absence de restriction se
     * signale par {@code ids == null}, jamais par une collection vide.
     */
    static Specification<UserAccount> idIn(java.util.Collection<Long> ids) {
        return (root, query, cb) -> (ids == null || ids.isEmpty()) ? cb.disjunction() : root.get("id").in(ids);
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
