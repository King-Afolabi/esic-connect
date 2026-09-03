package com.esic.connect.identity.internal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * @param captchaToken jeton anti-robot (EF-AUTH-011). Facultatif : il
 *                     n'est exigé qu'après des échecs répétés (AC-022),
 *                     et seulement si un fournisseur est configuré. Un
 *                     client peut donc l'envoyer systématiquement sans
 *                     rien apprendre du compte visé.
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        String captchaToken) {
}
