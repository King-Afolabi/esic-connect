package com.esic.connect.identity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
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
     * Identités civiles de <strong>plusieurs</strong> comptes en une
     * requête.
     *
     * <p>La variante unitaire ci-dessus, appelée dans une boucle sur un
     * effectif, produit autant de requêtes que d'apprenants : c'est le
     * coût proportionnel au nombre d'éléments affichés que NFR-PERF-08
     * interdit. Les identifiants inconnus sont simplement absents du
     * résultat.
     */
    java.util.Map<Long, PersonName> findNames(java.util.Collection<Long> userInternalIds);

    /**
     * Comme {@link #findNames}, mais renvoie aussi l'identifiant public du
     * compte : une liste d'apprenants doit afficher le nom <em>et</em>
     * pouvoir lier vers la fiche, sans une requête de résolution par
     * ligne (NFR-PERF-08). Les identifiants inconnus sont absents du
     * résultat.
     */
    java.util.Map<Long, NamedUserRef> findNamedRefs(java.util.Collection<Long> userInternalIds);

    /**
     * Numéros étudiants (refonte 2026-09, ex-{@code student_profile.student_number})
     * d'un lot de comptes, en une requête (anti-N+1, NFR-PERF-08). Un
     * compte sans numéro — y compris un compte non-{@code STUDENT} — est
     * simplement absent du résultat : ce n'est jamais une erreur.
     */
    java.util.Map<Long, String> findStudentNumbers(java.util.Collection<Long> userInternalIds);

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

    /**
     * Comptes <strong>actifs</strong> porteurs du rôle donné dont le nom
     * ou le prénom contient {@code query} (EF-USER-009).
     *
     * <p>L'adresse électronique n'est jamais un critère de recherche :
     * elle permettrait de confirmer l'existence d'un compte à partir
     * d'une adresse devinée.
     *
     * @param roleCode code du rôle, par exemple {@code "STUDENT"}
     */
    java.util.List<NamedUserRef> searchByName(String query, String roleCode, int limit);

    /**
     * Comme {@link #searchByName}, mais inclut les comptes <strong>non
     * activés</strong> (tout statut sauf {@code ARCHIVED}). Réservé à la
     * liste d'administration des apprenants, qui doit retrouver par son
     * nom un apprenant fraîchement importé ou créé, encore en attente
     * d'activation. La recherche globale continue d'utiliser
     * {@link #searchByName} (comptes actifs uniquement).
     */
    java.util.List<NamedUserRef> searchByNameIncludingInactive(String query, String roleCode, int limit);

    /**
     * Comptes <strong>actifs</strong> porteurs du rôle donné dont le
     * numéro étudiant (colonne {@code user_account.student_number},
     * refonte 2026-09 — ex-{@code student_profile.student_number})
     * contient {@code query}, insensible à la casse.
     *
     * <p>Complète {@link #searchByName} : un apprenant se retrouve aussi
     * bien par son nom que par son numéro (EF-USER-009). Un compte sans
     * numéro n'est jamais candidat.
     *
     * @param roleCode code du rôle, par exemple {@code "STUDENT"}
     */
    java.util.List<NamedUserRef> searchByStudentNumber(String query, String roleCode, int limit);

    /**
     * Comptes porteurs d'un rôle actif donné, paginés / filtrés / triés —
     * pour construire un référentiel (par exemple l'écran « Apprenants »,
     * qui liste tous les comptes {@code STUDENT}) sans dupliquer la
     * pagination et le filtrage déjà résolus par ce module
     * ({@code UserManagementService.listUsers}).
     *
     * <p>Le rôle est ici l'unique source de vérité du statut correspondant :
     * ce point d'entrée ne consulte aucune donnée d'un autre module pour
     * décider qui porte le rôle {@code roleCode}. Un module appelant (par
     * exemple {@code enrollment}) peut restreindre le résultat à un
     * périmètre qu'il est seul à connaître (par exemple les comptes ayant
     * une inscription active dans une classe visible par l'appelant) via
     * {@link AccountRoleQuery#restrictToInternalIds()}, sans casser la
     * pagination — le filtrage est appliqué dans la même requête SQL.
     *
     * @param query paramètres de la recherche
     * @return la page de comptes correspondants
     */
    AccountPage listAccountsByActiveRole(AccountRoleQuery query);

    /**
     * @param roleCode              code du rôle actif requis, par exemple {@code "STUDENT"}
     * @param status                filtre optionnel sur le statut du compte (ex. {@code "ACTIVE"}) ;
     *                              {@code null}/vide = tous statuts
     * @param text                  filtre optionnel, sous-chaîne insensible à la casse de l'email,
     *                              du prénom, du nom ou du numéro étudiant ({@code student_number},
     *                              refonte 2026-09) ; {@code null}/vide = pas de filtre
     * @param restrictToInternalIds restreint le résultat à ces identifiants internes de compte ;
     *                              {@code null} = aucune restriction (périmètre global) ; une
     *                              collection <strong>vide</strong> (non {@code null}) ne renvoie
     *                              donc aucun résultat, jamais un périmètre global par défaut
     * @param page                  page demandée (0-based, négatif ramené à 0)
     * @param size                  taille de page demandée (le port applique ses propres bornes)
     * @param sort                  tri, forme {@code champ} ou {@code champ,asc|desc} ; liste
     *                              blanche interne ({@code lastName}, {@code email},
     *                              {@code createdAt}, {@code lastLoginAt}) — un champ hors liste
     *                              retombe sur le tri par défaut, jamais une erreur SQL
     */
    record AccountRoleQuery(String roleCode, String status, String text,
                            Collection<Long> restrictToInternalIds, int page, int size, String sort) {

        public AccountRoleQuery {
            page = Math.max(page, 0);
        }

        public static AccountRoleQuery of(String roleCode, int page, int size) {
            return new AccountRoleQuery(roleCode, null, null, null, page, size, null);
        }
    }

    /**
     * Compte, tel qu'exposé pour bâtir un référentiel (ex. liste des
     * apprenants) — identité civile et statut, jamais un identifiant SQL
     * interne côté appelant.
     *
     * @param studentNumber numéro étudiant ({@code null} si absent) —
     *                      refonte 2026-09, ex-{@code student_profile.student_number} ;
     *                      sans lien avec le rôle porté par le compte
     * @param birthDate     date de naissance ({@code null} si absente) —
     *                      refonte 2026-09, ex-{@code student_profile.birth_date}
     */
    record AccountSummary(long internalId, UUID publicId, String email, String firstName, String lastName,
                          String status, Instant createdAt, Instant lastLoginAt,
                          String studentNumber, LocalDate birthDate) {
    }

    /**
     * Page de comptes. Forme minimale volontairement indépendante de
     * {@code org.springframework.data.domain.Page} : un port public ne
     * transporte pas un type dont la sérialisation dépend de la version de
     * Spring Data (docs/03 §6.4, frontières de module).
     */
    record AccountPage(List<AccountSummary> content, long totalElements) {
    }

    /** Compte trouvé par recherche : identité civile, jamais d'adresse. */
    record NamedUserRef(long internalId, UUID publicId, String firstName, String lastName) {
    }

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
