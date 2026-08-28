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
 * Référentiel des salles (docs/04-modele-donnees.md §9.3). Rattachées à un
 * site (immuable), éventuellement à un bâtiment du même site. {@code code}
 * unique par site et immuable. Création interdite sous un site ou un
 * bâtiment archivé. Aucune suppression physique : une salle retirée est
 * archivée.
 */
@Service
@Transactional
class RoomService {

    private static final Set<String> SORTABLE = Set.of("code", "name", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "code");

    private final RoomRepository roomRepository;
    private final SiteRepository siteRepository;
    private final BuildingRepository buildingRepository;
    private final OrganizationChangePublisher changePublisher;

    RoomService(RoomRepository roomRepository, SiteRepository siteRepository,
                BuildingRepository buildingRepository, OrganizationChangePublisher changePublisher) {
        this.roomRepository = roomRepository;
        this.siteRepository = siteRepository;
        this.buildingRepository = buildingRepository;
        this.changePublisher = changePublisher;
    }

    @Transactional(readOnly = true)
    PageResponse<RoomResponse> listForSite(UUID sitePublicId, String buildingPublicId, String statusFilter,
                                           String textFilter, int page, int size, String sort) {
        Site site = requireSite(sitePublicId);
        Pageable pageable = OrganizationQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        List<Specification<Room>> specs = new ArrayList<>();
        specs.add(OrganizationSpecifications.roomHasSite(site.getId()));
        if (buildingPublicId != null && !buildingPublicId.isBlank()) {
            Building building = requireBuilding(parseUuid(buildingPublicId,
                    OrganizationException.Kind.BUILDING_NOT_FOUND));
            specs.add(OrganizationSpecifications.roomHasBuilding(building.getId()));
        }
        parseStatus(statusFilter).ifPresent(status -> specs.add(OrganizationSpecifications.roomHasStatus(status)));
        OrganizationQuerySupport.normalizeText(textFilter)
                .ifPresent(text -> specs.add(OrganizationSpecifications.roomMatchesText(text)));
        Page<Room> result = roomRepository.findAll(Specification.allOf(specs), pageable);
        return PageResponse.of(result, RoomResponse::from);
    }

    @Transactional(readOnly = true)
    RoomResponse get(UUID publicId) {
        return RoomResponse.from(requireRoom(publicId));
    }

    RoomResponse create(UUID sitePublicId, CreateRoomRequest request, String callerSubject) {
        Site site = requireSite(sitePublicId);
        if (site.isArchived()) {
            throw new OrganizationException(OrganizationException.Kind.ARCHIVED_PARENT);
        }
        Building building = resolveBuilding(request.buildingPublicId(), site);
        String code = request.code().trim();
        if (roomRepository.existsBySiteIdAndCode(site.getId(), code)) {
            throw new OrganizationException(OrganizationException.Kind.DUPLICATE_CODE);
        }
        Room room = new Room(site, building, code, request.name().trim(), request.capacity(),
                trimToNull(request.floorLabel()), trimToNull(request.staticQrReference()));
        Long actorId = changePublisher.actorId(callerSubject);
        room.markCreatedBy(actorId);
        Room saved = roomRepository.save(room);
        changePublisher.publish(OrganizationResourceType.ROOM, saved.getPublicId(),
                OrganizationChangeAction.CREATED, actorId, "code=" + code);
        return RoomResponse.from(saved);
    }

    RoomResponse update(UUID publicId, UpdateRoomRequest request, String callerSubject) {
        Room room = requireRoom(publicId);
        if (room.isArchived()) {
            throw new OrganizationException(OrganizationException.Kind.ENTITY_ARCHIVED);
        }
        Building building = resolveBuilding(request.buildingPublicId(), room.getSite());
        Long actorId = changePublisher.actorId(callerSubject);
        room.updateDetails(request.name().trim(), request.capacity(), trimToNull(request.floorLabel()),
                trimToNull(request.staticQrReference()), building, actorId);
        changePublisher.publish(OrganizationResourceType.ROOM, room.getPublicId(),
                OrganizationChangeAction.UPDATED, actorId, "code=" + room.getCode());
        return RoomResponse.from(room);
    }

    void archive(UUID publicId, String reason, String callerSubject) {
        Room room = requireRoom(publicId);
        if (room.isArchived()) {
            throw new OrganizationException(OrganizationException.Kind.INVALID_STATE);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        room.archive(reason, actorId, Instant.now());
        changePublisher.publish(OrganizationResourceType.ROOM, room.getPublicId(),
                OrganizationChangeAction.ARCHIVED, actorId, "code=" + room.getCode());
    }

    void restore(UUID publicId, String callerSubject) {
        Room room = requireRoom(publicId);
        if (!room.isArchived()) {
            throw new OrganizationException(OrganizationException.Kind.INVALID_STATE);
        }
        if (room.getSite().isArchived()
                || (room.getBuilding() != null && room.getBuilding().isArchived())) {
            throw new OrganizationException(OrganizationException.Kind.ARCHIVED_PARENT);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        room.restore(actorId);
        changePublisher.publish(OrganizationResourceType.ROOM, room.getPublicId(),
                OrganizationChangeAction.RESTORED, actorId, "code=" + room.getCode());
    }

    /** Résout le bâtiment demandé et impose sa cohérence avec le site de la salle. */
    private Building resolveBuilding(String buildingPublicId, Site site) {
        if (buildingPublicId == null || buildingPublicId.isBlank()) {
            return null;
        }
        Building building = requireBuilding(parseUuid(buildingPublicId, OrganizationException.Kind.BUILDING_NOT_FOUND));
        if (!building.getSite().getId().equals(site.getId())) {
            throw new OrganizationException(OrganizationException.Kind.BUILDING_SITE_MISMATCH);
        }
        if (building.isArchived()) {
            throw new OrganizationException(OrganizationException.Kind.ARCHIVED_PARENT);
        }
        return building;
    }

    private Site requireSite(UUID publicId) {
        return siteRepository.findByPublicId(publicId)
                .orElseThrow(() -> new OrganizationException(OrganizationException.Kind.SITE_NOT_FOUND));
    }

    private Building requireBuilding(UUID publicId) {
        return buildingRepository.findByPublicId(publicId)
                .orElseThrow(() -> new OrganizationException(OrganizationException.Kind.BUILDING_NOT_FOUND));
    }

    private Room requireRoom(UUID publicId) {
        return roomRepository.findByPublicId(publicId)
                .orElseThrow(() -> new OrganizationException(OrganizationException.Kind.ROOM_NOT_FOUND));
    }

    private static UUID parseUuid(String value, OrganizationException.Kind notFound) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException notAUuid) {
            throw new OrganizationException(notFound);
        }
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
