package com.esic.connect.organization.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Création d'un site. Le {@code code} est un identifiant fonctionnel
 * stable et immuable ; les autres champs restent modifiables ensuite.
 */
record CreateSiteRequest(
        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$",
                message = "caractères autorisés : lettres, chiffres, point, tiret, tiret bas")
        String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 20) String postalCode,
        @Size(max = 100) String city,
        @Size(max = 2) String countryCode,
        @NotBlank @Size(max = 64) String timeZoneId) {
}
