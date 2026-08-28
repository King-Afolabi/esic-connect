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
 * Référentiel des bâtiments (docs/04-modele-donnees.md §9.2). Rattachés à
 * un site, {@code code} unique par site et immuable. Création interdite
 * sous un site archivé ; archivage refusé tant qu'il reste des salles
 * actives.
 */
@Service
@Transactional
class BuildingService {

    private static final Set<String> SORTABLE = Set.of("code", "name", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "code");

    private final BuildingRepository buildingRepository;
    private final SiteRepository siteRepository;
    private final RoomRepository roomRepository;
    private final OrganizationChangePublisher changePublisher;

    BuildingService(BuildingRepository buildingRepository, SiteRepository siteRepository,
                    RoomRepository roomRepository, OrganizationChangePublisher changePublisher) {
        this.buildingRepository = buildingRepository;
        this.siteRepository = siteRepository;
        this.roomRepository = roomRepository;
        this.changePublisher = changePublisher;
    }

    @Transactional(readOnly = true)
    PageResponse<BuildingResponse> listForSite(UUID sitePublicId, String statusFilter, String textFilter,
                                               int page, int size, String sort) {
        Site site = requireSite(sitePublicId);
        Pageable pageable = OrganizationQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        List<Specification<Building>> specs = new ArrayList<>();
        specs.add(OrganizationSpecifications.buildingHasSite(site.getId()));
        parseStatus(statusFilter).ifPresent(status -> specs.add(OrganizationSpecifications.buildingHasStatus(status)));
        OrganizationQuerySupport.normalizeText(textFilter)
                .ifPresent(text -> specs.add(OrganizationSpecifications.buildingMatchesText(text)));
        Page<Building> result = buildingRepository.findAll(Specification.allOf(specs), pageable);
        return PageResponse.of(result, BuildingResponse::from);
    }

    @Transactional(readOnly = true)
    BuildingResponse get(UUID publicId) {
        return BuildingResponse.from(requireBuilding(publicId));
    }

    BuildingResponse create(UUID sitePublicId, CreateBuildingRequest request, String callerSubject) {
        Site site = requireSite(sitePublicId);
        if (site.isArchived()) {
            throw new OrganizationException(OrganizationException.Kind.ARCHIVED_PARENT);
        }
        String code = request.code().trim();
        if (buildingRepository.existsBySiteIdAndCode(site.getId(), code)) {
            throw new OrganizationException(OrganizationException.Kind.DUPLICATE_CODE);
        }
        Building building = new Building(site, code, request.name().trim());
        Long actorId = changePublisher.actorId(callerSubject);
        building.markCreatedBy(actorId);
        Building saved = buildingRepository.save(building);
        changePublisher.publish(OrganizationResourceType.BUILDING, saved.getPublicId(),
                OrganizationChangeAction.CREATED, actorId, "code=" + code);
        return BuildingResponse.from(saved);
    }

    BuildingResponse update(UUID publicId, UpdateBuildingRequest request, String callerSubject) {
        Building building = requireBuilding(publicId);
        if (building.isArchived()) {
            throw new OrganizationException(OrganizationException.Kind.ENTITY_ARCHIVED);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        building.rename(request.name().trim(), actorId);
        changePublisher.publish(OrganizationResourceType.BUILDING, building.getPublicId(),
                OrganizationChangeAction.UPDATED, actorId, "code=" + building.getCode());
        return BuildingResponse.from(building);
    }

    void archive(UUID publicId, String reason, String callerSubject) {
        Building building = requireBuilding(publicId);
        if (building.isArchived()) {
            throw new OrganizationException(OrganizationException.Kind.INVALID_STATE);
        }
        if (roomRepository.existsByBuildingIdAndStatus(building.getId(), OrganizationStatus.ACTIVE)) {
            throw new OrganizationException(OrganizationException.Kind.HAS_ACTIVE_CHILDREN);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        building.archive(reason, actorId, Instant.now());
        changePublisher.publish(OrganizationResourceType.BUILDING, building.getPublicId(),
                OrganizationChangeAction.ARCHIVED, actorId, "code=" + building.getCode());
    }

    void restore(UUID publicId, String callerSubject) {
        Building building = requireBuilding(publicId);
        if (!building.isArchived()) {
            throw new OrganizationException(OrganizationException.Kind.INVALID_STATE);
        }
        if (building.getSite().isArchived()) {
            throw new OrganizationException(OrganizationException.Kind.ARCHIVED_PARENT);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        building.restore(actorId);
        changePublisher.publish(OrganizationResourceType.BUILDING, building.getPublicId(),
                OrganizationChangeAction.RESTORED, actorId, "code=" + building.getCode());
    }

    private Site requireSite(UUID publicId) {
        return siteRepository.findByPublicId(publicId)
                .orElseThrow(() -> new OrganizationException(OrganizationException.Kind.SITE_NOT_FOUND));
    }

    private Building requireBuilding(UUID publicId) {
        return buildingRepository.findByPublicId(publicId)
                .orElseThrow(() -> new OrganizationException(OrganizationException.Kind.BUILDING_NOT_FOUND));
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
}
