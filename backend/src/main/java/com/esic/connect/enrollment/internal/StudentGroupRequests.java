package com.esic.connect.enrollment.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Contrats d'écriture des groupes temporaires (EF-ACA-007). */
public final class StudentGroupRequests {

    private StudentGroupRequests() {
    }

    /**
     * @param code               unique dans l'année scolaire, immuable ensuite
     * @param programPublicId    formation de rattachement — porte le périmètre pédagogique
     * @param subjectPublicId    matière concernée ; facultatif (un groupe projet n'en a pas)
     */
    public record Create(
            @NotBlank @Size(max = 40)
            @Pattern(regexp = "[A-Za-z0-9._-]+",
                    message = "Le code ne peut contenir que lettres, chiffres, point, tiret ou souligné.")
            String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank String academicYearPublicId,
            @NotBlank String programPublicId,
            String subjectPublicId,
            LocalDate startsOn,
            LocalDate endsOn) {
    }

    /** Ni le code, ni l'année, ni la formation ne changent : ils fixent l'identité du groupe. */
    public record Update(
            @NotBlank @Size(max = 200) String name,
            String subjectPublicId,
            LocalDate startsOn,
            LocalDate endsOn) {
    }

    /**
     * Ajout de membres. Les inscriptions peuvent venir de classes
     * différentes : c'est tout l'intérêt d'un groupe (docs/02 §6.5).
     */
    public record AddMembers(@Size(min = 1, max = 500) List<String> enrollmentPublicIds) {
    }
}
