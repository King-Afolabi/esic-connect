package com.esic.connect.identity.internal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Demande d'émission (ou de réémission) d'une invitation d'activation
 * pour un compte existant en attente d'activation. Le rôle est attribué
 * au compte lors de l'émission (via {@code user_role}).
 */
record IssueInvitationRequest(
        @NotBlank @Email String email,
        @NotNull RoleCode role) {
}
