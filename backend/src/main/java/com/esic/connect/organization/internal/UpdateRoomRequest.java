package com.esic.connect.organization.internal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Modification d'une salle. Remplace l'ensemble des champs modifiables ;
 * {@code buildingPublicId} absent (ou vide) détache la salle de tout
 * bâtiment. Le {@code code} et le rattachement au site sont immuables.
 */
record UpdateRoomRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 36) String buildingPublicId,
        @Min(1) Integer capacity,
        @Size(max = 50) String floorLabel,
        @Size(max = 255) String staticQrReference) {
}
