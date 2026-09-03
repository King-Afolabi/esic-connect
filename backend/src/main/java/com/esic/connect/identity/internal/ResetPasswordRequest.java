package com.esic.connect.identity.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Consommation d'un jeton de réinitialisation.
 *
 * <p>La longueur du mot de passe est bornée ici pour rejeter au plus tôt
 * une charge démesurée ; la politique complète est appliquée par
 * {@link PasswordPolicy}, qui produit des messages explicites.
 */
public record ResetPasswordRequest(
        @NotBlank @Size(max = 200) String token,
        @NotBlank @Size(max = 200) String newPassword) {
}
