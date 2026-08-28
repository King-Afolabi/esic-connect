package com.esic.connect.organization.internal;

import java.time.Instant;
import java.util.UUID;

/** Vue API d'un site — jamais d'identifiant SQL interne. */
record SiteResponse(
        UUID publicId,
        String code,
        String name,
        String addressLine1,
        String addressLine2,
        String postalCode,
        String city,
        String countryCode,
        String timeZoneId,
        OrganizationStatus status,
        Instant archivedAt,
        String archiveReason,
        Instant createdAt,
        Instant updatedAt) {

    static SiteResponse from(Site site) {
        return new SiteResponse(
                site.getPublicId(),
                site.getCode(),
                site.getName(),
                site.getAddressLine1(),
                site.getAddressLine2(),
                site.getPostalCode(),
                site.getCity(),
                site.getCountryCode(),
                site.getTimeZoneId(),
                site.getStatus(),
                site.getArchivedAt(),
                site.getArchiveReason(),
                site.getCreatedAt(),
                site.getUpdatedAt());
    }
}
