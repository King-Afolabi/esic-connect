package com.esic.connect.planning.internal;

import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Normalisation technique des valeurs de cellule d'un CSV de planning
 * (analogue de {@code studentimport.internal.CsvValueNormalizer}, réduit
 * aux besoins du planning). Composant pur : aucune règle métier, aucune
 * base. La décision de gravité d'une valeur mal formée appartient à
 * {@link PlanningSimulationService}.
 */
final class PlanningCsvValues {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter FR_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int RECEIVED_VALUE_MAX = 200;

    private PlanningCsvValues() {
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Analyse {@code yyyy-MM-dd} ou {@code dd/MM/yyyy}. */
    static Optional<LocalDate> parseDate(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return Optional.empty();
        }
        for (DateTimeFormatter formatter : new DateTimeFormatter[] {ISO_DATE, FR_DATE}) {
            try {
                return Optional.of(LocalDate.parse(trimmed, formatter));
            } catch (DateTimeParseException ignored) {
                // format suivant
            }
        }
        return Optional.empty();
    }

    /** Analyse {@code HH:mm} ou {@code HH:mm:ss}. */
    static Optional<LocalTime> parseTime(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return Optional.empty();
        }
        for (DateTimeFormatter formatter : new DateTimeFormatter[] {HH_MM, HH_MM_SS}) {
            try {
                return Optional.of(LocalTime.parse(trimmed, formatter));
            } catch (DateTimeParseException ignored) {
                // format suivant
            }
        }
        return Optional.empty();
    }

    /** Fuseau IANA valide, ou vide. */
    static Optional<ZoneId> parseZone(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(ZoneId.of(trimmed));
        } catch (DateTimeException notAZone) {
            return Optional.empty();
        }
    }

    /** Instant UTC d'une date + heure locale dans un fuseau donné. */
    static java.time.Instant toUtc(LocalDate date, LocalTime time, ZoneId zone) {
        return ZonedDateTime.of(date, time, zone).toInstant();
    }

    static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "planning.csv";
        }
        String base;
        try {
            base = Paths.get(fileName.replace('\\', '/')).getFileName().toString();
        } catch (RuntimeException invalidPath) {
            base = fileName;
        }
        base = base.replaceAll("[^A-Za-z0-9._ -]", "_").strip();
        while (base.startsWith(".")) {
            base = base.substring(1);
        }
        if (base.isEmpty()) {
            base = "planning.csv";
        }
        return base.length() > 255 ? base.substring(0, 255) : base;
    }

    static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    /** Valeur reçue tronquée pour {@code planning_import_row_issue.received_value}. */
    static String truncateReceivedValue(String value) {
        if (value == null) {
            return null;
        }
        String oneLine = value.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() > RECEIVED_VALUE_MAX ? oneLine.substring(0, RECEIVED_VALUE_MAX) : oneLine;
    }

    /** Tronque à {@code max} pour respecter une longueur de colonne. */
    static String clamp(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
