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
     * Assiduité agrégée <strong>par classe</strong> sur une fenêtre, dans
     * le périmètre de l'appelant (EF-REP-007 ; docs/02 §22.6 — « taux par
     * formation et par classe »).
     *
     * <p>Le périmètre n'est pas un paramètre : il est relu du contexte de
     * sécurité par le module {@code attendance}, comme pour les rapports.
     * Le tableau de bord ne peut donc pas élargir ce qu'il voit en
     * changeant son appel.
     */
    java.util.List<ClassAttendanceDigest> classDigests(java.time.Instant from, java.time.Instant to);

    /**
     * Justificatifs {@code PENDING} <strong>dans le périmètre</strong> de
     * l'appelant, pour les séances de la fenêtre {@code [from, to]}.
     *
     * <p>La fenêtre est obligatoire : sans borne, le compte impose de
     * parcourir toutes les séances jamais créées, et le coût du tableau
     * de bord grandit à chaque séance ajoutée (NFR-PERF-08).
     */
    long countPendingJustificationsInScope(java.time.Instant from, java.time.Instant to);

    /**
     * Volume et délai de traitement des justificatifs sur une fenêtre
     * (carte administration ; §22.6).
     */
    JustificationThroughput justificationThroughput(java.time.Instant from, java.time.Instant to);

    /**
     * @param classGroupPublicId classe concernée
     * @param classCode          code de la classe
     * @param programCode        code de la formation, pour le regroupement
     * @param expectedHalfDays   demi-journées attendues (hors entreprise)
     * @param presentHalfDays    demi-journées suivies
     * @param absentHalfDays     demi-journées absentes non excusées
     * @param excusedHalfDays    demi-journées excusées
     * @param lateCount          retards constatés
     * @param attendanceRate     taux de présence, entre 0 et 1
     */
    record ClassAttendanceDigest(
            UUID classGroupPublicId,
            String classCode,
            String programCode,
            long expectedHalfDays,
            long presentHalfDays,
            long absentHalfDays,
            long excusedHalfDays,
            long lateCount,
            double attendanceRate) {
    }

    /**
     * @param pending       justificatifs encore en attente d'examen
     * @param decided       justificatifs examinés sur la fenêtre
     * @param medianDelayHours délai médian entre dépôt et décision, en
     *                      heures ; {@code null} si rien n'a été décidé —
     *                      afficher « 0 h » ferait croire à un traitement
     *                      instantané là où il n'y a eu aucun traitement
     */
    record JustificationThroughput(long pending, long decided, Double medianDelayHours) {
    }

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
