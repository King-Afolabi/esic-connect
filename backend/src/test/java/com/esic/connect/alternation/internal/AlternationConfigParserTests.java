package com.esic.connect.alternation.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;

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

    private void assertConfigError(WorkStudyPatternType type, Integer cycleLength, String json) {
        assertThatThrownBy(() -> parser.parse(type, cycleLength, json))
                .isInstanceOf(AlternationException.class)
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.INVALID_CONFIGURATION);
    }
}
