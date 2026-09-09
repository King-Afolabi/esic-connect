package com.esic.connect.audit;

import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.notification.internal.InvitationMailer;
import com.esic.connect.reporting.S11TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consultation et export de la piste d'audit (EF-AUD-002 ;
 * docs/02 §23.3, §23.4).
 *
 * <p>Vérifie que la piste est lisible et filtrable par l'administration,
 * exportable dans les trois formats, refusée à tous les autres rôles,
 * et qu'elle <strong>ne comporte ni adresse IP ni colonne JSON brute</strong>.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuditQueryIntegrationTests {

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
    void anAdministratorReadsThePaginatedAuditTrail() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        // Une opération auditée : création d'une chaîne académique.
        fx.academicChain(admin);

        Map<String, Object> page = list(admin, "?size=20");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
        assertThat(content).isNotEmpty();
        Map<String, Object> row = content.get(0);
        assertThat(row).containsKeys("occurredAt", "action", "category", "resourceType", "result");
        // §23.3 : jamais d'adresse IP, jamais de colonne JSON brute.
        assertThat(row).doesNotContainKeys("oldValuesJson", "newValuesJson", "metadataJson",
                "ipAddress", "clientIp");
        assertThat(page.toString()).doesNotContain("127.0.0.1");
    }

    @Test
    void theTrailIsFilterableByActionAndFacetsListWhatExists() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        fx.academicChain(admin);

        Map<String, Object> facets = getMap(admin, "/api/v1/audit-events/facets");
        @SuppressWarnings("unchecked")
        List<String> actions = (List<String>) facets.get("actions");
        assertThat(actions).isNotEmpty();

        String someAction = actions.get(0);
        Map<String, Object> filtered = list(admin, "?action=" + someAction + "&size=50");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) filtered.get("content");
        assertThat(content).isNotEmpty();
        assertThat(content).allSatisfy(row -> assertThat(row.get("action")).isEqualTo(someAction));
    }

    @Test
    void theTrailExportsInTheThreeFormats() throws Exception {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        fx.academicChain(admin);

        ResponseEntity<byte[]> csv = fx.bytes(HttpMethod.GET, "/api/v1/audit-events/export", admin);
        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        String content = new String(csv.getBody(), StandardCharsets.UTF_8);
        // Le CSV n'a aucun préambule : la ligne 1 est l'en-tête.
        assertThat(content).startsWith("﻿");
        assertThat(content.split("\r\n", 2)[0])
                .contains("Date").contains("Acteur").contains("Action").contains("Résultat");

        ResponseEntity<byte[]> xlsx = fx.bytes(HttpMethod.GET,
                "/api/v1/audit-events/export?format=xlsx", admin);
        assertThat(xlsx.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(xlsx.getBody()[0]).isEqualTo((byte) 'P');

        ResponseEntity<byte[]> pdf = fx.bytes(HttpMethod.GET,
                "/api/v1/audit-events/export?format=pdf", admin);
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(pdf.getBody(), 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void anUnknownExportFormatOrMalformedFilterProducesAFourHundred() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        ResponseEntity<Map<String, Object>> badFormat = fx.exchange(HttpMethod.GET,
                "/api/v1/audit-events/export?format=docx", null, admin);
        assertThat(badFormat.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badFormat.getBody().get("code")).isEqualTo("AUDIT_INVALID_FORMAT");

        ResponseEntity<Map<String, Object>> badFilter = fx.exchange(HttpMethod.GET,
                "/api/v1/audit-events?actor=pas-un-uuid", null, admin);
        assertThat(badFilter.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badFilter.getBody().get("code")).isEqualTo("AUDIT_INVALID_FILTER");
    }

    @Test
    void everyOtherRoleIsRefused() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain chain = fx.academicChain(admin);
        S11TestFixture.Account manager = fx.account(RoleCode.PEDAGOGICAL_MANAGER);
        fx.assignManager(admin, manager, chain.program());
        S11TestFixture.Account schoolAdmin = fx.account(RoleCode.SCHOOL_ADMINISTRATION);
        S11TestFixture.Account teacher = fx.account(RoleCode.TEACHER);
        S11TestFixture.Student student = fx.enrolledStudent(admin, chain.classA());

        for (String token : List.of(fx.tokenFor(manager), fx.tokenFor(schoolAdmin),
                fx.tokenFor(teacher), fx.tokenFor(student.account()))) {
            assertThat(fx.exchange(HttpMethod.GET, "/api/v1/audit-events", null, token).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(fx.exchange(HttpMethod.GET, "/api/v1/audit-events/export", null, token)
                    .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(fx.exchange(HttpMethod.GET, "/api/v1/audit-events", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theTrailOffersNoWayToWriteOrDelete() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        Map<String, Object> page = list(admin, "?size=1");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
        org.junit.jupiter.api.Assumptions.assumeFalse(content.isEmpty(),
                "aucune trace d'audit en base : rien à tenter de supprimer");
        String publicId = content.get(0).get("publicId").toString();

        // §23.4 : « ne peut être modifié ni effacé ». Aucune route
        // n'existe, et l'absence se traduit par un 404 ou un 405 —
        // jamais par un 500, qui signalerait une panne serveur là où il
        // n'y a qu'un appel client incorrect (docs/02 §30.1).
        assertThat(fx.exchange(HttpMethod.DELETE, "/api/v1/audit-events/" + publicId, null, admin)
                .getStatusCode().value()).isIn(404, 405);
        ResponseEntity<Map<String, Object>> write = fx.exchange(HttpMethod.POST,
                "/api/v1/audit-events", Map.of("action", "X"), admin);
        assertThat(write.getStatusCode().value()).isIn(404, 405);
        if (write.getStatusCode().value() == 405) {
            // RFC 9110 §15.5.6 : un 405 doit annoncer les méthodes admises.
            assertThat(write.getHeaders().getAllow()).contains(HttpMethod.GET);
        }
    }

    // ------------------------------------------------------------------

    private Map<String, Object> list(String token, String query) {
        return getMap(token, "/api/v1/audit-events" + query);
    }

    private Map<String, Object> getMap(String token, String path) {
        ResponseEntity<Map<String, Object>> r = fx.exchange(HttpMethod.GET, path, null, token);
        assertThat(r.getStatusCode()).as("GET " + path + " -> " + r.getBody()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }
}
