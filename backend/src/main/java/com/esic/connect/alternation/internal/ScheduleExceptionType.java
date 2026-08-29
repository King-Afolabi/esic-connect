package com.esic.connect.alternation.internal;

/**
 * Type d'une exception individuelle de calendrier (docs/04 §14.3,
 * docs/02 §8.3). Valeurs minimales, déduites des cas explicitement
 * décrits — aucune valeur arbitraire ajoutée :
 *
 * <ul>
 *   <li>{@link #REMOTE_ALLOWED} — autorisation de suivre à distance ;
 *       n'agit <em>pas</em> sur l'axe SCHOOL / COMPANY (l'apprenant reste
 *       attendu « à l'école », mais à distance) ;</li>
 *   <li>{@link #ON_SITE_REQUIRED} — présence exceptionnelle à l'école ;
 *       prime sur le rythme et impose le contexte {@code SCHOOL} ;</li>
 *   <li>{@link #COMPANY_PERIOD} — période en entreprise ; prime sur le
 *       rythme et impose le contexte {@code COMPANY} ;</li>
 *   <li>{@link #VALIDATED_UNAVAILABILITY} — indisponibilité validée ;
 *       n'agit pas sur l'axe SCHOOL / COMPANY dans ce lot (aucun calcul
 *       d'assiduité), mais est renvoyée dans la réponse pour information.</li>
 * </ul>
 */
enum ScheduleExceptionType {
    REMOTE_ALLOWED,
    ON_SITE_REQUIRED,
    COMPANY_PERIOD,
    VALIDATED_UNAVAILABILITY
}
