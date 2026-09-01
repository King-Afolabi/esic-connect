package com.esic.connect.coursesession.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Requêtes de l'API des séances. */
final class CourseSessionRequests {

    private CourseSessionRequests() {
    }

    /**
     * Création d'une séance exceptionnelle.
     *
     * <p>{@code teacherPublicId} : compte {@code TEACHER} actif ;
     * {@code classPublicIds} : au moins une classe ; {@code reason} :
     * motif obligatoire (séance exceptionnelle) ; {@code timeZoneId} :
     * fuseau IANA de saisie ; {@code startsAt} / {@code endsAt} : instants
     * absolus (le back-end reste l'autorité sur la cohérence de période).
     */
    record Create(
            @NotBlank String teacherPublicId,
            @NotEmpty List<@NotBlank String> classPublicIds,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotBlank @Size(max = 64) String timeZoneId,
            @NotBlank @Size(max = 500) String reason,
            @Size(max = 191) String title) {
    }

    /**
     * Annulation d'une séance (G1-C ; EF-SES-004). {@code reason} : motif
     * obligatoire et borné — la validation fine (vide après trim) est
     * refaite côté service.
     */
    record Cancel(
            @NotBlank @Size(max = 500) String reason) {
    }

    /**
     * Affectation d'un remplaçant sur une séance (G1-C.2 ; EF-SES-005).
     *
     * <p>{@code substituteTeacherPublicId} : compte {@code TEACHER} actif,
     * différent du formateur principal ; {@code reason} : motif
     * obligatoire et borné ; {@code validFrom} / {@code validUntil} :
     * période de validité (fin strictement postérieure au début).
     */
    record CreateSubstitution(
            @NotBlank String substituteTeacherPublicId,
            @NotBlank @Size(max = 500) String reason,
            @NotNull Instant validFrom,
            @NotNull Instant validUntil) {
    }
}
