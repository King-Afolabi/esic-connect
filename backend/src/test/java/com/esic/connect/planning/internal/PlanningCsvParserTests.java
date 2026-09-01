package com.esic.connect.planning.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du lecteur CSV de planning (composant pur, sans Spring
 * ni base) : auto-détection du séparateur, appariement de l'en-tête par
 * nom, colonnes obligatoires manquantes, RFC 4180 (guillemets, cellule
 * multi-lignes), lignes vides ignorées, borne de lignes.
 */
class PlanningCsvParserTests {

    private static final String HEADER =
            "slot_key,session_date,start_time,end_time,time_zone_id,title,teacher_public_id,room_code\n";

    @Test
    void parsesAWellFormedFileAndMapsEveryColumn() {
        String csv = HEADER
                + "S1,2026-09-07,09:00,12:00,Europe/Paris,Algorithmique,11111111-1111-1111-1111-111111111111,A101\n"
                + "S2,2026-09-07,13:30,17:00,Europe/Paris,Bases de données,11111111-1111-1111-1111-111111111111,\n";
        ParsedPlanningCsv parsed = PlanningCsvParser.parse(csv, 500);

        assertThat(parsed.separator()).isEqualTo(',');
        assertThat(parsed.missingMandatoryNames()).isEmpty();
        assertThat(parsed.unknownColumnNames()).isEmpty();
        assertThat(parsed.rows()).hasSize(2);
        assertThat(parsed.tooManyRows()).isFalse();
        assertThat(parsed.noDataRows()).isFalse();
        assertThat(parsed.cell(parsed.rows().get(0), PlanningColumn.TITLE)).isEqualTo("Algorithmique");
        assertThat(parsed.cell(parsed.rows().get(0), PlanningColumn.ROOM_CODE)).isEqualTo("A101");
        assertThat(parsed.cell(parsed.rows().get(1), PlanningColumn.ROOM_CODE)).isEmpty();
    }

    @Test
    void autoDetectsSemicolonSeparatorAndCaseInsensitiveHeaders() {
        String csv = "SLOT_KEY;Session Date;start-time;END_TIME;Time_Zone_Id;Title;teacher_public_id;room_code\n"
                + "S1;2026-09-07;09:00;12:00;Europe/Paris;Cours;22222222-2222-2222-2222-222222222222;B12\n";
        ParsedPlanningCsv parsed = PlanningCsvParser.parse(csv, 500);
        assertThat(parsed.separator()).isEqualTo(';');
        assertThat(parsed.missingMandatoryNames()).isEmpty();
        assertThat(parsed.cell(parsed.rows().get(0), PlanningColumn.TIME_ZONE_ID)).isEqualTo("Europe/Paris");
    }

    @Test
    void reportsMissingMandatoryColumns() {
        String csv = "slot_key,session_date,start_time\nS1,2026-09-07,09:00\n";
        ParsedPlanningCsv parsed = PlanningCsvParser.parse(csv, 500);
        assertThat(parsed.missingMandatoryNames())
                .contains("end_time", "time_zone_id", "title", "teacher_public_id");
    }

    @Test
    void honoursRfc4180QuotingAndMultilineCells() {
        String csv = HEADER
                + "\"S,1\",2026-09-07,09:00,12:00,Europe/Paris,\"Titre ; avec \"\"guillemets\"\"\","
                + "33333333-3333-3333-3333-333333333333,\"Salle\nA\"\n";
        ParsedPlanningCsv parsed = PlanningCsvParser.parse(csv, 500);
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.cell(parsed.rows().get(0), PlanningColumn.SLOT_KEY)).isEqualTo("S,1");
        assertThat(parsed.cell(parsed.rows().get(0), PlanningColumn.TITLE))
                .isEqualTo("Titre ; avec \"guillemets\"");
        assertThat(parsed.cell(parsed.rows().get(0), PlanningColumn.ROOM_CODE)).isEqualTo("Salle\nA");
    }

    @Test
    void ignoresFullyEmptyLinesAndFlagsAnEmptyFile() {
        String csv = HEADER + "\n\nS1,2026-09-07,09:00,12:00,Europe/Paris,Cours,44444444-4444-4444-4444-444444444444,\n\n";
        ParsedPlanningCsv parsed = PlanningCsvParser.parse(csv, 500);
        assertThat(parsed.rows()).hasSize(1);

        ParsedPlanningCsv empty = PlanningCsvParser.parse("", 500);
        assertThat(empty.noDataRows()).isTrue();
        assertThat(empty.missingMandatoryNames()).isNotEmpty();
    }

    @Test
    void stopsAtTheRowLimit() {
        StringBuilder csv = new StringBuilder(HEADER);
        for (int i = 0; i < 5; i++) {
            csv.append("S").append(i)
                    .append(",2026-09-07,09:00,12:00,Europe/Paris,Cours,55555555-5555-5555-5555-555555555555,\n");
        }
        ParsedPlanningCsv parsed = PlanningCsvParser.parse(csv.toString(), 3);
        assertThat(parsed.rows()).hasSize(3);
        assertThat(parsed.tooManyRows()).isTrue();
    }

    @Test
    void flagsColumnCountMismatch() {
        String csv = HEADER + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours\n";
        ParsedPlanningCsv parsed = PlanningCsvParser.parse(csv, 500);
        assertThat(parsed.rows().get(0).columnCountMismatch()).isTrue();
    }
}
