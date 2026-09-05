package com.esic.connect.integration.internal;

import com.esic.connect.integration.ExternalCalendarWriter;
import com.esic.connect.integration.MeetingProvider;

import java.util.Optional;

/**
 * Adaptateurs de repli lorsqu'aucune intégration Microsoft n'est
 * configurée (docs/02 §28.1).
 *
 * <p>Ils ne créent rien, ne renvoient aucun lien et ne prétendent pas le
 * contraire : {@code isActive()} vaut {@code false}, et l'API l'expose
 * pour que l'écran affiche « intégration non configurée » plutôt qu'un
 * bouton qui échouerait à l'heure du cours.
 */
final class InactiveMicrosoftAdapters {

    static final String PROVIDER_NAME = "none";

    private InactiveMicrosoftAdapters() {
    }

    static MeetingProvider meetingProvider() {
        return new MeetingProvider() {
            @Override
            public boolean isActive() {
                return false;
            }

            @Override
            public String providerName() {
                return PROVIDER_NAME;
            }

            @Override
            public Optional<Meeting> createMeeting(MeetingRequest request) {
                return Optional.empty();
            }
        };
    }

    static ExternalCalendarWriter calendarWriter() {
        return new ExternalCalendarWriter() {
            @Override
            public boolean isActive() {
                return false;
            }

            @Override
            public String providerName() {
                return PROVIDER_NAME;
            }

            @Override
            public Optional<String> upsertEvent(CalendarEvent event) {
                return Optional.empty();
            }

            @Override
            public void deleteEvent(String externalEventId) {
                // Rien à supprimer : rien n'a jamais été écrit.
            }
        };
    }
}
