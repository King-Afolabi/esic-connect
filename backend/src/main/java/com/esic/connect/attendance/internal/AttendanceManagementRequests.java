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

    /**
     * Signalement d'un apprenant provisoire (EF-ATT-007 ; docs/02 §16.12).
     *
     * <p>{@code status} : {@code UNREGISTERED_GUEST} (personne inconnue)
     * ou {@code PENDING_REGISTRATION} (apprenant attendu dont
     * l'inscription n'est pas enregistrée). Les statuts de régularisation
     * sont refusés ici : ils sont des <em>résultats</em> de décision.
     *
     * <p>L'identité saisie est <strong>déclarée</strong>, jamais vérifiée.
     */
    record DeclareGuest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 100) String firstName,
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 100) String lastName,
            @jakarta.validation.constraints.Email
            @jakarta.validation.constraints.Size(max = 255) String email,
            @jakarta.validation.constraints.Size(max = 500) String comment,
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Pattern(
                    regexp = "UNREGISTERED_GUEST|PENDING_REGISTRATION") String status,
            @jakarta.validation.constraints.Size(max = 64) String checkpointPublicId) {
    }

    /**
     * Régularisation d'une entrée provisoire (EF-ATT-007).
     *
     * <p>{@code link = true} rattache à {@code enrollmentPublicId} ;
     * {@code false} écarte l'entrée. Dans les deux cas le motif est
     * obligatoire : une décision sans motif n'est pas relisible.
     */
    record ResolveGuest(
            @jakarta.validation.constraints.NotNull Boolean link,
            @jakarta.validation.constraints.Size(max = 64) String enrollmentPublicId,
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 500) String comment) {
    }
}
