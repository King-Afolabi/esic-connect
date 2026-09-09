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
            @Size(max = 32) String shortCode,
            /**
             * L'apprenant déclare suivre la séance à distance (docs/02
             * §15.3). Sur une séance présentielle, cela exige une
             * autorisation individuelle active, sans quoi le canal est
             * refusé.
             *
             * <p>Ce n'est pas un contrôle de localisation — un client
             * pourrait ne pas lever le drapeau. Le contrôle de présence
             * sur site est le QR fixe de salle et la plage réseau
             * (EF-ATT-008/010). Ce drapeau sert à tracer le canal et à
             * faire respecter une décision pédagogique.
             */
            Boolean remote) {
    }

    /**
     * Émargement par le QR fixe de salle (EF-ATT-010 ; docs/02 §16.6).
     *
     * <p>Le corps ne porte que le jeton lu dans l'affiche : ni séance, ni
     * point de contrôle, ni apprenant. Le serveur déduit tout le reste —
     * c'est ce qui permet d'imprimer l'affiche une fois pour toutes, et ce
     * qui empêche un client de désigner la séance qui l'arrange.
     */
    record ValidateRoomQr(
            @jakarta.validation.constraints.NotBlank @Size(max = 128) String roomReference) {
    }
}
