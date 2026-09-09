package com.esic.connect.organization.internal;

import com.esic.connect.organization.RoomDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Implémentation du port {@link RoomDirectory}. */
@Component
class DefaultRoomDirectory implements RoomDirectory {

    private final RoomRepository roomRepository;
    private final SiteRepository siteRepository;
    private final SiteNetworkRangeRepository networkRangeRepository;
    private final Clock clock;

    DefaultRoomDirectory(RoomRepository roomRepository,
                         SiteRepository siteRepository,
                         SiteNetworkRangeRepository networkRangeRepository,
                         Clock clock) {
        this.roomRepository = roomRepository;
        this.siteRepository = siteRepository;
        this.networkRangeRepository = networkRangeRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoomRef> findActiveByStaticQrReference(String staticQrReference) {
        if (staticQrReference == null || staticQrReference.isBlank()) {
            return Optional.empty();
        }
        return roomRepository.findByStaticQrReference(staticQrReference.trim())
                // Une affiche restée au mur d'une salle archivée ne doit
                // pas rouvrir un émargement.
                .filter(room -> !room.isArchived())
                .map(room -> new RoomRef(room.getId(), room.getPublicId(), room.getCode(),
                        room.getSite().getPublicId()));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<RoomSearchRef> search(String query, int limit) {
        String pattern = com.esic.connect.shared.SearchPattern.of(query);
        if (pattern == null) {
            return java.util.List.of();
        }
        return roomRepository.search(pattern, org.springframework.data.domain.PageRequest.of(0,
                        com.esic.connect.shared.SearchPattern.bound(limit)))
                .stream()
                // Le jeton de QR fixe n'entre jamais dans un résultat de
                // recherche : il vaut émargement (EF-ATT-010).
                .map(room -> new RoomSearchRef(room.getPublicId(), room.getCode(), room.getName(),
                        room.getBuilding() != null ? room.getBuilding().getName() : null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isWithinAuthorizedRange(UUID sitePublicId, String ipAddress) {
        if (sitePublicId == null || ipAddress == null || ipAddress.isBlank()) {
            // Refus par défaut : une adresse absente n'est pas une adresse
            // autorisée (docs/02 §18.2).
            return false;
        }
        Optional<Site> site = siteRepository.findByPublicId(sitePublicId);
        if (site.isEmpty()) {
            return false;
        }
        Instant now = clock.instant();
        return networkRangeRepository.findBySite_IdAndActiveTrue(site.get().getId()).stream()
                .filter(range -> covers(range, now))
                .anyMatch(range -> CidrMatcher.matches(range.getCidr(), ipAddress));
    }

    /** Plage dans sa période de validité ; bornes ouvertes acceptées. */
    private static boolean covers(SiteNetworkRange range, Instant now) {
        return (range.getValidFrom() == null || !range.getValidFrom().isAfter(now))
                && (range.getValidUntil() == null || !range.getValidUntil().isBefore(now));
    }
}
