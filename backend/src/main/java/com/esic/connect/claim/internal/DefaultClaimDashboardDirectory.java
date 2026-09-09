package com.esic.connect.claim.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.claim.ClaimAudience;
import com.esic.connect.claim.ClaimDashboardDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Implémentation du port {@link ClaimDashboardDirectory}. Confinée à
 * {@code claim.internal} ; un seul {@code COUNT} par appel.
 */
@Component
class DefaultClaimDashboardDirectory implements ClaimDashboardDirectory {

    private final ClaimRepository claimRepository;
    private final ClassGroupDirectory classGroupDirectory;

    DefaultClaimDashboardDirectory(ClaimRepository claimRepository,
                                   ClassGroupDirectory classGroupDirectory) {
        this.claimRepository = claimRepository;
        this.classGroupDirectory = classGroupDirectory;
    }

    @Override
    @Transactional(readOnly = true)
    public long countOpenClaims(ClaimAudience audience, Collection<UUID> classGroupPublicIds) {
        if (audience == null) {
            return 0L;
        }
        if (classGroupPublicIds == null) {
            return claimRepository.countOpenByAudience(audience);
        }
        // Périmètre vide ≠ périmètre global : compter globalement
        // afficherait à un responsable des dossiers qu'il ne peut pas
        // ouvrir.
        if (classGroupPublicIds.isEmpty()) {
            return 0L;
        }
        List<Long> internalIds = classGroupDirectory.findByPublicIds(classGroupPublicIds).stream()
                .map(ClassGroupDirectory.ClassGroupRef::internalId)
                .toList();
        return internalIds.isEmpty() ? 0L
                : claimRepository.countOpenByAudienceWithin(audience, internalIds);
    }
}
