package com.esic.connect.coursesession.internal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Requêtes de l'API des points de contrôle d'émargement (V10). */
final class CheckpointRequests {

    private CheckpointRequests() {
    }

    /**
     * Création d'un point de contrôle. {@code type} : {@code START} |
     * {@code END} | {@code CUSTOM}. {@code displayOrder} facultatif (le
     * serveur attribue {@code max + 1} sinon). {@code required} facultatif
     * (défaut {@code true}).
     */
    record Create(
            @NotBlank @Size(max = 120) String label,
            @NotBlank @Pattern(regexp = "START|END|CUSTOM") String type,
            Boolean required,
            @Min(0) Integer displayOrder) {
    }

    /** Annulation d'un point de contrôle : motif obligatoire. */
    record Cancel(@NotBlank @Size(max = 500) String reason) {
    }
}
