package com.esic.connect.identity.internal;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contrats de la comparaison contrôlée de deux doublons (ANO-USER-001 ;
 * docs/02 §9.5).
 *
 * <p><strong>Lecture seule.</strong> {@code POST
 * /api/v1/users/duplicates/compare} ne fusionne rien, ne modifie rien et
 * n'écrit aucune trace. Il produit une <em>simulation</em> : ce qui
 * rapproche les deux comptes, ce qui les distingue, ce qui empêche ou
 * fragilise une fusion, et le volume de données rattaché de part et
 * d'autre. La décision — et, plus tard, la fusion elle-même — reste
 * humaine (docs/02 §9.5).
 */
public final class DuplicateComparisonWeb {

    private DuplicateComparisonWeb() {
    }

    /**
     * @param firstUserId  identifiant public du premier compte
     * @param secondUserId identifiant public du second compte ;
     *                     obligatoirement différent du premier
     */
    public record CompareRequest(
            @NotNull UUID firstUserId,
            @NotNull UUID secondUserId) {
    }

    /**
     * Verdict informatif de la simulation. Ne déclenche jamais d'action :
     * il oriente la revue humaine.
     */
    public enum Assessment {
        /** Aucun conflit ; identité concordante ; historique concentré sur un seul compte. */
        POTENTIALLY_SAFE,
        /** Aucun conflit bloquant, mais des points à trancher à la main. */
        MANUAL_REVIEW_REQUIRED,
        /** Au moins un conflit rend une fusion automatique impossible. */
        NOT_MERGEABLE
    }

    /**
     * Un compte vu par la comparaison. N'expose ni hachage de mot de
     * passe, ni secret MFA, ni code de récupération, ni jeton
     * d'invitation, ni structure de passkey : uniquement des décomptes et
     * des indicateurs. L'adresse électronique et le numéro étudiant sont
     * présents parce que la route est réservée à {@code ADMIN} /
     * {@code SUPER_ADMIN}, qui les voient déjà sur la fiche du compte
     * ({@code GET /api/v1/users/{id}}).
     */
    public record ComparisonSide(
            UUID id,
            String displayName,
            String email,
            String phone,
            String status,
            List<String> roles,
            boolean hasStudentProfile,
            String studentNumber,
            boolean hasActiveEnrollment,
            boolean hasLoginCredential,
            boolean mfaConfigured,
            long passkeys,
            long trustedDevices,
            Instant createdAt,
            Instant lastLoginAt) {
    }

    /** Un point de divergence, de blocage ou de vigilance, en clair. */
    public record Note(String code, String detail) {
    }

    public record ComparisonResponse(
            ComparisonSide first,
            ComparisonSide second,
            List<String> matchingFields,
            List<String> differentFields,
            List<Note> conflicts,
            List<Note> warnings,
            List<String> consequences,
            Map<String, Long> dependencySummary,
            Assessment assessment,
            List<String> reasons) {
    }
}
