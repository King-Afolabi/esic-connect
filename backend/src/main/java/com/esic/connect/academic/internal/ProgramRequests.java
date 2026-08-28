package com.esic.connect.academic.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Requêtes de l'API formation. */
final class ProgramRequests {

    private ProgramRequests() {
    }

    /** Création. {@code code} unique et immuable ; {@code programType} : BTS/BACHELOR/MASTER/OTHER. */
    record Create(
            @NotBlank @Size(max = 50)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$",
                    message = "caractères autorisés : lettres, chiffres, point, tiret, tiret bas")
            String code,
            @NotBlank @Size(max = 191) String name,
            @NotBlank @Size(max = 50) String programType,
            @Size(max = 5000) String description) {
    }

    /** Modification (le code reste immuable). */
    record Update(
            @NotBlank @Size(max = 191) String name,
            @NotBlank @Size(max = 50) String programType,
            @Size(max = 5000) String description) {
    }
}
