package com.esic.connect.attendance;

/**
 * Effet d'un départ anticipé sur la journée de l'apprenant (EF-ATT-013 ;
 * docs/02 §16.13 : « L'effet est {@code PARTIAL}, {@code EXCUSED_PARTIAL}
 * ou {@code TO_CONFIRM} »).
 *
 * <p>Cette valeur est <strong>dérivée</strong> de l'état du dossier, jamais
 * stockée : une copie en base divergerait de sa source à la première
 * décision révisée.
 *
 * <ul>
 *   <li>{@link #TO_CONFIRM} : tant que personne n'a tranché. Un départ
 *       annoncé n'est pas un départ autorisé — la journée appelle un
 *       humain ;</li>
 *   <li>{@link #EXCUSED_PARTIAL} : départ accepté — la fin de journée
 *       manquante est excusée ;</li>
 *   <li>{@link #PARTIAL} : départ refusé — la journée reste incomplète et
 *       l'absence non excusée. Le refus ne fabrique pas une présence.</li>
 * </ul>
 */
public enum EarlyDepartureEffect {
    PARTIAL,
    EXCUSED_PARTIAL,
    TO_CONFIRM
}
