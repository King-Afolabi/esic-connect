package com.esic.connect.enrollment.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Requêtes de l'API des inscriptions. */
final class EnrollmentRequests {

    private EnrollmentRequests() {
    }

    /**
     * Inscription initiale d'un apprenant dans une classe.
     * {@code startDate} par défaut = aujourd'hui (horloge injectée).
     */
    record Enroll(
            @NotBlank @Size(max = 40) String studentProfilePublicId,
            @NotBlank @Size(max = 40) String classGroupPublicId,
            LocalDate startDate) {
    }

    /**
     * Changement de classe (docs/04 §13.2). L'inscription courante est
     * clôturée en {@code TRANSFERRED} à {@code effectiveDate} (par défaut
     * aujourd'hui, ≥ sa date de début) et une nouvelle inscription
     * {@code ACTIVE} est créée, {@code previous_enrollment_id} renseigné.
     * {@code reason} obligatoire (opération auditée).
     */
    record Transfer(
            @NotBlank @Size(max = 40) String classGroupPublicId,
            @NotBlank @Size(max = 500) String reason,
            LocalDate effectiveDate) {
    }

    /**
     * Clôture d'une inscription active (fin de cursus / départ).
     * {@code status} : {@code COMPLETED} ou {@code WITHDRAWN} ;
     * {@code reason} obligatoire ; {@code effectiveDate} par défaut
     * aujourd'hui, doit être ≥ la date de début de l'inscription.
     */
    record Close(
            @NotBlank @Pattern(regexp = "COMPLETED|WITHDRAWN",
                    message = "status attendu : COMPLETED ou WITHDRAWN") String status,
            @NotBlank @Size(max = 500) String reason,
            LocalDate effectiveDate) {
    }
}
