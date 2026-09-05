package com.esic.connect.notification.internal;

import com.esic.connect.attendance.JustificationReviewedEvent;
import com.esic.connect.claim.ClaimChangeAction;
import com.esic.connect.claim.ClaimChangeEvent;
import com.esic.connect.coursesession.CourseSessionChangeEvent;
import com.esic.connect.coursesession.CourseSessionResourceType;
import com.esic.connect.planning.PlanningPublishedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Traduit les événements métier en demandes de notification
 * (EF-NOTIF-002, EF-NOTIF-003).
 *
 * <p><strong>Dans la transaction métier, et sans lecture.</strong> Cet
 * écouteur ne résout aucun destinataire et n'écrit aucune notification :
 * il enregistre dans l'outbox <em>ce qu'il faut annoncer et à quelle
 * audience</em>. Deux conséquences :
 *
 * <ul>
 *   <li>une transaction annulée n'annonce rien (RG-097, AC-027), et une
 *       panne du diffuseur ne perd rien (RG-096, AC-028) — ce que le
 *       motif précédent, « après commit, au mieux », ne garantissait pas
 *       (dette T-01) ;</li>
 *   <li>l'audience est résolue <em>après</em> commit, sur l'état
 *       réellement établi : l'effectif d'une classe ou la liste des
 *       remplaçants est alors stable.</li>
 * </ul>
 *
 * <p><strong>Contenu neutre</strong> : les libellés ne portent que des
 * informations déjà connues du destinataire — identifiant ou titre de
 * séance, numéro de version — jamais de jeton, code court, adresse IP,
 * motif nominatif ni chemin d'interface.
 */
@Component
class NotificationListener {

    private final NotificationDispatcher dispatcher;

