package com.esic.connect.integration.internal;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sérialisation iCalendar (RFC 5545) du planning d'une personne
 * (EF-INT-001).
 *
 * <p>Trois contraintes du format sont respectées parce qu'un agenda
 * refuse silencieusement un flux qui les ignore :
 * <ul>
 *   <li>les lignes sont terminées par {@code CRLF} et
 *       <strong>pliées</strong> à 75 octets — pas 75 caractères : un
 *       accent occupe deux octets en UTF-8, et plier au mauvais endroit
 *       coupe le caractère en deux ;</li>
 *   <li>{@code ; , \} et les retours à la ligne sont échappés dans les
 *       valeurs texte ;</li>
 *   <li>{@code UID} est stable d'un rafraîchissement à l'autre — sinon
 *       l'agenda ne met pas l'événement à jour, il en empile un
 *       nouveau.</li>
 * </ul>
 *
 * <p>Le flux ne porte que le planning de la personne : ni note interne,
 * ni statut d'assiduité, ni identité d'un tiers (AC-034).
 */
final class IcsWriter {

    private static final String CRLF = "\r\n";
    private static final int FOLD_OCTETS = 73;
    private static final DateTimeFormatter UTC_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private IcsWriter() {
    }

    /**
     * @param calendarName nom affiché par l'agenda abonné
     * @param stamp        horodatage {@code DTSTAMP} commun au flux
     * @param events       événements, déjà filtrés au périmètre de la personne
     */
    static String write(String calendarName, Instant stamp, List<IcsEvent> events) {
        StringBuilder sb = new StringBuilder();
        line(sb, "BEGIN:VCALENDAR");
        line(sb, "VERSION:2.0");
        line(sb, "PRODID:-//ESIC//ESIC Connect//FR");
        line(sb, "CALSCALE:GREGORIAN");
        line(sb, "METHOD:PUBLISH");
        line(sb, "X-WR-CALNAME:" + escape(calendarName));
        // Rafraîchissement suggéré : un agenda qui interroge toutes les
        // minutes n'apprendrait rien de plus, le planning changeant à
        // l'échelle de la journée.
        line(sb, "X-PUBLISHED-TTL:PT1H");
        line(sb, "REFRESH-INTERVAL;VALUE=DURATION:PT1H");
        for (IcsEvent event : events) {
            line(sb, "BEGIN:VEVENT");
            line(sb, "UID:" + event.uid());
            line(sb, "DTSTAMP:" + UTC_STAMP.format(stamp));
            line(sb, "DTSTART:" + UTC_STAMP.format(event.startsAt()));
            line(sb, "DTEND:" + UTC_STAMP.format(event.endsAt()));
            line(sb, "SUMMARY:" + escape(event.summary()));
            if (event.location() != null && !event.location().isBlank()) {
                line(sb, "LOCATION:" + escape(event.location()));
            }
            if (event.description() != null && !event.description().isBlank()) {
                line(sb, "DESCRIPTION:" + escape(event.description()));
            }
            if (event.url() != null && !event.url().isBlank()) {
                line(sb, "URL:" + escape(event.url()));
            }
            line(sb, "STATUS:" + event.status());
            line(sb, "END:VEVENT");
        }
        line(sb, "END:VCALENDAR");
        return sb.toString();
    }

    /** Écrit une ligne pliée selon RFC 5545 §3.1, en comptant les octets. */
    private static void line(StringBuilder sb, String content) {
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= FOLD_OCTETS + 2) {
            sb.append(content).append(CRLF);
            return;
        }
        int index = 0;
        boolean first = true;
        while (index < content.length()) {
            int octets = 0;
            int end = index;
            int budget = first ? FOLD_OCTETS + 2 : FOLD_OCTETS;
            while (end < content.length()) {
                int codePoint = content.codePointAt(end);
                int width = new String(Character.toChars(codePoint))
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (octets + width > budget) {
                    break;
                }
                octets += width;
                end += Character.charCount(codePoint);
            }
            if (!first) {
                sb.append(' ');
            }
            sb.append(content, index, end).append(CRLF);
            index = end;
            first = false;
        }
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    /**
     * @param uid         identifiant stable de l'événement
     * @param summary     intitulé
     * @param location    salle ou modalité ({@code null} possible)
     * @param description complément ({@code null} possible)
     * @param url         lien distanciel ({@code null} possible)
     * @param status      {@code CONFIRMED} / {@code CANCELLED} / {@code TENTATIVE}
     */
    record IcsEvent(
            String uid,
            String summary,
            String location,
            String description,
            String url,
            String status,
            Instant startsAt,
            Instant endsAt) {
    }
}
