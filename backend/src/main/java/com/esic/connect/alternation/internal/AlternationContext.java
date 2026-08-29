package com.esic.connect.alternation.internal;

/**
 * Contexte attendu d'une classe (ou d'une inscription) à une date donnée
 * (docs/02 §8.1) :
 *
 * <ul>
 *   <li>{@link #SCHOOL} — jour à l'école ;</li>
 *   <li>{@link #COMPANY} — jour en entreprise (jamais une absence,
 *       docs/02 §8.4) ;</li>
 *   <li>{@link #UNKNOWN} — indéterminé : aucune affectation, date hors
 *       cycle, week-end, zone non classifiée d'un rythme {@code CUSTOM},
 *       ou contexte non déductible sans inventer de règle.</li>
 * </ul>
 */
enum AlternationContext {
    SCHOOL,
    COMPANY,
    UNKNOWN
}
