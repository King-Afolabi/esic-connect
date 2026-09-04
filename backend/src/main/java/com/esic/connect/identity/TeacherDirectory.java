package com.esic.connect.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port public minimal du module {@code identity}.
 *
 * <p>Permet au module {@code coursesession} de lister les comptes
 * <em>éligibles comme formateur d'une séance</em> et de vérifier
 * l'éligibilité d'un compte cible, sans dépendre des classes internes
 * d'{@code identity} ni élargir la surface de {@code GET /api/v1/users}
 * (réservé à l'administration). Un compte est éligible s'il est
 * {@code ACTIVE} et porte une affectation active du rôle {@code TEACHER}.
 *
 * <p>Ne renvoie ni l'entité {@code UserAccount}, ni un repository, ni
 * aucun type de {@code identity.internal} : uniquement le
 * {@link TeacherRef} ci-dessous, composé de types standard. Même approche
 * que {@link UserDirectory} et {@link CurrentUserResolver}.
 */
public interface TeacherDirectory {

    /**
     * @return les comptes formateurs éligibles (compte {@code ACTIVE} +
     *         rôle {@code TEACHER} actif), triés par nom puis prénom ;
     *         liste éventuellement vide
     */
    List<TeacherRef> listEligibleTeachers();

    /**
     * @param userPublicId identifiant public du compte ; peut être
     *                     {@code null}
     * @return la référence si le compte existe, est {@code ACTIVE} et
     *         porte un rôle {@code TEACHER} actif ; {@link Optional#empty()}
     *         sinon (compte inconnu, non actif ou sans rôle formateur)
     */
    Optional<TeacherRef> findEligibleTeacher(UUID userPublicId);

    /**
     * Résout un formateur par son identifiant <strong>interne</strong>.
     *
     * <p>Nécessaire au retour à une version antérieure de planning
     * (EF-PLAN-008) : les entrées de planning stockent la clé interne du
     * formateur, alors que la commande de publication attend un
     * identifiant public. Sans cette résolution, une version restaurée
     * perdrait son formateur.
     *
     * @return la référence si le compte existe encore et reste éligible ;
     *         {@link Optional#empty()} sinon — un formateur parti depuis
     *         doit être signalé, pas silencieusement remplacé
     */
    Optional<TeacherRef> findEligibleTeacherByInternalId(long userInternalId);

    /**
     * Référence technique d'un compte formateur, strictement suffisante
     * pour qu'une séance stocke la clé étrangère {@code teacher_user_id}
     * et affiche l'identité du formateur.
     *
     * @param internalId clé primaire SQL du compte
     * @param publicId   identifiant public du compte
     * @param firstName  prénom
     * @param lastName    nom
     */
    record TeacherRef(long internalId, UUID publicId, String firstName, String lastName) {
    }
}
