package com.esic.connect.academic;

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
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrôles d'autorisation du référentiel académique :
 * <ul>
 *   <li>lecture : {@code ADMIN}/{@code SUPER_ADMIN}/
 *       {@code SCHOOL_ADMINISTRATION}/{@code PEDAGOGICAL_MANAGER} ;</li>
 *   <li>écriture : {@code ADMIN}/{@code SUPER_ADMIN} uniquement
 *       ({@code PEDAGOGICAL_MANAGER} en lecture seule pour ce lot) ;</li>
 *   <li>{@code STUDENT} et {@code TEACHER} exclus.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AcademicSecurityTests {

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

    @Test
    void anonymousRequestIsRejectedWith401() {
        ResponseEntity<String> response = restTemplate.exchange(
                RequestEntity.get(URI.create("/api/v1/programs")).build(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void studentCannotReadPrograms() {
        assertThat(get("/api/v1/programs", tokenFor(RoleCode.STUDENT))).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void teacherCannotReadPrograms() {
        assertThat(get("/api/v1/programs", tokenFor(RoleCode.TEACHER))).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void schoolAdministrationCanReadButNotWritePrograms() {
        String token = tokenFor(RoleCode.SCHOOL_ADMINISTRATION);
        assertThat(get("/api/v1/programs", token)).isEqualTo(HttpStatus.OK);
        assertThat(createProgramStatus(token)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void pedagogicalManagerCanReadButNotWritePrograms() {
        String token = tokenFor(RoleCode.PEDAGOGICAL_MANAGER);
        assertThat(get("/api/v1/academic-years", token)).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/class-groups", token)).isEqualTo(HttpStatus.OK);
        assertThat(createProgramStatus(token)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanCreateProgram() {
        assertThat(createProgramStatus(tokenFor(RoleCode.ADMIN))).isEqualTo(HttpStatus.CREATED);
    }

    // ------------------------------------------------------------------

    private HttpStatus get(String path, String token) {
        return (HttpStatus) restTemplate.exchange(
                RequestEntity.get(URI.create(path)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                String.class).getStatusCode();
    }

    private HttpStatus createProgramStatus(String token) {
        return (HttpStatus) restTemplate.exchange(
                RequestEntity.post(URI.create("/api/v1/programs"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("code", "PRG-" + UUID.randomUUID().toString().substring(0, 8),
                                "name", "P", "programType", "OTHER")),
                String.class).getStatusCode();
    }

    private String tokenFor(RoleCode... roles) {
        UserAccount account = new UserAccount("acad-sec-" + UUID.randomUUID() + "@esic-connect.test",
                "Acad", "Sec", AccountStatus.ACTIVE);
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
