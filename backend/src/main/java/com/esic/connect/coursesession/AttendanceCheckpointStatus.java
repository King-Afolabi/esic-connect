package com.esic.connect.coursesession;

/**
 * Cycle de vie propre d'un point de contrôle d'émargement (V10).
 *
 * <p>Transitions : {@code PLANNED → OPEN → CLOSED} ; {@code PLANNED → CANCELLED} ;
 * {@code OPEN → CANCELLED}.
 *
 * <ul>
 *   <li>{@link #PLANNED} : créé, émargement indisponible ;</li>
 *   <li>{@link #OPEN} : émargement possible (jeton dynamique / code
 *       court émis pour ce point de contrôle) ;</li>
 *   <li>{@link #CLOSED} : terminal — plus aucun émargement, jetons Redis
 *       du point de contrôle invalidés. Fermé individuellement ou avec la
 *       séance. Pas de réouverture ;</li>
 *   <li>{@link #CANCELLED} : terminal — retiré du calcul d'assiduité
 *       (dénominateurs de rapport). Annulable depuis {@code PLANNED} ou
 *       {@code OPEN}, motif obligatoire.</li>
 * </ul>
 */
public enum AttendanceCheckpointStatus {
    PLANNED,
    OPEN,
    CLOSED,
    CANCELLED
}
