package com.esic.connect.organization.internal;

import com.esic.connect.organization.SiteDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Implémentation du port {@link SiteDirectory}. Reste confinée à
 * {@code organization.internal} : les autres modules ne connaissent que
 * l'interface publique et le {@link SiteDirectory.SiteRef}.
 */
@Component
class DefaultSiteDirectory implements SiteDirectory {

    private final SiteRepository siteRepository;

    DefaultSiteDirectory(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SiteRef> findByPublicId(UUID sitePublicId) {
        if (sitePublicId == null) {
            return Optional.empty();
        }
        return siteRepository.findByPublicId(sitePublicId).map(DefaultSiteDirectory::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SiteRef> findByInternalId(long siteInternalId) {
        return siteRepository.findById(siteInternalId).map(DefaultSiteDirectory::toRef);
    }

    private static SiteRef toRef(Site site) {
        return new SiteRef(site.getId(), site.getPublicId(), site.isArchived());
    }
}
