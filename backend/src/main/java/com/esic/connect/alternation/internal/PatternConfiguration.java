package com.esic.connect.alternation.internal;

import java.time.DayOfWeek;
import java.util.Set;

/**
 * Configuration de rythme <em>normalisée</em> et validée, produite par
 * {@link AlternationConfigParser} à partir de {@code configuration_json}.
 * Objet de valeur immuable, sans I/O : consommé tel quel par
 * {@link AlternationResolver}.
 *
 * <p>Le contexte d'un couple (semaine du cycle, jour) se résout ainsi
 * (bornes inclusives, calcul déterministe) :
 * <ol>
 *   <li>samedi / dimanche → {@link AlternationContext#UNKNOWN} (aucun
 *       cours) ;</li>
 *   <li>semaine dans {@link #schoolWeeks} : jour dans {@link #schoolDays}
 *       → {@link AlternationContext#SCHOOL} ; sinon jour dans
 *       {@link #companyDays} → {@link AlternationContext#COMPANY} ; sinon
 *       {@link AlternationContext#UNKNOWN} ;</li>
 *   <li>semaine dans {@link #companyWeeks} : jour ouvré →
 *       {@link AlternationContext#COMPANY} ;</li>
 *   <li>semaine non classifiée → {@link AlternationContext#UNKNOWN}.</li>
 * </ol>
 *
 * @param cycleLengthWeeks longueur du cycle en semaines (≥ 1)
 * @param schoolWeeks      indices 1..{@code cycleLengthWeeks} des semaines école
 * @param companyWeeks     indices 1..{@code cycleLengthWeeks} des semaines entreprise
 * @param schoolDays       jours « école » à l'intérieur d'une semaine école
 * @param companyDays      jours « entreprise » à l'intérieur d'une semaine école
 *                         (utilisé par le rythme 3 jours / 2 jours)
 */
record PatternConfiguration(
        int cycleLengthWeeks,
        Set<Integer> schoolWeeks,
        Set<Integer> companyWeeks,
        Set<DayOfWeek> schoolDays,
        Set<DayOfWeek> companyDays) {

    /**
     * @param weekIndex position 1..{@code cycleLengthWeeks} dans le cycle
     * @param day       jour de la semaine
     * @return le contexte attendu, jamais {@code null}
     */
    AlternationContext resolve(int weekIndex, DayOfWeek day) {
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return AlternationContext.UNKNOWN;
        }
        if (schoolWeeks.contains(weekIndex)) {
            if (schoolDays.contains(day)) {
                return AlternationContext.SCHOOL;
            }
            if (companyDays.contains(day)) {
                return AlternationContext.COMPANY;
            }
            return AlternationContext.UNKNOWN;
        }
        if (companyWeeks.contains(weekIndex)) {
            return AlternationContext.COMPANY;
        }
        return AlternationContext.UNKNOWN;
    }
}
