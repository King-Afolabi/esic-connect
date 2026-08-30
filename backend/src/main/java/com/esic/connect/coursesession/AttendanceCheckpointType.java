package com.esic.connect.coursesession;

/**
 * Type d'un point de contrôle d'émargement (V10).
 *
 * <p>Cette tranche généralise le point de contrôle unique de V9 à
 * plusieurs points de contrôle typés par séance. Les quatre types du
 * cahier (docs/02 §17.3 : {@code MORNING_ARRIVAL}, ...) restent
 * réalisables via des points {@link #CUSTOM} libellés et ordonnés.
 *
 * <ul>
 *   <li>{@link #START} : arrivée / début de séance — créé automatiquement
 *       avec la séance pour préserver le parcours V9 ;</li>
 *   <li>{@link #END} : fin de séance / départ ;</li>
 *   <li>{@link #CUSTOM} : point de contrôle intermédiaire libre.</li>
 * </ul>
 */
public enum AttendanceCheckpointType {
    START,
    END,
    CUSTOM
}
