package com.esic.connect.enrollment;

import java.time.LocalDate;
import java.util.Optional;
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
 * (invariants T2, T5). Les endpoints HTTP existants
 * ({@code POST /student-profiles}, {@code /enrollments}) conservent leur
 * chemin actuel : la duplication de chemin d'écriture est assumée
 * (garantie transactionnelle stricte de l'import).
 *
 * <p><strong>Numéro étudiant</strong> : la table
 * {@code student_number_sequence} appartient au module {@code studentimport}
 * (migration V11). L'allocation atomique d'un numéro est donc faite par
 * l'orchestrateur d'import <em>avant</em> l'appel : {@link #provisionProfile}
 * reçoit toujours un numéro déjà déterminé (écart assumé vs rapport §4.2,
 * qui plaçait la génération dans ce port — frontières Spring Modulith).
 */
public interface StudentEnrollmentProvisioner {

    // --- Lecture seule (simulation) ---

    Optional<StudentProfileView> findProfileByUser(UUID userPublicId);

    Optional<StudentProfileView> findProfileByStudentNumber(String studentNumber);

    boolean studentNumberTaken(String studentNumber);

    /**
     * Situation d'inscription d'un profil apprenant vis-à-vis d'une classe
     * cible (rapport §3.3). {@link Situation#currentEnrollmentPublicId} est
     * renseigné pour le seul cas {@link Situation.Kind#OTHER_CLASS_SAME_YEAR}
     * (changement de classe).
     */
    Situation describeSituation(UUID studentProfilePublicId, UUID targetClassGroupPublicId);

    // --- Application (confirmation) — dans la transaction de l'appelant ---

    /**
     * Crée un {@code student_profile}. Le numéro étudiant est fourni par
     * l'appelant (jamais {@code null}). Une collision d'unicité
     * ({@code uq_student_profile_user} / {@code uq_student_profile_student_number})
     * remonte en {@link org.springframework.dao.DataIntegrityViolationException}
     * dans la transaction unique — l'orchestrateur abandonne tout.
     */
    StudentProfileView provisionProfile(ProvisionProfile command);

    /** Nouvelle inscription {@code ACTIVE} dans la classe indiquée. */
    EnrollmentView provisionEnrollment(UUID studentProfilePublicId, UUID classGroupPublicId,
                                       LocalDate startDate, Long actorUserInternalId);

    /**
     * Changement de classe conservant l'historique : l'inscription courante
     * est clôturée en {@code TRANSFERRED} ({@code end_date = effectiveDate},
     * inclusif) puis une nouvelle inscription {@code ACTIVE} liée est créée
     * ({@code start_date = effectiveDate + 1 jour}, {@code CLASS_TRANSFER},
     * {@code previous_enrollment_id} renseigné). Écriture directe, aucun
     * événement.
     */
    EnrollmentView provisionTransfer(UUID currentEnrollmentPublicId, UUID targetClassGroupPublicId,
                                     LocalDate effectiveDate, String reason, Long actorUserInternalId);

    /**
     * Met à jour {@code work_study} / {@code company_name} d'un profil
     * existant — jamais l'identité, jamais le numéro étudiant, jamais la
     * date de naissance (action {@code UPDATE_PROFILE} de l'import).
     */
    void updateProfileAlternation(UUID studentProfilePublicId, boolean workStudy, String companyName,
                                  Long actorUserInternalId);

    /**
     * @param publicId        identifiant public du profil apprenant
     * @param userPublicId    compte porteur
     * @param studentNumber   numéro étudiant
     * @param workStudy       apprenant en alternance
     * @param companyName     entreprise ({@code null} si non renseignée)
     * @param archived        {@code true} si le profil est archivé
     */
    record StudentProfileView(UUID publicId, UUID userPublicId, String studentNumber, boolean workStudy,
                              String companyName, boolean archived) {
    }

    /**
     * @param publicId            identifiant public de l'inscription
     * @param studentProfilePublicId profil rattaché
     * @param classGroupPublicId  classe de l'inscription
     * @param active              {@code true} si {@code ACTIVE}
     */
    record EnrollmentView(UUID publicId, UUID studentProfilePublicId, UUID classGroupPublicId, boolean active) {
    }

    /**
     * @param userPublicId  compte porteur du futur profil
     * @param studentNumber numéro étudiant déjà déterminé (jamais {@code null})
     * @param birthDate     date de naissance ({@code null} accepté)
     * @param workStudy     alternance
     * @param companyName   entreprise ({@code null} accepté)
     * @param generated     {@code true} si le numéro a été généré par le serveur
     * @param actorUserInternalId auteur ({@code null} accepté)
     */
    record ProvisionProfile(UUID userPublicId, String studentNumber, LocalDate birthDate, boolean workStudy,
                            String companyName, boolean generated, Long actorUserInternalId) {
    }

    /**
     * @param kind                     situation vis-à-vis de la classe cible
     * @param currentEnrollmentPublicId inscription courante (uniquement pour {@code OTHER_CLASS_SAME_YEAR})
     */
    record Situation(Kind kind, UUID currentEnrollmentPublicId) {

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
            return new Situation(Kind.NONE, null);
        }
    }
}
