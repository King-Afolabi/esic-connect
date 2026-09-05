package com.esic.connect.audit.internal;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

/**
 * Filtres de consultation de la piste d'audit (EF-AUD-002 ;
 * docs/02 §23.4 — « consultable par les rôles autorisés, filtrable,
 * exportable »).
 *
 * <p>Chaque filtre porte sur une colonne <strong>fermée</strong> :
 * action, catégorie, type de ressource, acteur, corrélation, période.
 * Aucun filtre libre sur {@code reason} ni sur les colonnes JSON — les
 * y ouvrir transformerait la piste d'audit en moteur de recherche sur
 * du texte que le produit n'a pas vocation à indexer.
 */
final class AuditEventSpecifications {

    private AuditEventSpecifications() {
    }

    static Specification<AuditEvent> occurredFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), from);
    }

    static Specification<AuditEvent> occurredUntil(Instant to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("occurredAt"), to);
    }

    static Specification<AuditEvent> hasAction(String action) {
        return (root, query, cb) -> cb.equal(root.get("action"), action);
    }

    static Specification<AuditEvent> hasCategory(String category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    static Specification<AuditEvent> hasResourceType(String resourceType) {
        return (root, query, cb) -> cb.equal(root.get("resourceType"), resourceType);
    }

    static Specification<AuditEvent> hasResult(String result) {
        return (root, query, cb) -> cb.equal(root.get("result"), result);
    }

    static Specification<AuditEvent> hasActor(UUID actorPublicId) {
        return (root, query, cb) -> cb.equal(root.get("actorPublicIdSnapshot"), actorPublicId);
    }

    static Specification<AuditEvent> hasResource(UUID resourcePublicId) {
        return (root, query, cb) -> cb.equal(root.get("resourcePublicId"), resourcePublicId);
    }

    static Specification<AuditEvent> hasCorrelation(UUID correlationId) {
        return (root, query, cb) -> cb.equal(root.get("correlationId"), correlationId);
    }
}
