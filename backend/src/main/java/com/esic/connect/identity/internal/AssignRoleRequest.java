package com.esic.connect.identity.internal;

import jakarta.validation.constraints.NotBlank;

/**
 * Corps d'une attribution de rôle. Le code est reçu en chaîne puis
 * converti par le service : un code inconnu produit une erreur 400
 * homogène plutôt qu'un échec de désérialisation.
 */
record AssignRoleRequest(
        @NotBlank String role,
        @NotBlank String reason) {
}
