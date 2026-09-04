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
     * Création d'un point de contrôle. {@code type} : {@code START},
     * {@code END}, {@code CUSTOM}, ou l'un des quatre points journaliers
     * nommés du cahier (docs/02 §16.2 — {@code MORNING_ARRIVAL},
     * {@code MORNING_BREAK_RETURN}, {@code AFTERNOON_ARRIVAL},
     * {@code AFTERNOON_BREAK_RETURN}), seuls à entrer dans le calcul de
     * demi-journée. {@code displayOrder} facultatif (le serveur attribue
     * {@code max + 1} sinon). {@code required} facultatif (défaut
     * {@code true}).
     */
    record Create(
            @NotBlank @Size(max = 120) String label,
            @NotBlank @Pattern(regexp = "START|END|CUSTOM|MORNING_ARRIVAL|MORNING_BREAK_RETURN"
                    + "|AFTERNOON_ARRIVAL|AFTERNOON_BREAK_RETURN") String type,
            Boolean required,
            @Min(0) Integer displayOrder) {
    }

    /** Annulation d'un point de contrôle : motif obligatoire. */
    record Cancel(@NotBlank @Size(max = 500) String reason) {
    }
}
