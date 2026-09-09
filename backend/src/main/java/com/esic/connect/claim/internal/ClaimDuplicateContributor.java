package com.esic.connect.claim.internal;

import com.esic.connect.identity.DuplicateDependencyContributor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Remonte à {@code identity}, <strong>en lecture seule</strong>, le nombre
 * de réclamations déposées par un compte (ANO-USER-001 — comparaison de
 * doublons). Un seul décompte borné, aucune écriture.
 */
@Component
class ClaimDuplicateContributor implements DuplicateDependencyContributor {

    private final ClaimRepository claimRepository;

    ClaimDuplicateContributor(ClaimRepository claimRepository) {
        this.claimRepository = claimRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countsFor(long userInternalId) {
        return Map.of("claims", claimRepository.countByAuthorUserId(userInternalId));
    }
}
