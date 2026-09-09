package com.esic.connect.notification.internal;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Demande de notification telle qu'elle traverse l'outbox — <em>ce qu'il
 * faut annoncer et à qui</em>, jamais la liste des comptes déjà résolue.
 *
 * <p><strong>Pourquoi l'audience est décrite et non énumérée.</strong>
 * L'écouteur s'exécute dans la transaction métier, où l'effectif d'une
 * classe ou la liste des remplaçants est encore en cours de modification.
 * Décrire l'audience — « les apprenants de ces classes », « le formateur
 * de cette séance » — et la résoudre au moment du traitement, après
 * commit, donne l'état réellement établi. Cela garde aussi la ligne
 * d'outbox petite et stable : une classe de trente apprenants n'y écrit
 * pas trente identifiants.
 *
 * <p>Aucune donnée personnelle : identifiants publics, libellés neutres.
 */
record NotificationRequest(NotificationType type,
                           String resourceType,
                           UUID resourcePublicId,
                           UUID eventKey,
                           String title,
                           String body,
                           Set<UUID> explicitRecipients,
                           Set<UUID> sessionPublicIds,
                           Set<UUID> classPublicIds,
                           boolean includeSessionTeachers,
                           boolean includeStudents,
                           boolean includeManagers,
                           LocalDate referenceDate) {

    /** Type de message routant vers {@link NotificationOutboxHandler}. */
    static final String MESSAGE_TYPE = "NOTIFICATION";

    static Builder of(NotificationType type, String resourceType, UUID resourcePublicId, UUID eventKey) {
        return new Builder(type, resourceType, resourcePublicId, eventKey);
    }

    Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type.name());
        payload.put("resourceType", resourceType);
        payload.put("resourcePublicId", resourcePublicId.toString());
        payload.put("eventKey", eventKey.toString());
        payload.put("title", title);
        payload.put("body", body);
        payload.put("explicitRecipients", explicitRecipients.stream().map(UUID::toString).toList());
        payload.put("sessionPublicIds", sessionPublicIds.stream().map(UUID::toString).toList());
        payload.put("classPublicIds", classPublicIds.stream().map(UUID::toString).toList());
        payload.put("includeSessionTeachers", includeSessionTeachers);
        payload.put("includeStudents", includeStudents);
        payload.put("includeManagers", includeManagers);
        payload.put("referenceDate", referenceDate == null ? null : referenceDate.toString());
        return payload;
    }

    static NotificationRequest fromPayload(Map<String, Object> payload) {
        return new NotificationRequest(
                NotificationType.valueOf(text(payload, "type")),
                text(payload, "resourceType"),
                UUID.fromString(text(payload, "resourcePublicId")),
                UUID.fromString(text(payload, "eventKey")),
                text(payload, "title"),
                text(payload, "body"),
                uuids(payload, "explicitRecipients"),
                uuids(payload, "sessionPublicIds"),
                uuids(payload, "classPublicIds"),
                flag(payload, "includeSessionTeachers"),
                flag(payload, "includeStudents"),
                flag(payload, "includeManagers"),
                optionalDate(text(payload, "referenceDate")));
    }

    private static String text(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    private static boolean flag(Map<String, Object> payload, String key) {
        return Boolean.TRUE.equals(payload.get(key));
    }

    private static Set<UUID> uuids(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof List<?> list)) {
            return Set.of();
        }
        Set<UUID> result = new LinkedHashSet<>();
        for (Object item : list) {
            if (item != null) {
                result.add(UUID.fromString(item.toString()));
            }
        }
        return result;
    }

    private static LocalDate optionalDate(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    /** Construction lisible d'une audience, côté écouteur. */
    static final class Builder {

        private final NotificationType type;
        private final String resourceType;
        private final UUID resourcePublicId;
        private final UUID eventKey;
        private final Set<UUID> explicitRecipients = new LinkedHashSet<>();
        private final Set<UUID> classPublicIds = new LinkedHashSet<>();
        private final Set<UUID> sessionPublicIds = new LinkedHashSet<>();
        private String title = "";
        private String body = "";
        private boolean includeSessionTeachers;
        private boolean includeStudents;
        private boolean includeManagers;
        private LocalDate referenceDate;

        private Builder(NotificationType type, String resourceType, UUID resourcePublicId, UUID eventKey) {
            this.type = type;
            this.resourceType = resourceType;
            this.resourcePublicId = resourcePublicId;
            this.eventKey = eventKey;
        }

        Builder label(String title, String body) {
            this.title = title;
            this.body = body;
            return this;
        }

        Builder recipients(java.util.Collection<UUID> recipients) {
            if (recipients != null) {
                recipients.stream().filter(java.util.Objects::nonNull).forEach(explicitRecipients::add);
            }
            return this;
        }

        /** Formateur principal et remplaçants de ces séances (§21.3). */
        Builder sessions(java.util.Collection<UUID> ids) {
            if (ids != null) {
                ids.stream().filter(java.util.Objects::nonNull).forEach(sessionPublicIds::add);
            }
            this.includeSessionTeachers = true;
            return this;
        }

        Builder session(UUID sessionPublicId) {
            return sessions(sessionPublicId == null ? Set.of() : Set.of(sessionPublicId));
        }

        Builder classes(java.util.Collection<UUID> ids) {
            if (ids != null) {
                ids.stream().filter(java.util.Objects::nonNull).forEach(classPublicIds::add);
            }
            return this;
        }

        Builder students() {
            this.includeStudents = true;
            return this;
        }

        Builder managers() {
            this.includeManagers = true;
            return this;
        }

        Builder on(LocalDate referenceDate) {
            this.referenceDate = referenceDate;
            return this;
        }

        NotificationRequest build() {
            return new NotificationRequest(type, resourceType, resourcePublicId, eventKey, title, body,
                    Set.copyOf(explicitRecipients), Set.copyOf(sessionPublicIds), Set.copyOf(classPublicIds),
                    includeSessionTeachers, includeStudents, includeManagers, referenceDate);
        }
    }
}
