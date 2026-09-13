package com.esic.connect.coursesession.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Câblage interne du module {@code coursesession} : binding de
 * {@link CourseSessionAutoCloseProperties} et ordonnancement de la
 * fermeture automatique des séances ({@link CourseSessionAutoCloseScheduler},
 * Lot 9). Même posture que {@code planning.internal.PlanningConfig}.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(CourseSessionAutoCloseProperties.class)
class CourseSessionConfig {
}
