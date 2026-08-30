package com.esic.connect.attendance.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Requêtes du cycle de vie d'un justificatif d'absence (V10). */
final class JustificationRequests {

    private JustificationRequests() {
    }

    private static final String CATEGORIES = "MEDICAL|TRANSPORT|FAMILY|ADMINISTRATIVE|OTHER";

    /**
     * Dépôt d'un justificatif par l'apprenant. Rattaché à un
     * <em>point de contrôle</em> : le serveur crée / réutilise la
     * présence {@code ABSENT} correspondante. Aucune pièce jointe dans
     * cette tranche.
     */
    record Submit(
            @NotBlank String checkpointPublicId,
            @NotBlank @Pattern(regexp = CATEGORIES) String category,
            @Size(max = 120) String externalReference,
            @NotBlank @Size(max = 1000) String comment) {
    }

    /** Modification par l'apprenant, autorisée seulement tant que {@code PENDING}. */
    record Amend(
            @NotBlank @Pattern(regexp = CATEGORIES) String category,
            @Size(max = 120) String externalReference,
            @NotBlank @Size(max = 1000) String comment) {
    }

    /**
     * Examen par un gestionnaire. {@code decisionReason} obligatoire si
     * {@code decision = REJECTED} (contrôle service).
     */
    record Review(
            @NotBlank @Pattern(regexp = "ACCEPTED|REJECTED") String decision,
            @Size(max = 500) String decisionReason) {
    }
}
