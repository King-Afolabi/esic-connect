package com.esic.connect.attendance.internal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Requêtes de gestion manuelle des présences (V10). */
final class AttendanceManagementRequests {

    private AttendanceManagementRequests() {
    }

    /**
     * Présence saisie manuellement. {@code status} ∈ {PRESENT, LATE,
     * ABSENT} — {@code EXCUSED_ABSENCE} ne se saisit pas directement (il
     * résulte d'un justificatif accepté). {@code comment} obligatoire.
     */
    record ManualRecord(
            @NotBlank String enrollmentPublicId,
            @NotBlank String checkpointPublicId,
            @NotBlank @Pattern(regexp = "PRESENT|LATE|ABSENT") String status,
            @Min(0) Integer lateMinutes,
            @NotBlank @Size(max = 500) String comment) {
    }

    /**
     * Correction d'une présence. Au moins un des champs {@code status} /
     * {@code lateMinutes} / {@code comment} doit être fourni (contrôle
     * service). {@code reason} obligatoire.
     */
    record Correct(
            @Pattern(regexp = "PRESENT|LATE|ABSENT") String status,
            @Min(0) Integer lateMinutes,
            @Size(max = 500) String comment,
            @NotBlank @Size(max = 500) String reason) {
    }

    /** Annulation logique d'une présence : motif obligatoire. */
    record Cancel(@NotBlank @Size(max = 500) String reason) {
    }
}
