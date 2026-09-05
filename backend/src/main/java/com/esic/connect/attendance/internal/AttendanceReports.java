package com.esic.connect.attendance.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO des rapports d'assiduité (V10) — jamais d'identifiant SQL, jamais
 * d'adresse électronique.
 *
 * <p>Unité de calcul : la <strong>demi-journée</strong>
 * (docs/02 §24.2). Un point de contrôle est classé matin / après-midi
 * selon l'heure locale de la séance ({@code < 13:00} = matin). Une
 * demi-journée est <em>présente</em> si tous ses points de contrôle
 * obligatoires (non annulés) portent une présence {@code PRESENT} /
 * {@code LATE} / {@code EXCUSED_ABSENCE}. Les demi-journées de contexte
 * d'alternance {@code COMPANY} sont exclues du dénominateur scolaire ;
 * celles de contexte {@code UNKNOWN} sont comptées à part.
 */
final class AttendanceReports {

    private AttendanceReports() {
    }

    /** Ligne d'un rapport par séance. */
    record SessionRow(
            UUID sessionPublicId,
            String sessionTitle,
            Instant startsAt,
            Instant endsAt,
            String classCodes,
            String teacherName,
            int checkpointCount,
            long expectedCount,
            int presentCount,
            int lateCount,
            int absentCount,
            int excusedCount,
            double attendanceRate) {
    }

    /** Indicateurs de demi-journées agrégés (rapport classe / apprenant / synthèse). */
    record HalfDayTotals(
            long expectedHalfDays,
            long presentHalfDays,
            long absentHalfDays,
            long excusedHalfDays,
            long companyHalfDays,
            long unknownHalfDays,
            long lateCount,
            double attendanceRate,
            double unjustifiedAbsenceRate) {

        static HalfDayTotals of(long expected, long present, long excused, long company, long unknown,
                                long late) {
            long absent = Math.max(0, expected - present - excused);
            double rate = expected == 0 ? 0d : (double) present / expected;
            double unjustified = expected == 0 ? 0d : (double) absent / expected;
            return new HalfDayTotals(expected, present, absent, excused, company, unknown, late,
                    round(rate), round(unjustified));
        }

        private static double round(double value) {
            return Math.round(value * 10000d) / 10000d;
        }
    }

    /** Ligne d'un rapport par classe. */
    record ClassRow(
            UUID classGroupPublicId,
            String classCode,
            int studentCount,
            HalfDayTotals totals) {
    }

    /** Ligne d'un rapport par apprenant. */
    record StudentRow(
            UUID studentProfilePublicId,
            UUID enrollmentPublicId,
            String studentNumber,
            String firstName,
            String lastName,
            String classCode,
            HalfDayTotals totals) {
    }

    /**
     * Vérification d'un document officiel présenté par un tiers
     * (EF-REP-006, AC-033).
     *
     * <p>Ne porte <strong>aucune donnée d'assiduité</strong> : le tiers
     * doit pouvoir constater qu'un identifiant correspond bien à une
     * attestation émise, pas lire le taux de présence de la personne.
     */
    record DocumentCheck(
            String documentId,
            String documentType,
            java.time.Instant issuedAt,
            String issuedBy,
            java.time.LocalDate periodStart,
            java.time.LocalDate periodEnd,
            boolean revoked) {
    }

    /** Ligne du registre des documents émis (écran d'administration). */
    record DocumentSummary(
            UUID publicId,
            String documentId,
            String documentType,
            String subject,
            java.time.Instant issuedAt,
            String issuedBy,
            boolean revoked) {
    }

    /** Cartes de synthèse. */
    record Summary(
            Instant from,
            Instant to,
            int classCount,
            int sessionCount,
            HalfDayTotals totals,
            long pendingJustifications,
            List<String> notes) {
    }
}
