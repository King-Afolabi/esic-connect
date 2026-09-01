package com.esic.connect.planning.internal;

import java.util.Locale;
import java.util.Optional;

/**
 * Colonnes du CSV de planning G1 (docs/02 §13.3, adapté par
 * {@code DEC-G1-002} — {@code slot_key} obligatoire — et
 * {@code DEC-G1-B-CSV} — {@code teacher_public_id} au lieu de
 * {@code teacher_email}). Ordre des colonnes indifférent ; l'en-tête est
 * apparié par nom (insensible à la casse, {@code ' '} / {@code '-'}
 * assimilés à {@code '_'}).
 *
 * <p>Une seule classe et une seule année par import : elles sont portées
 * par la requête ({@code classGroupPublicId}), pas par les lignes.
 */
enum PlanningColumn {

    SLOT_KEY("slot_key", true),
    SESSION_DATE("session_date", true),
    START_TIME("start_time", true),
    END_TIME("end_time", true),
    TIME_ZONE_ID("time_zone_id", true),
    TITLE("title", true),
    TEACHER_PUBLIC_ID("teacher_public_id", true),
    ROOM_CODE("room_code", false);

    private final String header;
    private final boolean mandatory;

    PlanningColumn(String header, boolean mandatory) {
        this.header = header;
        this.mandatory = mandatory;
    }

    String header() {
        return header;
    }

    boolean mandatory() {
        return mandatory;
    }

    static Optional<PlanningColumn> forHeader(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (PlanningColumn column : values()) {
            if (column.header.equals(normalized)) {
                return Optional.of(column);
            }
        }
        return Optional.empty();
    }
}
