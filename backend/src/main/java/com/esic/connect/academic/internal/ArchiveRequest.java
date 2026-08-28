package com.esic.connect.academic.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps commun d'une opération d'archivage (année, formation, niveau,
 * promotion, classe). Le motif est obligatoire : il alimente la piste
 * d'audit (cahier §30.2).
 */
record ArchiveRequest(
        @NotBlank @Size(max = 500) String reason) {
}
