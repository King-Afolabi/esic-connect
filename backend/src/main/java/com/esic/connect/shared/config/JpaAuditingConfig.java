package com.esic.connect.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Active uniquement l'horodatage automatique ({@code @CreatedDate} /
 * {@code @LastModifiedDate}). Aucun {@code AuditorAware} n'est fourni tant
 * que l'authentification n'existe pas : les colonnes *_by_id restent
 * renseignées manuellement par le code appelant.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
