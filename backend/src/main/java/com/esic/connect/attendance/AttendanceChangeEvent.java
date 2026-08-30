package com.esic.connect.attendance;

import java.util.UUID;

/**
 * Événement de changement d'une présence. Consommé par le module
 * {@code audit}.
 *
 * <p>Ne transporte aucune donnée personnelle : identifiant public de la
 * présence, action et complément non sensible (par exemple
 * {@code "session=<uuid>;source=SHORT_CODE"}). Ni numéro étudiant, ni nom,
 * ni jeton, ni code court (cahier §30.3).
 *
 * @param resourceType     type de ressource concernée
 * @param resourcePublicId identifiant public de la présence
 * @param actorUserId      compte de l'apprenant émargeur (identifiant
 *                         interne), {@code null} si non résolu
 * @param action           action réalisée
 * @param detail           complément non sensible, {@code null} si sans objet
 */
public record AttendanceChangeEvent(
        AttendanceResourceType resourceType,
        UUID resourcePublicId,
        Long actorUserId,
        AttendanceChangeAction action,
        String detail) {
}
