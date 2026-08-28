package com.esic.connect.organization.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Création d'une plage réseau autorisée pour un site. {@code cidr} en
 * notation IPv4 ou IPv6 (validée côté serveur) ; immuable ensuite.
 */
record CreateNetworkRangeRequest(
        @NotBlank @Size(max = 50) String cidr,
        @NotBlank @Size(max = 100) String label) {
}
