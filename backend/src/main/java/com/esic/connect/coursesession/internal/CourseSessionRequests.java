package com.esic.connect.coursesession.internal;

import com.esic.connect.coursesession.SessionAttendanceMode;
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
     * {@code classPublicIds} : au moins une classe — <strong>plusieurs
     * classes qui suivent la séance ensemble se déclarent ici en une
     * seule fois</strong>, ce n'est jamais une séance par classe ;
     * {@code subjectPublicId} : matière enseignée, facultative (une
     * séance sans matière précisée reste valide) ; {@code roomPublicId} :
     * salle, facultative — sert aussi au contrôle anti-double-réservation
     * (deux séances ne peuvent occuper la même salle sur des horaires qui
     * se chevauchent) ; {@code reason} : motif obligatoire (séance
     * exceptionnelle) ; {@code timeZoneId} : fuseau IANA de saisie ;
     * {@code startsAt} / {@code endsAt} : instants absolus (le back-end
     * reste l'autorité sur la cohérence de période et sur l'absence de
     * double réservation du formateur — RG-105).
     */
    record Create(
            @NotBlank String teacherPublicId,
            @Size(max = 40) String subjectPublicId,
            @Size(max = 40) String roomPublicId,
            @NotEmpty List<@NotBlank String> classPublicIds,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotBlank @Size(max = 64) String timeZoneId,
            @NotBlank @Size(max = 500) String reason,
            @Size(max = 191) String title,
            /**
             * Modalité d'enseignement (docs/02 §15). Absente ⇒
             * {@code ON_SITE} : le présentiel reste le défaut, il n'est
             * jamais déduit d'un lien renseigné par erreur.
             */
            SessionAttendanceMode attendanceMode,
            @Size(max = 500) String remoteLink) {
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
     * Affectation ou changement de salle a posteriori (V35) — la décision
     * de salle est souvent prise au dernier moment, bien après la
     * création de la séance.
     */
    record AssignRoom(
            @NotBlank @Size(max = 40) String roomPublicId) {
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

    /**
     * Report d'une séance annulée (EF-SES-007 ; docs/02 §14.4).
     *
     * <p>Le report crée une séance <strong>nouvelle</strong>, liée à
     * l'originale. Celle-ci reste annulée et consultable : le cahier
     * interdit un report automatique, la nouvelle date est une décision.
     *
     * @param classPublicIds classes de la séance reportée ; permet de
     *                       reporter sur un périmètre réduit si la
     *                       situation l'exige
     */
    record Postpone(
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotBlank @Size(max = 64) String timeZoneId,
            @NotBlank @Size(max = 500) String reason,
            String teacherPublicId,
            List<@NotBlank String> classPublicIds,
            @Size(max = 191) String title) {
    }

    /**
     * Demande d'annulation déposée par le formateur (EF-SES-008).
     *
     * <p>Le formateur demande, il ne décide pas : la validation revient au
     * responsable pédagogique ou à l'administration (docs/02 §14.4).
     */
    record RequestCancellation(@NotBlank @Size(max = 500) String reason) {
    }

    /** Décision sur une demande d'annulation (EF-SES-008). */
    record DecideCancellation(
            @NotNull Boolean approved,
            @Size(max = 500) String comment) {
    }
}
