package com.esic.connect.attendance;

import java.util.UUID;

/**
 * Port public de lecture d'agrégats d'assiduité pour les tableaux de bord
 * (bloc G1-F ; DEC-G1-010). Le module {@code dashboard} l'utilise pour
 * des cartes chiffrées — <strong>requêtes agrégées bornées</strong>
 * ({@code COUNT} / {@code GROUP BY}), aucune entité ni repository exposé,
 * aucun contenu de justificatif.
 */
public interface AttendanceDashboardDirectory {

    /** Nombre de justificatifs {@code PENDING} sur tout le périmètre (carte administration). */
    long countPendingJustifications();

    /**
     * Digest d'assiduité d'un apprenant (carte {@code STUDENT}) — ses
     * <strong>propres</strong> données uniquement (AC-017).
     *
     * @param studentUserPublicId identifiant public du compte apprenant
     */
    StudentAttendanceDigest studentDigest(UUID studentUserPublicId);

    /**
     * @param present     présences enregistrées {@code PRESENT}
     * @param late        présences {@code LATE}
     * @param absent      absences {@code ABSENT}
     * @param excused     absences excusées {@code EXCUSED_ABSENCE}
     * @param pendingJustifications justificatifs de l'apprenant en attente d'examen
     * @param rejectedJustifications justificatifs de l'apprenant refusés
     */
    record StudentAttendanceDigest(
            long present,
            long late,
            long absent,
            long excused,
            long pendingJustifications,
            long rejectedJustifications) {
    }
}
