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
}
