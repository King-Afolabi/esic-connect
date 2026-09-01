package com.esic.connect.planning.internal;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Identité <strong>stable</strong> d'un créneau de planning à travers les
 * versions (DEC-G1-002 ; audit G1-B.1).
 *
 * <p>Déterministe à partir du planning et du {@code slot_key}, jamais un
 * {@code planning_entry.public_id} (aléatoire, propre à chaque version).
 * Portée par {@code planning_entry.slot_public_id} et
 * {@code course_session.planning_slot_public_id}. La même formule est
 * utilisée à la simulation (détection de conflit avec des séances déjà
 * publiées) et à la publication.
 */
final class PlanningSlotIds {

    private PlanningSlotIds() {
    }

    /**
     * @param schedulePublicId identifiant public du {@code planning_schedule}
     * @param slotKey          libellé de créneau du CSV (non nul)
     * @return l'identité stable du créneau, ou {@code null} si
     *         {@code schedulePublicId} est {@code null} (aucun planning
     *         encore publié pour cette classe)
     */
    static UUID stableSlotId(UUID schedulePublicId, String slotKey) {
        if (schedulePublicId == null || slotKey == null) {
            return null;
        }
        return UUID.nameUUIDFromBytes(
                (schedulePublicId + "|" + slotKey).getBytes(StandardCharsets.UTF_8));
    }
}
