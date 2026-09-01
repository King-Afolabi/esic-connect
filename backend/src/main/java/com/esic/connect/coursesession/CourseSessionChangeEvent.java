package com.esic.connect.coursesession;

import java.util.Set;
import java.util.UUID;

/**
 * Événement de changement d'une séance. Consommé par le module
 * {@code audit} (piste d'audit), par le module {@code attendance}
 * (purge des jetons Redis à la fermeture / l'annulation) et, à partir de
 * G1-D, par le module {@code notification}.
 *
 * <p>Ne transporte aucune donnée personnelle : identifiant public de la
 * séance, action, complément non sensible (par exemple
 * {@code "teacher=…;classes=2"}) et, le cas échéant, les
 * <strong>identifiants publics</strong> des utilisateurs explicitement
 * concernés par cette occurrence ({@code affectedUserPublicIds}). Le nom
 * du formateur ou d'un apprenant n'y figure jamais (cahier §30.3), aucune
 * clé SQL, aucun motif nominatif.
 *
 * <p>{@code affectedUserPublicIds} sert aux consommateurs
 * {@code AFTER_COMMIT} qui doivent cibler un utilisateur que l'état
 * <em>committé</em> ne désigne plus : pour
 * {@link CourseSessionChangeAction#SUBSTITUTION_ADDED} et
 * {@link CourseSessionChangeAction#SUBSTITUTION_ENDED} il contient l'UUID
 * public du remplaçant concerné (celui qui vient de terminer n'est plus
 * {@code ACTIVE} et ne serait pas retrouvé autrement). Vide — jamais
 * {@code null} — pour les autres actions.
 *
 * @param eventId               identifiant unique de <em>cette occurrence</em>
 *                              (G1-D) — sert de clé d'idempotence aux
 *                              consommateurs {@code AFTER_COMMIT} qui
 *                              pourraient rejouer l'événement (aucune
 *                              donnée personnelle)
 * @param resourceType          type de ressource concernée
 * @param resourcePublicId      identifiant public de la séance
 * @param actorUserId           auteur de l'action (identifiant interne),
 *                              {@code null} si non résolu
 * @param action                action réalisée
 * @param detail                complément non sensible, {@code null} si sans objet
 * @param affectedUserPublicIds identifiants publics des utilisateurs
 *                              explicitement concernés (jamais {@code null} ;
 *                              rendu immuable)
 */
public record CourseSessionChangeEvent(
        UUID eventId,
        CourseSessionResourceType resourceType,
        UUID resourcePublicId,
        Long actorUserId,
        CourseSessionChangeAction action,
        String detail,
        Set<UUID> affectedUserPublicIds) {

    public CourseSessionChangeEvent {
        affectedUserPublicIds = affectedUserPublicIds == null
                ? Set.of()
                : Set.copyOf(affectedUserPublicIds);
    }
}
