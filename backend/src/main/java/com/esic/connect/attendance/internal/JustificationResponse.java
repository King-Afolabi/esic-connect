package com.esic.connect.attendance.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Vue API d'un justificatif d'absence — jamais d'identifiant SQL, jamais
 * d'adresse électronique. Utilisée pour l'espace apprenant comme pour la
 * file de gestion (les champs d'examen sont {@code null} tant que
 * {@code PENDING}).
 *
 * @param publicId              identifiant public du justificatif
 * @param status                {@code PENDING} | {@code ACCEPTED} | {@code REJECTED}
 * @param category              catégorie
 * @param externalReference     référence externe facultative
 * @param comment               commentaire de l'apprenant
 * @param submittedAt           date de dépôt
 * @param reviewedAt            date d'examen ({@code null} tant que PENDING)
 * @param decisionReason        motif de décision ({@code null} sauf refus motivé)
 * @param sessionPublicId       séance de l'absence
 * @param sessionTitle          libellé de la séance ({@code null} possible)
 * @param sessionStartsAt       début de la séance
 * @param checkpointPublicId    point de contrôle de l'absence
 * @param checkpointLabel       libellé du point de contrôle
 * @param classCode             code de la classe de l'inscription
 * @param studentProfilePublicId profil apprenant (renseigné côté gestion uniquement)
 * @param studentNumber         numéro étudiant (renseigné côté gestion uniquement)
 * @param firstName             prénom (renseigné côté gestion uniquement)
 * @param lastName              nom (renseigné côté gestion uniquement)
 * @param attendanceStatus      statut courant de la présence rattachée
 */
record JustificationResponse(
        UUID publicId,
        String status,
        String category,
        String externalReference,
        String comment,
        Instant submittedAt,
        Instant reviewedAt,
        String decisionReason,
        UUID sessionPublicId,
        String sessionTitle,
        Instant sessionStartsAt,
        UUID checkpointPublicId,
        String checkpointLabel,
        String classCode,
        UUID studentProfilePublicId,
        String studentNumber,
        String firstName,
        String lastName,
        String attendanceStatus) {
}
