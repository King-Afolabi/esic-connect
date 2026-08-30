package com.esic.connect.attendance;

import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.notification.internal.InvitationMailer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrôles d'accès du module {@code attendance} sur les six rôles
 * système :
 * <ul>
 *   <li>{@code POST /api/v1/attendance/validate} : {@code STUDENT}
 *       uniquement ; 401 anonyme ; 403 pour tout autre rôle ;</li>
 *   <li>{@code POST /api/v1/sessions/{id}/attendance-token} :
 *       {@code ADMIN}/{@code SUPER_ADMIN}/{@code PEDAGOGICAL_MANAGER}/{@code TEACHER}
 *       franchissent le {@code @PreAuthorize} ; {@code SCHOOL_ADMINISTRATION}
 *       et {@code STUDENT} → 403 ;</li>
 *   <li>{@code GET /api/v1/sessions/{id}/attendance} : rôles de lecture
 *       des séances ({@code SCHOOL_ADMINISTRATION} inclus) ; {@code STUDENT}
 *       → 403.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AttendanceSecurityTests {

    private static final String PASSWORD = "S3cure-Pass!word";

    @TestConfiguration
    static class NoopMailerConfig {
        @Bean
        @Primary
        InvitationMailer noopInvitationMailer() {
            return (toEmail, firstName, rawToken, expiresAt) -> {
            };
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void useJdkClient() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void anonymousIsRejectedOnEveryRoute() {
        assertThat(status(HttpMethod.POST, "/api/v1/attendance/validate", Map.of("shortCode", "ABCD2345"), null))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + UUID.randomUUID() + "/attendance-token",
                null, null)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(status(HttpMethod.GET, "/api/v1/sessions/" + UUID.randomUUID() + "/attendance", null, null))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void onlyStudentReachesValidate() {
        Map<String, Object> body = Map.of("shortCode", "ABCD2345");
        for (RoleCode role : List.of(RoleCode.ADMIN, RoleCode.SUPER_ADMIN, RoleCode.SCHOOL_ADMINISTRATION,
                RoleCode.PEDAGOGICAL_MANAGER, RoleCode.TEACHER)) {
            assertThat(status(HttpMethod.POST, "/api/v1/attendance/validate", body, tokenFor(role)))
                    .as("validate as " + role).isEqualTo(HttpStatus.FORBIDDEN);
        }
        // Un STUDENT franchit le @PreAuthorize : le code court inconnu produit un 409 métier, pas un 403.
        assertThat(status(HttpMethod.POST, "/api/v1/attendance/validate", body, tokenFor(RoleCode.STUDENT)))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void issueTokenPreAuthorizeMatrix() {
        String unknownSession = "/api/v1/sessions/" + UUID.randomUUID() + "/attendance-token";
        // Rôles autorisés par @PreAuthorize : la séance inconnue donne un 404 métier (et non un 403).
        for (RoleCode role : List.of(RoleCode.ADMIN, RoleCode.SUPER_ADMIN, RoleCode.PEDAGOGICAL_MANAGER,
                RoleCode.TEACHER)) {
            assertThat(status(HttpMethod.POST, unknownSession, null, tokenFor(role)))
                    .as("issue token as " + role).isEqualTo(HttpStatus.NOT_FOUND);
        }
        // Rôles refusés par @PreAuthorize.
        for (RoleCode role : List.of(RoleCode.SCHOOL_ADMINISTRATION, RoleCode.STUDENT)) {
            assertThat(status(HttpMethod.POST, unknownSession, null, tokenFor(role)))
                    .as("issue token as " + role).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void attendanceListPreAuthorizeMatrix() {
        String unknownSession = "/api/v1/sessions/" + UUID.randomUUID() + "/attendance";
        for (RoleCode role : List.of(RoleCode.ADMIN, RoleCode.SUPER_ADMIN, RoleCode.SCHOOL_ADMINISTRATION,
                RoleCode.PEDAGOGICAL_MANAGER, RoleCode.TEACHER)) {
            assertThat(status(HttpMethod.GET, unknownSession, null, tokenFor(role)))
                    .as("attendance list as " + role).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(status(HttpMethod.GET, unknownSession, null, tokenFor(RoleCode.STUDENT)))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------

    private HttpStatus status(HttpMethod method, String path, Map<String, Object> body, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(method, URI.create(path));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        RequestEntity<?> entity = body == null
                ? builder.build()
                : builder.contentType(MediaType.APPLICATION_JSON).body(body);
        return (HttpStatus) restTemplate.exchange(entity, new ParameterizedTypeReference<Map<String, Object>>() {
        }).getStatusCode();
    }

    private String tokenFor(RoleCode role) {
        UserAccount account = new UserAccount("att-sec-" + UUID.randomUUID() + "@esic-connect.test",
                "Att", "Sec", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        Role r = roleRepository.findByCode(role).orElseThrow();
        userRoleRepository.saveAndFlush(new UserRole(account, r, Instant.now(), true));
        Map<String, Object> body = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", account.getEmail(), "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) body.get("accessToken");
    }
}
