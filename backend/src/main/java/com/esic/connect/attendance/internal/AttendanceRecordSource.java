package com.esic.connect.attendance.internal;

/**
 * Canal d'enregistrement d'une présence (docs/02 §15.4 et §16.4 ; étendu
 * par V10 puis par le sprint 7).
 *
 * <p>Les variantes {@code REMOTE_*} ne sont pas une preuve de
 * localisation : elles enregistrent ce que l'apprenant a <em>déclaré</em>.
 * Le contrôle réel de la présence sur site est le QR fixe de salle associé
 * à la plage réseau de l'établissement (EF-ATT-008/010, sprint 8). Ce que
 * la distinction garantit, c'est qu'un suivi à distance sur une séance
 * présentielle exige une autorisation et laisse une trace.
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
    CORRECTION,
    /** QR dynamique scanné à distance (docs/02 §15.4 — {@code REMOTE_QR}). */
    REMOTE_QR,
    /** Code court saisi à distance (docs/02 §15.4 — {@code REMOTE_CODE}). */
    REMOTE_CODE,
    /**
     * QR fixe de salle, sous contrôle de plage réseau (docs/02 §16.6 ;
     * EF-ATT-010). Contrairement aux autres canaux, celui-ci porte une
     * <strong>attestation d'origine réseau</strong> : il n'est accepté que
     * depuis une plage déclarée de l'établissement.
     */
    ROOM_STATIC_QR
}
