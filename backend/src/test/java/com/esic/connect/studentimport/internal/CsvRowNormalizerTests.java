package com.esic.connect.studentimport.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bout-en-bout de la normalisation d'une ligne (rapport §5.2) : mapping
 * par en-tête (ordre libre), casse des champs, indicateurs de forme,
 * conservation de la valeur brute des cellules reconnues.
 */
class CsvRowNormalizerTests {

    @Test
    void mapsCellsByHeaderAndNormalizesEachField() {
        String csv = "class_code,email,last_name,first_name,formation_code,academic_year,phone,work_study,company_name\n"
                + "  bts-sio-1 , JANE@X.TEST ,  Van   der Berg , Jane ,  bts-sio , 2026-2027 , 01 02 03 04 05 , Oui , ACME \n";
        ParsedCsv parsed = CsvParser.parse(csv, 500);
        NormalizedRow row = CsvRowNormalizer.normalize(parsed, parsed.rows().get(0));

        assertThat(row.rowNumber()).isEqualTo(2);
        assertThat(row.classCode()).isEqualTo("BTS-SIO-1");
        assertThat(row.email()).isEqualTo("jane@x.test");
        assertThat(row.lastName()).isEqualTo("Van der Berg");
        assertThat(row.firstName()).isEqualTo("Jane");
        assertThat(row.formationCode()).isEqualTo("BTS-SIO");
        assertThat(row.academicYear()).isEqualTo("2026-2027");
        assertThat(row.phone()).isEqualTo("0102030405");
        assertThat(row.phonePresent()).isTrue();
        assertThat(row.hasWorkStudyTrue()).isTrue();
        assertThat(row.companyName()).isEqualTo("ACME");
        assertThat(row.raw(RecognizedColumn.EMAIL)).contains("JANE@X.TEST");
    }

    @Test
    void carriesFormIndicatorsForMalformedOptionalCells() {
        String csv = "email,birth_date,work_study\nx@y.test,le 6 mai,peut-être\n";
        ParsedCsv parsed = CsvParser.parse(csv, 500);
        NormalizedRow row = CsvRowNormalizer.normalize(parsed, parsed.rows().get(0));

        assertThat(row.birthDate()).isNull();
        assertThat(row.birthDatePresent()).isTrue();
        assertThat(row.birthDateMalformed()).isTrue();
        assertThat(row.workStudy()).isNull();
        assertThat(row.workStudyMalformed()).isTrue();
    }

    @Test
    void leavesUnfilledOptionalFieldsNull() {
        String csv = "email,birth_date\nx@y.test,2004-01-02\n";
        ParsedCsv parsed = CsvParser.parse(csv, 500);
        NormalizedRow row = CsvRowNormalizer.normalize(parsed, parsed.rows().get(0));

        assertThat(row.birthDate()).isEqualTo(LocalDate.of(2004, 1, 2));
        assertThat(row.phone()).isNull();
        assertThat(row.phonePresent()).isFalse();
        assertThat(row.studentNumber()).isNull();
        assertThat(row.workStudyPresent()).isFalse();
    }

    @Test
    void propagatesTheColumnCountMismatchFlag() {
        ParsedCsv parsed = CsvParser.parse("last_name,first_name,email\nDoe,Jane\n", 500);
        NormalizedRow row = CsvRowNormalizer.normalize(parsed, parsed.rows().get(0));
        assertThat(row.columnCountMismatch()).isTrue();
    }
}
