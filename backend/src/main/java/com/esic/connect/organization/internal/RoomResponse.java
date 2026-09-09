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
         * Date d'émission du QR fixe (EF-ORG-003), ou {@code null} si
         * aucun n'a été émis. Sert d'indicateur « affiche disponible »
         * dans la liste des salles. Le <strong>jeton</strong> lui-même
         * n'apparaît <strong>jamais</strong> ici : c'est un secret
         * d'affiche, renvoyé uniquement par {@code GET
         * /rooms/{id}/static-qr} ({@link RoomStaticQrView}).
         */
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
                room.getStaticQrIssuedAt(),
                room.getStatus(),
                room.getArchivedAt(),
                room.getArchiveReason(),
                room.getCreatedAt(),
                room.getUpdatedAt());
    }
}
