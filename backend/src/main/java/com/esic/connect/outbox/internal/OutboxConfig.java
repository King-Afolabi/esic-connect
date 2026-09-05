package com.esic.connect.outbox.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Câblage interne du module {@code outbox} : binding de
 * {@link OutboxProperties} et ordonnancement de la reprise
 * ({@link OutboxDispatcher#scheduledDrain()}).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
class OutboxConfig {
}
