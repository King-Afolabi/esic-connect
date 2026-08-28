package com.esic.connect.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration de sécurité provisoire du socle applicatif.
 *
 * Seules les routes techniques (santé Actuator, documentation OpenAPI) sont
 * ouvertes. Toutes les autres routes restent protégées par défaut : aucun
 * mécanisme d'authentification métier (utilisateurs, rôles, sessions) n'est
 * encore implémenté à ce stade — cette tâche est hors périmètre du socle
 * (voir docs/02-cahier-des-charges.md §26, module {@code identity} à venir).
 * Cette configuration sera remplacée lors de la mise en place réelle de
 * l'authentification.
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated());
        return http.build();
    }
}
