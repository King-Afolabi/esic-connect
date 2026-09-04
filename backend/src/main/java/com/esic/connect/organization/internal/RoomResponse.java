package com.esic.connect.organization.internal;

import java.time.Instant;
import java.util.UUID;

/** Vue API d'une salle — références par identifiant public uniquement. */
record RoomResponse(
        UUID publicId,
        UUID sitePublicId,
        UUID buildingPublicId,
        String code,
        String name,
        Integer capacity,
        String floorLabel,
        /**
         * Jeton du QR fixe (EF-ORG-003). Exposé aux seuls rôles qui
         * peuvent l'imprimer : c'est un secret d'affiche, pas un
         * identifiant public de salle.
         */
        String staticQrReference,
        Instant staticQrIssuedAt,
        OrganizationStatus status,
        Instant archivedAt,
        String archiveReason,
        Instant createdAt,
        Instant updatedAt) {

    static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getPublicId(),
                room.getSite().getPublicId(),
                room.getBuilding() != null ? room.getBuilding().getPublicId() : null,
                room.getCode(),
                room.getName(),
                room.getCapacity(),
                room.getFloorLabel(),
                room.getStaticQrReference(),
                room.getStaticQrIssuedAt(),
                room.getStatus(),
                room.getArchivedAt(),
                room.getArchiveReason(),
                room.getCreatedAt(),
                room.getUpdatedAt());
    }
}
