package com.esic.connect.organization.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Modification d'un site. Remplace l'ensemble des champs modifiables
 * (le {@code code} n'en fait jamais partie).
 */
record UpdateSiteRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 20) String postalCode,
        @Size(max = 100) String city,
        @Size(max = 2) String countryCode,
        @NotBlank @Size(max = 64) String timeZoneId) {
}
