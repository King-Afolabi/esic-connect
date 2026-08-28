package com.esic.connect.identity.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps commun des opérations de cycle de vie (suspension, réactivation,
 * archivage, retrait de rôle). Le motif est obligatoire : il alimente la
 * piste d'audit (cahier §9.5, §30.1/§30.2).
 */
record AccountActionRequest(
        @NotBlank @Size(max = 500) String reason) {
}
