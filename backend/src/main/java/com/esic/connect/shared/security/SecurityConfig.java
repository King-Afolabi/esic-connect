package com.esic.connect.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration de sécurité du back-end.
 *
 * <p>API stateless à jeton porteur : JWT HS256 émis et validé par les
 * mécanismes standards de Spring Security OAuth2 Resource Server + Nimbus
 * (aucun filtre artisanal). Session désactivée ; CSRF non pertinent (pas
 * de cookie de session, jeton transmis dans l'en-tête
 * {@code Authorization}).
 *
 * <p><strong>Contrôle d'accès par rôle métier</strong> : il n'est
 * <em>pas</em> décidé ici. Chaque module (`identity`, `academic`,
 * `enrollment`, `alternation`, `organization`, `coursesession`,
 * `attendance`, `studentimport`) porte ses propres
 * {@code @PreAuthorize} sur ses contrôleurs REST (grâce à
 * {@link EnableMethodSecurity}), plus un contrôle de périmètre côté
 * serveur (`AcademicScopeGuard`, `CourseSessionAccessGuard`,
 * `StudentImportQueryService`…). Cette classe ne fait que : (1) laisser
 * passer les 3 routes publiques + les routes techniques
 * ({@link #PUBLIC_PATHS}), (2) exiger un JWT valide pour tout le reste,
 * (3) durcir les en-têtes HTTP de réponse, (4) restreindre CORS.
 *
 * <p><strong>En-têtes de sécurité</strong> : les valeurs par défaut de
 * Spring Security sont conservées ({@code X-Content-Type-Options: nosniff},
 * {@code X-Frame-Options: DENY}, en-têtes anti-cache, HSTS émis
 * uniquement sur les réponses HTTPS). Ce fichier ajoute explicitement une
 * {@code Content-Security-Policy} et une {@code Referrer-Policy} (docs/07
 * §8). La CSP autorise {@code style-src 'unsafe-inline'} et
 * {@code img-src data:} car Swagger UI (springdoc) en a besoin ; aucun
 * {@code script-src 'unsafe-inline'} ni {@code 'unsafe-eval'}.
 *
 * <p><strong>CORS</strong> : origines lues dans
 * {@code app.security.cors.allowed-origins} (jamais {@code *}),
 * {@code allowCredentials=false} (le jeton circule dans l'en-tête, pas
 * dans un cookie). En local, {@code ng serve} proxifie {@code /api} :
 * aucune requête cross-origin, cette configuration ne sert qu'à un
 * déploiement multi-origin.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api/v1/auth/login",
            // Parcours public d'activation (le jeton reçu par email fait foi).
            "/api/v1/account-invitations/validate",
            "/api/v1/account-invitations/activate"
    };

    /**
     * Content-Security-Policy appliquée à toutes les réponses. Stricte
     * pour les scripts (pas d'inline, pas d'eval) ; {@code style-src}
     * tolère l'inline et {@code img-src} tolère {@code data:} pour que
     * Swagger UI reste fonctionnel (springdoc sert son bundle depuis le
     * même origin). {@code frame-ancestors 'none'} double
     * {@code X-Frame-Options: DENY}.
     */
    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data:",
            "font-src 'self'",
            "connect-src 'self'",
            "worker-src 'self' blob:",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                           CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        // Conserve nosniff / X-Frame-Options: DENY / anti-cache / HSTS(HTTPS)
                        // par défaut ; ajoute CSP + Referrer-Policy.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint()));
        return http.build();
    }

    /**
     * CORS restrictif : origines explicites (jamais {@code *}), méthodes
     * réellement utilisées par l'API, en-têtes de requête limités.
     * {@code allowCredentials=false} : aucun cookie n'est utilisé, le
     * jeton porteur circule dans {@code Authorization}. Un déploiement
     * cross-origin doit renseigner {@code APP_ALLOWED_ORIGINS}.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.cors.allowed-origins:}") String allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * Par défaut, l'entrée d'authentification du Resource Server détaille
     * le motif du refus dans l'en-tête {@code WWW-Authenticate} (ex. :
     * « the iss claim is not valid »). Aucun détail de validation du JWT
     * ne doit être exposé à l'appelant : réponse 401 volontairement nue.
     */
    private AuthenticationEntryPoint jwtAuthenticationEntryPoint() {
        return (request, response, authException) -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    /** Utilisé uniquement par le flux de connexion (identity), jamais par la validation des requêtes suivantes. */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Mécanisme standard Spring Security ; encode en BCrypt par
        // défaut (accepté par docs/02 §16.1 : « Argon2id ou BCrypt »),
        // permet une migration future d'algorithme sans nouvelle
        // dépendance (Argon2 nécessiterait Bouncy Castle).
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Clé de signature HS256. Aucune valeur par défaut : le démarrage
     * échoue volontairement si {@code JWT_SECRET} est absent ou fait
     * moins de 32 octets (256 bits, minimum requis pour HS256).
     */
    @Bean
    public SecretKey jwtSigningKey(@Value("${app.security.jwt.secret}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET doit contenir au moins 32 octets (256 bits) pour HS256.");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    /**
     * Signature HS256 vérifiée par {@code withSecretKey}, expiration
     * vérifiée par défaut, et émetteur (`iss`) vérifié explicitement
     * contre {@code app.security.jwt.issuer} : un jeton par ailleurs
     * valide mais émis par un autre émetteur est refusé.
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey, @Value("${app.security.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /** Claim "roles" (codes bruts, sans préfixe) → autorités `ROLE_*`. */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
