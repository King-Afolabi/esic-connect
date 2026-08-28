package com.esic.connect.academic.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Requêtes de l'API classe/groupe. */
final class ClassGroupRequests {

    private ClassGroupRequests() {
    }

    /**
     * Création. {@code code} unique dans la promotion et immuable ; les
     * rattachements promotion/niveau/site sont immuables. Le niveau doit
     * appartenir à la formation de la promotion.
     */
    record Create(
            @NotBlank @Size(max = 36) String promotionPublicId,
            @NotBlank @Size(max = 36) String programLevelPublicId,
            @NotBlank @Size(max = 36) String sitePublicId,
            @NotBlank @Size(max = 80)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$",
                    message = "caractères autorisés : lettres, chiffres, point, tiret, tiret bas")
            String code,
            @NotBlank @Size(max = 191) String name,
            @Positive Integer capacity) {
    }

    /** Modification (code et rattachements immuables). */
    record Update(
            @NotBlank @Size(max = 191) String name,
            @Positive Integer capacity) {
    }
}
