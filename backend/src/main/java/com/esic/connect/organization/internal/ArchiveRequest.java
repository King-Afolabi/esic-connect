package com.esic.connect.organization.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps commun d'une opération d'archivage (site, bâtiment, salle). Le
 * motif est obligatoire : il alimente la piste d'audit (cahier §30.2).
 */
record ArchiveRequest(
        @NotBlank @Size(max = 500) String reason) {
}
