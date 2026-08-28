package com.esic.connect.organization.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Création d'un bâtiment sous un site. {@code code} unique par site, immuable. */
record CreateBuildingRequest(
        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$",
                message = "caractères autorisés : lettres, chiffres, point, tiret, tiret bas")
        String code,
        @NotBlank @Size(max = 150) String name) {
}
