package com.esic.connect.attendance.internal;

/**
 * Canal d'enregistrement d'une présence dans cette tranche (docs/02 §16.4,
 * réduit).
 *
 * <ul>
 *   <li>{@link #DYNAMIC_QR} : jeton opaque du QR dynamique du formateur ;</li>
 *   <li>{@link #SHORT_CODE} : code court saisi dans l'application (parcours
 *       fiable de cette PR — distanciel, problème de caméra, appareil
 *       unique).</li>
 * </ul>
 */
public enum AttendanceRecordSource {
    DYNAMIC_QR,
    SHORT_CODE
}
