package com.esic.connect.alternation.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Requêtes de l'API des exceptions individuelles de calendrier. */
final class StudentExceptionRequests {

    private StudentExceptionRequests() {
    }

    /**
     * Création. {@code enrollmentPublicId} doit désigner une inscription
     * {@code ACTIVE}. {@code type} validé ici puis revérifié côté service.
     * {@code startAt} / {@code endAt} sont des instants (ISO-8601 avec
     * fuseau) ; {@code endAt} doit être strictement postérieur.
     * {@code timeZoneId} = identifiant IANA (ex. {@code Europe/Paris}).
     * {@code reason} obligatoire, borné. Deux exceptions ACTIVE de même
     * type ne peuvent pas se chevaucher pour une même inscription.
     */
    record Create(
            @NotBlank @Size(max = 40) String enrollmentPublicId,
            @NotBlank @Pattern(regexp = "REMOTE_ALLOWED|ON_SITE_REQUIRED|COMPANY_PERIOD|VALIDATED_UNAVAILABILITY",
                    message = "type d'exception invalide") String type,
            @NotNull Instant startAt,
            @NotNull Instant endAt,
            @NotBlank @Size(max = 64) String timeZoneId,
            @NotBlank @Size(max = 500) String reason) {
    }

    /** Annulation : motif obligatoire (historique conservé). */
    record Cancel(
            @NotBlank @Size(max = 500) String reason) {
    }
}
