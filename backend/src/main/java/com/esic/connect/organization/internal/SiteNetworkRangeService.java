package com.esic.connect.organization.internal;

import com.esic.connect.organization.OrganizationChangeAction;
import com.esic.connect.organization.OrganizationResourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Set;

/**
 * Plages réseau autorisées par site (docs/04-modele-donnees.md §9.4,
 * cahier §17.9). Réservé au {@code SUPER_ADMIN}, consultation comprise
 * (contrôle porté par le contrôleur). Modèle « ajout + activation /
 * désactivation » : jamais de suppression physique. Le {@code cidr} est
 * validé IPv4/IPv6 avec préfixe borné.
 */
@Service
@Transactional
class SiteNetworkRangeService {

    private static final Set<String> SORTABLE = Set.of("cidr", "label", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final SiteNetworkRangeRepository rangeRepository;
    private final SiteRepository siteRepository;
    private final OrganizationChangePublisher changePublisher;

    SiteNetworkRangeService(SiteNetworkRangeRepository rangeRepository, SiteRepository siteRepository,
                            OrganizationChangePublisher changePublisher) {
        this.rangeRepository = rangeRepository;
        this.siteRepository = siteRepository;
        this.changePublisher = changePublisher;
    }

    @Transactional(readOnly = true)
    PageResponse<SiteNetworkRangeResponse> listForSite(UUID sitePublicId, String activeFilter,
                                                       int page, int size, String sort) {
        Site site = requireSite(sitePublicId);
        Pageable pageable = OrganizationQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        List<Specification<SiteNetworkRange>> specs = new ArrayList<>();
        specs.add(OrganizationSpecifications.rangeHasSite(site.getId()));
        parseActive(activeFilter).ifPresent(active -> specs.add(OrganizationSpecifications.rangeIsActive(active)));
        Page<SiteNetworkRange> result = rangeRepository.findAll(Specification.allOf(specs), pageable);
        return PageResponse.of(result, SiteNetworkRangeResponse::from);
    }

    @Transactional(readOnly = true)
    SiteNetworkRangeResponse get(UUID publicId) {
        return SiteNetworkRangeResponse.from(requireRange(publicId));
    }

    SiteNetworkRangeResponse create(UUID sitePublicId, CreateNetworkRangeRequest request, String callerSubject) {
        Site site = requireSite(sitePublicId);
        if (site.isArchived()) {
            throw new OrganizationException(OrganizationException.Kind.ARCHIVED_PARENT);
        }
        String cidr = request.cidr().trim();
        if (!CidrValidator.isValid(cidr)) {
            throw new OrganizationException(OrganizationException.Kind.INVALID_CIDR);
        }
        if (rangeRepository.existsBySiteIdAndCidrAndActiveTrue(site.getId(), cidr)) {
            throw new OrganizationException(OrganizationException.Kind.DUPLICATE_ACTIVE_RANGE);
        }
        SiteNetworkRange range = new SiteNetworkRange(site, cidr, request.label().trim(), Instant.now());
        Long actorId = changePublisher.actorId(callerSubject);
        range.markCreatedBy(actorId);
        SiteNetworkRange saved = rangeRepository.save(range);
        changePublisher.publish(OrganizationResourceType.SITE_NETWORK_RANGE, saved.getPublicId(),
                OrganizationChangeAction.CREATED, actorId, "cidr=" + cidr);
        return SiteNetworkRangeResponse.from(saved);
    }

    void deactivate(UUID publicId, String callerSubject) {
        SiteNetworkRange range = requireRange(publicId);
        if (!range.isActive()) {
            throw new OrganizationException(OrganizationException.Kind.INVALID_STATE);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        range.deactivate(Instant.now());
        changePublisher.publish(OrganizationResourceType.SITE_NETWORK_RANGE, range.getPublicId(),
                OrganizationChangeAction.DEACTIVATED, actorId, "cidr=" + range.getCidr());
    }

    void activate(UUID publicId, String callerSubject) {
        SiteNetworkRange range = requireRange(publicId);
        if (range.isActive()) {
            throw new OrganizationException(OrganizationException.Kind.INVALID_STATE);
        }
        if (rangeRepository.existsBySiteIdAndCidrAndActiveTrue(range.getSite().getId(), range.getCidr())) {
            throw new OrganizationException(OrganizationException.Kind.DUPLICATE_ACTIVE_RANGE);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        range.reactivate(Instant.now());
        changePublisher.publish(OrganizationResourceType.SITE_NETWORK_RANGE, range.getPublicId(),
                OrganizationChangeAction.ACTIVATED, actorId, "cidr=" + range.getCidr());
    }

    private Site requireSite(UUID publicId) {
        return siteRepository.findByPublicId(publicId)
                .orElseThrow(() -> new OrganizationException(OrganizationException.Kind.SITE_NOT_FOUND));
    }

    private SiteNetworkRange requireRange(UUID publicId) {
        return rangeRepository.findByPublicId(publicId)
                .orElseThrow(() -> new OrganizationException(OrganizationException.Kind.NETWORK_RANGE_NOT_FOUND));
    }

    private static java.util.Optional<Boolean> parseActive(String value) {
        if (value == null || value.isBlank()) {
            return java.util.Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true" -> java.util.Optional.of(Boolean.TRUE);
            case "false" -> java.util.Optional.of(Boolean.FALSE);
            default -> throw new OrganizationException(OrganizationException.Kind.INVALID_FILTER);
        };
    }
}
