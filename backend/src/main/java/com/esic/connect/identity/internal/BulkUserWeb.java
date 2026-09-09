package com.esic.connect.identity.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Contrats des opérations de masse et de la détection de doublons
 * (EF-USER-004, EF-USER-005 ; docs/02 §9.4 et §9.5).
 */
public final class BulkUserWeb {

    private BulkUserWeb() {
    }

    /** Opérations groupées autorisées. Volontairement fermée : pas de suppression. */
    public enum BulkAction {
        SUSPEND,
        RESTORE,
        ARCHIVE,
        /** Réémet l'invitation des comptes encore en attente d'activation. */
        RESEND_INVITATION
    }

    /**
     * @param confirm {@code false} — le défaut — produit une
     *                <strong>prévisualisation</strong> : rien n'est écrit,
     *                et la réponse chiffre ce qui se passerait. Le cahier
     *                l'exige (§9.4 : « avant exécution, le système affiche
     *                le nombre d'utilisateurs concernés, les conséquences,
     *                les erreurs, les éléments ignorés, et demande
     *                confirmation »).
     */
    public record BulkRequest(
            @NotBlank String action,
            @NotEmpty @Size(max = 500) List<String> userIds,
            @NotBlank @Size(max = 500) String reason,
            Boolean confirm) {

        boolean isConfirmed() {
            return Boolean.TRUE.equals(confirm);
        }
    }

    /**
     * Résultat d'une opération groupée.
     *
     * @param applied {@code false} pour une prévisualisation : les
     *                compteurs décrivent ce qui <em>serait</em> fait
     * @param eligible nombre de comptes sur lesquels l'action s'appliquera
     * @param ignored  comptes déjà dans l'état visé — sans effet, pas une erreur
     * @param rejected comptes refusés (protection, auto-action, introuvable)
     */
    public record BulkResult(
            boolean applied,
            String action,
            int requested,
            int eligible,
            int ignored,
            int rejected,
            List<BulkOutcome> outcomes) {
    }

    /**
     * Sort d'un compte dans l'opération.
     *
     * @param outcome {@code ELIGIBLE}, {@code IGNORED} ou {@code REJECTED}
     * @param reason  motif du refus ou de l'omission, en clair
     */
    public record BulkOutcome(UUID userId, String email, String outcome, String reason) {
    }

    /**
     * Groupe de comptes soupçonnés d'être un doublon (EF-USER-005).
     *
     * @param signature ce qui les rapproche — nom normalisé, téléphone…
     *                  Jamais une décision : le cahier réserve la
     *                  suppression d'un doublon à une action humaine
     *                  doublement confirmée (§9.5).
     */
    public record DuplicateGroup(String signature, String reason, List<DuplicateCandidate> accounts) {
    }

    public record DuplicateCandidate(UUID userId, String email, String firstName, String lastName,
                                     String status, java.time.Instant createdAt) {
    }
}
