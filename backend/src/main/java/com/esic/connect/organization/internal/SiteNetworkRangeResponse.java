package com.esic.connect.organization.internal;

import java.time.Instant;
import java.util.UUID;

/** Vue API d'une plage réseau de site — aucune adresse IP d'utilisateur. */
record SiteNetworkRangeResponse(
        UUID publicId,
        UUID sitePublicId,
        String cidr,
        String label,
        boolean active,
        Instant validFrom,
        Instant validUntil,
        Instant createdAt,
        Instant updatedAt) {

    static SiteNetworkRangeResponse from(SiteNetworkRange range) {
        return new SiteNetworkRangeResponse(
                range.getPublicId(),
                range.getSite().getPublicId(),
                range.getCidr(),
                range.getLabel(),
                range.isActive(),
                range.getValidFrom(),
                range.getValidUntil(),
                range.getCreatedAt(),
                range.getUpdatedAt());
    }
}
