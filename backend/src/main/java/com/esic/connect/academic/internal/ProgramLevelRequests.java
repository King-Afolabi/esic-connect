package com.esic.connect.academic.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Requêtes de l'API niveau. */
final class ProgramLevelRequests {

    private ProgramLevelRequests() {
    }

    /** Création sous une formation. {@code code} unique par formation et immuable. */
    record Create(
            @NotBlank @Size(max = 50)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$",
                    message = "caractères autorisés : lettres, chiffres, point, tiret, tiret bas")
            String code,
            @NotBlank @Size(max = 100) String name,
            @NotNull @Positive @Max(9999) Short sequenceNumber) {
    }

    /** Modification (le code et la formation restent immuables). */
    record Update(
            @NotBlank @Size(max = 100) String name,
            @NotNull @Positive @Max(9999) Short sequenceNumber) {
    }
}
