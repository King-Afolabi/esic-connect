package com.esic.connect.enrollment;

import java.util.UUID;

/**
 * Événement de changement d'une ressource du module {@code enrollment}
 * (profil apprenant, inscription). Consommé par le module {@code audit}.
 *
 * <p>Ne transporte aucune donnée personnelle ni aucun secret : seulement
 * des identifiants, l'action réalisée et un complément non sensible
 * (par exemple {@code "class=BTS-SIO-1;year=2026-2027"}). Le numéro
 * étudiant, le nom et l'adresse ne figurent jamais dans {@code detail}
 * (cahier §30.3).
 *
 * @param resourceType     type de ressource concernée
 * @param resourcePublicId identifiant public de la ressource
 * @param actorUserId      auteur de l'action (identifiant interne),
 *                         {@code null} si non résolu
 * @param action           action réalisée
 * @param detail           complément non sensible, {@code null} si sans objet
 */
public record EnrollmentChangeEvent(
        EnrollmentResourceType resourceType,
        UUID resourcePublicId,
        Long actorUserId,
        EnrollmentChangeAction action,
        String detail) {
}
