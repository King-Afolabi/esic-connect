package com.esic.connect.studentimport.internal;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Une ligne de données CSV après normalisation <em>technique</em>
 * (rapport §5.2) : valeurs rognées, e-mail en minuscule, codes en
 * majuscule, date et booléen analysés. Les indicateurs {@code *Malformed}
 * signalent une valeur présente mais non analysable — la décision de
 * gravité (WARNING / ERROR) et la construction des anomalies persistées
 * appartiennent au checkpoint de validation (CP4).
 *
 * <p>{@code rawValues} conserve la valeur <em>brute</em> des cellules
 * reconnues, uniquement pour renseigner
 * {@code student_import_row_issue.received_value} (tronquée) quand une
 * anomalie est produite — jamais reprise dans l'audit.
 */
record NormalizedRow(
        int rowNumber,
        boolean columnCountMismatch,
        String lastName,
        String firstName,
        String email,
        String phone,
        boolean phonePresent,
        String formationCode,
        String classCode,
        String academicYear,
        String studentNumber,
        LocalDate birthDate,
        boolean birthDatePresent,
        boolean birthDateMalformed,
        Boolean workStudy,
        boolean workStudyPresent,
        boolean workStudyMalformed,
        String companyName,
        Map<RecognizedColumn, String> rawValues) {

    Optional<String> raw(RecognizedColumn column) {
        return Optional.ofNullable(rawValues.get(column));
    }

    boolean hasWorkStudyTrue() {
        return Boolean.TRUE.equals(workStudy);
    }

    /**
     * Reconstruit une ligne normalisée à partir des colonnes déjà
     * persistées de {@link StudentImportRow}, pour la re-validation à la
     * confirmation (rapport §4.4). Les indicateurs {@code *Malformed} sont
     * {@code false} : la valeur stockée est déjà normalisée / analysée ;
     * {@code rawValues} est vide (la valeur brute d'origine n'est pas
     * conservée — minimisation).
     */
    static NormalizedRow fromPersistedRow(StudentImportRow row) {
        return new NormalizedRow(
                row.getRowNumber(),
                false,
                row.getInputLastName(),
                row.getInputFirstName(),
                row.getInputEmail(),
                row.getInputPhone(),
                row.getInputPhone() != null,
                row.getInputFormationCode(),
                row.getInputClassCode(),
                row.getInputAcademicYear(),
                row.getInputStudentNumber(),
                row.getInputBirthDate(),
                row.getInputBirthDate() != null,
                false,
                row.getInputWorkStudy(),
                row.getInputWorkStudy() != null,
                false,
                row.getInputCompanyName(),
                Map.of());
    }
}
