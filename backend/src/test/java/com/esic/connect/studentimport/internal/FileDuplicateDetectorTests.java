package com.esic.connect.studentimport.internal;

import com.esic.connect.studentimport.internal.StudentImportIssueDrafts.RowIssueDraft;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dé-duplication à l'intérieur du fichier (rapport §5.2, §14.1) : charge
 * identique → {@code WARNING} sur chaque occurrence ; charge divergente →
 * {@code ERROR} sur chaque occurrence.
 */
class FileDuplicateDetectorTests {

    private static List<NormalizedRow> rows(String csv) {
        ParsedCsv parsed = CsvParser.parse(csv, 500);
        return parsed.rows().stream().map(r -> CsvRowNormalizer.normalize(parsed, r)).toList();
    }

    private static List<StudentImportIssueSeverity> severities(Map<Integer, List<RowIssueDraft>> byRow, int rowNumber) {
        return byRow.getOrDefault(rowNumber, List.of()).stream().map(RowIssueDraft::severity).toList();
    }

    @Test
    void identicalDuplicateEmailsAreWarnings() {
        Map<Integer, List<RowIssueDraft>> byRow = FileDuplicateDetector.detect(rows(
                "last_name,first_name,email,formation_code,class_code,academic_year\n"
                        + "Doe,Jane,jane@x.test,BTS,C1,2026-2027\n"
                        + "Doe,Jane,JANE@X.TEST,BTS,C1,2026-2027\n"));
        assertThat(severities(byRow, 2)).containsExactly(StudentImportIssueSeverity.WARNING);
        assertThat(severities(byRow, 3)).containsExactly(StudentImportIssueSeverity.WARNING);
        assertThat(byRow.get(2).get(0).code()).isEqualTo(StudentImportIssueCodes.EMAIL_DUPLICATE_IN_FILE);
    }

    @Test
    void divergentDuplicateEmailsAreErrorsOnBothRows() {
        Map<Integer, List<RowIssueDraft>> byRow = FileDuplicateDetector.detect(rows(
                "last_name,first_name,email,formation_code,class_code,academic_year\n"
                        + "Doe,Jane,jane@x.test,BTS,C1,2026-2027\n"
                        + "Roe,Jane,jane@x.test,BTS,C2,2026-2027\n"));
        assertThat(severities(byRow, 2)).containsExactly(StudentImportIssueSeverity.ERROR);
        assertThat(severities(byRow, 3)).containsExactly(StudentImportIssueSeverity.ERROR);
    }

    @Test
    void divergentDuplicateStudentNumbersAreErrors() {
        Map<Integer, List<RowIssueDraft>> byRow = FileDuplicateDetector.detect(rows(
                "last_name,first_name,email,formation_code,class_code,academic_year,student_number\n"
                        + "Doe,Jane,jane@x.test,BTS,C1,2026-2027,ESIC-1\n"
                        + "Roe,John,john@x.test,BTS,C1,2026-2027,esic-1\n"));
        assertThat(byRow.get(2)).extracting(RowIssueDraft::code)
                .containsExactly(StudentImportIssueCodes.STUDENT_NUMBER_DUPLICATE_IN_FILE);
        assertThat(severities(byRow, 3)).containsExactly(StudentImportIssueSeverity.ERROR);
    }

    @Test
    void uniqueRowsProduceNoDuplicateIssue() {
        Map<Integer, List<RowIssueDraft>> byRow = FileDuplicateDetector.detect(rows(
                "last_name,first_name,email,formation_code,class_code,academic_year\n"
                        + "Doe,Jane,jane@x.test,BTS,C1,2026-2027\n"
                        + "Roe,John,john@x.test,BTS,C1,2026-2027\n"));
        assertThat(byRow).isEmpty();
    }
}
