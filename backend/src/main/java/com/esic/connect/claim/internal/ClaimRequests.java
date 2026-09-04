package com.esic.connect.claim.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Requêtes de l'API des réclamations (docs/02 §20). */
final class ClaimRequests {

    private ClaimRequests() {
    }

    /**
     * Dépôt d'une réclamation (EF-CLAIM-001).
     *
     * <p>{@code audience} désigne un <strong>guichet</strong>, pas une
     * personne : formateur, responsable pédagogique ou administration
     * scolaire. La description devient le premier message du fil — le
     * cahier veut une conversation, pas un formulaire suivi d'un fil vide.
     */
    record Create(
            @NotBlank @Size(max = 24) String category,
            @NotBlank @Size(max = 191) String subject,
            @NotBlank @Size(max = 5000) String description,
            @NotBlank @Size(max = 32) String audience,
            @Size(max = 64) String sessionPublicId,
            LocalDate periodStart,
            LocalDate periodEnd) {
    }

    /** Message ajouté au fil (EF-CLAIM-002). Append-only. */
    record PostMessage(@NotBlank @Size(max = 5000) String body) {
    }

    /** Transfert vers un autre guichet (EF-CLAIM-003). Motif obligatoire. */
    record Transfer(
            @NotBlank @Size(max = 32) String audience,
            @NotBlank @Size(max = 500) String motive) {
    }

    /**
     * Décision d'un intervenant. {@code REOPENED} et {@code TRANSFERRED}
     * sont refusés ici : ce sont des résultats d'opérations dédiées, et
     * les poser directement contournerait leur traçabilité.
     */
    record Decide(
            @NotBlank @Size(max = 24) String status,
            @NotBlank @Size(max = 500) String motive) {
    }

    /** Réouverture d'une réclamation close (EF-CLAIM-004). */
    record Reopen(@NotBlank @Size(max = 500) String motive) {
    }
}
