package com.esic.connect.alternation.internal;

/**
 * Types de rythme d'alternance (docs/02 §8.2, docs/04 §14.1).
 *
 * <ul>
 *   <li>{@link #THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY} — cycle d'une
 *       semaine, jours école / entreprise explicites ;</li>
 *   <li>{@link #ONE_WEEK_SCHOOL_OUT_OF_FOUR} — cycle de 4 semaines, une
 *       seule semaine à l'école ;</li>
 *   <li>{@link #TWO_WEEKS_SCHOOL_OUT_OF_FOUR} — cycle de 4 semaines, deux
 *       semaines à l'école ;</li>
 *   <li>{@link #CUSTOM} — cycle et répartition libres, entièrement
 *       décrits par {@code configuration_json}.</li>
 * </ul>
 */
enum WorkStudyPatternType {
    THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY,
    ONE_WEEK_SCHOOL_OUT_OF_FOUR,
    TWO_WEEKS_SCHOOL_OUT_OF_FOUR,
    CUSTOM
}
