package com.esic.connect.planning.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Câblage interne du module {@code planning} : binding de
 * {@link PlanningProperties} (le reste du projet utilise {@code @Value}) et
 * ordonnancement de la purge des imports temporaires
 * ({@link PlanningPurgeService}).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(PlanningProperties.class)
class PlanningConfig {
}
