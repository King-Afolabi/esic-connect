package com.esic.connect.enrollment;

/**
 * Action du cycle de vie d'une ressource du module {@code enrollment},
 * tracée par l'audit (cahier §30.1 : « import d'apprenants », changements
 * de classe).
 *
 * <ul>
 *   <li>{@link #CREATED} : profil apprenant créé, ou inscription créée ;</li>
 *   <li>{@link #TRANSFERRED} : inscription clôturée à la suite d'un
 *       changement de classe (docs/04 §13.2) ;</li>
 *   <li>{@link #CLOSED} : inscription clôturée (fin de cursus / départ) ;</li>
 *   <li>{@link #UPDATED}, {@link #ARCHIVED}, {@link #RESTORED} : cycle de
 *       vie d'un groupe temporaire (EF-ACA-007).</li>
 * </ul>
 */
public enum EnrollmentChangeAction {
    CREATED,
    TRANSFERRED,
    CLOSED,
    UPDATED,
    ARCHIVED,
    /** Autorisation de suivi à distance retirée (EF-ENR-004). */
    REVOKED,
    RESTORED
}
