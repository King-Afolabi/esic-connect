package com.esic.connect.integration.internal;

import com.esic.connect.integration.ExternalCalendarWriter;
import com.esic.connect.integration.MeetingProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Choisit les adaptateurs Microsoft <strong>par configuration</strong>
 * (docs/02 §28.1), sur le modèle de {@code CaptchaConfig} : Graph si le
 * locataire, l'application et le secret sont fournis par
 * l'environnement ; adaptateurs inactifs sinon.
 *
 * <p>Un seul bean par port, choisi à la construction : préférer cela à
 * un bean éventuellement {@code null} évite qu'un point d'injection
 * reçoive un {@code null} au lieu d'un adaptateur qui sait dire non.
 *
 * <p>Aucun secret n'a de valeur par défaut.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MicrosoftGraphProperties.class)
class MicrosoftIntegrationConfig {

    @Bean
    MeetingProvider meetingProvider(MicrosoftGraphProperties properties,
                                    RestClient.Builder builder,
                                    Clock clock) {
        return properties.isConfigured()
                ? new MicrosoftGraphMeetingProvider(new MicrosoftGraphClient(builder, properties, clock))
                : InactiveMicrosoftAdapters.meetingProvider();
    }

    @Bean
    ExternalCalendarWriter externalCalendarWriter(MicrosoftGraphProperties properties,
                                                  RestClient.Builder builder,
                                                  Clock clock) {
        return properties.isConfigured()
                ? new MicrosoftGraphCalendarWriter(new MicrosoftGraphClient(builder, properties, clock))
                : InactiveMicrosoftAdapters.calendarWriter();
    }
}