    NotificationListener(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventListener
    public void onCourseSessionChange(CourseSessionChangeEvent event) {
        if (event.resourceType() != CourseSessionResourceType.COURSE_SESSION) {
            return;
        }
        NotificationType type = switch (event.action()) {
            case CANCELLED -> NotificationType.SESSION_CANCELLED;
            case SUBSTITUTION_ADDED -> NotificationType.SESSION_SUBSTITUTION_ADDED;
            case SUBSTITUTION_ENDED -> NotificationType.SESSION_SUBSTITUTION_ENDED;
            default -> null; // CREATED / OPENED / CLOSED : pas de notification
        };
        if (type == null) {
            return;
        }
        String title = switch (type) {
            case SESSION_CANCELLED -> "Séance annulée";
            case SESSION_SUBSTITUTION_ADDED -> "Remplaçant affecté";
            default -> "Remplacement terminé";
        };
        String body = switch (type) {
            case SESSION_CANCELLED -> "Une séance de votre planning a été annulée.";
            case SESSION_SUBSTITUTION_ADDED -> "Un remplaçant a été affecté à une séance de votre planning.";
            default -> "Un remplacement sur une séance de votre planning a pris fin.";
        };

        NotificationRequest.Builder request = NotificationRequest
                .of(type, "COURSE_SESSION", event.resourcePublicId(), event.eventId())
                .label(title, body)
                // Utilisateurs explicitement désignés par l'occurrence :
                // pour SUBSTITUTION_ENDED, le remplaçant qui vient de
                // terminer n'est plus ACTIVE et ne figurerait donc pas
                // dans les remplaçants de la séance.
                .recipients(event.affectedUserPublicIds())
                .session(event.resourcePublicId())
                .managers();
        // Une annulation change ce que les apprenants doivent faire de leur
        // journée ; un changement de formateur, non — les avertir de chaque
        // remplacement transformerait le centre en bruit de fond.
        if (type == NotificationType.SESSION_CANCELLED) {
            request.students();
        }
        dispatcher.dispatch(request.build());
    }

    @EventListener
    public void onPlanningPublished(PlanningPublishedEvent event) {
        Set<UUID> affected = new HashSet<>();
        affected.addAll(event.addedSessionPublicIds());
        affected.addAll(event.updatedSessionPublicIds());
        if (affected.isEmpty()) {
            return;
        }
        String title = event.initialPublication() ? "Planning publié" : "Planning mis à jour";
        String body = event.initialPublication()
                ? "Le planning de votre classe a été publié (version " + event.versionNumber() + ")."
                : "Le planning de votre classe a été mis à jour (version " + event.versionNumber() + ").";
        dispatcher.dispatch(NotificationRequest
                .of(NotificationType.PLANNING_PUBLISHED, "PLANNING_VERSION",
                        event.versionPublicId(), event.versionPublicId())
                .label(title, body)
                // L'audience se déduit des séances publiées : formateurs
                // principaux et remplaçants, apprenants des classes
                // concernées, et responsables du périmètre (§21.3).
                .sessions(affected)
                .students()
                .managers()
                .build());
    }

    /**
     * Examen d'un justificatif d'absence → notification au
     * <strong>propriétaire</strong>. Un justificatif est examiné une seule
     * fois (machine à états) : la clé d'occurrence est donc l'identifiant
     * du justificatif lui-même.
     */
    @EventListener
    public void onJustificationReviewed(JustificationReviewedEvent event) {
        NotificationType type = event.accepted()
                ? NotificationType.JUSTIFICATION_ACCEPTED
                : NotificationType.JUSTIFICATION_REJECTED;
        String title = event.accepted() ? "Justificatif accepté" : "Justificatif refusé";
        String body = event.accepted()
                ? "Votre justificatif d'absence a été accepté."
                : "Votre justificatif d'absence a été refusé. Consultez le motif dans votre espace.";
        dispatcher.dispatch(NotificationRequest
                .of(type, "JUSTIFICATION", event.justificationPublicId(), event.justificationPublicId())
                .label(title, body)
                .recipients(Set.of(event.ownerUserPublicId()))
                .build());
    }

    /**
     * Réclamations (dette T-12). Sans cette notification, un destinataire
     * n'apprenait qu'un dossier l'attendait qu'en ouvrant l'écran — ce qui
     * revient à demander à chacun de surveiller une file en permanence.
     *
     * <p>Les destinataires sont portés par l'événement : le module
     * {@code claim} sait, lui, qui siège au guichet visé et qui participe
     * au fil. Le déduire ici obligerait {@code notification} à connaître
     * la notion de guichet.
     */
    @EventListener
    public void onClaimChange(ClaimChangeEvent event) {
        NotificationType type = switch (event.action()) {
            case CREATED -> NotificationType.CLAIM_OPENED;
            case MESSAGE_POSTED -> NotificationType.CLAIM_MESSAGE_ADDED;
            case TRANSFERRED, STATUS_CHANGED, REOPENED -> NotificationType.CLAIM_STATUS_CHANGED;
        };
        if (event.recipientUserPublicIds().isEmpty()) {
            return;
        }
        String title = switch (type) {
            case CLAIM_OPENED -> "Nouvelle réclamation";
            case CLAIM_MESSAGE_ADDED -> "Nouveau message sur une réclamation";
            default -> "Réclamation mise à jour";
        };
        // Ni sujet, ni extrait de message : ce sont des données
        // personnelles, et le centre de notifications n'est pas le lieu où
        // les recopier (§23.3).
        String body = switch (type) {
            case CLAIM_OPENED -> "Une réclamation vous a été adressée.";
            case CLAIM_MESSAGE_ADDED -> "Un message a été ajouté à une réclamation vous concernant.";
            default -> "Une réclamation vous concernant a changé d'état ("
                    + label(event.action()) + ").";
        };
        dispatcher.dispatch(NotificationRequest
                .of(type, "CLAIM", event.claimPublicId(), event.eventId())
                .label(title, body)
                .recipients(event.recipientUserPublicIds())
                .build());
    }

    private static String label(ClaimChangeAction action) {
        return switch (action) {
            case TRANSFERRED -> "transférée";
            case REOPENED -> "rouverte";
            // Le statut exact appartient au dossier, pas au libellé : le
            // recopier ici obligerait à maintenir deux vocabulaires.
            default -> "changement d'état";
        };
    }
}
