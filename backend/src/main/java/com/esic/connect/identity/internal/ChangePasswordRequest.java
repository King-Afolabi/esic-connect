package com.esic.connect.identity.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Changement volontaire de mot de passe par un utilisateur connecté
 * (docs/02-cahier-des-charges.md §17.1, §34.2 « sécurité du compte »).
 *
 * <p>Deux champs seulement : le mot de passe actuel (preuve de présence,
 * revérifié côté serveur) et le nouveau. La confirmation du nouveau mot
 * de passe est un contrôle d'interface — l'imposer au serveur
 * n'ajouterait aucune sécurité. La longueur est bornée ici pour rejeter
 * au plus tôt une charge démesurée ; la politique complète est appliquée
 * par {@link PasswordPolicy}.
 */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 200) String currentPassword,
        @NotBlank @Size(max = 200) String newPassword) {
}
