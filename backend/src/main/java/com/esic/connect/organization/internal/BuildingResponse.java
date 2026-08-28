package com.esic.connect.organization.internal;

import java.time.Instant;
import java.util.UUID;

/** Vue API d'un bâtiment — références par identifiant public uniquement. */
record BuildingResponse(
        UUID publicId,
        UUID sitePublicId,
        String code,
        String name,
        OrganizationStatus status,
        Instant archivedAt,
        String archiveReason,
        Instant createdAt,
        Instant updatedAt) {

    static BuildingResponse from(Building building) {
        return new BuildingResponse(
                building.getPublicId(),
                building.getSite().getPublicId(),
                building.getCode(),
                building.getName(),
                building.getStatus(),
                building.getArchivedAt(),
                building.getArchiveReason(),
                building.getCreatedAt(),
                building.getUpdatedAt());
    }
}
