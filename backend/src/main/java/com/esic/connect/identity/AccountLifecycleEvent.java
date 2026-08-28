package com.esic.connect.identity;

import java.util.UUID;

/**
 * Événement d'audit du cycle de vie d'un compte (émission d'invitation,
 * activation). Consommé par le module {@code audit} uniquement. Ne
 * transporte aucune donnée personnelle ni aucun secret : seulement des
 * identifiants et l'action réalisée.
 *
 * @param userId          compte concerné (identifiant interne)
 * @param userPublicId    identifiant public du compte concerné
 * @param actorUserId     auteur de l'action, {@code null} si l'action est
 *                        publique (activation par le titulaire du lien)
 * @param action          action réalisée
 * @param detail          complément non sensible (ex. nombre d'invitations
 *                        précédentes révoquées), {@code null} si sans objet
 */
public record AccountLifecycleEvent(
        Long userId,
        UUID userPublicId,
        Long actorUserId,
        AccountLifecycleAction action,
        String detail) {
}
