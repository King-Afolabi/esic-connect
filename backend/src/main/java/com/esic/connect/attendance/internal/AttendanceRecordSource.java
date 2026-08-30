package com.esic.connect.attendance.internal;

/**
 * Canal d'enregistrement d'une présence (docs/02 §16.4, réduit ; étendu
 * par V10).
 *
 * <ul>
 *   <li>{@link #DYNAMIC_QR} : jeton opaque du QR dynamique du formateur ;</li>
 *   <li>{@link #SHORT_CODE} : code court saisi dans l'application
 *       (distanciel, problème de caméra, appareil unique) ;</li>
 *   <li>{@link #MANUAL} : présence saisie manuellement par un formateur
 *       ou un gestionnaire (motif obligatoire), y compris la ligne
 *       {@code ABSENT} créée à la volée lors du dépôt d'un justificatif ;</li>
 *   <li>{@link #CORRECTION} : réservé — une correction ne change pas la
 *       {@code source} d'origine de la ligne, mais cette valeur reste
 *       disponible pour un futur besoin de traçabilité fine.</li>
 * </ul>
 */
public enum AttendanceRecordSource {
    DYNAMIC_QR,
    SHORT_CODE,
    MANUAL,
    CORRECTION
}
