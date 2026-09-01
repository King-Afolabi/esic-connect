package com.esic.connect.attendance.internal;

import com.esic.connect.coursesession.CourseSessionChangeAction;
import com.esic.connect.coursesession.CourseSessionChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * À la fermeture <strong>ou à l'annulation</strong> (G1-C) d'une séance,
 * purge immédiatement les jetons Redis d'émargement de cette séance —
 * au-delà de l'expiration par TTL. Aucune dépendance vers
 * {@code coursesession.internal} : l'événement public suffit.
 */
@Component
class CourseSessionCloseListener {

    private final AttendanceTokenService tokenService;

    CourseSessionCloseListener(AttendanceTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @EventListener
    public void onCourseSessionChange(CourseSessionChangeEvent event) {
        if (event.action() == CourseSessionChangeAction.CLOSED
                || event.action() == CourseSessionChangeAction.CANCELLED) {
            tokenService.invalidateSession(event.resourcePublicId());
        }
    }
}
