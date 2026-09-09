package com.esic.connect.integration.internal;

import com.esic.connect.integration.ExternalCalendarWriter;
import com.esic.connect.integration.MeetingProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * État <strong>déclaré</strong> des intégrations Microsoft (EF-INT-002,
 * EF-INT-003).
 *
 * <p>Cette route existe pour que l'interface puisse dire la vérité :
 * sans identifiants configurés, elle affiche « intégration non
 * configurée » au lieu d'offrir un bouton « créer la réunion Teams » qui
 * échouerait à l'heure du cours.
 */
@RestController
@RequestMapping("/api/v1/integrations/microsoft")
class MicrosoftIntegrationController {

    private static final String INACTIVE_NOTICE =
            "Aucune intégration Microsoft n'est configurée : aucune réunion Teams n'est créée "
                    + "et aucun calendrier externe n'est écrit.";
    private static final String ACTIVE_NOTICE =
            "Intégration Microsoft configurée. Aucun locataire réel n'a été sollicité par le "
                    + "dépôt : vérifiez le premier appel avant de vous y fier.";

    private final MeetingProvider meetingProvider;
    private final ExternalCalendarWriter calendarWriter;

    MicrosoftIntegrationController(MeetingProvider meetingProvider,
                                   ExternalCalendarWriter calendarWriter) {
        this.meetingProvider = meetingProvider;
        this.calendarWriter = calendarWriter;
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER')")
    IntegrationResponses.MicrosoftStatus status() {
        boolean active = meetingProvider.isActive() || calendarWriter.isActive();
        return new IntegrationResponses.MicrosoftStatus(
                meetingProvider.providerName(), meetingProvider.isActive(),
                calendarWriter.providerName(), calendarWriter.isActive(),
                active ? ACTIVE_NOTICE : INACTIVE_NOTICE);
    }
}
