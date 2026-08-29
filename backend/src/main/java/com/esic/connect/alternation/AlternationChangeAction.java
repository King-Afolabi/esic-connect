package com.esic.connect.alternation;

/**
 * Action du cycle de vie d'une ressource du module {@code alternation},
 * tracée par l'audit (cahier §30.1).
 *
 * <ul>
 *   <li>{@link #CREATED} / {@link #UPDATED} / {@link #ARCHIVED} /
 *       {@link #RESTORED} : cycle de vie d'un modèle de rythme ;</li>
 *   <li>{@link #ASSIGNED} : rythme affecté à une classe ;</li>
 *   <li>{@link #CLOSED} : affectation de classe clôturée ;</li>
 *   <li>{@link #CANCELLED} : exception individuelle annulée.</li>
 * </ul>
 */
public enum AlternationChangeAction {
    CREATED,
    UPDATED,
    ARCHIVED,
    RESTORED,
    ASSIGNED,
    CLOSED,
    CANCELLED
}
