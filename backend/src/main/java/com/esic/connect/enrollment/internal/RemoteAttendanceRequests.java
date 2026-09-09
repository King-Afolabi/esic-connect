package com.esic.connect.enrollment.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Requêtes de l'API du suivi à distance individuel (EF-ENR-004). */
final class RemoteAttendanceRequests {

    private RemoteAttendanceRequests() {
    }

    /**
     * Autorisation d'un apprenant à suivre à distance (docs/02 §15.3).
     *
     * <p>{@code classGroupPublicId} facultatif : absent, l'autorisation
     * est générale ; présent, elle ne vaut que pour cette classe.
     * {@code validUntil} facultatif : absente, l'autorisation est ouverte —
     * ce que le cahier appelle « pour l'année ».
     */
    record Authorize(
            @NotBlank @Size(max = 64) String studentUserPublicId,
            @Size(max = 64) String classGroupPublicId,
            @NotBlank @Size(max = 500) String reason,
            @NotNull LocalDate validFrom,
            LocalDate validUntil) {
    }

    /** Révocation : motif obligatoire, l'autorisation n'est pas supprimée. */
    record Revoke(@NotBlank @Size(max = 500) String reason) {
    }
}
