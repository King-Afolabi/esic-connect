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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Référentiel des sites (docs/04-modele-donnees.md §9.1). CRUD, archivage
 * logique et restauration ; aucune suppression physique. Le {@code code}
 * est immuable après création. L'archivage est refusé tant que des
 * bâtiments ou des salles actifs subsistent.
 */
@Service
@Transactional
class SiteService {

    private static final Set<String> SORTABLE = Set.of("code", "name", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "code");

    private final SiteRepository siteRepository;
    private final BuildingRepository buildingRepository;
    private final RoomRepository roomRepository;
    private final OrganizationChangePublisher changePublisher;

    SiteService(SiteRepository siteRepository, BuildingRepository buildingRepository,
                RoomRepository roomRepository, OrganizationChangePublisher changePublisher) {
        this.siteRepository = siteRepository;
        this.buildingRepository = buildingRepository;
        this.roomRepository = roomRepository;
        this.changePublisher = changePublisher;
    }

    @Transactional(readOnly = true)
    PageResponse<SiteResponse> list(String statusFilter, String textFilter, int page, int size, String sort) {
        Pageable pageable = OrganizationQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        List<Specification<Site>> specs = new ArrayList<>();
        parseStatus(statusFilter).ifPresent(status -> specs.add(OrganizationSpecifications.siteHasStatus(status)));
        OrganizationQuerySupport.normalizeText(textFilter)
                .ifPresent(text -> specs.add(OrganizationSpecifications.siteMatchesText(text)));
        Page<Site> result = siteRepository.findAll(Specification.allOf(specs), pageable);
        return PageResponse.of(result, SiteResponse::from);
    }

    @Transactional(readOnly = true)
    SiteResponse get(UUID publicId) {
        return SiteResponse.from(requireSite(publicId));
    }

    SiteResponse create(CreateSiteRequest request, String callerSubject) {
        String code = request.code().trim();
        String timeZone = SiteFieldValidator.requireIanaTimeZone(request.timeZoneId());
        String country = SiteFieldValidator.normalizeCountryCode(request.countryCode());
        if (siteRepository.existsByCode(code)) {
            throw new OrganizationException(OrganizationException.Kind.DUPLICATE_CODE);
        }
        Site site = new Site(code, request.name().trim(), timeZone);
        site.updateDetails(request.name().trim(), trimToNull(request.addressLine1()), trimToNull(request.addressLine2()),
                trimToNull(request.postalCode()), trimToNull(request.city()), country, timeZone, null);
        Long actorId = changePublisher.actorId(callerSubject);
        site.markCreatedBy(actorId);
        Site saved = siteRepository.save(site);
        changePublisher.publish(OrganizationResourceType.SITE, saved.getPublicId(),
                OrganizationChangeAction.CREATED, actorId, "code=" + code);
        return SiteResponse.from(saved);
    }

    SiteResponse update(UUID publicId, UpdateSiteRequest request, String callerSubject) {
        Site site = requireSite(publicId);
        if (site.isArchived()) {
            throw new OrganizationException(OrganizationException.Kind.ENTITY_ARCHIVED);
        }
        String timeZone = SiteFieldValidator.requireIanaTimeZone(request.timeZoneId());
        String country = SiteFieldValidator.normalizeCountryCode(request.countryCode());
        Long actorId = changePublisher.actorId(callerSubject);
        site.updateDetails(request.name().trim(), trimToNull(request.addressLine1()), trimToNull(request.addressLine2()),
                trimToNull(request.postalCode()), trimToNull(request.city()), country, timeZone, actorId);
        changePublisher.publish(OrganizationResourceType.SITE, site.getPublicId(),
                OrganizationChangeAction.UPDATED, actorId, "code=" + site.getCode());
        return SiteResponse.from(site);
    }

    void archive(UUID publicId, String reason, String callerSubject) {
        Site site = requireSite(publicId);
        if (site.isArchived()) {
            throw new OrganizationException(OrganizationException.Kind.INVALID_STATE);
        }
        boolean hasActiveChildren =
                buildingRepository.existsBySiteIdAndStatus(site.getId(), OrganizationStatus.ACTIVE)
                        || roomRepository.existsBySiteIdAndStatus(site.getId(), OrganizationStatus.ACTIVE);
        if (hasActiveChildren) {
            throw new OrganizationException(OrganizationException.Kind.HAS_ACTIVE_CHILDREN);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        site.archive(reason, actorId, Instant.now());
        changePublisher.publish(OrganizationResourceType.SITE, site.getPublicId(),
                OrganizationChangeAction.ARCHIVED, actorId, "code=" + site.getCode());
    }

    void restore(UUID publicId, String callerSubject) {
        Site site = requireSite(publicId);
        if (!site.isArchived()) {
            throw new OrganizationException(OrganizationException.Kind.INVALID_STATE);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        site.restore(actorId);
        changePublisher.publish(OrganizationResourceType.SITE, site.getPublicId(),
                OrganizationChangeAction.RESTORED, actorId, "code=" + site.getCode());
    }

    private Site requireSite(UUID publicId) {
        return siteRepository.findByPublicId(publicId)
                .orElseThrow(() -> new OrganizationException(OrganizationException.Kind.SITE_NOT_FOUND));
    }

    private static Optional<OrganizationStatus> parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OrganizationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException invalid) {
            throw new OrganizationException(OrganizationException.Kind.INVALID_FILTER);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
