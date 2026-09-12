package com.esic.connect.identity.internal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Création d'un compte en attente d'activation (EF-USER-001).
 *
 * <p><strong>Aucun champ de mot de passe, et il ne doit jamais y en
 * avoir.</strong> Le compte naît sans identifiant secret ; la personne
 * choisit son mot de passe via le lien d'invitation. Transmettre un mot
 * de passe par un tiers est explicitement proscrit (docs/02 §11.2).
 *
 * <p>{@code studentNumber} / {@code birthDate} (refonte 2026-09) :
 * données personnelles générales et strictement facultatives — jamais
 * requises pour qu'un compte {@code STUDENT} soit un apprenant valide (le
 * rôle seul l'établit). Sans objet pour un rôle autre que
 * {@code STUDENT}, mais non rejetées pour autant : ce ne sont que deux
 * colonnes optionnelles de {@code user_account}, pas un profil séparé.
 * {@code studentNumber} laissé vide n'est jamais généré automatiquement
 * ici (contrairement à l'import CSV) : un apprenant créé à la main sans
 * numéro reste un apprenant pleinement valide.
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
        Boolean sendInvitation,
        @Size(max = 50) String studentNumber,
        LocalDate birthDate) {

    boolean shouldSendInvitation() {
        return sendInvitation == null || sendInvitation;
    }
}
