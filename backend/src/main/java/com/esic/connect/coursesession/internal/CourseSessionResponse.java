package com.esic.connect.coursesession.internal;

import com.esic.connect.coursesession.AttendanceCheckpointStatus;
import com.esic.connect.coursesession.AttendanceCheckpointType;
import com.esic.connect.coursesession.SessionLifecycle;

import com.esic.connect.coursesession.SessionAttendanceMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vue API d'une séance — jamais d'identifiant SQL interne, jamais de
 * jeton d'émargement.
 *
 * <p>{@code checkpointPublicId} / {@code checkpointOpen} sont conservés
 * (compat V9) et reflètent le <em>premier</em> point de contrôle
 * ({@code START}) ; {@code checkpoints} donne la liste complète (V10).
 */
record CourseSessionResponse(
        UUID publicId,
        SessionLifecycle status,
        String title,
        String exceptionReason,
        TeacherView teacher,
        /** Matière enseignée ; {@code null} si non précisée (V35). */
        SubjectView subject,
        /** Code fonctionnel de la salle ; {@code null} si non encore affectée. */
        String roomCode,
        List<SessionClassView> classes,
        Instant startsAt,
        Instant endsAt,
        String timeZoneId,
        /** Modalité d'enseignement (docs/02 §15). */
        SessionAttendanceMode attendanceMode,
        /** Lien distant ; {@code null} sur une séance {@code ON_SITE}. */
        String remoteLink,
        Instant openedAt,
        Instant closedAt,
        String cancellationReason,
        Instant cancelledAt,
        /**
         * Séance de remplacement créée par un report (EF-SES-007). Non
         * {@code null} uniquement sur une séance annulée puis reportée :
         * le cahier veut que l'originale « reste consultable en
         * historique » en portant le lien (docs/02 §14.4).
         */
        UUID postponedToPublicId,
        UUID checkpointPublicId,
        boolean checkpointOpen,
        List<CheckpointView> checkpoints,
        Instant createdAt,
        Instant updatedAt) {

    /** Identité minimale d'un formateur pour l'affichage. */
    record TeacherView(UUID publicId, String firstName, String lastName) {
    }

    /** Identité minimale d'une matière pour l'affichage (V35). */
    record SubjectView(UUID publicId, String code, String name) {
    }

    /** Identité minimale d'une classe rattachée. */
    record SessionClassView(UUID publicId, String code) {
    }

    /** Vue d'un point de contrôle d'émargement (V10). */
    record CheckpointView(
            UUID publicId,
            String label,
            AttendanceCheckpointType type,
            AttendanceCheckpointStatus status,
            boolean required,
            int displayOrder,
            Instant openedAt,
            Instant closedAt) {

        static CheckpointView from(AttendanceCheckpoint cp) {
            return new CheckpointView(cp.getPublicId(), cp.getLabel(), cp.getCheckpointType(),
                    cp.getStatus(), cp.isRequired(), cp.getDisplayOrder(),
                    cp.getOpenedAt(), cp.getClosedAt());
        }
    }
}
