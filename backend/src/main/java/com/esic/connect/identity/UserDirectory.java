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

    /**
     * Comptes non archivés porteurs d'un rôle actif donné.
     *
     * <p>Sert à désigner un <strong>guichet</strong> plutôt qu'une
     * personne : une réclamation adressée à l'administration scolaire doit
     * atteindre celles et ceux qui y siègent, sans que le module appelant
     * ait à tenir sa propre liste — laquelle se périmerait au premier
     * changement d'affectation.
     *
     * @param roleCode code du rôle, par exemple {@code "SCHOOL_ADMINISTRATION"}
     * @return les identifiants publics des comptes concernés ; vide si aucun
     */
    Set<UUID> findActiveUserPublicIdsByRole(String roleCode);

    /**
     * Adresse électronique d'un compte, <strong>pour lui adresser un
     * message et rien d'autre</strong> (EF-NOTIF-004).
     *
     * <p>Volontairement absente de {@link UserRef} : une adresse n'a pas à
     * circuler par défaut dans les modules qui n'ont besoin que d'une
     * référence de compte. La demander explicitement rend visible, à la
     * lecture, chaque endroit du produit qui manipule une adresse.
     *
     * <p>L'appelant ne doit ni la journaliser, ni l'afficher, ni la
     * stocker : le journal de délivrabilité conserve une empreinte et une
     * forme masquée, jamais la valeur (docs/02 §11.3).
     *
     * @param userInternalId identifiant interne du compte
     * @return l'adresse si le compte existe, {@link Optional#empty()} sinon
     */
    Optional<String> findEmailForDelivery(long userInternalId);

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
