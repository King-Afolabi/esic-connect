package com.esic.connect.organization;

import java.util.UUID;

/**
 * Événement de changement d'une ressource organisationnelle (site,
 * bâtiment, salle, plage réseau). Consommé par le module {@code audit}.
 *
 * <p>Ne transporte aucune donnée personnelle ni aucun secret : seulement
 * des identifiants, l'action réalisée et un complément non sensible
 * (par exemple {@code "code=ESIC-PARIS"} ou {@code "cidr=10.0.0.0/8"}).
 *
 * @param resourceType     type de ressource concernée
 * @param resourcePublicId identifiant public de la ressource
 * @param actorUserId      auteur de l'action (identifiant interne),
 *                         {@code null} si non résolu
 * @param action           action réalisée
 * @param detail           complément non sensible, {@code null} si sans objet
 */
public record OrganizationChangeEvent(
        OrganizationResourceType resourceType,
        UUID resourcePublicId,
        Long actorUserId,
        OrganizationChangeAction action,
        String detail) {
}
