package com.esic.connect.identity.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Contrats HTTP du second facteur (EF-AUTH-008, EF-AUTH-009).
 *
 * <p>Aucun de ces objets ne transporte de secret durable : le secret
 * partagé n'apparaît qu'une fois, dans {@link EnrollmentResponse}, et les
 * codes de récupération une seule fois, dans {@link ConfirmationResult}.
 */
public final class MfaWeb {

    private MfaWeb() {
    }

    /** Démarre un enrôlement. {@code challengeId} n'est fourni que pendant une connexion bloquée. */
    public record EnrollRequest(String challengeId) {
    }

    /**
     * Secret d'enrôlement. Renvoyé <strong>une seule fois</strong> :
     * l'appel suivant ne le redonne pas.
     *
     * @param secret          secret partagé en base32, à recopier à la main
     * @param provisioningUri URI {@code otpauth://} à encoder en QR code
     * @param periodSeconds   pas de temps, pour l'affichage du compte à rebours
     */
    public record EnrollmentResponse(String secret, String provisioningUri, int periodSeconds) {
    }

    /** Confirme l'enrôlement par un premier code valide. */
    public record ConfirmEnrollmentRequest(
            String challengeId,
            @NotBlank @Size(min = 6, max = 10) String code) {
    }

    /** Codes de récupération remis à la confirmation, affichés une seule fois. */
    public record ConfirmationResult(List<String> recoveryCodes) {
    }

    /**
     * Réponse de confirmation d'enrôlement : les codes de récupération et,
     * lorsque l'enrôlement était imposé pendant une connexion, le jeton
     * d'accès qui débloque enfin la session.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConfirmEnrollmentResponse(List<String> recoveryCodes, LoginResponse session) {
    }

    /** Résout un défi de connexion par un code TOTP ou de récupération. */
    public record VerifyRequest(
            @NotBlank String challengeId,
            @NotBlank @Size(min = 6, max = 20) String code) {
    }

    /** Code seul, pour les opérations exigeant une preuve du facteur déjà actif. */
    public record CodeRequest(@NotBlank @Size(min = 6, max = 20) String code) {
    }

    /**
     * État du second facteur d'un compte.
     *
     * @param enabled            un facteur est actif
     * @param enrollmentPending  un enrôlement est ouvert mais non confirmé
     * @param requiredByRole     la politique l'impose pour les rôles détenus
     * @param confirmedAt        date de confirmation, nulle si inactif
     * @param activeRecoveryCodes nombre de codes de récupération restants
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StatusResponse(boolean enabled,
                                 boolean enrollmentPending,
                                 boolean requiredByRole,
                                 Instant confirmedAt,
                                 int activeRecoveryCodes) {
    }
}
