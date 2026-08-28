package com.esic.connect.academic.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Requêtes de l'API promotion. */
final class PromotionRequests {

    private PromotionRequests() {
    }

    /**
     * Création. {@code code} unique pour (formation, année) et immuable ;
     * les rattachements formation/année sont immuables. Période optionnelle,
     * incluse dans celle de l'année scolaire si renseignée.
     */
    record Create(
            @NotBlank @Size(max = 36) String programPublicId,
            @NotBlank @Size(max = 36) String academicYearPublicId,
            @NotBlank @Size(max = 80)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$",
                    message = "caractères autorisés : lettres, chiffres, point, tiret, tiret bas")
            String code,
            @NotBlank @Size(max = 191) String name,
            LocalDate startDate,
            LocalDate endDate) {
    }

    /** Modification (code et rattachements immuables). */
    record Update(
            @NotBlank @Size(max = 191) String name,
            LocalDate startDate,
            LocalDate endDate) {
    }
}
