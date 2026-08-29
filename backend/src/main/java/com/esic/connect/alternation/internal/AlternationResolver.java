package com.esic.connect.alternation.internal;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Service métier <em>pur</em> (aucune I/O) qui résout, pour une
 * affectation de rythme et une date, le contexte attendu
 * {@link AlternationContext} (section 9 du lot).
 *
 * <p>Algorithme déterministe, bornes inclusives :
 * <ol>
 *   <li>si {@code date} est antérieure à {@code cycle_start_date} de
 *       l'affectation → {@link AlternationContext#UNKNOWN} (comportement
 *       explicite : la date précède l'ancre du cycle) ;</li>
 *   <li>la <em>semaine du cycle</em> est le bloc de 7 jours depuis
 *       {@code cycle_start_date} : jours 0..6 = semaine 1, jours 7..13 =
 *       semaine 2, etc. La position dans le cycle est
 *       {@code ((joursDepuisAncre / 7) modulo cycleLengthWeeks) + 1} ;</li>
 *   <li>la {@link PatternConfiguration} interprète le couple (semaine,
 *       jour) — voir sa javadoc : week-end et zones non classifiées
 *       produisent {@link AlternationContext#UNKNOWN}.</li>
 * </ol>
 *
 * <p>Le cas « aucune affectation » n'est pas traité ici : l'appelant
 * ({@code AlternationContextService}) renvoie {@link AlternationContext#UNKNOWN}
 * avec la source {@link ContextSource#NONE}. Aucun calcul d'absence,
 * aucune dépendance à {@code planning} ou {@code attendance}.
 */
@Component
class AlternationResolver {

    AlternationContext resolve(LocalDate cycleStartDate, PatternConfiguration config, LocalDate date) {
        if (date.isBefore(cycleStartDate)) {
            return AlternationContext.UNKNOWN;
        }
        long daysSinceAnchor = ChronoUnit.DAYS.between(cycleStartDate, date);
        int weekNumberZeroBased = (int) (daysSinceAnchor / 7);
        int weekIndexOneBased = Math.floorMod(weekNumberZeroBased, config.cycleLengthWeeks()) + 1;
        return config.resolve(weekIndexOneBased, date.getDayOfWeek());
    }

    /**
     * @return la position 1..{@code cycleLengthWeeks} dans le cycle, ou
     *         {@code 0} si la date précède l'ancre (exposé pour le
     *         diagnostic dans la réponse HTTP)
     */
    int cycleWeekIndex(LocalDate cycleStartDate, int cycleLengthWeeks, LocalDate date) {
        if (date.isBefore(cycleStartDate)) {
            return 0;
        }
        long daysSinceAnchor = ChronoUnit.DAYS.between(cycleStartDate, date);
        return Math.floorMod((int) (daysSinceAnchor / 7), cycleLengthWeeks) + 1;
    }
}
