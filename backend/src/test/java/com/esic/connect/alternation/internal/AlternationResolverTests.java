package com.esic.connect.alternation.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Résolution calendaire <em>pure</em> (section 9 du lot) : calcul
 * déterministe, bornes inclusives, comportements explicites pour une date
 * antérieure à l'ancre et pour les week-ends.
 */
class AlternationResolverTests {

    private final AlternationResolver resolver = new AlternationResolver();
    private final AlternationConfigParser parser = new AlternationConfigParser(new ObjectMapper());

    private PatternConfiguration threeTwo() {
        return parser.parse(WorkStudyPatternType.THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY, null,
                "{\"schoolDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\"],"
                        + "\"companyDays\":[\"THURSDAY\",\"FRIDAY\"]}").configuration();
    }

    private PatternConfiguration oneOutOfFour() {
        return parser.parse(WorkStudyPatternType.ONE_WEEK_SCHOOL_OUT_OF_FOUR, null,
                "{\"schoolWeeks\":[1],\"companyWeeks\":[2,3,4]}").configuration();
    }

    private PatternConfiguration twoOutOfFour() {
        return parser.parse(WorkStudyPatternType.TWO_WEEKS_SCHOOL_OUT_OF_FOUR, 4,
                "{\"schoolWeeks\":[1,2],\"companyWeeks\":[3,4]}").configuration();
    }

    private PatternConfiguration custom() {
        return parser.parse(WorkStudyPatternType.CUSTOM, 3,
                "{\"schoolWeeks\":[1],\"companyWeeks\":[2],\"schoolDays\":[\"MONDAY\",\"TUESDAY\"]}")
                .configuration();
    }

    // 2026-09-01 is a Tuesday.
    private static final LocalDate ANCHOR = LocalDate.of(2026, 9, 1);

    @Test
    void threeTwoResolvesByDayWithinTheSingleWeek() {
        PatternConfiguration config = threeTwo();
        assertThat(resolver.resolve(ANCHOR, config, LocalDate.of(2026, 9, 7))) // Monday
                .isEqualTo(AlternationContext.SCHOOL);
        assertThat(resolver.resolve(ANCHOR, config, LocalDate.of(2026, 9, 10))) // Thursday
                .isEqualTo(AlternationContext.COMPANY);
        assertThat(resolver.resolve(ANCHOR, config, LocalDate.of(2026, 9, 11))) // Friday
                .isEqualTo(AlternationContext.COMPANY);
    }

    @Test
    void weekendAlwaysUnknown() {
        assertThat(resolver.resolve(ANCHOR, threeTwo(), LocalDate.of(2026, 9, 5))) // Saturday
                .isEqualTo(AlternationContext.UNKNOWN);
        assertThat(resolver.resolve(ANCHOR, oneOutOfFour(), LocalDate.of(2026, 9, 6))) // Sunday
                .isEqualTo(AlternationContext.UNKNOWN);
    }

    @Test
    void dateBeforeAnchorIsUnknown() {
        assertThat(resolver.resolve(ANCHOR, threeTwo(), LocalDate.of(2026, 8, 31)))
                .isEqualTo(AlternationContext.UNKNOWN);
        assertThat(resolver.cycleWeekIndex(ANCHOR, 1, LocalDate.of(2026, 8, 31))).isZero();
    }

    @Test
    void oneWeekOutOfFourAlternatesByWeekBlock() {
        PatternConfiguration config = oneOutOfFour();
        // Semaine 1 (jours 0..6 depuis l'ancre) : école
        assertThat(resolver.resolve(ANCHOR, config, ANCHOR)).isEqualTo(AlternationContext.SCHOOL);
        assertThat(resolver.resolve(ANCHOR, config, ANCHOR.plusDays(6))).isEqualTo(AlternationContext.SCHOOL);
        // Semaine 2 (jours 7..13) : entreprise
        assertThat(resolver.resolve(ANCHOR, config, ANCHOR.plusDays(7))).isEqualTo(AlternationContext.COMPANY);
        // Semaine 5 = semaine 1 du cycle suivant : école
        assertThat(resolver.resolve(ANCHOR, config, ANCHOR.plusDays(28))).isEqualTo(AlternationContext.SCHOOL);
        assertThat(resolver.cycleWeekIndex(ANCHOR, 4, ANCHOR.plusDays(28))).isEqualTo(1);
    }

    @Test
    void twoWeeksOutOfFourAlternatesByWeekBlock() {
        PatternConfiguration config = twoOutOfFour();
        assertThat(resolver.resolve(ANCHOR, config, ANCHOR.plusDays(7))).isEqualTo(AlternationContext.SCHOOL);
        assertThat(resolver.resolve(ANCHOR, config, ANCHOR.plusDays(14))).isEqualTo(AlternationContext.COMPANY);
        assertThat(resolver.resolve(ANCHOR, config, ANCHOR.plusDays(21))).isEqualTo(AlternationContext.COMPANY);
    }

    @Test
    void customUnclassifiedWeekResolvesUnknown() {
        PatternConfiguration config = custom();
        // Semaine 1 : école, mais seulement lundi/mardi
        assertThat(resolver.resolve(ANCHOR, config, ANCHOR.plusDays(0))).isEqualTo(AlternationContext.SCHOOL); // Tue
        assertThat(resolver.resolve(ANCHOR, config, ANCHOR.plusDays(2))).isEqualTo(AlternationContext.UNKNOWN); // Thu
        // Semaine 2 : entreprise
        assertThat(resolver.resolve(ANCHOR, config, ANCHOR.plusDays(7))).isEqualTo(AlternationContext.COMPANY);
        // Semaine 3 : non classifiée -> UNKNOWN
        assertThat(resolver.resolve(ANCHOR, config, ANCHOR.plusDays(14))).isEqualTo(AlternationContext.UNKNOWN);
    }
}
