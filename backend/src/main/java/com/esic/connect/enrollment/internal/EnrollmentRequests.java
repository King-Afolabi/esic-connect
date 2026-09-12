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
     * {@code studentUserPublicId} désigne directement le
     * <strong>compte</strong> apprenant (rôle {@code STUDENT} actif, non
     * archivé) — il n'existe plus de {@code student_profile} intermédiaire
     * (refonte 2026-09). {@code startDate} par défaut = aujourd'hui
     * (horloge injectée). {@code workStudy} / {@code companyName}
     * décrivent la situation d'alternance pendant cette inscription
     * (ex-{@code student_profile.work_study} / {@code company_name}) —
     * {@code workStudy} par défaut {@code false}, {@code companyName}
     * facultative.
     */
    record Enroll(
            @NotBlank @Size(max = 40) String studentUserPublicId,
            @NotBlank @Size(max = 40) String classGroupPublicId,
            LocalDate startDate,
            Boolean workStudy,
            @Size(max = 191) String companyName) {
    }

    /**
     * Changement de classe (docs/04 §13.2). L'inscription courante est
     * clôturée en {@code TRANSFERRED} avec {@code end_date = effectiveDate}
     * (borne inclusive ; {@code effectiveDate} par défaut aujourd'hui, ≥ sa
     * date de début). La nouvelle inscription {@code ACTIVE} débute le
     * lendemain ({@code effectiveDate + 1 jour}) — les deux périodes ne se
     * chevauchent pas — avec {@code previous_enrollment_id} renseigné.
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
