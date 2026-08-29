package com.esic.connect.alternation;

import java.util.UUID;

/**
 * Événement de changement d'une ressource du module {@code alternation}
 * (modèle de rythme, affectation de classe, exception individuelle).
 * Consommé par le module {@code audit}.
 *
 * <p>Ne transporte aucune donnée personnelle ni aucun secret : seulement
 * des identifiants publics, l'action réalisée et un complément non
 * sensible (par exemple {@code "code=RYT-3-2;type=THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY"}
 * ou {@code "class=BTS-SIO-1;pattern=RYT-3-2"}). Le nom, le numéro
 * étudiant et l'adresse d'un apprenant ne figurent jamais dans
 * {@code detail} (cahier §30.3).
 *
 * @param resourceType     type de ressource concernée
 * @param resourcePublicId identifiant public de la ressource
 * @param actorUserId      auteur de l'action (identifiant interne),
 *                         {@code null} si non résolu
 * @param action           action réalisée
 * @param detail           complément non sensible, {@code null} si sans objet
 */
public record AlternationChangeEvent(
        AlternationResourceType resourceType,
        UUID resourcePublicId,
        Long actorUserId,
        AlternationChangeAction action,
        String detail) {
}
