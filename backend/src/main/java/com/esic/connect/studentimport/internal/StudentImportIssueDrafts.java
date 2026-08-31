package com.esic.connect.studentimport.internal;

/**
 * Anomalies calculées avant persistance — découplées des entités JPA
 * ({@link StudentImportJobIssue} / {@link StudentImportRowIssue}) pour que
 * le validateur de champ, le détecteur de doublons et le résolveur
 * d'action restent purs et testables sans base.
 */
final class StudentImportIssueDrafts {

    private StudentImportIssueDrafts() {
    }

    /**
     * @param severity      gravité
     * @param code          code {@code IMP_*} ({@link StudentImportIssueCodes})
     * @param message       message lisible, sans donnée personnelle inutile
     * @param columnName    colonne concernée ({@code null} si transverse)
     * @param receivedValue valeur reçue tronquée ({@code null} si sans objet) — jamais dans l'audit
     * @param suggestedValue correction attendue ({@code null} si sans objet)
     */
    record RowIssueDraft(
            StudentImportIssueSeverity severity,
            String code,
            String message,
            String columnName,
            String receivedValue,
            String suggestedValue) {

        static RowIssueDraft error(String code, String message, String columnName, String receivedValue) {
            return new RowIssueDraft(StudentImportIssueSeverity.ERROR, code, message, columnName, receivedValue, null);
        }

        static RowIssueDraft warning(String code, String message, String columnName, String receivedValue) {
            return new RowIssueDraft(StudentImportIssueSeverity.WARNING, code, message, columnName, receivedValue, null);
        }

        static RowIssueDraft info(String code, String message, String columnName) {
            return new RowIssueDraft(StudentImportIssueSeverity.INFO, code, message, columnName, null, null);
        }

        boolean isError() {
            return severity == StudentImportIssueSeverity.ERROR || severity == StudentImportIssueSeverity.BLOCKING;
        }

        boolean isWarning() {
            return severity == StudentImportIssueSeverity.WARNING;
        }
    }

    /**
     * @param severity   gravité
     * @param code       code {@code IMP_*}
     * @param message    message lisible
     * @param columnName colonne concernée ({@code null} si transverse)
     */
    record JobIssueDraft(StudentImportIssueSeverity severity, String code, String message, String columnName) {

        static JobIssueDraft warning(String code, String message, String columnName) {
            return new JobIssueDraft(StudentImportIssueSeverity.WARNING, code, message, columnName);
        }
    }
}
