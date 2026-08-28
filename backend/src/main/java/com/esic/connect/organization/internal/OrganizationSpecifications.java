package com.esic.connect.organization.internal;

import org.springframework.data.jpa.domain.Specification;

/**
 * Fabriques de {@link Specification} pour les consultations du module. Le
 * filtre texte est déjà normalisé (trim, minuscules, longueur bornée) par
 * l'appelant ; les métacaractères {@code LIKE} sont échappés ici pour
 * éviter toute injection de motif.
 */
final class OrganizationSpecifications {

    private static final char ESCAPE = '\\';

    private OrganizationSpecifications() {
    }

    static Specification<Site> siteHasStatus(OrganizationStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<Site> siteMatchesText(String normalizedQuery) {
        String pattern = likePattern(normalizedQuery);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("name")), pattern, ESCAPE));
    }

    static Specification<Building> buildingHasSite(Long siteId) {
        return (root, query, cb) -> cb.equal(root.get("site").get("id"), siteId);
    }

    static Specification<Building> buildingHasStatus(OrganizationStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<Building> buildingMatchesText(String normalizedQuery) {
        String pattern = likePattern(normalizedQuery);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("name")), pattern, ESCAPE));
    }

    static Specification<Room> roomHasSite(Long siteId) {
        return (root, query, cb) -> cb.equal(root.get("site").get("id"), siteId);
    }

    static Specification<Room> roomHasBuilding(Long buildingId) {
        return (root, query, cb) -> cb.equal(root.get("building").get("id"), buildingId);
    }

    static Specification<Room> roomHasStatus(OrganizationStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<Room> roomMatchesText(String normalizedQuery) {
        String pattern = likePattern(normalizedQuery);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("name")), pattern, ESCAPE));
    }

    static Specification<SiteNetworkRange> rangeHasSite(Long siteId) {
        return (root, query, cb) -> cb.equal(root.get("site").get("id"), siteId);
    }

    static Specification<SiteNetworkRange> rangeIsActive(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
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
