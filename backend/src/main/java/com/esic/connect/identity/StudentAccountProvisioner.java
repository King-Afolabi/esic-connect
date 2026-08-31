package com.esic.connect.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * Port public du module {@code identity} pour l'import CSV des apprenants
 * (rapport §4.1). Deux usages disjoints :
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
     */
    record ExistingAccountView(
            UUID publicId,
            long internalId,
            StatusView status,
            String firstName,
            String lastName,
            String phone,
            boolean hasActiveStudentRole) {
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
