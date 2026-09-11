package com.esic.connect.identity;

import java.util.UUID;

/**
 * Port public minimal du module {@code identity} : déclenche, pour un
 * compte désigné par un tiers habilité, le même parcours que « mot de
 * passe oublié » — sans jamais transmettre ni afficher le mot de passe
 * lui-même (RG-009, docs/02 §17.8).
 *
 * <p>Distinct de {@code PasswordResetService#requestReset}, qui reste le
 * point d'entrée du parcours <strong>auto-service</strong> (par adresse
 * courriel, réponse neutre pour ne rien révéler sur l'existence d'un
 * compte). Ici, l'appelant est authentifié et vise un compte par son
 * identifiant public déjà résolu : la réponse peut donc être franche
 * ({@link Outcome#USER_NOT_FOUND}), et c'est au module appelant de
 * décider — hiérarchie de rôles, périmètre pédagogique — s'il a le droit
 * de viser ce compte, avant d'invoquer ce port.
 *
 * <p>Consommé par le module {@code passwordadmin}, qui porte cette
 * décision d'autorisation.
 */
public interface AdminPasswordResetDirectory {

    /**
     * Révoque toute demande en attente, émet un nouveau jeton et publie
     * l'événement de notification — exactement comme
     * {@code PasswordResetService#requestReset}, mais ciblé par
     * identifiant public plutôt que par adresse.
     *
     * @param targetUserPublicId identifiant public du compte visé
     * @return {@link Outcome#RESET_SENT} en cas de succès ;
     *         {@link Outcome#USER_NOT_FOUND} si l'identifiant ne
     *         correspond à aucun compte ; {@link Outcome#NOT_ELIGIBLE} si
     *         le compte est suspendu ou archivé (un compte dans cet état
     *         ne peut pas contourner la décision qui l'y a mis)
     */
    Outcome triggerReset(UUID targetUserPublicId);

    enum Outcome {
        RESET_SENT,
        USER_NOT_FOUND,
        NOT_ELIGIBLE
    }
}
