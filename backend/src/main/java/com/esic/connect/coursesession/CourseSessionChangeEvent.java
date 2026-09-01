package com.esic.connect.coursesession;

import java.util.UUID;

/**
 * Événement de changement d'une séance. Consommé par le module
 * {@code audit} (piste d'audit), par le module {@code attendance}
 * (purge des jetons Redis à la fermeture / l'annulation) et, à partir de
 * G1-D, par le module {@code notification}.
 *
 * <p>Ne transporte aucune donnée personnelle : identifiant public de la
 * séance, action et complément non sensible (par exemple
 * {@code "teacher=…;classes=2"}). Le nom du formateur ou d'un apprenant
 * n'y figure jamais (cahier §30.3).
 *
 * @param eventId          identifiant unique de <em>cette occurrence</em>
 *                         (G1-D) — sert de clé d'idempotence aux
 *                         consommateurs {@code AFTER_COMMIT} qui pourraient
 *                         rejouer l'événement (aucune donnée personnelle)
 * @param resourceType     type de ressource concernée
 * @param resourcePublicId identifiant public de la séance
 * @param actorUserId      auteur de l'action (identifiant interne),
 *                         {@code null} si non résolu
 * @param action           action réalisée
 * @param detail           complément non sensible, {@code null} si sans objet
 */
public record CourseSessionChangeEvent(
        UUID eventId,
        CourseSessionResourceType resourceType,
        UUID resourcePublicId,
        Long actorUserId,
        CourseSessionChangeAction action,
        String detail) {
}
