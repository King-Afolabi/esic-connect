package com.esic.connect.enrollment;

import java.util.Optional;
import java.util.UUID;

/**
 * Port public minimal du module {@code enrollment}.
 *
 * <p>Permet à un autre module (ici {@code alternation}, pour rattacher
 * une exception individuelle de calendrier à une inscription) de résoudre
 * une référence technique d'inscription sans dépendre des classes
 * internes d'{@code enrollment}. Ne renvoie ni l'entité {@code Enrollment},
 * ni un repository, ni aucun type de {@code enrollment.internal} :
 * uniquement le {@link EnrollmentRef} ci-dessous, composé de types
 * standard. Même approche que
 * {@link com.esic.connect.academic.ClassGroupDirectory} et
 * {@link com.esic.connect.identity.UserDirectory}.
 */
public interface EnrollmentDirectory {

    /**
     * @param enrollmentPublicId identifiant public de l'inscription (forme
     *                           UUID) ; peut être {@code null}
     * @return la référence de l'inscription si une inscription correspond,
     *         {@link Optional#empty()} sinon
     */
    Optional<EnrollmentRef> findByPublicId(UUID enrollmentPublicId);

    /**
     * @param enrollmentInternalId identifiant interne de l'inscription
     * @return la référence de l'inscription si une inscription correspond,
     *         {@link Optional#empty()} sinon
     */
    Optional<EnrollmentRef> findByInternalId(long enrollmentInternalId);

    /**
     * Référence technique d'une inscription, strictement suffisante pour
     * qu'un autre module stocke la clé étrangère {@code enrollment_id},
     * réaffiche des identifiants publics, contrôle l'exploitabilité de
     * l'inscription et vérifie que sa classe appartient au périmètre
     * pédagogique de l'appelant (via
     * {@link com.esic.connect.academic.AcademicScopeDirectory}).
     *
     * @param internalId              clé primaire SQL de l'inscription
     * @param publicId                identifiant public de l'inscription
     * @param studentProfilePublicId  identifiant public du profil apprenant
     * @param classGroupPublicId      identifiant public de la classe de
     *                                l'inscription
     * @param classGroupCode          code fonctionnel de cette classe
     *                                (complément d'audit non sensible)
     * @param academicYearPublicId    identifiant public de l'année scolaire
     * @param academicYearCode        code de cette année scolaire
     * @param usable                  {@code true} si l'inscription est
     *                                {@code ACTIVE} — seule une inscription
     *                                active peut recevoir une nouvelle
     *                                exception de calendrier
     */
    record EnrollmentRef(
            long internalId,
            UUID publicId,
            UUID studentProfilePublicId,
            UUID classGroupPublicId,
            String classGroupCode,
            UUID academicYearPublicId,
            String academicYearCode,
            boolean usable) {
    }
}
