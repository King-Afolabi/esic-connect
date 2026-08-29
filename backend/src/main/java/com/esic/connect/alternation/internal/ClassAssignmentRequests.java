package com.esic.connect.alternation.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Requêtes de l'API des affectations de rythme à une classe. */
final class ClassAssignmentRequests {

    private ClassAssignmentRequests() {
    }

    /**
     * Affectation d'un rythme à une classe. {@code cycleStartDate}
     * obligatoire (ancre du cycle). {@code validFrom} obligatoire ;
     * {@code validUntil} facultatif (borne inclusive, affectation ouverte
     * si absente). Les bornes ne doivent chevaucher aucune affectation
     * ACTIVE existante de la classe (adjacence stricte autorisée).
     */
    record Assign(
            @NotBlank @Size(max = 40) String classGroupPublicId,
            @NotBlank @Size(max = 40) String workStudyPatternPublicId,
            @NotNull LocalDate cycleStartDate,
            @NotNull LocalDate validFrom,
            LocalDate validUntil) {
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
