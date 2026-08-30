package com.esic.connect.enrollment;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
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
     * Inscriptions {@code ACTIVE} d'un compte apprenant dont la période
     * {@code [start_date, end_date]} (bornes inclusives, {@code end_date}
     * {@code null} = ouverte) couvre {@code date}.
     *
     * <p>Consommé par le module {@code attendance} : le serveur détermine
     * l'inscription de l'apprenant émargeur à partir du seul compte
     * authentifié — jamais d'un identifiant fourni par le client. Le
     * module appelant vérifie ensuite que la classe de l'inscription est
     * bien rattachée à la séance, et refuse s'il y a zéro ou plusieurs
     * correspondances (jamais de choix silencieux).
     *
     * @param userPublicId identifiant public du compte apprenant ; peut
     *                     être {@code null}
     * @param date         jour civil de référence (typiquement la date de
     *                     l'émargement)
     * @return la liste — éventuellement vide — des inscriptions actives
     *         couvrant la date ; compte inconnu ou sans profil apprenant
     *         → liste vide
     */
    List<EnrollmentRef> findActiveEnrollmentsForUserOn(UUID userPublicId, LocalDate date);

    /**
     * Identité minimale de l'apprenant d'une inscription, pour bâtir la
     * liste des présences d'une séance (module {@code attendance}).
     *
     * @param enrollmentInternalId identifiant interne de l'inscription
     * @return le descriptif si l'inscription existe, {@link Optional#empty()} sinon
     */
    Optional<AttendeeRef> describeAttendee(long enrollmentInternalId);

    /**
     * Nombre d'inscriptions {@code ACTIVE} rattachées à l'une des classes
     * indiquées — « effectif attendu » d'une séance couvrant ces classes.
     *
     * @param classGroupPublicIds identifiants publics des classes
     * @return le nombre d'inscriptions actives ; {@code 0} si aucune
     */
    long countActiveEnrollmentsInClasses(Collection<UUID> classGroupPublicIds);

    /**
     * Identité minimale d'un apprenant pour l'affichage d'une ligne de
     * présence — jamais d'adresse électronique ni d'identifiant interne.
     *
     * @param studentProfilePublicId identifiant public du profil apprenant
     * @param enrollmentPublicId     identifiant public de l'inscription
     * @param studentNumber          numéro étudiant
     * @param firstName              prénom ({@code null} si non résolu)
     * @param lastName               nom ({@code null} si non résolu)
     */
    record AttendeeRef(
            UUID studentProfilePublicId,
            UUID enrollmentPublicId,
            String studentNumber,
            String firstName,
            String lastName) {
    }

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
