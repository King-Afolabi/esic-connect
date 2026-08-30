package com.esic.connect.coursesession;

import java.util.UUID;

/**
 * Événement de changement d'un point de contrôle d'émargement (V10).
 * Consommé par le module {@code audit} (piste d'audit, catégorie
 * {@code COURSE_SESSION}) et par le module {@code attendance} (purge du
 * jeton Redis du point de contrôle à sa fermeture / annulation).
 *
 * <p>Ne transporte aucune donnée personnelle : identifiants publics de la
 * séance et du point de contrôle, action et complément non sensible
 * (par exemple {@code "type=CUSTOM;label=Retour de pause"}).
 *
 * @param sessionPublicId    séance concernée
 * @param checkpointPublicId point de contrôle concerné
 * @param actorUserId        auteur de l'action (identifiant interne),
 *                           {@code null} si non résolu
 * @param action             action réalisée
 * @param detail             complément non sensible, {@code null} si sans objet
 */
public record AttendanceCheckpointChangeEvent(
        UUID sessionPublicId,
        UUID checkpointPublicId,
        Long actorUserId,
        AttendanceCheckpointChangeAction action,
        String detail) {
}
