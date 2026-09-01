package com.esic.connect.dashboard.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO du tableau de bord (bloc G1-F). Une seule enveloppe
 * {@link Dashboard} : le champ {@code role} discrimine, une seule des
 * sections {@code student} / {@code teacher} / {@code manager} /
 * {@code administration} est renseignée. Jamais d'identifiant SQL,
 * jamais d'adresse électronique, jamais de contenu d'audit.
 */
final class DashboardResponses {

    private DashboardResponses() {
    }

    /**
     * @param role          rôle effectif retenu côté serveur
     * @param generatedAt   horodatage de génération
     * @param student       carte apprenant ({@code null} si autre rôle)
     * @param teacher       carte formateur ({@code null} si autre rôle)
     * @param manager       carte responsable pédagogique ({@code null} si autre rôle)
     * @param administration carte administration ({@code null} si autre rôle)
     * @param notes         mentions honnêtes (cartes `PARTIAL`, périmètre…)
     */
    record Dashboard(
            String role,
            Instant generatedAt,
            StudentCard student,
            TeacherCard teacher,
            ManagerCard manager,
            AdministrationCard administration,
            List<String> notes) {
    }

    /** Séance courte cliquable (identifiants publics uniquement). */
    record SessionLine(
            UUID sessionPublicId,
            String title,
            String status,
            Instant startsAt,
            Instant endsAt,
            List<String> classCodes) {
    }

    record ImportLine(UUID publicId, String status, int totalRows, Instant createdAt) {
    }

    /**
     * @param nextSession           prochaine séance de l'apprenant ({@code null} si aucune)
     * @param weekSessions          séances des 7 prochains jours (≤ 10)
     * @param present               présences {@code PRESENT}
     * @param late                  présences {@code LATE}
     * @param absent                absences {@code ABSENT}
     * @param excused               absences excusées
     * @param pendingJustifications justificatifs en attente
     * @param rejectedJustifications justificatifs refusés
     */
    record StudentCard(
            SessionLine nextSession,
            List<SessionLine> weekSessions,
            long present,
            long late,
            long absent,
            long excused,
            long pendingJustifications,
            long rejectedJustifications) {
    }

    /**
     * @param nextSession    prochaine séance ({@code null} si aucune)
     * @param upcoming       séances des 7 prochains jours (≤ 10)
     * @param toOpen         séances déjà commencées non encore ouvertes (≤ 10)
     */
    record TeacherCard(
            SessionLine nextSession,
            List<SessionLine> upcoming,
            List<SessionLine> toOpen) {
    }

    /**
     * @param classCount           classes du périmètre
     * @param upcomingSessions     séances des 7 prochains jours dans le périmètre (≤ 10)
     * @param classCodes           codes des classes du périmètre (≤ 10)
     */
    record ManagerCard(
            long classCount,
            List<SessionLine> upcomingSessions,
            List<String> classCodes) {
    }

    /**
     * @param activeAccounts        comptes actifs
     * @param suspendedAccounts     comptes suspendus / verrouillés
     * @param pendingActivation     comptes en attente d'activation
     * @param archivedAccounts      comptes archivés
     * @param pendingJustifications justificatifs en attente (global)
     * @param recentImports         derniers imports d'apprenants (≤ 10)
     * @param todaySessions         séances du jour (≤ 10)
     */
    record AdministrationCard(
            long activeAccounts,
            long suspendedAccounts,
            long pendingActivation,
            long archivedAccounts,
            long pendingJustifications,
            List<ImportLine> recentImports,
            List<SessionLine> todaySessions) {
    }
}
