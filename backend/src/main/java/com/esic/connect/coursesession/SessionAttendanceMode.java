package com.esic.connect.coursesession;

/**
 * Modalité d'enseignement d'une séance (docs/02 §15).
 *
 * <ul>
 *   <li>{@link #ON_SITE} : l'apprenant est attendu sur site (§15.1) ;</li>
 *   <li>{@link #REMOTE} : la séance est à distance pour toute la classe
 *       (§15.2) ;</li>
 *   <li>{@link #HYBRID} : participants sur site et à distance dans la même
 *       séance (§15.4).</li>
 * </ul>
 *
 * <p>Type public : le module {@code attendance} en a besoin pour décider
 * si un canal distant est recevable, et le contrat ne peut donc pas rester
 * interne à {@code coursesession}.
 */
public enum SessionAttendanceMode {
    ON_SITE,
    REMOTE,
    HYBRID
}
