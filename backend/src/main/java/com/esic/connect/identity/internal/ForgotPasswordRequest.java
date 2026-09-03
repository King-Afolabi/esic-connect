package com.esic.connect.identity.internal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Demande de réinitialisation. La validation de forme est volontairement
 * minimale : un format d'adresse refusé côté serveur ne doit pas non plus
 * renseigner l'appelant sur l'existence du compte.
 *
 * @param captchaToken jeton anti-robot (EF-AUTH-011, docs/02 §17.9 :
 *                     « la demande de réinitialisation » figure parmi les
 *                     formulaires protégés)
 */
public record ForgotPasswordRequest(
        @NotBlank @Email @Size(max = 320) String email,
        String captchaToken) {
}
