package com.esic.connect.studentimport.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Câblage interne du module {@code studentimport}. Active le binding de
 * {@link StudentImportProperties} sans exiger de {@code @ConfigurationPropertiesScan}
 * global (le reste du projet utilise {@code @Value}) : le module reste
 * maître de sa configuration. Active aussi l'ordonnancement pour la purge
 * planifiée des imports temporaires ({@code StudentImportPurgeService}).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(StudentImportProperties.class)
class StudentImportConfig {
}
