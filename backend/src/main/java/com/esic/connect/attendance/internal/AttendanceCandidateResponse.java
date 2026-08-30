package com.esic.connect.attendance.internal;

import java.util.UUID;

/**
 * Candidat à une saisie manuelle de présence (correctif PR #22 §2) :
 * inscription <strong>active</strong> d'une classe rattachée à la séance.
 * Le formateur choisit dans cette liste au lieu de saisir un identifiant
 * d'inscription à l'aveugle.
 *
 * <p>Jamais d'adresse électronique ni d'identifiant SQL — uniquement des
 * identifiants publics, le numéro étudiant, le nom et le code de classe.
 *
 * @param studentProfilePublicId identifiant public du profil apprenant
 * @param enrollmentPublicId     identifiant public de l'inscription (valeur
 *                               à renvoyer dans {@code enrollmentPublicId}
 *                               d'une saisie manuelle)
 * @param studentNumber          numéro étudiant
 * @param firstName              prénom ({@code null} si non résolu)
 * @param lastName               nom ({@code null} si non résolu)
 * @param classCode              code fonctionnel de la classe de l'inscription
 */
record AttendanceCandidateResponse(
        UUID studentProfilePublicId,
        UUID enrollmentPublicId,
        String studentNumber,
        String firstName,
        String lastName,
        String classCode) {
}
