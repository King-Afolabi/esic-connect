package com.esic.connect.studentimport.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Câblage interne du module {@code studentimport}. Active le binding de
 * {@link StudentImportProperties} sans exiger de {@code @ConfigurationPropertiesScan}
 * global (le reste du projet utilise {@code @Value}) : le module reste
 * maître de sa configuration.
 */
@Configuration
@EnableConfigurationProperties(StudentImportProperties.class)
class StudentImportConfig {
}
