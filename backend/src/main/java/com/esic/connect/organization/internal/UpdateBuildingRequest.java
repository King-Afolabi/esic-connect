package com.esic.connect.organization.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Modification d'un bâtiment (nom uniquement ; code immuable). */
record UpdateBuildingRequest(
        @NotBlank @Size(max = 150) String name) {
}
