package com.esic.connect.studentimport.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lecteur CSV RFC 4180 (rapport §5, §14.1) : détection du séparateur,
 * fins de ligne {@code CRLF} / {@code LF}, guillemets, guillemet doublé,
 * cellule multi-lignes, lignes vides ignorées, classification de
 * l'en-tête, colonnes obligatoires absentes, écart de nombre de colonnes,
 * limites de lignes.
 */
class CsvParserTests {

    private static final int MAX_ROWS = 500;

    @Test
    void detectsTheCommaSeparator() {
        ParsedCsv parsed = CsvParser.parse("last_name,first_name,email\nDoe,Jane,jane@x.test\n", MAX_ROWS);
        assertThat(parsed.separator()).isEqualTo(',');
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().get(0).cells()).containsExactly("Doe", "Jane", "jane@x.test");
    }

    @Test
    void detectsTheSemicolonSeparator() {
        ParsedCsv parsed = CsvParser.parse("last_name;first_name;email\nDoe;Jane;jane@x.test\n", MAX_ROWS);
        assertThat(parsed.separator()).isEqualTo(';');
        assertThat(parsed.rows().get(0).cells()).containsExactly("Doe", "Jane", "jane@x.test");
    }

    @Test
    void handlesCrlfAndTrailingNewlineAbsence() {
        ParsedCsv parsed = CsvParser.parse("email\r\nx@y.test\r\nz@y.test", MAX_ROWS);
        assertThat(parsed.rows()).hasSize(2);
        assertThat(parsed.rows().get(1).cells()).containsExactly("z@y.test");
    }

    @Test
    void honoursRfc4180QuotingAndDoubledQuotes() {
        ParsedCsv parsed = CsvParser.parse(
                "last_name,company_name\n\"O'Brien\",\"ACME, \"\"the\"\" corp\"\n", MAX_ROWS);
        assertThat(parsed.rows().get(0).cells()).containsExactly("O'Brien", "ACME, \"the\" corp");
    }

    @Test
    void supportsANewlineInsideAQuotedCell() {
        ParsedCsv parsed = CsvParser.parse("last_name,company_name\nDoe,\"line1\nline2\"\nRoe,x\n", MAX_ROWS);
        assertThat(parsed.rows()).hasSize(2);
        assertThat(parsed.rows().get(0).cells().get(1)).isEqualTo("line1\nline2");
        // La ligne physique du 2e enregistrement tient compte du saut interne.
        assertThat(parsed.rows().get(1).rowNumber()).isEqualTo(4);
    }

    @Test
    void skipsFullyEmptyLinesWithoutCountingThem() {
        ParsedCsv parsed = CsvParser.parse("email\n\nx@y.test\n\n\ny@y.test\n", MAX_ROWS);
        assertThat(parsed.rows()).hasSize(2);
        assertThat(parsed.rows()).extracting(ParsedCsv.DataRow::rowNumber).containsExactly(3, 6);
    }

    @Test
    void classifiesHeadersAsRecognizedIgnoredOrUnknown() {
        ParsedCsv parsed = CsvParser.parse("last_name,LEVEL_CODE,note\nDoe,B1,x\n", MAX_ROWS);
        assertThat(parsed.header()).extracting(ParsedCsv.HeaderColumn::kind)
                .containsExactly(ParsedCsv.HeaderKind.RECOGNIZED, ParsedCsv.HeaderKind.IGNORED,
                        ParsedCsv.HeaderKind.UNKNOWN);
        assertThat(parsed.ignoredColumnNames()).containsExactly("LEVEL_CODE");
        assertThat(parsed.unknownColumnNames()).containsExactly("note");
    }

    @Test
    void headerMatchingIsCaseInsensitiveAndTrimmed() {
        ParsedCsv parsed = CsvParser.parse(" Last_Name , EMAIL \nDoe,x@y.test\n", MAX_ROWS);
        assertThat(parsed.indexOf(RecognizedColumn.LAST_NAME)).contains(0);
        assertThat(parsed.indexOf(RecognizedColumn.EMAIL)).contains(1);
    }

    @Test
    void reportsMissingMandatoryColumns() {
        ParsedCsv parsed = CsvParser.parse("first_name,phone\nJane,0102030405\n", MAX_ROWS);
        assertThat(parsed.missingMandatoryNames())
                .contains("last_name", "email", "formation_code", "class_code", "academic_year")
                .doesNotContain("first_name");
        assertThat(parsed.hasBlockingStructure()).isTrue();
    }

    @Test
    void flagsRowsWhoseColumnCountDiffersFromTheHeader() {
        ParsedCsv parsed = CsvParser.parse("last_name,first_name,email\nDoe,Jane\n", MAX_ROWS);
        assertThat(parsed.rows().get(0).columnCountMismatch()).isTrue();
    }

    @Test
    void flagsTooManyRows() {
        StringBuilder csv = new StringBuilder("email\n");
        for (int i = 0; i < 7; i++) {
            csv.append("u").append(i).append("@y.test\n");
        }
        ParsedCsv parsed = CsvParser.parse(csv.toString(), 5);
        assertThat(parsed.tooManyRows()).isTrue();
        assertThat(parsed.rows()).hasSize(5);
    }

    @Test
    void flagsNoDataRows() {
        ParsedCsv parsed = CsvParser.parse("last_name,first_name,email\n", MAX_ROWS);
        assertThat(parsed.noDataRows()).isTrue();
        assertThat(parsed.rows()).isEmpty();
    }

    @Test
    void reportsNoDataWhenTheFileIsBlank() {
        ParsedCsv parsed = CsvParser.parse("\n\n  \n", MAX_ROWS);
        assertThat(parsed.noDataRows()).isTrue();
        assertThat(parsed.header()).isEmpty();
    }

    @Test
    void aDuplicateRecognizedHeaderBecomesUnknown() {
        ParsedCsv parsed = CsvParser.parse("email,email\nx@y.test,z@y.test\n", MAX_ROWS);
        assertThat(parsed.header().get(0).kind()).isEqualTo(ParsedCsv.HeaderKind.RECOGNIZED);
        assertThat(parsed.header().get(1).kind()).isEqualTo(ParsedCsv.HeaderKind.UNKNOWN);
    }
}
