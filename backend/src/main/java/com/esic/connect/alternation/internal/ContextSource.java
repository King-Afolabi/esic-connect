package com.esic.connect.alternation.internal;

/**
 * Origine d'une décision de contexte effectif pour une inscription
 * (section 10 du lot) :
 *
 * <ul>
 *   <li>{@link #PATTERN} — issu du rythme affecté à la classe ;</li>
 *   <li>{@link #INDIVIDUAL_EXCEPTION} — une exception individuelle active
 *       de type {@code ON_SITE_REQUIRED} ou {@code COMPANY_PERIOD} prime
 *       sur le rythme ;</li>
 *   <li>{@link #NONE} — aucune information exploitable (ni rythme, ni
 *       exception déterminante).</li>
 * </ul>
 */
enum ContextSource {
    PATTERN,
    INDIVIDUAL_EXCEPTION,
    NONE
}
