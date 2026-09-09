package com.esic.connect.reporting;

import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccountRepository;
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
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rapport des invitations non activées (EF-REP-010 ; docs/02 §22.3).
 *
 * <p>Point vérifié en priorité : l'adresse électronique n'y figure
 * <strong>que sous forme masquée</strong>. Un rapport de relance n'a pas
 * besoin d'un annuaire exportable, qui circulerait ensuite par courriel
 * et par clé USB.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PendingInvitationReportIntegrationTests {

    @TestConfiguration
    static class NoopMailerConfig {
        @Bean
        @Primary
        InvitationMailer noopMailer() {
            return (a, b, c, d) -> {
            };
        }
    }

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private S11TestFixture fx;

    @BeforeEach
    void setUp() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        fx = new S11TestFixture(rest, userAccountRepository, userRoleRepository,
                roleRepository, passwordEncoder);
    }

    @Test
    void anInvitedAccountAppearsWithItsEmailMasked() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        String email = "invite-" + UUID.randomUUID() + "@esic-connect.test";
        ResponseEntity<Map<String, Object>> invited = fx.exchange(HttpMethod.POST, "/api/v1/users",
                Map.of("email", email, "firstName", "Alix", "lastName", "Nonactive",
                        "role", "STUDENT"), admin);
        assertThat(invited.getStatusCode().is2xxSuccessful())
                .as("création -> " + invited.getStatusCode() + " " + invited.getBody()).isTrue();

        List<Map<String, Object>> rows = rows(admin);
        assertThat(rows).isNotEmpty();
        Map<String, Object> mine = rows.stream()
                .filter(r -> "Nonactive".equals(r.get("lastName")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("compte invité absent du rapport"));

        assertThat(mine.get("maskedEmail").toString()).contains("…").contains("@");
        assertThat(mine.get("maskedEmail")).isNotEqualTo(email);
        assertThat(mine).containsKeys("lastSentAt", "expiresAt", "expired", "daysPending");
        // Aucune adresse en clair, nulle part dans la charge utile.
        assertThat(rows.toString()).doesNotContain(email);
    }

    @Test
    void theReportExportsInTheThreeFormatsWithoutAnyClearEmail() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        String email = "invite-" + UUID.randomUUID() + "@esic-connect.test";
        fx.exchange(HttpMethod.POST, "/api/v1/users",
                Map.of("email", email, "firstName", "Bo", "lastName", "Attente",
                        "role", "STUDENT"), admin);

        ResponseEntity<byte[]> csv = fx.bytes(HttpMethod.GET,
                "/api/v1/reports/pending-invitations/export", admin);
        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        String content = new String(csv.getBody(), StandardCharsets.UTF_8);
        assertThat(content).startsWith("﻿");
        assertThat(content.split("\r\n", 2)[0]).contains("Nom").contains("Adresse (masquée)");
        assertThat(content).doesNotContain(email);

        assertThat(fx.bytes(HttpMethod.GET,
                "/api/v1/reports/pending-invitations/export?format=xlsx", admin)
                .getBody()[0]).isEqualTo((byte) 'P');
        assertThat(new String(fx.bytes(HttpMethod.GET,
                "/api/v1/reports/pending-invitations/export?format=pdf", admin).getBody(),
                0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void anUnknownFormatIsRefused() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        ResponseEntity<Map<String, Object>> response = fx.exchange(HttpMethod.GET,
                "/api/v1/reports/pending-invitations/export?format=docx", null, admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("INVITATION_INVALID_EXPORT_FORMAT");
    }

    @Test
    void teachersStudentsAndAnonymousCallersAreRefused() {
        S11TestFixture.Account teacher = fx.account(RoleCode.TEACHER);
        S11TestFixture.Account student = fx.account(RoleCode.STUDENT);
        for (String path : List.of("/api/v1/reports/pending-invitations",
                "/api/v1/reports/pending-invitations/export")) {
            assertThat(fx.exchange(HttpMethod.GET, path, null, fx.tokenFor(teacher)).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(fx.exchange(HttpMethod.GET, path, null, fx.tokenFor(student)).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(fx.exchange(HttpMethod.GET, path, null, null).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private List<Map<String, Object>> rows(String token) {
        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                RequestEntity.get(URI.create("/api/v1/reports/pending-invitations"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
