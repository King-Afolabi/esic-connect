package com.esic.connect.attendance.internal;

import com.esic.connect.coursesession.AttendanceCheckpointChangeAction;
import com.esic.connect.coursesession.AttendanceCheckpointChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * À la fermeture ou à l'annulation d'un point de contrôle (V10), purge
 * immédiatement le jeton Redis d'émargement de ce point de contrôle
 * s'il est le jeton courant de la séance — au-delà de l'expiration par
 * TTL. Aucune dépendance vers {@code coursesession.internal} :
 * l'événement public suffit.
 *
 * <p>Complémentaire de {@link CourseSessionCloseListener}, qui purge tous
 * les jetons de la séance à la fermeture de la séance.
 */
@Component
class AttendanceCheckpointCloseListener {

    private final AttendanceTokenService tokenService;

    AttendanceCheckpointCloseListener(AttendanceTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @EventListener
    public void onCheckpointChange(AttendanceCheckpointChangeEvent event) {
        if (event.action() == AttendanceCheckpointChangeAction.CLOSED
                || event.action() == AttendanceCheckpointChangeAction.CANCELLED) {
            tokenService.invalidateCheckpoint(event.sessionPublicId(), event.checkpointPublicId());
        }
    }
}
