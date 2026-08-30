package com.esic.connect.attendance;

/**
 * Statut métier d'une présence (V10, docs/04 §19.2 — réduit à cette
 * tranche : {@code PARTIAL} / {@code TO_CONFIRM} sont reportés,
 * {@code EXCUSED_ABSENCE} ≡ {@code EXCUSED} du cahier).
 *
 * <ul>
 *   <li>{@link #PRESENT} : émargement dans la tolérance de retard ;</li>
 *   <li>{@link #LATE} : émargement au-delà du seuil
 *       {@code app.attendance.late-threshold} ; {@code lateMinutes}
 *       renseigné ; compté comme présent pour la demi-journée mais
 *       tallié séparément ;</li>
 *   <li>{@link #ABSENT} : absence saisie manuellement, ou dérivée dans
 *       les rapports (effectif attendu moins présences valides) — dans ce
 *       dernier cas aucune ligne n'est persistée ;</li>
 *   <li>{@link #EXCUSED_ABSENCE} : absence justifiée — résulte
 *       <em>uniquement</em> de l'acceptation d'un justificatif
 *       ({@code ABSENT → EXCUSED_ABSENCE}), jamais d'une saisie directe.
 *       Exclue du taux d'absence injustifiée, reste visible ;</li>
 *   <li>{@link #CANCELLED} : présence annulée logiquement (ligne
 *       conservée, motif dans l'historique de correction).</li>
 * </ul>
 */
public enum AttendanceStatus {
    PRESENT,
    LATE,
    ABSENT,
    EXCUSED_ABSENCE,
    CANCELLED
}
