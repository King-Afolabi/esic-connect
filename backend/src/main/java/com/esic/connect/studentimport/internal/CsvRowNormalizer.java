package com.esic.connect.studentimport.internal;

import java.util.EnumMap;
import java.util.Map;

/**
 * Transforme une ligne brute ({@link ParsedCsv.DataRow}) en
 * {@link NormalizedRow}, en appliquant la normalisation technique
 * colonne par colonne (rapport §5.2). Aucune règle métier ici : ni
 * syntaxe d'e-mail, ni existence de classe, ni doublon.
 */
final class CsvRowNormalizer {

    private CsvRowNormalizer() {
    }

    static NormalizedRow normalize(ParsedCsv parsed, ParsedCsv.DataRow row) {
        Map<RecognizedColumn, String> raw = new EnumMap<>(RecognizedColumn.class);
        for (RecognizedColumn column : RecognizedColumn.values()) {
            parsed.indexOf(column)
                    .map(row::cell)
                    .map(String::strip)
                    .filter(value -> !value.isEmpty())
                    .ifPresent(value -> raw.put(column, value));
        }

        String rawBirth = raw.get(RecognizedColumn.BIRTH_DATE);
        CsvValueNormalizer.BirthDateResult birth = CsvValueNormalizer.parseBirthDate(rawBirth);
        String rawWorkStudy = raw.get(RecognizedColumn.WORK_STUDY);
        CsvValueNormalizer.WorkStudyResult workStudy = CsvValueNormalizer.parseWorkStudy(rawWorkStudy);
        String rawPhone = raw.get(RecognizedColumn.PHONE);

        return new NormalizedRow(
                row.rowNumber(),
                row.columnCountMismatch(),
                CsvValueNormalizer.collapseSpaces(raw.get(RecognizedColumn.LAST_NAME)),
                CsvValueNormalizer.collapseSpaces(raw.get(RecognizedColumn.FIRST_NAME)),
                CsvValueNormalizer.lowerCase(raw.get(RecognizedColumn.EMAIL)),
                CsvValueNormalizer.normalizePhone(rawPhone),
                rawPhone != null,
                CsvValueNormalizer.upperCase(raw.get(RecognizedColumn.FORMATION_CODE)),
                CsvValueNormalizer.upperCase(raw.get(RecognizedColumn.CLASS_CODE)),
                CsvValueNormalizer.trimToNull(raw.get(RecognizedColumn.ACADEMIC_YEAR)),
                CsvValueNormalizer.upperCase(raw.get(RecognizedColumn.STUDENT_NUMBER)),
                birth.value(),
                birth.present(),
                birth.malformed(),
                workStudy.value(),
                workStudy.present(),
                workStudy.malformed(),
                CsvValueNormalizer.trimToNull(raw.get(RecognizedColumn.COMPANY_NAME)),
                Map.copyOf(raw));
    }
}
