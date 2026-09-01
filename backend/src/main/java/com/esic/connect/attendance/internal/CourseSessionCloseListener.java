package com.esic.connect.attendance.internal;

import com.esic.connect.coursesession.CourseSessionChangeAction;
import com.esic.connect.coursesession.CourseSessionChangeEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * À la fermeture <strong>ou à l'annulation</strong> (G1-C) d'une séance,
 * purge les jetons Redis d'émargement de cette séance — au-delà de
 * l'expiration par TTL. Aucune dépendance vers
 * {@code coursesession.internal} : l'événement public suffit.
 *
 * <p><strong>Purge après commit (durcie au checkpoint G1-C.3).</strong>
 * {@link TransactionalEventListener}{@code (phase = AFTER_COMMIT)} : la
 * purge Redis — effet externe non transactionnel et irréversible — n'a
 * lieu que si la transaction métier de fermeture / annulation a
 * effectivement commité. Si elle <em>rollback</em>, la séance reste
 * {@code OPEN} <strong>et</strong> ses jetons ne sont pas purgés (défaut
 * corrigé : en {@code @EventListener} synchrone, la purge partait pendant
 * la transaction et n'était pas compensée par un rollback). La validation
 * d'un émargement reste de toute façon bloquée dès que le point de
 * contrôle est fermé — la purge est une défense en profondeur.
 */
@Component
class CourseSessionCloseListener {

    private final AttendanceTokenService tokenService;

    CourseSessionCloseListener(AttendanceTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCourseSessionChange(CourseSessionChangeEvent event) {
        if (event.action() == CourseSessionChangeAction.CLOSED
                || event.action() == CourseSessionChangeAction.CANCELLED) {
            tokenService.invalidateSession(event.resourcePublicId());
        }
    }
}
