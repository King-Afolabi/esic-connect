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
        UUID academicYearPublicId,
        String academicYearCode,
        Integer capacity,
        AcademicStatus status,
        Instant archivedAt,
        String archiveReason,
        Instant createdAt,
        Instant updatedAt) {

    static ClassGroupResponse from(ClassGroup classGroup, UUID sitePublicId) {
        // `promotion` / `promotion.academicYear` sont chargées EAGER
        // (Promotion, ClassGroup) : aucune requête supplémentaire ici.
        return new ClassGroupResponse(
                classGroup.getPublicId(),
                classGroup.getPromotion().getPublicId(),
                classGroup.getProgramLevel().getPublicId(),
                sitePublicId,
                classGroup.getCode(),
                classGroup.getName(),
                classGroup.getPromotion().getAcademicYear().getPublicId(),
                classGroup.getPromotion().getAcademicYear().getCode(),
                classGroup.getCapacity(),
                classGroup.getStatus(),
                classGroup.getArchivedAt(),
                classGroup.getArchiveReason(),
                classGroup.getCreatedAt(),
                classGroup.getUpdatedAt());
    }
}
