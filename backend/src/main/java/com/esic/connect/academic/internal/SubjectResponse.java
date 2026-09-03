package com.esic.connect.academic.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Matière telle qu'exposée par l'API (EF-ACA-006).
 *
 * @param programs formations de rattachement, réduites à leur identité
 *                 lisible : l'écran des matières n'a pas besoin du détail
 *                 des formations, et le transmettre créerait un couplage
 *                 inutile
 */
public record SubjectResponse(
        UUID id,
        String code,
        String name,
        String description,
        Integer hourlyVolume,
        String status,
        List<ProgramRef> programs,
        Instant createdAt,
        Instant updatedAt) {

    /** Identité minimale d'une formation rattachée. */
    public record ProgramRef(UUID id, String code, String name) {
    }
}
