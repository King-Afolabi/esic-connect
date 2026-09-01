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
}
