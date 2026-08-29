package com.esic.connect.alternation.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validation <em>pure</em> de {@code configuration_json} par type de
 * rythme (section 4 du lot) : jamais d'acceptation silencieuse d'un JSON
 * inconnu ou incohérent.
 */
class AlternationConfigParserTests {

    private final AlternationConfigParser parser = new AlternationConfigParser(new ObjectMapper());

    // ------------------------------------------------------------------
    // Cas valides
    // ------------------------------------------------------------------

    @Test
    void parsesThreeDaysTwoDaysAndNormalisesCycleToOne() {
        AlternationConfigParser.ParsedConfiguration parsed = parser.parse(
                WorkStudyPatternType.THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY, null,
                """
                {"schoolDays":["MONDAY","TUESDAY","WEDNESDAY"],"companyDays":["THURSDAY","FRIDAY"]}
                """);

        assertThat(parsed.normalizedCycleLengthWeeks()).isEqualTo(1);
        PatternConfiguration config = parsed.configuration();
        assertThat(config.cycleLengthWeeks()).isEqualTo(1);
        assertThat(config.schoolWeeks()).containsExactly(1);
        assertThat(config.schoolDays()).containsExactlyInAnyOrder(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY);
        assertThat(config.companyDays()).containsExactlyInAnyOrder(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
    }

    @Test
    void parsesOneWeekOutOfFour() {
        AlternationConfigParser.ParsedConfiguration parsed = parser.parse(
                WorkStudyPatternType.ONE_WEEK_SCHOOL_OUT_OF_FOUR, null,
                """
                {"schoolWeeks":[1],"companyWeeks":[2,3,4]}
                """);
        assertThat(parsed.normalizedCycleLengthWeeks()).isEqualTo(4);
        assertThat(parsed.configuration().schoolWeeks()).containsExactly(1);
        assertThat(parsed.configuration().companyWeeks()).containsExactlyInAnyOrder(2, 3, 4);
        // schoolDays par défaut = lundi..vendredi
        assertThat(parsed.configuration().schoolDays()).containsExactlyInAnyOrder(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
    }

    @Test
    void parsesTwoWeeksOutOfFour() {
        AlternationConfigParser.ParsedConfiguration parsed = parser.parse(
                WorkStudyPatternType.TWO_WEEKS_SCHOOL_OUT_OF_FOUR, 4,
                """
                {"schoolWeeks":[1,2],"companyWeeks":[3,4]}
                """);
        assertThat(parsed.configuration().schoolWeeks()).containsExactlyInAnyOrder(1, 2);
        assertThat(parsed.configuration().companyWeeks()).containsExactlyInAnyOrder(3, 4);
    }

    @Test
    void parsesCustomWithUnclassifiedWeeks() {
        AlternationConfigParser.ParsedConfiguration parsed = parser.parse(
                WorkStudyPatternType.CUSTOM, 3,
                """
                {"schoolWeeks":[1],"companyWeeks":[2],"schoolDays":["MONDAY","TUESDAY"]}
                """);
        assertThat(parsed.normalizedCycleLengthWeeks()).isEqualTo(3);
        assertThat(parsed.configuration().schoolWeeks()).containsExactly(1);
        assertThat(parsed.configuration().companyWeeks()).containsExactly(2);
        // semaine 3 non classifiée -> résolue en UNKNOWN par PatternConfiguration
        assertThat(parsed.configuration().resolve(3, DayOfWeek.MONDAY)).isEqualTo(AlternationContext.UNKNOWN);
    }

    @Test
    void canonicalizeProducesStableSortedJson() {
        AlternationConfigParser.ParsedConfiguration parsed = parser.parse(
                WorkStudyPatternType.THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY, null,
                """
                {"companyDays":["FRIDAY","THURSDAY"],"schoolDays":["WEDNESDAY","MONDAY","TUESDAY"]}
                """);
        String canonical = parser.canonicalize(parsed);
        assertThat(canonical).isEqualTo(
                "{\"cycleLengthWeeks\":1,\"schoolWeeks\":[1],\"companyWeeks\":[],"
                        + "\"schoolDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\"],"
                        + "\"companyDays\":[\"THURSDAY\",\"FRIDAY\"]}");
    }

    // ------------------------------------------------------------------
    // Cas invalides
    // ------------------------------------------------------------------

    @Test
    void rejectsUnreadableJson() {
        assertConfigError(WorkStudyPatternType.CUSTOM, 4, "not json at all");
    }

    @Test
    void rejectsNonObjectJson() {
        assertConfigError(WorkStudyPatternType.CUSTOM, 4, "[1,2,3]");
    }

    @Test
    void rejectsUnknownProperty() {
        assertConfigError(WorkStudyPatternType.THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY, null,
                "{\"schoolDays\":[\"MONDAY\"],\"companyDays\":[\"TUESDAY\"],\"foo\":1}");
    }

    @Test
    void rejectsUnknownDayName() {
        assertConfigError(WorkStudyPatternType.THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY, null,
                "{\"schoolDays\":[\"MONDAY\",\"FUNDAY\"],\"companyDays\":[\"THURSDAY\",\"FRIDAY\"]}");
    }

    @Test
    void rejectsDayInBothSchoolAndCompany() {
        assertConfigError(WorkStudyPatternType.THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY, null,
                "{\"schoolDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\"],"
                        + "\"companyDays\":[\"THURSDAY\",\"FRIDAY\"]}");
    }

    @Test
    void rejectsIncompleteWeekdayClassificationForThreeTwo() {
        // jeudi non classifié
        assertConfigError(WorkStudyPatternType.THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY, null,
                "{\"schoolDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\"],\"companyDays\":[\"FRIDAY\"]}");
    }

    @Test
    void rejectsWeekendDayForThreeTwo() {
        assertConfigError(WorkStudyPatternType.THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY, null,
                "{\"schoolDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"SATURDAY\"],"
                        + "\"companyDays\":[\"THURSDAY\",\"FRIDAY\"]}");
    }

    @Test
    void rejectsWrongSchoolWeekCountForOneOutOfFour() {
        assertConfigError(WorkStudyPatternType.ONE_WEEK_SCHOOL_OUT_OF_FOUR, null,
                "{\"schoolWeeks\":[1,2],\"companyWeeks\":[3,4]}");
    }

    @Test
    void rejectsSchoolCompanyWeekIntersection() {
        assertConfigError(WorkStudyPatternType.TWO_WEEKS_SCHOOL_OUT_OF_FOUR, 4,
                "{\"schoolWeeks\":[1,2],\"companyWeeks\":[2,3,4]}");
    }

    @Test
    void rejectsIncompleteFourWeekCycle() {
        assertConfigError(WorkStudyPatternType.ONE_WEEK_SCHOOL_OUT_OF_FOUR, 4,
                "{\"schoolWeeks\":[1],\"companyWeeks\":[2,3]}");
    }

    @Test
    void rejectsWeekIndexOutOfCycleForCustom() {
        assertConfigError(WorkStudyPatternType.CUSTOM, 3,
                "{\"schoolWeeks\":[1,4],\"companyWeeks\":[2]}");
    }

    @Test
    void rejectsCustomWithoutCycleLength() {
        assertConfigError(WorkStudyPatternType.CUSTOM, null,
                "{\"schoolWeeks\":[1],\"companyWeeks\":[2]}");
    }

    @Test
    void rejectsCustomWithNonPositiveCycleLength() {
        assertConfigError(WorkStudyPatternType.CUSTOM, null,
                "{\"cycleLengthWeeks\":0,\"schoolWeeks\":[],\"companyWeeks\":[]}");
    }

    @Test
    void rejectsCustomWithNoClassifiedPeriod() {
        assertConfigError(WorkStudyPatternType.CUSTOM, 4,
                "{\"schoolWeeks\":[],\"companyWeeks\":[]}");
    }

    @Test
    void rejectsCycleLengthMismatchBetweenBodyAndConfigForCustom() {
        assertConfigError(WorkStudyPatternType.CUSTOM, 4,
                "{\"cycleLengthWeeks\":6,\"schoolWeeks\":[1],\"companyWeeks\":[2]}");
    }

    @Test
    void rejectsWrongCycleLengthForThreeTwo() {
        assertConfigError(WorkStudyPatternType.THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY, 2,
                "{\"schoolDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\"],\"companyDays\":[\"THURSDAY\",\"FRIDAY\"]}");
    }

    // ------------------------------------------------------------------
    // Round-trip canonique : parseCanonical(canonicalize(parse(...)))
    // ------------------------------------------------------------------

    private final AlternationResolver resolver = new AlternationResolver();
    private static final LocalDate ANCHOR = LocalDate.of(2026, 9, 1); // mardi

    @Test
    void roundTripThreeDaysTwoDaysPreservesResolution() {
        assertRoundTrip(WorkStudyPatternType.THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY, null,
                "{\"schoolDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\"],"
                        + "\"companyDays\":[\"THURSDAY\",\"FRIDAY\"]}");
    }

    @Test
    void roundTripOneWeekOutOfFourPreservesResolution() {
        assertRoundTrip(WorkStudyPatternType.ONE_WEEK_SCHOOL_OUT_OF_FOUR, null,
                "{\"schoolWeeks\":[1],\"companyWeeks\":[2,3,4]}");
    }

    @Test
    void roundTripTwoWeeksOutOfFourPreservesResolution() {
        assertRoundTrip(WorkStudyPatternType.TWO_WEEKS_SCHOOL_OUT_OF_FOUR, 4,
                "{\"schoolWeeks\":[1,2],\"companyWeeks\":[3,4]}");
    }

    @Test
    void roundTripCustomWithEmptyCompanyDaysPreservesResolution() {
        // companyDays absent -> canonicalisé en [] ; parseCanonical doit l'accepter.
        assertRoundTrip(WorkStudyPatternType.CUSTOM, 2,
                "{\"cycleLengthWeeks\":2,\"schoolWeeks\":[1],\"companyWeeks\":[2],"
                        + "\"schoolDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\"]}");
    }

    @Test
    void roundTripCustomWithNonEmptyCompanyDaysPreservesResolution() {
        assertRoundTrip(WorkStudyPatternType.CUSTOM, 2,
                "{\"cycleLengthWeeks\":2,\"schoolWeeks\":[1],\"companyWeeks\":[2],"
                        + "\"schoolDays\":[\"MONDAY\",\"TUESDAY\"],\"companyDays\":[\"THURSDAY\",\"FRIDAY\"]}");
    }

    /**
     * Vérifie que la configuration relue depuis sa forme canonique produit
     * exactement les mêmes contextes SCHOOL / COMPANY / UNKNOWN que la
     * configuration initiale, sur un balayage de 8 semaines couvrant tout
     * cycle du MVP (1 ou 4 semaines) et tous les jours (y compris week-end
     * et une date antérieure à l'ancre).
     */
    private void assertRoundTrip(WorkStudyPatternType type, Integer cycleLength, String input) {
        AlternationConfigParser.ParsedConfiguration parsed = parser.parse(type, cycleLength, input);
        String canonical = parser.canonicalize(parsed);
        PatternConfiguration reread = parser.parseCanonical(canonical);

        // parseCanonical est idempotent : re-canonicaliser la forme relue
        // ne la modifie pas.
        assertThat(parser.canonicalize(
                new AlternationConfigParser.ParsedConfiguration(reread.cycleLengthWeeks(), reread)))
                .isEqualTo(canonical);

        PatternConfiguration original = parsed.configuration();
        for (int offset = -3; offset <= 56; offset++) {
            LocalDate date = ANCHOR.plusDays(offset);
            assertThat(resolver.resolve(ANCHOR, reread, date))
                    .as("contexte au %s (offset %d)", date, offset)
                    .isEqualTo(resolver.resolve(ANCHOR, original, date));
        }
    }

    // ------------------------------------------------------------------
    // parseCanonical : tolérances et refus spécifiques
    // ------------------------------------------------------------------

    private static final String CANON_ONE_OUT_OF_FOUR =
            "{\"cycleLengthWeeks\":4,\"schoolWeeks\":[1],\"companyWeeks\":[2,3,4],"
                    + "\"schoolDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\"],"
                    + "\"companyDays\":[]}";

    @Test
    void parseCanonicalAcceptsEmptySchoolDays() {
        String json = "{\"cycleLengthWeeks\":4,\"schoolWeeks\":[1],\"companyWeeks\":[2,3,4],"
                + "\"schoolDays\":[],\"companyDays\":[]}";
        PatternConfiguration config = parser.parseCanonical(json);
        assertThat(config.schoolDays()).isEmpty();
        assertThat(config.companyDays()).isEmpty();
    }

    @Test
    void parseCanonicalAcceptsEmptyCompanyDays() {
        PatternConfiguration config = parser.parseCanonical(CANON_ONE_OUT_OF_FOUR);
        assertThat(config.companyDays()).isEmpty();
        assertThat(config.schoolDays()).hasSize(5);
    }

    @Test
    void parseCanonicalRejectsUnknownProperty() {
        assertCanonicalError(CANON_ONE_OUT_OF_FOUR.replace("\"companyDays\":[]}",
                "\"companyDays\":[],\"foo\":1}"));
    }

    @Test
    void parseCanonicalRejectsMissingMandatoryProperty() {
        assertCanonicalError("{\"cycleLengthWeeks\":4,\"schoolWeeks\":[1],\"companyWeeks\":[2,3,4],"
                + "\"schoolDays\":[\"MONDAY\"]}"); // companyDays absent
    }

    @Test
    void parseCanonicalRejectsUnknownDay() {
        assertCanonicalError(CANON_ONE_OUT_OF_FOUR.replace("[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\","
                + "\"FRIDAY\"]", "[\"MONDAY\",\"FUNDAY\"]"));
    }

    @Test
    void parseCanonicalRejectsDuplicateDay() {
        assertCanonicalError(CANON_ONE_OUT_OF_FOUR.replace("[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\","
                + "\"FRIDAY\"]", "[\"MONDAY\",\"MONDAY\"]"));
    }

    @Test
    void parseCanonicalRejectsDayIntersection() {
        assertCanonicalError("{\"cycleLengthWeeks\":4,\"schoolWeeks\":[1],\"companyWeeks\":[2,3,4],"
                + "\"schoolDays\":[\"MONDAY\",\"TUESDAY\"],\"companyDays\":[\"TUESDAY\"]}");
    }

    @Test
    void parseCanonicalRejectsWeekIntersection() {
        assertCanonicalError("{\"cycleLengthWeeks\":4,\"schoolWeeks\":[1,2],\"companyWeeks\":[2,3,4],"
                + "\"schoolDays\":[],\"companyDays\":[]}");
    }

    @Test
    void parseCanonicalRejectsWeekIndexOutOfCycle() {
        assertCanonicalError("{\"cycleLengthWeeks\":4,\"schoolWeeks\":[1,5],\"companyWeeks\":[2,3],"
                + "\"schoolDays\":[],\"companyDays\":[]}");
    }

    private void assertCanonicalError(String canonicalJson) {
        assertThatThrownBy(() -> parser.parseCanonical(canonicalJson))
                .isInstanceOf(AlternationException.class)
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.INVALID_CONFIGURATION);
    }

    // ------------------------------------------------------------------

    private void assertConfigError(WorkStudyPatternType type, Integer cycleLength, String json) {
        assertThatThrownBy(() -> parser.parse(type, cycleLength, json))
                .isInstanceOf(AlternationException.class)
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.INVALID_CONFIGURATION);
    }
}
