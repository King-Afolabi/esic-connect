package com.esic.connect.identity.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AccountInvitationRepository extends JpaRepository<AccountInvitation, Long> {

    Optional<AccountInvitation> findByTokenHash(String tokenHash);

    Optional<AccountInvitation> findByPublicId(UUID publicId);

    List<AccountInvitation> findByUserIdAndStatus(Long userId, AccountInvitationStatus status);

    /**
     * Compte chargé dans la même requête : le suivi affiche l'adresse et
     * le nom du destinataire, une page de vingt invitations ne doit pas
     * produire vingt et une requêtes (NFR-PERF-08).
     */
    @EntityGraph(attributePaths = "user")
    Page<AccountInvitation> findByStatus(AccountInvitationStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "user")
    Page<AccountInvitation> findAll(Pageable pageable);
}
