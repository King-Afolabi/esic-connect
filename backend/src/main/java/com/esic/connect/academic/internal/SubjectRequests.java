package com.esic.connect.academic.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Contrats d'écriture des matières (EF-ACA-006). */
public final class SubjectRequests {

    private SubjectRequests() {
    }

    /**
     * @param code           code fonctionnel, unique et <strong>immuable</strong>
     *                       après création : il est repris dans les
     *                       fichiers de planning importés
     * @param programPublicIds formations de rattachement ; facultatif — une
     *                       matière transverse n'en a pas
     */
    public record Create(
            @NotBlank @Size(max = 40)
            @Pattern(regexp = "[A-Za-z0-9._-]+",
                    message = "Le code ne peut contenir que lettres, chiffres, point, tiret ou souligné.")
            String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @Positive Integer hourlyVolume,
            List<String> programPublicIds) {
    }

    /** Le code n'est pas modifiable : il sert de référence dans les imports. */
    public record Update(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @Positive Integer hourlyVolume,
            List<String> programPublicIds) {
    }
}
