package com.esic.connect.studentimport.internal;

import com.esic.connect.studentimport.internal.StudentImportIssueDrafts.RowIssueDraft;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Règles de validation de champ (rapport §5.2, §5.3, §14.1) : valeurs
 * obligatoires, syntaxe d'e-mail, téléphone, date de naissance, booléen
 * d'alternance, entreprise requise ; dérivation du statut de ligne.
 */
class StudentImportFieldValidatorTests {

    private static NormalizedRow row(String csvBody) {
        ParsedCsv parsed = CsvParser.parse(csvBody, 500);
        return CsvRowNormalizer.normalize(parsed, parsed.rows().get(0));
    }

    private static List<String> codes(List<RowIssueDraft> issues) {
        return issues.stream().map(RowIssueDraft::code).toList();
    }

    @Test
    void aFullyValidRowHasNoIssueAndIsValid() {
        List<RowIssueDraft> issues = StudentImportFieldValidator.validate(row(
                "last_name,first_name,email,formation_code,class_code,academic_year\n"
                        + "Doe,Jane,jane@x.test,BTS-SIO,C1,2026-2027\n"));
        assertThat(issues).isEmpty();
        assertThat(StudentImportFieldValidator.statusFrom(issues)).isEqualTo(StudentImportRowStatus.VALID);
    }

    @Test
    void missingMandatoryValuesAreErrors() {
        List<RowIssueDraft> issues = StudentImportFieldValidator.validate(row(
                "last_name,first_name,email,formation_code,class_code,academic_year\n"
                        + ",Jane,jane@x.test,BTS-SIO,,2026-2027\n"));
        assertThat(codes(issues))
                .contains(StudentImportIssueCodes.REQUIRED_VALUE_MISSING);
        assertThat(issues.stream().filter(i -> i.columnName() != null)
                .map(RowIssueDraft::columnName)).contains("last_name", "class_code");
        assertThat(StudentImportFieldValidator.statusFrom(issues)).isEqualTo(StudentImportRowStatus.ERROR);
    }

    @Test
    void anInvalidEmailSyntaxIsAnError() {
        List<RowIssueDraft> issues = StudentImportFieldValidator.validate(row(
                "last_name,first_name,email,formation_code,class_code,academic_year\n"
                        + "Doe,Jane,not-an-email,BTS-SIO,C1,2026-2027\n"));
        assertThat(codes(issues)).contains(StudentImportIssueCodes.EMAIL_INVALID);
        assertThat(issues.stream().filter(i -> StudentImportIssueCodes.EMAIL_INVALID.equals(i.code())).findFirst()
                .orElseThrow().receivedValue()).isEqualTo("not-an-email");
    }

    @Test
    void aMalformedPhoneIsOnlyAWarningAndNotAnError() {
        List<RowIssueDraft> issues = StudentImportFieldValidator.validate(row(
                "last_name,first_name,email,formation_code,class_code,academic_year,phone\n"
                        + "Doe,Jane,jane@x.test,BTS-SIO,C1,2026-2027,12ab\n"));
        assertThat(codes(issues)).containsExactly(StudentImportIssueCodes.PHONE_FORMAT);
        assertThat(StudentImportFieldValidator.statusFrom(issues)).isEqualTo(StudentImportRowStatus.WARNING);
    }

    @Test
    void aWellFormedPhoneRaisesNoIssue() {
        List<RowIssueDraft> issues = StudentImportFieldValidator.validate(row(
                "last_name,first_name,email,formation_code,class_code,academic_year,phone\n"
                        + "Doe,Jane,jane@x.test,BTS-SIO,C1,2026-2027,+33 1 02 03 04 05\n"));
        assertThat(issues).isEmpty();
    }

    @Test
    void malformedBirthDateAndWorkStudyAreWarnings() {
        List<RowIssueDraft> issues = StudentImportFieldValidator.validate(row(
                "last_name,first_name,email,formation_code,class_code,academic_year,birth_date,work_study\n"
                        + "Doe,Jane,jane@x.test,BTS-SIO,C1,2026-2027,hier,bof\n"));
        assertThat(codes(issues)).contains(StudentImportIssueCodes.BIRTH_DATE_FORMAT,
                StudentImportIssueCodes.WORK_STUDY_INVALID);
        assertThat(StudentImportFieldValidator.statusFrom(issues)).isEqualTo(StudentImportRowStatus.WARNING);
    }

    @Test
    void companyNameIsRecommendedWhenWorkStudyIsTrue() {
        List<RowIssueDraft> issues = StudentImportFieldValidator.validate(row(
                "last_name,first_name,email,formation_code,class_code,academic_year,work_study\n"
                        + "Doe,Jane,jane@x.test,BTS-SIO,C1,2026-2027,oui\n"));
        assertThat(codes(issues)).containsExactly(StudentImportIssueCodes.COMPANY_NAME_MISSING);
    }

    @Test
    void aColumnCountMismatchIsAnError() {
        ParsedCsv parsed = CsvParser.parse(
                "last_name,first_name,email,formation_code,class_code,academic_year\nDoe,Jane\n", 500);
        NormalizedRow normalized = CsvRowNormalizer.normalize(parsed, parsed.rows().get(0));
        List<RowIssueDraft> issues = StudentImportFieldValidator.validate(normalized);
        assertThat(codes(issues)).contains(StudentImportIssueCodes.COLUMN_COUNT);
        assertThat(StudentImportFieldValidator.statusFrom(issues)).isEqualTo(StudentImportRowStatus.ERROR);
    }
}
