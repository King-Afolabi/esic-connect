package com.esic.connect.identity.internal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Demande de réinitialisation. La validation de forme est volontairement
 * minimale : un format d'adresse refusé côté serveur ne doit pas non plus
 * renseigner l'appelant sur l'existence du compte.
 */
public record ForgotPasswordRequest(
        @NotBlank @Email @Size(max = 320) String email) {
}
