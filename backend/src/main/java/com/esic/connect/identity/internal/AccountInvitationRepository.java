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

    /**
     * Invitations encore {@code PENDING} dont la date d'expiration est
     * passée (§22.6 — « invitations échouées »). L'expiration se déduit
     * de {@code expires_at} : ce n'est pas un statut stocké.
     */
    long countByStatusAndExpiresAtBefore(AccountInvitationStatus status, java.time.Instant instant);

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

    /**
     * Invitations encore {@code PENDING} d'un compte encore en attente
     * d'activation (EF-REP-010 — « comptes en attente, dernière
     * relance »).
     *
     * <p>Le filtre porte sur les <strong>deux</strong> états : une
     * invitation {@code PENDING} dont le compte a été activé par un autre
     * chemin n'est pas un compte non activé, et l'afficher enverrait
     * relancer quelqu'un qui n'attend rien.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT i FROM AccountInvitation i JOIN FETCH i.user u
            WHERE i.status = com.esic.connect.identity.internal.AccountInvitationStatus.PENDING
              AND u.status = com.esic.connect.identity.internal.AccountStatus.PENDING_ACTIVATION
            ORDER BY i.createdAt ASC
            """)
    List<AccountInvitation> findPendingActivations(Pageable pageable);
}