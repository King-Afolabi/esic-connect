package com.esic.connect.organization.internal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Création d'une salle sous un site. {@code code} unique par site, immuable.
 * {@code buildingPublicId} est optionnel ; s'il est fourni, le bâtiment
 * doit appartenir au même site.
 */
record CreateRoomRequest(
        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$",
                message = "caractères autorisés : lettres, chiffres, point, tiret, tiret bas")
        String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 36) String buildingPublicId,
        @Min(1) Integer capacity,
        @Size(max = 50) String floorLabel,
        @Size(max = 255) String staticQrReference) {
}
