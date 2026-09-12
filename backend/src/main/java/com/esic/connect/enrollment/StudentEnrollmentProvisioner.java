package com.esic.connect.enrollment;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port public du module {@code enrollment} pour l'import CSV des
 * apprenants (rapport §4.2). Les méthodes d'<em>application</em>
 * s'exécutent <strong>dans la transaction de l'appelant</strong>
 * (propagation {@code REQUIRED}) : écriture directe
 * ({@code saveAndFlush}), <strong>sans</strong> {@code EnrollmentPersister}
 * ({@code REQUIRES_NEW}) et <strong>sans</strong>
 * {@link EnrollmentChangeEvent} — l'audit d'un import passe par un unique
 * {@code StudentImportChangeEvent} côté {@code studentimport}
 * (invariants T2, T5).
 *
 * <p>Refonte 2026-09 : il n'existe plus de {@code student_profile}. Le
 * numéro étudiant et la date de naissance sont désormais des colonnes de
 * {@code user_account} (module {@code identity}, port
 * {@code identity.StudentAccountProvisioner}) ; {@code studentimport} les
 * atteint par ce port-là, pas par celui-ci. {@code work_study} /
 * {@code company_name} restent du ressort de {@code enrollment}, mais
 * portés par {@code enrollment} elle-même (situation d'alternance
 * <strong>pendant une inscription donnée</strong>, pas une donnée
 * personnelle générale).
 */
public interface StudentEnrollmentProvisioner {

    // --- Lecture seule (simulation) ---

    /**
     * Situation d'inscription d'un <strong>compte</strong> apprenant
     * vis-à-vis d'une classe cible (rapport §3.3). Une inscription
     * rattache directement le compte, jamais un profil (refonte 2026-09).
     * {@link Situation#currentEnrollmentPublicId} est renseigné dès qu'une
     * inscription active existe pour l'année de la classe cible (que ce
     * soit la même classe ou une autre) ; {@code null} pour {@code NONE}.
     */
    Situation describeSituation(UUID userPublicId, UUID targetClassGroupPublicId);

    // --- Application (confirmation) — dans la transaction de l'appelant ---

    /**
     * Nouvelle inscription {@code ACTIVE} dans la classe indiquée, pour le
     * <strong>compte</strong> apprenant désigné.
     */
    EnrollmentView provisionEnrollment(UUID userPublicId, UUID classGroupPublicId, LocalDate startDate,
                                       boolean workStudy, String companyName, Long actorUserInternalId);

    /**
     * Changement de classe conservant l'historique : l'inscription courante
     * est clôturée en {@code TRANSFERRED} ({@code end_date = effectiveDate},
     * inclusif) puis une nouvelle inscription {@code ACTIVE} liée est créée
     * ({@code start_date = effectiveDate + 1 jour}, {@code CLASS_TRANSFER},
     * {@code previous_enrollment_id} renseigné). Écriture directe, aucun
     * événement.
     */
    EnrollmentView provisionTransfer(UUID currentEnrollmentPublicId, UUID targetClassGroupPublicId,
                                     LocalDate effectiveDate, String reason, boolean workStudy,
                                     String companyName, Long actorUserInternalId);

    /**
     * Met à jour {@code work_study} / {@code company_name} d'une
     * inscription existante — jamais l'identité, jamais le numéro
     * étudiant, jamais la date de naissance (action {@code UPDATE_PROFILE}
     * de l'import ; ex-{@code updateProfileAlternation}).
     */
    void updateEnrollmentAlternation(UUID enrollmentPublicId, boolean workStudy, String companyName,
                                     Long actorUserInternalId);

    /**
     * @param publicId            identifiant public de l'inscription
     * @param userPublicId        compte apprenant rattaché
     * @param classGroupPublicId  classe de l'inscription
     * @param active              {@code true} si {@code ACTIVE}
     */
    record EnrollmentView(UUID publicId, UUID userPublicId, UUID classGroupPublicId, boolean active) {
    }

    /**
     * @param kind                      situation vis-à-vis de la classe cible
     * @param currentEnrollmentPublicId inscription active pour l'année de la classe cible
     *                                  ({@code null} pour {@link Kind#NONE})
     * @param currentWorkStudy          alternance de cette inscription ({@code null} pour {@link Kind#NONE})
     * @param currentCompanyName        entreprise de cette inscription ({@code null} si non renseignée
     *                                  ou pour {@link Kind#NONE})
     */
    record Situation(Kind kind, UUID currentEnrollmentPublicId, Boolean currentWorkStudy,
                     String currentCompanyName) {

        /** Situations distinctes (rapport §3.3). */
        public enum Kind {
            /** Aucune inscription active pour l'année de la classe cible. */
            NONE,
            /** Inscription active déjà dans la classe cible. */
            SAME_CLASS,
            /** Inscription active dans une autre classe de la même année. */
            OTHER_CLASS_SAME_YEAR
        }

        public static Situation none() {
            return new Situation(Kind.NONE, null, null, null);
        }
    }
}
