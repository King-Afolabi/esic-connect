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

    /** File d'un guichet (vue formateur / responsable / administration). */
    Page<Claim> findByAudience(ClaimAudience audience, Pageable pageable);

    /**
     * File d'un guichet restreinte à un périmètre de classes. Utilisé pour
     * le {@code PEDAGOGICAL_MANAGER}, qui ne voit que ses formations.
     */
    Page<Claim> findByAudienceAndClassGroupIdIn(ClaimAudience audience,
                                                Collection<Long> classGroupIds, Pageable pageable);
}
