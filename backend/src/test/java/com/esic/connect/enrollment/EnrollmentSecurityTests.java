package com.esic.connect.enrollment;

import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.notification.internal.InvitationMailer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrôles d'autorisation du module {@code enrollment} : gestion des
 * profils apprenants et des inscriptions réservée à
 * {@code ADMIN}/{@code SUPER_ADMIN}/{@code SCHOOL_ADMINISTRATION} ;
 * {@code STUDENT}, {@code TEACHER} et {@code PEDAGOGICAL_MANAGER} exclus ;
 * accès anonyme rejeté en 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EnrollmentSecurityTests {

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
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Test
    void anonymousRequestsAreRejectedWith401() {
        assertThat(anonymous("/api/v1/enrollments")).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous("/api/v1/student-profiles")).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deniedRolesCannotReadOrWrite() {
        for (RoleCode role : List.of(RoleCode.STUDENT, RoleCode.TEACHER, RoleCode.PEDAGOGICAL_MANAGER)) {
            String token = tokenFor(role);
            assertThat(get("/api/v1/enrollments", token)).as("GET enrollments as " + role)
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(get("/api/v1/student-profiles", token)).as("GET student-profiles as " + role)
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(post("/api/v1/enrollments", Map.of("studentProfilePublicId", UUID.randomUUID().toString(),
                    "classGroupPublicId", UUID.randomUUID().toString()), token)).as("POST enrollments as " + role)
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(post("/api/v1/student-profiles", Map.of("userPublicId", UUID.randomUUID().toString(),
                    "studentNumber", "ESIC-2026-0001"), token)).as("POST student-profiles as " + role)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void managementRolesCanRead() {
        for (RoleCode role : List.of(RoleCode.ADMIN, RoleCode.SUPER_ADMIN, RoleCode.SCHOOL_ADMINISTRATION)) {
            String token = tokenFor(role);
            assertThat(get("/api/v1/enrollments", token)).as("GET enrollments as " + role)
                    .isEqualTo(HttpStatus.OK);
            assertThat(get("/api/v1/student-profiles", token)).as("GET student-profiles as " + role)
                    .isEqualTo(HttpStatus.OK);
        }
    }

    // ------------------------------------------------------------------

    private HttpStatus anonymous(String path) {
        return (HttpStatus) restTemplate.exchange(RequestEntity.get(URI.create(path)).build(), String.class)
                .getStatusCode();
    }

    private HttpStatus get(String path, String token) {
        return (HttpStatus) restTemplate.exchange(RequestEntity.get(URI.create(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(), String.class).getStatusCode();
    }

    private HttpStatus post(String path, Map<String, Object> body, String token) {
        return (HttpStatus) restTemplate.exchange(RequestEntity.post(URI.create(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(body), String.class).getStatusCode();
    }

    private String tokenFor(RoleCode... roles) {
        UserAccount account = new UserAccount("enr-sec-" + UUID.randomUUID() + "@esic-connect.test",
                "Enr", "Sec", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        Map<String, Object> login = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", account.getEmail(), "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) login.get("accessToken");
    }
}
