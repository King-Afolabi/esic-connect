package com.esic.connect.academic.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Requêtes de l'API des affectations de responsable pédagogique. */
final class PedagogicalAssignmentRequests {

    private PedagogicalAssignmentRequests() {
    }

    /**
     * Création. {@code programPublicId} et {@code userPublicId} en forme
     * UUID ; {@code type} : {@code PRIMARY_MANAGER} ou {@code DELEGATE}
     * (validé ici, revérifié côté service). {@code validFrom} par défaut
     * = aujourd'hui ; {@code validUntil} facultatif (borne inclusive,
     * affectation ouverte si absente). {@code reason} facultatif.
     */
    record Create(
            @NotBlank @Size(max = 40) String programPublicId,
            @NotBlank @Size(max = 40) String userPublicId,
            @NotBlank @Pattern(regexp = "PRIMARY_MANAGER|DELEGATE",
                    message = "type attendu : PRIMARY_MANAGER ou DELEGATE") String type,
            LocalDate validFrom,
            LocalDate validUntil,
            @Size(max = 500) String reason) {
    }

    /**
     * Clôture. {@code reason} obligatoire ; {@code effectiveDate}
     * facultative (par défaut aujourd'hui), doit être ≥ {@code validFrom}
     * de l'affectation ; devient la borne {@code validUntil}.
     */
    record Close(
            @NotBlank @Size(max = 500) String reason,
            LocalDate effectiveDate) {
    }
}
