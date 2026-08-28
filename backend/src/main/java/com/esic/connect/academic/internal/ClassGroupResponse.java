package com.esic.connect.academic.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Vue API d'une classe/groupe — références par identifiant public
 * uniquement. Le {@code sitePublicId} est résolu par le service via le
 * port {@code organization.SiteDirectory} (jamais exposé en valeur SQL).
 */
record ClassGroupResponse(
        UUID publicId,
        UUID promotionPublicId,
        UUID programLevelPublicId,
        UUID sitePublicId,
        String code,
        String name,
        Integer capacity,
        AcademicStatus status,
        Instant archivedAt,
        String archiveReason,
        Instant createdAt,
        Instant updatedAt) {

    static ClassGroupResponse from(ClassGroup classGroup, UUID sitePublicId) {
        return new ClassGroupResponse(
                classGroup.getPublicId(),
                classGroup.getPromotion().getPublicId(),
                classGroup.getProgramLevel().getPublicId(),
                sitePublicId,
                classGroup.getCode(),
                classGroup.getName(),
                classGroup.getCapacity(),
                classGroup.getStatus(),
                classGroup.getArchivedAt(),
                classGroup.getArchiveReason(),
                classGroup.getCreatedAt(),
                classGroup.getUpdatedAt());
    }
}
