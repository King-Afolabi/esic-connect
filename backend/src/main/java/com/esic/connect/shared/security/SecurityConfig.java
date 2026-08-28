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

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Configuration de sécurité du socle applicatif.
 *
 * API stateless à jeton porteur : JWT HS256 émis et validé par les
 * mécanismes standards de Spring Security OAuth2 Resource Server +
 * Nimbus (aucun filtre artisanal). Session désactivée, CSRF non
 * pertinent (pas de cookie de session, jeton transmis en en-tête
 * `Authorization`). Seules les routes techniques et
 * `/api/v1/auth/login` restent ouvertes ; le reste exige un jeton
 * valide. Aucun contrôle d'accès par rôle métier n'est câblé ici : il
 * n'existe encore aucune route métier.
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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
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
