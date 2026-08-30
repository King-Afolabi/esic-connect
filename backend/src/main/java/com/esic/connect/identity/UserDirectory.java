package com.esic.connect.identity;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Port public minimal du module {@code identity}.
 *
 * <p>Permet à un autre module (ici {@code academic}, pour vérifier la
 * cible d'une affectation de responsable pédagogique et réafficher son
 * identifiant public) de résoudre une référence technique de compte sans
 * dépendre des classes internes d'{@code identity}. Ne renvoie ni
 * l'entité {@code UserAccount}, ni un repository, ni aucun type de
 * {@code identity.internal} : uniquement le {@link UserRef} ci-dessous,
 * composé de types standard.
 *
 * <p>Complète {@link CurrentUserResolver}, qui ne résout que l'identifiant
 * interne de l'appelant courant.
 */
public interface UserDirectory {

    /**
     * @param userPublicId identifiant public du compte (forme UUID) ; peut
     *                     être {@code null}
     * @return la référence du compte si un compte correspond,
     *         {@link Optional#empty()} sinon
     */
    Optional<UserRef> findByPublicId(UUID userPublicId);

    /**
     * @param userInternalId identifiant interne du compte
     * @return la référence du compte si un compte correspond,
     *         {@link Optional#empty()} sinon
     */
    Optional<UserRef> findByInternalId(long userInternalId);

    /**
     * Identité civile d'un compte (prénom, nom) — strictement suffisante
     * pour afficher un formateur de séance ou une ligne de présence, sans
     * exposer l'adresse électronique ni l'identifiant interne.
     *
     * @param userInternalId identifiant interne du compte
     * @return le nom si le compte existe, {@link Optional#empty()} sinon
     */
    Optional<PersonName> findName(long userInternalId);

    /** Prénom / nom d'un compte, pour affichage. */
    record PersonName(String firstName, String lastName) {
    }

    /**
     * Référence technique d'un compte, strictement suffisante pour qu'un
     * autre module stocke la clé étrangère {@code manager_user_id},
     * réaffiche l'identifiant public et contrôle l'éligibilité d'une
     * cible (compte non archivé, porteur d'un rôle actif attendu).
     *
     * @param internalId  clé primaire SQL du compte
     * @param publicId    identifiant public du compte
     * @param archived    {@code true} si le compte est archivé
     * @param activeRoles codes des rôles actuellement actifs du compte
     *                    (par exemple {@code "PEDAGOGICAL_MANAGER"})
     */
    record UserRef(long internalId, UUID publicId, boolean archived, Set<String> activeRoles) {
    }
}
