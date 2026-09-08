package com.esic.connect.claim.internal;

import com.esic.connect.claim.ClaimAudience;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

interface ClaimRepository extends JpaRepository<Claim, Long> {

    Optional<Claim> findByPublicId(UUID publicId);

    /** Réclamations déposées par un utilisateur (vue apprenant). */
    Page<Claim> findByAuthorUserId(Long authorUserId, Pageable pageable);

    /** Décompte des réclamations déposées par un compte (comparaison de doublons, ANO-USER-001). */
    long countByAuthorUserId(Long authorUserId);

    /** File d'un guichet (vue formateur / responsable / administration). */
    Page<Claim> findByAudience(ClaimAudience audience, Pageable pageable);

    /**
     * File d'un guichet restreinte à un périmètre de classes. Utilisé pour
     * le {@code PEDAGOGICAL_MANAGER}, qui ne voit que ses formations.
     */
    Page<Claim> findByAudienceAndClassGroupIdIn(ClaimAudience audience,
                                                Collection<Long> classGroupIds, Pageable pageable);

    /**
     * Réclamations non closes d'un guichet (EF-REP-007). Le filtre de
     * statut exclut {@code RESOLVED}, {@code CLOSED} et {@code REJECTED} —
     * une réclamation rouverte redevient donc comptée, ce qui est
     * l'intention : elle attend de nouveau une réponse.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT COUNT(c) FROM Claim c
            WHERE c.audience = :audience
              AND c.status NOT IN (com.esic.connect.claim.ClaimStatus.RESOLVED,
                                   com.esic.connect.claim.ClaimStatus.CLOSED,
                                   com.esic.connect.claim.ClaimStatus.REJECTED)
            """)
    long countOpenByAudience(
            @org.springframework.data.repository.query.Param("audience")
            com.esic.connect.claim.ClaimAudience audience);

    @org.springframework.data.jpa.repository.Query("""
            SELECT COUNT(c) FROM Claim c
            WHERE c.audience = :audience AND c.classGroupId IN :classIds
              AND c.status NOT IN (com.esic.connect.claim.ClaimStatus.RESOLVED,
                                   com.esic.connect.claim.ClaimStatus.CLOSED,
                                   com.esic.connect.claim.ClaimStatus.REJECTED)
            """)
    long countOpenByAudienceWithin(
            @org.springframework.data.repository.query.Param("audience")
            com.esic.connect.claim.ClaimAudience audience,
            @org.springframework.data.repository.query.Param("classIds") Collection<Long> classIds);
}