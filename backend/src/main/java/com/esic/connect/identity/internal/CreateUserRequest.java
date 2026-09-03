package com.esic.connect.identity.internal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Création d'un compte en attente d'activation (EF-USER-001).
 *
 * <p><strong>Aucun champ de mot de passe, et il ne doit jamais y en
 * avoir.</strong> Le compte naît sans identifiant secret ; la personne
 * choisit son mot de passe via le lien d'invitation. Transmettre un mot
 * de passe par un tiers est explicitement proscrit (docs/02 §11.2).
 *
 * @param sendInvitation émettre l'invitation dans la foulée. Par défaut
 *                       oui : un compte créé sans invitation reste un
 *                       compte fantôme que personne ne peut activer.
 */
record CreateUserRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 120) String firstName,
        @NotBlank @Size(max = 120) String lastName,
        @NotBlank String role,
        Boolean sendInvitation) {

    boolean shouldSendInvitation() {
        return sendInvitation == null || sendInvitation;
    }
}
