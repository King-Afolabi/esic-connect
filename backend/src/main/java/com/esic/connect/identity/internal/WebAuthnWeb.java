package com.esic.connect.identity.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Contrats HTTP des passkeys (EF-AUTH-006, EF-AUTH-007).
 *
 * <p>Les valeurs binaires circulent en <strong>base64url sans
 * remplissage</strong>, forme attendue et produite par l'API
 * {@code navigator.credentials} du navigateur.
 *
 * <p>Aucune donnée biométrique n'apparaît dans ces objets, et il n'existe
 * aucun champ où elle pourrait apparaître (RG-091, AC-020).
 */
public final class WebAuthnWeb {

    private WebAuthnWeb() {
    }

    /** Options d'enregistrement d'une passkey, à passer à {@code navigator.credentials.create}. */
    public record RegistrationOptions(String challenge,
                                      RelyingParty rp,
                                      UserEntity user,
                                      List<CredentialParameter> pubKeyCredParams,
                                      List<CredentialDescriptor> excludeCredentials,
                                      AuthenticatorSelection authenticatorSelection,
                                      long timeout,
                                      String attestation) {
    }

    public record RelyingParty(String id, String name) {
    }

    /**
     * @param id identifiant public du compte, en base64url — jamais
     *           l'identifiant interne, et jamais l'adresse électronique
     */
    public record UserEntity(String id, String name, String displayName) {
    }

    public record CredentialParameter(String type, long alg) {
    }

    public record CredentialDescriptor(String type, String id) {
    }

    /**
     * @param residentKey       {@code preferred} : la passkey est stockée
     *                          sur l'appareil, ce qui permet la connexion
     *                          sans saisir d'adresse
     * @param userVerification  {@code preferred} : l'appareil demande son
     *                          code ou sa biométrie <em>localement</em>
     */
    public record AuthenticatorSelection(String residentKey,
                                         String userVerification,
                                         boolean requireResidentKey) {
    }

    /** Réponse du navigateur à {@code create()}. */
    public record RegistrationRequestBody(
            @NotBlank String id,
            @NotNull @Valid AttestationResponse response,
            List<String> transports,
            @Size(max = 120) String label) {
    }

    public record AttestationResponse(@NotBlank String clientDataJSON,
                                      @NotBlank String attestationObject) {
    }

    /** Options d'assertion, à passer à {@code navigator.credentials.get}. */
    public record AuthenticationOptions(String challengeId,
                                        String challenge,
                                        String rpId,
                                        long timeout,
                                        String userVerification,
                                        List<CredentialDescriptor> allowCredentials) {
    }

    /** Réponse du navigateur à {@code get()}. */
    public record AuthenticationRequestBody(
            @NotBlank String challengeId,
            @NotBlank String id,
            @NotNull @Valid AssertionResponse response) {
    }

    public record AssertionResponse(@NotBlank String clientDataJSON,
                                    @NotBlank String authenticatorData,
                                    @NotBlank String signature,
                                    String userHandle) {
    }

    /** Passkey enregistrée, telle qu'affichée dans l'écran de sécurité du compte. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CredentialResponse(UUID id,
                                     String label,
                                     Instant createdAt,
                                     Instant lastUsedAt) {
    }
}
