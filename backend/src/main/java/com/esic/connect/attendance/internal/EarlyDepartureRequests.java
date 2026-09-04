package com.esic.connect.attendance.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Requêtes du dossier de départ anticipé (EF-ATT-013 ; docs/02 §16.13). */
final class EarlyDepartureRequests {

    private EarlyDepartureRequests() {
    }

    /**
     * Signalement par l'apprenant. L'inscription n'est pas un paramètre :
     * le serveur la déduit du JWT et de la séance. La laisser au client
     * permettrait de signaler le départ de quelqu'un d'autre.
     */
    record Declare(
            @NotBlank @Size(max = 64) String sessionPublicId,
            @NotNull Instant departureAt,
            @NotBlank @Size(max = 500) String reason) {
    }

    /**
     * Transmission au responsable pédagogique. {@code opinion} facultatif :
     * le formateur peut transmettre sans se prononcer.
     */
    record Forward(
            @Pattern(regexp = "FAVOURABLE|UNFAVOURABLE") String opinion,
            @Size(max = 500) String comment) {
    }

    /**
     * Décision finale. Le commentaire est obligatoire dans les deux sens :
     * une acceptation sans motif est aussi peu relisible qu'un refus sans
     * motif (RG-060 étendu au dossier).
     */
    record Decide(
            @NotNull Boolean accepted,
            @NotBlank @Size(max = 500) String comment) {
    }
}
