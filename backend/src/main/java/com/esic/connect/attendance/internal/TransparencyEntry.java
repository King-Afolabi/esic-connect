package com.esic.connect.attendance.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Une ligne du journal de transparence d'un apprenant (EF-ATT-014 ;
 * docs/02 §5.7 et §34.2).
 *
 * <p>Le journal répond à une seule question : <em>qu'est-il arrivé à mes
 * présences, quand, par quel canal, à quel titre et pourquoi ?</em> Il
 * n'est pas une seconde vue de l'assiduité — celle-ci est déjà servie par
 * {@code GET /api/v1/me/attendance}.
 *
 * <p>{@code actorRole} porte la <strong>fonction</strong> de l'auteur ;
 * {@code SELF} lorsque l'apprenant est lui-même à l'origine du fait
 * (son propre émargement). Aucune identité civile d'agent n'y figure
 * (docs/02 §14), aucune adresse réseau (RG-094).
 *
 * @param occurredAt          horodatage du fait
 * @param event               nature du fait (voir {@code TransparencyEvent})
 * @param actorRole           fonction de l'auteur, ou {@code SELF}
 * @param channel             canal d'émargement, {@code null} hors émargement
 * @param sessionPublicId     séance concernée
 * @param sessionTitle        intitulé de la séance
 * @param sessionStartsAt     début de la séance
 * @param checkpointLabel     point de contrôle concerné, s'il y en a un
 * @param previousStatus      valeur avant, {@code null} si sans objet
 * @param newStatus           valeur après, {@code null} si sans objet
 * @param previousLateMinutes retard avant, {@code null} si sans objet
 * @param newLateMinutes      retard après, {@code null} si sans objet
 * @param reason              motif tel que saisi par son auteur
 */
record TransparencyEntry(
        Instant occurredAt,
        String event,
        String actorRole,
        String channel,
        UUID sessionPublicId,
        String sessionTitle,
        Instant sessionStartsAt,
        String checkpointLabel,
        String previousStatus,
        String newStatus,
        Integer previousLateMinutes,
        Integer newLateMinutes,
        String reason) {

    /** Auteur : l'apprenant lui-même. */
    static final String SELF = "SELF";
}
