package com.esic.connect.notification.internal;

import com.esic.connect.attendance.JustificationReviewedEvent;
import com.esic.connect.coursesession.CourseSessionChangeEvent;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.coursesession.CourseSessionResourceType;
import com.esic.connect.planning.PlanningPublishedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Produit les notifications métier persistantes (G1-D ; EF-NOTIF-001 /
 * EF-NOTIF-002 ; RG-033) à partir des événements des modules
 * {@code coursesession} et {@code planning}.
 *
 * <p><strong>Après commit uniquement</strong>
 * ({@code @TransactionalEventListener(AFTER_COMMIT)}) : une notification
 * n'est créée que si la transaction métier source a effectivement
 * committé. L'écriture est déléguée à {@link NotificationWriter}
 * ({@code REQUIRES_NEW}, idempotent) — un échec de notification ne
 * rollbacke jamais le métier.
 *
 * <p><strong>Destinataires dérivés côté serveur</strong> — jamais d'un
 * identifiant client : formateur principal et remplaçants {@code ACTIVE}
 * de la séance ({@link CourseSessionDirectory}). Les apprenants et les
 * responsables pédagogiques comme destinataires supplémentaires sont un
 * prolongement documenté de G1-D (nouveaux ports {@code enrollment} /
 * {@code academic} requis) — voir {@code G1_IMPLEMENTATION_PROGRESS.md}.
 *
 * <p><strong>Contenu neutre</strong> : le libellé ne contient que des
 * informations déjà publiques (identifiant / titre de séance, numéro de
 * version) — jamais de jeton, code court, IP, motif nominatif, chemin.
 */
@Component
class NotificationListener {

    private final NotificationWriter writer;
    private final CourseSessionDirectory courseSessionDirectory;

    NotificationListener(NotificationWriter writer, CourseSessionDirectory courseSessionDirectory) {
        this.writer = writer;
        this.courseSessionDirectory = courseSessionDirectory;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCourseSessionChange(CourseSessionChangeEvent event) {
        if (event.resourceType() != CourseSessionResourceType.COURSE_SESSION) {
            return;
        }
        NotificationType type = switch (event.action()) {
            case CANCELLED -> NotificationType.SESSION_CANCELLED;
            case SUBSTITUTION_ADDED -> NotificationType.SESSION_SUBSTITUTION_ADDED;
            case SUBSTITUTION_ENDED -> NotificationType.SESSION_SUBSTITUTION_ENDED;
            default -> null; // CREATED / OPENED / CLOSED : pas de notification en G1-D
        };
        if (type == null) {
            return;
        }
        Optional<CourseSessionDirectory.SessionNotificationInfo> info =
                courseSessionDirectory.findSessionNotificationInfo(event.resourcePublicId());
        if (info.isEmpty()) {
            return;
        }
        CourseSessionDirectory.SessionNotificationInfo session = info.get();
        Set<UUID> recipients = new HashSet<>();
        recipients.add(session.principalTeacherPublicId());
        recipients.addAll(session.substituteTeacherPublicIds());
        // Utilisateurs explicitement désignés par l'occurrence (G1-D.1) :
        // pour SUBSTITUTION_ENDED, le remplaçant qui vient de terminer
        // n'est plus ACTIVE et ne figure donc pas dans
        // substituteTeacherPublicIds — il est ici.
        recipients.addAll(event.affectedUserPublicIds());

        String label = sessionLabel(session);
        String title = switch (type) {
            case SESSION_CANCELLED -> "Séance annulée";
            case SESSION_SUBSTITUTION_ADDED -> "Remplaçant affecté";
            case SESSION_SUBSTITUTION_ENDED -> "Remplacement terminé";
            default -> "Séance modifiée";
        };
        String body = switch (type) {
            case SESSION_CANCELLED -> "La séance " + label + " a été annulée.";
            case SESSION_SUBSTITUTION_ADDED -> "Un remplaçant a été affecté à la séance " + label + ".";
            case SESSION_SUBSTITUTION_ENDED -> "Le remplacement sur la séance " + label + " a pris fin.";
            default -> "La séance " + label + " a été modifiée.";
        };
        writer.write(type, "COURSE_SESSION", session.sessionPublicId(), event.eventId(),
                recipients, title, body);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlanningPublished(PlanningPublishedEvent event) {
        Set<UUID> affected = new HashSet<>();
        affected.addAll(event.addedSessionPublicIds());
        affected.addAll(event.updatedSessionPublicIds());
        Set<UUID> teachers = courseSessionDirectory.findPrincipalTeacherPublicIds(affected);
        if (teachers.isEmpty()) {
            return;
        }
        String title = event.initialPublication() ? "Planning publié" : "Planning mis à jour";
        String body = event.initialPublication()
                ? "Le planning de votre classe a été publié (version " + event.versionNumber() + ")."
                : "Le planning de votre classe a été mis à jour (version " + event.versionNumber() + ").";
        // Clé d'occurrence : versionPublicId (unique par publication).
        writer.write(NotificationType.PLANNING_PUBLISHED, "PLANNING_VERSION", event.versionPublicId(),
                event.versionPublicId(), teachers, title, body);
    }

    /**
     * Examen d'un justificatif d'absence (G1-E) → notification au
     * <strong>propriétaire</strong> (destinataire unique porté par
     * l'événement). Un justificatif est examiné une seule fois (machine à
     * états) : la clé d'occurrence de {@code dedup_key} est donc
     * l'identifiant du justificatif lui-même.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJustificationReviewed(JustificationReviewedEvent event) {
        NotificationType type = event.accepted()
                ? NotificationType.JUSTIFICATION_ACCEPTED
                : NotificationType.JUSTIFICATION_REJECTED;
        String title = event.accepted() ? "Justificatif accepté" : "Justificatif refusé";
        String body = event.accepted()
                ? "Votre justificatif d'absence a été accepté."
                : "Votre justificatif d'absence a été refusé. Consultez le motif dans votre espace.";
        writer.write(type, "JUSTIFICATION", event.justificationPublicId(), event.justificationPublicId(),
                Set.of(event.ownerUserPublicId()), title, body);
    }

    private static String sessionLabel(CourseSessionDirectory.SessionNotificationInfo session) {
        String title = session.title();
        return (title != null && !title.isBlank()) ? "« " + title.trim() + " »" : "exceptionnelle";
    }
}
