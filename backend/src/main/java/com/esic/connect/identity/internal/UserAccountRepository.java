package com.esic.connect.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

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
}
