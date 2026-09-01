package com.esic.connect.planning.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Câblage interne du module {@code planning}. Active le binding de
 * {@link PlanningProperties} (le reste du projet utilise {@code @Value}).
 * L'ordonnancement de la purge des imports temporaires sera activé au
 * checkpoint qui introduit {@code @Scheduled}.
 */
@Configuration
@EnableConfigurationProperties(PlanningProperties.class)
class PlanningConfig {
}
