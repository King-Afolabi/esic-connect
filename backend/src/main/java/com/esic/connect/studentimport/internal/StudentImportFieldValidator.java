package com.esic.connect.studentimport.internal;

import com.esic.connect.studentimport.internal.StudentImportIssueDrafts.RowIssueDraft;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Règles de validation <em>de champ</em> d'une ligne normalisée
 * (rapport §5.2, §5.3). Pur, sans base : ni existence de classe, ni
 * doublon, ni résolution de compte (ceux-ci relèvent du détecteur de
 * doublons et du résolveur d'action).
 *
 * <ul>
 *   <li>valeurs obligatoires absentes → {@code ERROR IMP_REQUIRED_VALUE_MISSING} ;</li>
 *   <li>nombre de cellules ≠ en-tête → {@code ERROR IMP_COLUMN_COUNT} ;</li>
 *   <li>syntaxe d'e-mail invalide → {@code ERROR IMP_EMAIL_INVALID} ;</li>
 *   <li>téléphone non conforme → {@code WARNING IMP_PHONE_FORMAT} ;</li>
 *   <li>date de naissance illisible → {@code WARNING IMP_BIRTH_DATE_FORMAT} ;</li>
 *   <li>booléen d'alternance illisible → {@code WARNING IMP_WORK_STUDY_INVALID} ;</li>
 *   <li>{@code company_name} requis si {@code work_study=true} → {@code WARNING IMP_COMPANY_NAME_MISSING}.</li>
 * </ul>
 */
final class StudentImportFieldValidator {

    // Syntaxe d'e-mail volontairement conservatrice (aligne @Email + garde).
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{6,20}$");

    private StudentImportFieldValidator() {
    }

    static List<RowIssueDraft> validate(NormalizedRow row) {
        List<RowIssueDraft> issues = new ArrayList<>();

        if (row.columnCountMismatch()) {
            issues.add(RowIssueDraft.error(StudentImportIssueCodes.COLUMN_COUNT,
                    "Le nombre de colonnes de cette ligne diffère de l'en-tête.", null, null));
        }

        requireValue(issues, "last_name", row.lastName());
        requireValue(issues, "first_name", row.firstName());
        requireValue(issues, "formation_code", row.formationCode());
        requireValue(issues, "class_code", row.classCode());
        requireValue(issues, "academic_year", row.academicYear());

        if (row.email() == null) {
            issues.add(RowIssueDraft.error(StudentImportIssueCodes.REQUIRED_VALUE_MISSING,
                    "L'adresse électronique est obligatoire.", "email", null));
        } else if (!EMAIL.matcher(row.email()).matches()) {
            issues.add(RowIssueDraft.error(StudentImportIssueCodes.EMAIL_INVALID,
                    "L'adresse électronique n'est pas syntaxiquement valide.", "email",
                    row.raw(RecognizedColumn.EMAIL).map(CsvValueNormalizer::truncateReceivedValue).orElse(null)));
        }

        if (row.phonePresent() && (row.phone() == null || !PHONE.matcher(row.phone()).matches())) {
            issues.add(RowIssueDraft.warning(StudentImportIssueCodes.PHONE_FORMAT,
                    "Le téléphone n'est pas au format attendu ; il ne sera pas repris.", "phone",
                    row.raw(RecognizedColumn.PHONE).map(CsvValueNormalizer::truncateReceivedValue).orElse(null)));
        }

        if (row.birthDateMalformed()) {
            issues.add(RowIssueDraft.warning(StudentImportIssueCodes.BIRTH_DATE_FORMAT,
                    "La date de naissance est illisible (aaaa-mm-jj ou jj/mm/aaaa attendu) ; elle sera ignorée.",
                    "birth_date",
                    row.raw(RecognizedColumn.BIRTH_DATE).map(CsvValueNormalizer::truncateReceivedValue).orElse(null)));
        }

        if (row.workStudyMalformed()) {
            issues.add(RowIssueDraft.warning(StudentImportIssueCodes.WORK_STUDY_INVALID,
                    "La valeur d'alternance est illisible ; l'apprenant sera considéré non alternant.",
                    "work_study",
                    row.raw(RecognizedColumn.WORK_STUDY).map(CsvValueNormalizer::truncateReceivedValue).orElse(null)));
        }

        if (row.hasWorkStudyTrue() && row.companyName() == null) {
            issues.add(RowIssueDraft.warning(StudentImportIssueCodes.COMPANY_NAME_MISSING,
                    "L'entreprise d'alternance est recommandée lorsque work_study=true.", "company_name", null));
        }

        return issues;
    }

    private static void requireValue(List<RowIssueDraft> issues, String column, String value) {
        if (value == null) {
            issues.add(RowIssueDraft.error(StudentImportIssueCodes.REQUIRED_VALUE_MISSING,
                    "La valeur de « " + column + " » est obligatoire.", column, null));
        }
    }

    /** Statut de ligne dérivé des anomalies accumulées (rapport §7.3). */
    static StudentImportRowStatus statusFrom(List<RowIssueDraft> issues) {
        if (issues.stream().anyMatch(RowIssueDraft::isError)) {
            return StudentImportRowStatus.ERROR;
        }
        if (issues.stream().anyMatch(RowIssueDraft::isWarning)) {
            return StudentImportRowStatus.WARNING;
        }
        return StudentImportRowStatus.VALID;
    }
}
