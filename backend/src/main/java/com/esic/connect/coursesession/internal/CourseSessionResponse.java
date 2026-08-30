package com.esic.connect.coursesession.internal;

import com.esic.connect.coursesession.SessionLifecycle;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vue API d'une séance — jamais d'identifiant SQL interne, jamais de
 * jeton d'émargement.
 *
 * @param publicId          identifiant public de la séance
 * @param status            statut du cycle de vie
 * @param title             libellé facultatif
 * @param exceptionReason   motif de la séance exceptionnelle
 * @param teacher           formateur affecté (identité minimale)
 * @param classes           classes rattachées
 * @param startsAt          début planifié
 * @param endsAt            fin planifiée
 * @param timeZoneId        fuseau IANA de saisie
 * @param openedAt          instant d'ouverture ({@code null} tant que PLANNED)
 * @param closedAt          instant de fermeture ({@code null} tant que non CLOSED)
 * @param checkpointPublicId identifiant public du point de contrôle unique
 * @param checkpointOpen    {@code true} si l'émargement est ouvert
 * @param createdAt         horodatage de création
 * @param updatedAt         horodatage de dernière modification
 */
record CourseSessionResponse(
        UUID publicId,
        SessionLifecycle status,
        String title,
        String exceptionReason,
        TeacherView teacher,
        List<SessionClassView> classes,
        Instant startsAt,
        Instant endsAt,
        String timeZoneId,
        Instant openedAt,
        Instant closedAt,
        UUID checkpointPublicId,
        boolean checkpointOpen,
        Instant createdAt,
        Instant updatedAt) {

    /** Identité minimale d'un formateur pour l'affichage. */
    record TeacherView(UUID publicId, String firstName, String lastName) {
    }

    /** Identité minimale d'une classe rattachée. */
    record SessionClassView(UUID publicId, String code) {
    }
}
