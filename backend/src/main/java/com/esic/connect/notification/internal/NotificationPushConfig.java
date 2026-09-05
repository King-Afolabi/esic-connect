package com.esic.connect.notification.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Choisit l'adaptateur de poussée selon la configuration réellement
 * présente (EF-NOTIF-005).
 *
 * <p><strong>Pourquoi une fabrique et non {@code @ConditionalOnProperty}.</strong>
 * Cette annotation considère une propriété <em>déclarée mais vide</em>
 * comme présente. Or {@code application.yml} déclare les clés VAPID avec
 * une valeur vide par défaut, précisément pour documenter leur existence.
 * L'adaptateur HTTP serait donc retenu avec des clés vides, et
 * échouerait au démarrage — au lieu de basculer proprement sur le mode
 * inactif que ce déploiement demande.
 */
@Configuration
class NotificationPushConfig {

    @Bean
    WebPushSender webPushSender(
            @Value("${app.notification.push.vapid.public-key:}") String publicKey,
            @Value("${app.notification.push.vapid.private-key:}") String privateKey,
            @Value("${app.notification.push.vapid.subject:mailto:no-reply@esic-connect.test}") String subject,
            @Value("${app.notification.push.timeout:PT10S}") Duration timeout,
            ObjectMapper objectMapper, Clock clock) {
        if (isBlank(publicKey) || isBlank(privateKey)) {
            return new InactiveWebPushSender();
        }
        return new HttpWebPushSender(publicKey.trim(), privateKey.trim(), subject, timeout,
                objectMapper, clock);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
