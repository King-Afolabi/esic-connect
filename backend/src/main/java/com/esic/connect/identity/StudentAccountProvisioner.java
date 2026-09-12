package com.esic.connect.identity;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Port public du module {@code identity} pour l'import CSV des apprenants
 * (rapport §4.1). Usages :
 *
 * <ul>
 *   <li>{@link #findByEmail(String)} — <strong>lecture seule</strong>,
 *       détection d'un compte existant pendant la <em>simulation</em> ;</li>
 *   <li>{@link #prepareStudentAccountAndInvitation} — <strong>application</strong>,
 *       exécutée <em>dans la transaction de l'appelant</em> (propagation
 *       {@code REQUIRED}, jamais {@code REQUIRES_NEW}). Écrit
 *       {@code user_account} (si absent), le rôle {@code STUDENT} actif
 *       (si absent) et {@code account_invitation} (empreinte SHA-256, TTL
 *       configuré, révocation des invitations {@code PENDING}
 *       antérieures), et publie {@link AccountInvitationIssuedEvent} pour
 *       l'e-mail {@code AFTER_COMMIT}. Ne publie <strong>pas</strong>
 *       {@link AccountLifecycleEvent} : aucun audit synchrone sur le
 *       chemin d'import (invariant T5). Le jeton brut ne sort jamais du
 *       module {@code identity}.</li>
 *   <li>{@link #studentNumberTaken(String)} / {@link #assignStudentIdentity} —
 *       numéro étudiant / date de naissance (refonte 2026-09) : données
 *       personnelles générales portées directement par {@code user_account}
 *       (plus de {@code student_profile}). {@code identity} en reste seul
 *       propriétaire ; {@code studentimport} ne fait que lire/écrire via ce
 *       port, jamais directement en base.</li>
 * </ul>
 *
 * <p>Un compte {@code ACTIVE} / {@code SUSPENDED} / {@code LOCKED} /
 * {@code ARCHIVED} ne doit jamais être « préparé » : la
 * {@link StudentAccountProvisioningException} est levée sans aucune
 * écriture (l'orchestrateur d'import la retraduit).
 */
public interface StudentAccountProvisioner {

    /** Lecture seule (simulation). N'ouvre aucune écriture. */
    Optional<ExistingAccountView> findByEmail(String rawEmail);

    /**
     * Lecture seule — compte déjà résolu (par e-mail ou réutilisé dans le
     * même import) dont on veut relire l'état courant, par exemple avant
     * {@link #assignStudentIdentity} pour vérifier qu'un numéro étudiant
     * n'est pas déjà posé. N'ouvre aucune écriture.
     */
    Optional<ExistingAccountView> findByUserPublicId(UUID userPublicId);

    /**
     * Application (confirmation) — dans la transaction de l'appelant.
     *
     * @param command             identité civile + e-mail brut du nouvel apprenant
     * @param issuerUserInternalId auteur de l'écriture (peut être {@code null})
     * @return le compte préparé (créé ou déjà en attente) et les drapeaux d'effet
     * @throws StudentAccountProvisioningException si le compte existe mais n'est pas
     *                                             {@code PENDING_ACTIVATION}
     */
    PreparedAccount prepareStudentAccountAndInvitation(NewStudentAccount command, Long issuerUserInternalId);

    /**
     * Met à jour le seul <strong>téléphone</strong> d'un compte apprenant
     * (jamais l'identité : nom / prénom / e-mail). Dans la transaction de
     * l'appelant. Utilisée par l'action {@code UPDATE_PROFILE} de l'import.
     *
     * @param userPublicId         compte cible
     * @param phone                nouveau téléphone normalisé ({@code null} = ne pas toucher)
     * @param actorUserInternalId  auteur ({@code null} accepté)
     */
    void updateStudentPhone(UUID userPublicId, String phone, Long actorUserInternalId);

    /** Lecture seule. {@code true} si ce numéro étudiant est déjà attribué à un compte. */
    boolean studentNumberTaken(String studentNumber);

    /**
     * Attribue le numéro étudiant / la date de naissance à un compte qui
     * n'en a pas encore (refonte 2026-09, ex-{@code StudentProfileView}
     * création). Sans effet si le compte porte déjà un numéro — immuable
     * une fois posé, exactement comme l'ancien {@code student_profile}.
     * Dans la transaction de l'appelant.
     *
     * @param userPublicId         compte cible
     * @param studentNumber        numéro étudiant déjà déterminé (jamais {@code null})
     * @param birthDate            date de naissance ({@code null} accepté)
     * @param actorUserInternalId  auteur ({@code null} accepté)
     */
    void assignStudentIdentity(UUID userPublicId, String studentNumber, LocalDate birthDate,
                               Long actorUserInternalId);

    /** Statut d'un compte, exposé sans révéler l'entité interne. */
    enum StatusView { PENDING_ACTIVATION, ACTIVE, SUSPENDED, LOCKED, ARCHIVED }

    /**
     * @param publicId             identifiant public du compte
     * @param internalId           clé primaire SQL du compte
     * @param status               statut courant
     * @param firstName            prénom (identité civile, jamais réécrite par l'import)
     * @param lastName             nom
     * @param phone                téléphone courant ({@code null} si absent)
     * @param hasActiveStudentRole {@code true} si le rôle {@code STUDENT} est actif
     * @param studentNumber        numéro étudiant déjà attribué ({@code null} si aucun) —
     *                             refonte 2026-09, ex-{@code student_profile.student_number}
     * @param birthDate            date de naissance déjà renseignée ({@code null} si aucune)
     */
    record ExistingAccountView(
            UUID publicId,
            long internalId,
            StatusView status,
            String firstName,
            String lastName,
            String phone,
            boolean hasActiveStudentRole,
            String studentNumber,
            LocalDate birthDate) {
    }

    /**
     * @param rawEmail  adresse brute (sera normalisée par l'implémentation)
     * @param firstName prénom
     * @param lastName  nom
     * @param phone     téléphone normalisé, éventuellement {@code null}
     */
    record NewStudentAccount(String rawEmail, String firstName, String lastName, String phone) {
    }

    /**
     * @param userPublicId     identifiant public du compte (créé ou existant)
     * @param userInternalId   clé primaire SQL du compte
     * @param accountCreated   {@code true} si le compte vient d'être créé
     * @param invitationIssued {@code true} si une invitation a été (r)émise
     */
    record PreparedAccount(UUID userPublicId, long userInternalId, boolean accountCreated, boolean invitationIssued) {
    }
}
