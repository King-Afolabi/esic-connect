package com.esic.connect.planning.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO de sortie du module {@code planning} — jamais d'identifiant SQL, de
 * jeton, de chemin physique ni de contenu de cellule brut (seule la
 * valeur reçue tronquée d'une anomalie).
 */
final class PlanningResponses {

    private PlanningResponses() {
    }

    /** En-tête d'un job d'import + synthèse de simulation. */
    record JobResponse(
            UUID publicId,
            String status,
            UUID classGroupPublicId,
            UUID academicYearPublicId,
            String originalFileName,
            int fileSizeBytes,
            char csvSeparator,
            int totalRows,
            int validRows,
            int warningRows,
            int errorRows,
            int addedRows,
            int modifiedRows,
            int unchangedRows,
            int removedEntries,
            boolean confirmable,
            Instant simulatedAt,
            Instant expiresAt,
            Instant publishedAt,
            UUID publishedVersionPublicId,
            String failureReason,
            Instant createdAt) {
    }

    /** Une ligne d'import + ses anomalies. */
    record RowResponse(
            UUID publicId,
            int rowNumber,
            String slotKey,
            String sessionDate,
            String startTime,
            String endTime,
            String timeZoneId,
            String title,
            String teacherPublicId,
            String roomCode,
            String rowStatus,
            String plannedAction,
            Instant resolvedStartsAt,
            Instant resolvedEndsAt,
            List<IssueResponse> issues) {
    }

    /** Une anomalie de ligne. */
    record IssueResponse(
            String severity,
            String errorCode,
            String columnName,
            String receivedValue,
            String message) {
    }

    /** Résultat d'une publication. */
    record PublicationResponse(
            UUID jobPublicId,
            UUID versionPublicId,
            int versionNumber,
            boolean alreadyPublished) {
    }

    /** Une version de planning (liste / détail). */
    record VersionResponse(
            UUID publicId,
            UUID schedulePublicId,
            UUID classGroupPublicId,
            UUID academicYearPublicId,
            int versionNumber,
            String status,
            int entryCount,
            String changeSummary,
            UUID replacedByVersionPublicId,
            Instant publishedAt,
            Instant createdAt) {
    }

    /**
     * Une entrée (créneau) d'une version de planning.
     *
     * @param publicId       identifiant de CETTE ligne de version (aléatoire)
     * @param slotPublicId   identité <strong>stable</strong> du créneau à
     *                       travers les versions (même valeur d'une version
     *                       à la suivante — DEC-G1-002)
     * @param sessionPublicId séance {@code course_session} liée (peut être
     *                        {@code null} tant que non matérialisée)
     */
    record VersionEntryResponse(
            UUID publicId,
            UUID slotPublicId,
            String slotKey,
            String title,
            Instant startsAt,
            Instant endsAt,
            String timeZoneId,
            String roomCode,
            UUID sessionPublicId) {
    }

    /** Détail d'une version : en-tête + ses entrées. */
    record VersionDetailResponse(
            VersionResponse version,
            List<VersionEntryResponse> entries) {
    }
}
