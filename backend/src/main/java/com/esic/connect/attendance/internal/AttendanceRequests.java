package com.esic.connect.attendance.internal;

import jakarta.validation.constraints.Size;

/** Requêtes de l'API d'émargement. */
final class AttendanceRequests {

    private AttendanceRequests() {
    }

    /**
     * Validation d'une présence. Exactement l'un des deux champs doit
     * être renseigné (contrôlé côté service : {@code ATT_INVALID_SUBMISSION}).
     * Le serveur détermine l'apprenant à partir du seul JWT — il ne
     * reçoit jamais d'identifiant d'apprenant ni d'inscription.
     *
     * <p>Bornes de taille défensives ; la valeur soumise n'est jamais
     * renvoyée dans une réponse ni une erreur.
     */
    record Validate(
            @Size(max = 128) String token,
            @Size(max = 32) String shortCode) {
    }
}
