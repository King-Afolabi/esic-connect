package com.esic.connect.studentimport.internal;

import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * Normalisation <em>technique</em> des valeurs de cellule (rapport §5.2) :
 * {@code trim}, casse, réduction d'espaces, analyse de date et de booléen.
 * Composant pur, sans dépendance métier. La décision de gravité
 * ({@code WARNING} / {@code ERROR}) d'une valeur mal formée appartient au
 * checkpoint de validation (CP4) : ici on se contente de produire la
 * valeur normalisée et un indicateur de forme.
 */
final class CsvValueNormalizer {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter FR_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final Set<String> TRUE_TOKENS = Set.of("true", "1", "oui", "yes", "o");
    private static final Set<String> FALSE_TOKENS = Set.of("false", "0", "non", "no", "n");
    private static final int RECEIVED_VALUE_MAX = 200;

    private CsvValueNormalizer() {
    }

    /** {@code null} si vide/blanc, sinon la valeur rognée. */
    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** {@code trim} + espaces internes multiples réduits à un seul (noms). */
    static String collapseSpaces(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.replaceAll("\\s+", " ");
    }

    static String lowerCase(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    static String upperCase(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    /** {@code trim} puis suppression des espaces, points, tirets, parenthèses (rapport §5.2). */
    static String normalizePhone(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String cleaned = trimmed.replaceAll("[\\s.()\\-]", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** Analyse {@code yyyy-MM-dd} ou {@code dd/MM/yyyy}. */
    static BirthDateResult parseBirthDate(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return new BirthDateResult(null, false, false);
        }
        for (DateTimeFormatter formatter : new DateTimeFormatter[] {ISO_DATE, FR_DATE}) {
            try {
                return new BirthDateResult(LocalDate.parse(trimmed, formatter), true, false);
            } catch (DateTimeParseException ignored) {
                // essaie le format suivant
            }
        }
        return new BirthDateResult(null, true, true);
    }

    /** Analyse un booléen tolérant : {@code true/false/oui/non/1/0/vide}. */
    static WorkStudyResult parseWorkStudy(String value) {
        String token = lowerCase(value);
        if (token == null) {
            return new WorkStudyResult(null, false, false);
        }
        if (TRUE_TOKENS.contains(token)) {
            return new WorkStudyResult(Boolean.TRUE, true, false);
        }
        if (FALSE_TOKENS.contains(token)) {
            return new WorkStudyResult(Boolean.FALSE, true, false);
        }
        return new WorkStudyResult(null, true, true);
    }

    /**
     * Nom de fichier assaini (rapport §7.1) : basename seul, caractères
     * hors {@code [A-Za-z0-9._ -]} remplacés par {@code _}, point initial
     * retiré. Jamais utilisé comme chemin.
     */
    static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "import.csv";
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
            base = "import.csv";
        }
        return base.length() > 255 ? base.substring(0, 255) : base;
    }

    /** Empreinte hex minuscule SHA-256 du contenu reçu (le contenu n'est jamais conservé). */
    static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    /** Valeur reçue tronquée pour {@code student_import_row_issue.received_value} (jamais dans l'audit). */
    static String truncateReceivedValue(String value) {
        if (value == null) {
            return null;
        }
        String oneLine = value.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() > RECEIVED_VALUE_MAX ? oneLine.substring(0, RECEIVED_VALUE_MAX) : oneLine;
    }

    /** @param present {@code true} si la cellule contenait quelque chose (même mal formé) */
    record BirthDateResult(LocalDate value, boolean present, boolean malformed) {
    }

    /** @param present {@code true} si la cellule contenait quelque chose (même mal formé) */
    record WorkStudyResult(Boolean value, boolean present, boolean malformed) {
    }
}
