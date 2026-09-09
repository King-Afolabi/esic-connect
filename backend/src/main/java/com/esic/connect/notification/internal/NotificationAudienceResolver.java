package com.esic.connect.notification.internal;

import com.esic.connect.academic.PedagogicalResponsibilityDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.enrollment.EnrollmentDirectory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Résout l'audience décrite par une {@link NotificationRequest}
 * (EF-NOTIF-003 ; docs/02 §21.3).
 *
 * <p><strong>Résolution côté serveur, sans exception.</strong> Aucun
 * identifiant de destinataire ne vient d'un client : ils sont dérivés de
 * la ressource concernée par les ports publics des modules
 * {@code coursesession}, {@code enrollment} et {@code academic}. C'est ce
 * qui empêche qu'un appel forgé fasse notifier — donc informer — un
 * compte étranger au périmètre.
 *
 * <p><strong>Un échec de résolution n'annule pas la notification.</strong>
 * Si les apprenants d'une classe ne peuvent pas être résolus, le
 * formateur est quand même prévenu : « l'échec d'un destinataire
 * n'interrompt jamais les autres » (§21.3).
 */
@Component
class NotificationAudienceResolver {

    private final CourseSessionDirectory courseSessionDirectory;
    private final EnrollmentDirectory enrollmentDirectory;
    private final PedagogicalResponsibilityDirectory responsibilityDirectory;
    private final Clock clock;

    NotificationAudienceResolver(CourseSessionDirectory courseSessionDirectory,
                                 EnrollmentDirectory enrollmentDirectory,
                                 PedagogicalResponsibilityDirectory responsibilityDirectory,
                                 Clock clock) {
        this.courseSessionDirectory = courseSessionDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.responsibilityDirectory = responsibilityDirectory;
        this.clock = clock;
    }

    Set<UUID> resolve(NotificationRequest request) {
        Set<UUID> recipients = new LinkedHashSet<>(request.explicitRecipients());
        Set<UUID> classes = new LinkedHashSet<>(request.classPublicIds());
        LocalDate on = request.referenceDate() != null
                ? request.referenceDate()
                : LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);

        for (UUID sessionPublicId : request.sessionPublicIds()) {
            Optional<CourseSessionDirectory.SessionNotificationInfo> info =
                    courseSessionDirectory.findSessionNotificationInfo(sessionPublicId);
            if (info.isEmpty()) {
                continue;
            }
            CourseSessionDirectory.SessionNotificationInfo session = info.get();
            if (request.includeSessionTeachers()) {
                recipients.add(session.principalTeacherPublicId());
                recipients.addAll(session.substituteTeacherPublicIds());
            }
            // Les classes viennent de ce même enregistrement, et non de
            // `findForAttendance` : celle-ci écarte les séances non
            // opérationnelles, si bien qu'une séance ANNULÉE n'y répond
            // plus — précisément quand il faut prévenir sa classe.
            if (request.includeStudents() || request.includeManagers()) {
                classes.addAll(session.classGroupPublicIds());
            }
        }

        if (!classes.isEmpty()) {
            if (request.includeStudents()) {
                recipients.addAll(enrollmentDirectory.findActiveStudentUserPublicIds(classes, on));
            }
            if (request.includeManagers()) {
                recipients.addAll(responsibilityDirectory.findManagersOfClasses(classes, on));
            }
        }

        recipients.remove(null);
        return recipients;
    }
}
