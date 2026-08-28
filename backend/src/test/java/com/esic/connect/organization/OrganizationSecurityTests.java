package com.esic.connect.organization;

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
 * Contrôles d'autorisation du référentiel organisationnel :
 * <ul>
 *   <li>lecture site/bâtiment/salle : rôles de gestion uniquement ;</li>
 *   <li>écriture site/bâtiment/salle : {@code ADMIN}/{@code SUPER_ADMIN} ;</li>
 *   <li>plages réseau : {@code SUPER_ADMIN} exclusivement, consultation comprise.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrganizationSecurityTests {

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
                RequestEntity.get(URI.create("/api/v1/sites")).build(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void studentCannotReadSites() {
        assertThat(get("/api/v1/sites", tokenFor(RoleCode.STUDENT))).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void teacherCannotCreateSite() {
        ResponseEntity<String> response = restTemplate.exchange(
                RequestEntity.post("/api/v1/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(RoleCode.TEACHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("code", "X" + shortId(), "name", "S", "timeZoneId", "Europe/Paris")),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void pedagogicalManagerCanReadButNotWriteSites() {
        String token = tokenFor(RoleCode.PEDAGOGICAL_MANAGER);
        assertThat(get("/api/v1/sites", token)).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> create = restTemplate.exchange(
                RequestEntity.post("/api/v1/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("code", "X" + shortId(), "name", "S", "timeZoneId", "Europe/Paris")),
                String.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void schoolAdministrationCanReadSites() {
        assertThat(get("/api/v1/sites", tokenFor(RoleCode.SCHOOL_ADMINISTRATION))).isEqualTo(HttpStatus.OK);
    }

    @Test
    void networkRangesAreReservedToSuperAdminReadIncluded() {
        String superAdmin = tokenFor(RoleCode.SUPER_ADMIN);
        String admin = tokenFor(RoleCode.ADMIN);
        String sitePublicId = createSite(superAdmin);
        String base = "/api/v1/sites/" + sitePublicId + "/network-ranges";

        assertThat(get(base, superAdmin)).isEqualTo(HttpStatus.OK);
        assertThat(get(base, admin)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(base, tokenFor(RoleCode.SCHOOL_ADMINISTRATION))).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> adminCreate = restTemplate.exchange(
                RequestEntity.post(URI.create(base))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("cidr", "10.0.0.0/8", "label", "LAN")),
                String.class);
        assertThat(adminCreate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void networkRangeReadIsRejectedForAnonymous() {
        ResponseEntity<String> response = restTemplate.exchange(
                RequestEntity.get(URI.create("/api/v1/sites/" + UUID.randomUUID() + "/network-ranges")).build(),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------

    private HttpStatus get(String path, String token) {
        return (HttpStatus) restTemplate.exchange(
                RequestEntity.get(URI.create(path)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                String.class).getStatusCode();
    }

    private String createSite(String token) {
        Map<String, Object> body = restTemplate.exchange(
                RequestEntity.post("/api/v1/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("code", "SEC-" + UUID.randomUUID(), "name", "S", "timeZoneId", "Europe/Paris")),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) body.get("publicId");
    }

    private String tokenFor(RoleCode... roles) {
        UserAccount account = new UserAccount("org-sec-" + UUID.randomUUID() + "@esic-connect.test",
                "Org", "Sec", AccountStatus.ACTIVE);
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

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
