package com.esic.connect.academic;

import java.util.UUID;

/**
 * Événement de changement d'une ressource académique (formation, niveau,
 * année scolaire, promotion, classe). Consommé par le module {@code audit}.
 *
 * <p>Ne transporte aucune donnée personnelle ni aucun secret : seulement
 * des identifiants, l'action réalisée et un complément non sensible
 * (par exemple {@code "code=BTS-SIO"}).
 *
 * @param resourceType     type de ressource concernée
 * @param resourcePublicId identifiant public de la ressource
 * @param actorUserId      auteur de l'action (identifiant interne),
 *                         {@code null} si non résolu
 * @param action           action réalisée
 * @param detail           complément non sensible, {@code null} si sans objet
 */
public record AcademicChangeEvent(
        AcademicResourceType resourceType,
        UUID resourcePublicId,
        Long actorUserId,
        AcademicChangeAction action,
        String detail) {
}
