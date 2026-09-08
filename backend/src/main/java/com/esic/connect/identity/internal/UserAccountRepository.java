package com.esic.connect.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository
        extends JpaRepository<UserAccount, Long>, JpaSpecificationExecutor<UserAccount> {

    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findByPublicId(UUID publicId);

    /** Décompte borné des comptes par statut (bloc G1-F). {@code [status, count]} par ligne. */
    @Query("select u.status, count(u) from UserAccount u group by u.status")
    List<Object[]> countByStatusGrouped();

    /**
     * Résout un lot de comptes en UNE requête : une opération groupée sur
     * cinq cents comptes ne doit pas produire cinq cents requêtes
     * (NFR-PERF-08).
     */
    List<UserAccount> findByPublicIdIn(java.util.Collection<UUID> publicIds);

    /**
     * Comptes non archivés — base de la détection de doublons
     * (EF-USER-005). Un compte archivé n'est pas un doublon à traiter :
     * il est déjà sorti du circuit.
     */
    List<UserAccount> findByStatusNot(AccountStatus status);

    /** Comptes d'un statut donné parmi une liste d'identifiants publics (EF-REP-007). */
    long countByPublicIdInAndStatus(java.util.Collection<UUID> publicIds, AccountStatus status);

    /**
     * Recherche globale par identité civile (EF-USER-009 ; docs/02 §22.7),
     * restreinte aux comptes portant un rôle actif donné.
     *
     * <p><strong>L'adresse électronique n'est pas un critère</strong> :
     * la chercher permettrait de confirmer l'existence d'un compte à
     * partir d'une adresse devinée — une énumération, pas une recherche.
     *
     * <p>{@code LIKE '%…%'} n'utilise pas d'index : assumé à la
     * volumétrie d'un établissement, et le résultat est borné par
     * l'appelant.
     */
    @Query("""
            SELECT DISTINCT u FROM UserRole ur JOIN ur.user u JOIN ur.role r
            WHERE r.code = :roleCode
              AND ur.active = true
              AND u.status = :status
              AND (LOWER(u.lastName) LIKE :pattern OR LOWER(u.firstName) LIKE :pattern)
            ORDER BY u.lastName ASC, u.firstName ASC
            """)
    List<UserAccount> searchByName(@Param("pattern") String pattern,
                                   @Param("roleCode") RoleCode roleCode,
                                   @Param("status") AccountStatus status,
                                   org.springframework.data.domain.Pageable pageable);

    /**
     * Comme {@link #searchByName}, mais retient tout compte <strong>non
     * archivé</strong> (activé ou non). La liste des apprenants (réservée
     * à l'administration) doit retrouver par le nom un apprenant tout
     * juste créé, encore en attente d'activation — la recherche globale,
     * elle, reste limitée aux comptes actifs.
     */
    @Query("""
            SELECT DISTINCT u FROM UserRole ur JOIN ur.user u JOIN ur.role r
            WHERE r.code = :roleCode
              AND ur.active = true
              AND u.status <> :excludedStatus
              AND (LOWER(u.lastName) LIKE :pattern OR LOWER(u.firstName) LIKE :pattern)
            ORDER BY u.lastName ASC, u.firstName ASC
            """)
    List<UserAccount> searchByNameExcludingStatus(@Param("pattern") String pattern,
                                                  @Param("roleCode") RoleCode roleCode,
                                                  @Param("excludedStatus") AccountStatus excludedStatus,
                                                  org.springframework.data.domain.Pageable pageable);
}
