package com.esic.connect.audit.internal;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Description d'une trace d'audit à écrire, telle qu'elle traverse
 * l'outbox (EF-AUD-003).
 *
 * <p><strong>{@code occurredAt} est figé à la construction</strong>, donc
 * au moment de l'action, et non au moment où le gestionnaire l'écrit.
 * L'audit doit dire quand les choses se sont passées ; une reprise
 * différée d'une heure ne doit pas déplacer l'événement d'une heure.
 *
 * <p>Le contenu respecte les exclusions du cahier (§23.3) : ni mot de
 * passe, ni secret, ni jeton, ni donnée biométrique, ni adresse IP.
 */
record AuditIntent(Instant occurredAt,
                   Long actorUserId,
                   UUID actorPublicIdSnapshot,
                   String actorDisplaySnapshot,
                   String actorRole,
                   String action,
                   String category,
                   String resourceType,
                   UUID resourcePublicId,
                   String result,
                   String reason) {

    static AuditIntent of(Instant occurredAt, Long actorUserId, String action, String category,
                          String resourceType, String result) {
        return new AuditIntent(occurredAt, actorUserId, null, null, null,
                action, category, resourceType, null, result, null);
    }

    AuditIntent withResource(UUID resourcePublicId) {
        return new AuditIntent(occurredAt, actorUserId, actorPublicIdSnapshot, actorDisplaySnapshot,
                actorRole, action, category, resourceType, resourcePublicId, result, reason);
    }

    AuditIntent withReason(String reason) {
        return new AuditIntent(occurredAt, actorUserId, actorPublicIdSnapshot, actorDisplaySnapshot,
                actorRole, action, category, resourceType, resourcePublicId, result, reason);
    }

    AuditIntent withActorSnapshot(UUID publicId, String display, String role) {
        return new AuditIntent(occurredAt, actorUserId, publicId, display, role,
                action, category, resourceType, resourcePublicId, result, reason);
    }

    /** Sérialisation JSON du message d'outbox. {@code null} admis pour les champs facultatifs. */
    Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("occurredAt", occurredAt.toString());
        payload.put("actorUserId", actorUserId);
        payload.put("actorPublicId", asText(actorPublicIdSnapshot));
        payload.put("actorDisplay", actorDisplaySnapshot);
        payload.put("actorRole", actorRole);
        payload.put("action", action);
        payload.put("category", category);
        payload.put("resourceType", resourceType);
        payload.put("resourcePublicId", asText(resourcePublicId));
        payload.put("result", result);
        payload.put("reason", reason);
        return payload;
    }

    static AuditIntent fromPayload(Map<String, Object> payload) {
        return new AuditIntent(
                Instant.parse(text(payload, "occurredAt")),
                // Jackson relit un entier JSON en Integer ou Long selon sa
                // taille : la conversion doit passer par Number, jamais
                // par un transtypage direct vers Long.
                number(payload.get("actorUserId")),
                uuid(text(payload, "actorPublicId")),
                text(payload, "actorDisplay"),
                text(payload, "actorRole"),
                text(payload, "action"),
                text(payload, "category"),
                text(payload, "resourceType"),
                uuid(text(payload, "resourcePublicId")),
                text(payload, "result"),
                text(payload, "reason"));
    }

    private static String asText(UUID value) {
        return value == null ? null : value.toString();
    }

    private static String text(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    private static Long number(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }
}
