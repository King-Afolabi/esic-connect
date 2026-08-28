package com.esic.connect.identity.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Activation d'un compte : jeton reçu par email + mot de passe choisi.
 * Longueur minimale alignée sur le cahier §16.1 ; le mot de passe est
 * encodé par {@code PasswordEncoder} avant persistance, jamais journalisé.
 */
record ActivateAccountRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 12, max = 200) String password) {
}
