package com.esic.connect.audit.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO de consultation de la piste d'audit (EF-AUD-002).
 *
 * <p>Ne portent <strong>jamais</strong> les colonnes JSON
 * ({@code old_values_json}, {@code new_values_json},
 * {@code metadata_json}) : leur contenu dépend du module émetteur et
 * n'est pas gouverné pour l'affichage. Ni adresse IP, ni jeton, ni
 * secret — ils ne sont pas dans la table (§23.3).
 */
final class AuditResponses {

    private AuditResponses() {
    }

    record AuditRow(
            UUID publicId,
            Instant occurredAt,
            String actorDisplay,
            UUID actorPublicId,
            String actorRole,
            String action,
            String category,
            String resourceType,
            UUID resourcePublicId,
            String result,
            String reason,
            UUID correlationId) {
    }

    /** Valeurs distinctes réellement présentes, pour peupler les filtres. */
    record AuditFacets(
            List<String> actions,
            List<String> categories,
            List<String> resourceTypes,
            List<String> results) {
    }
}
