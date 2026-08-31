package com.esic.connect.studentimport.internal;

import com.esic.connect.studentimport.internal.StudentImportIssueDrafts.RowIssueDraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dé-duplication <em>à l'intérieur du fichier</em> (rapport §5.2, §14.1) :
 * plusieurs lignes portant la même adresse ou le même numéro étudiant.
 *
 * <ul>
 *   <li>charge <strong>identique</strong> sur toutes les colonnes métier
 *       → {@code WARNING} sur chaque occurrence (probable doublon
 *       inoffensif) ;</li>
 *   <li>charge <strong>divergente</strong> → {@code ERROR} sur chaque
 *       occurrence (le fichier se contredit).</li>
 * </ul>
 *
 * Pur, sans base. Renvoie une map {@code rowNumber → anomalies}.
 */
final class FileDuplicateDetector {

    private FileDuplicateDetector() {
    }

    static Map<Integer, List<RowIssueDraft>> detect(List<NormalizedRow> rows) {
        Map<Integer, List<RowIssueDraft>> byRow = new HashMap<>();

        groupBy(rows, row -> row.email() == null ? null : row.email().toLowerCase(Locale.ROOT))
                .forEach((email, group) -> flag(byRow, group,
                        StudentImportIssueCodes.EMAIL_DUPLICATE_IN_FILE, "email",
                        "Cette adresse électronique apparaît sur plusieurs lignes du fichier"));

        groupBy(rows, row -> row.studentNumber() == null ? null : row.studentNumber().toUpperCase(Locale.ROOT))
                .forEach((number, group) -> flag(byRow, group,
                        StudentImportIssueCodes.STUDENT_NUMBER_DUPLICATE_IN_FILE, "student_number",
                        "Ce numéro étudiant apparaît sur plusieurs lignes du fichier"));

        return byRow;
    }

    private static Map<String, List<NormalizedRow>> groupBy(List<NormalizedRow> rows,
                                                            java.util.function.Function<NormalizedRow, String> key) {
        Map<String, List<NormalizedRow>> groups = new LinkedHashMap<>();
        for (NormalizedRow row : rows) {
            String k = key.apply(row);
            if (k != null) {
                groups.computeIfAbsent(k, ignored -> new ArrayList<>()).add(row);
            }
        }
        groups.values().removeIf(group -> group.size() < 2);
        return groups;
    }

    private static void flag(Map<Integer, List<RowIssueDraft>> byRow, List<NormalizedRow> group,
                             String code, String column, String messagePrefix) {
        boolean identical = group.stream().map(FileDuplicateDetector::businessKey).distinct().count() == 1;
        StudentImportIssueSeverity severity = identical
                ? StudentImportIssueSeverity.WARNING : StudentImportIssueSeverity.ERROR;
        String message = messagePrefix + (identical
                ? " avec des informations identiques."
                : " avec des informations différentes ; corrigez le fichier.");
        for (NormalizedRow row : group) {
            byRow.computeIfAbsent(row.rowNumber(), ignored -> new ArrayList<>())
                    .add(new RowIssueDraft(severity, code, message, column, null, null));
        }
    }

    /** Empreinte des colonnes métier d'une ligne, pour comparer deux occurrences. */
    private static String businessKey(NormalizedRow row) {
        return String.join("",
                nullSafe(row.lastName()), nullSafe(row.firstName()), nullSafe(row.email()),
                nullSafe(row.phone()), nullSafe(row.formationCode()), nullSafe(row.classCode()),
                nullSafe(row.academicYear()), nullSafe(row.studentNumber()),
                String.valueOf(row.birthDate()), String.valueOf(row.workStudy()),
                nullSafe(row.companyName()));
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
