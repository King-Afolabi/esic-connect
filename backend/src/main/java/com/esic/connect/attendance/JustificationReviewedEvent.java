package com.esic.connect.attendance;

import java.util.UUID;

/**
 * Un justificatif d'absence vient d'être <strong>examiné</strong>
 * (accepté ou refusé) par un gestionnaire (bloc G1-E ; EF-NOTIF-002 ;
 * CDC §14.1 « traitement d'un justificatif » ; §23.2 « résultat d'une
 * correction »).
 *
 * <p>Publié <em>dans</em> la transaction de l'examen ; consommé
 * <strong>après commit</strong> par le module {@code notification} pour
 * avertir le <strong>propriétaire</strong> du justificatif. Le
 * destinataire est porté explicitement ({@code ownerUserPublicId}) : il
 * est le seul concerné et il est directement résolu par l'examen — aucun
 * port {@code enrollment} / {@code academic} n'est requis.
 *
 * <p>Ne transporte aucune donnée personnelle : identifiants publics et un
 * booléen. Ni motif de refus (potentiellement nominatif), ni nom, ni
 * numéro étudiant.
 *
 * @param justificationPublicId identifiant public du justificatif examiné
 * @param ownerUserPublicId     identifiant public du compte ayant déposé le justificatif
 * @param accepted              {@code true} si accepté, {@code false} si refusé
 */
public record JustificationReviewedEvent(
        UUID justificationPublicId,
        UUID ownerUserPublicId,
        boolean accepted) {
}
