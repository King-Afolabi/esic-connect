package com.esic.connect.academic.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Requêtes de l'API année scolaire. */
final class AcademicYearRequests {

    private AcademicYearRequests() {
    }

    /** Création. {@code code} unique et immuable ; {@code endDate} après {@code startDate}. */
    record Create(
            @NotBlank @Size(max = 30)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,29}$",
                    message = "caractères autorisés : lettres, chiffres, point, tiret, tiret bas")
            String code,
            @NotBlank @Size(max = 100) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate) {
    }

    /** Modification (le code reste immuable). */
    record Update(
            @NotBlank @Size(max = 100) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate) {
    }
}
