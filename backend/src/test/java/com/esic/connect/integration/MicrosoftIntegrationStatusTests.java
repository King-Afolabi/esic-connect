package com.esic.connect.integration;

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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégrations Microsoft (EF-INT-002, EF-INT-003).
 *
 * <p>Sans identifiants configurés, le produit doit <strong>déclarer</strong>
 * qu'aucune intégration n'est active — pas en simuler une. C'est la même
 * politique que Turnstile (T-08) et la poussée web (T-13).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MicrosoftIntegrationStatusTests {

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
    @Autowired
    private com.esic.connect.integration.MeetingProvider meetingProvider;
    @Autowired
    private com.esic.connect.integration.ExternalCalendarWriter calendarWriter;

    private S11TestFixture fx;

    @BeforeEach
    void setUp() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        fx = new S11TestFixture(rest, userAccountRepository, userRoleRepository,
                roleRepository, passwordEncoder);
    }

    @Test
    void withoutCredentialsTheProductDeclaresNoIntegrationRatherThanFakingOne() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        ResponseEntity<Map<String, Object>> status = fx.exchange(HttpMethod.GET,
                "/api/v1/integrations/microsoft/status", null, admin);
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status.getBody().get("meetingActive")).isEqualTo(false);
        assertThat(status.getBody().get("calendarActive")).isEqualTo(false);
        assertThat(status.getBody().get("meetingProvider")).isEqualTo("none");
        assertThat(status.getBody().get("notice").toString())
                .contains("Aucune intégration Microsoft n'est configurée");
    }

    @Test
    void theInactiveAdaptersReturnNothingRatherThanAPlausibleLink() {
        assertThat(meetingProvider.isActive()).isFalse();
        assertThat(calendarWriter.isActive()).isFalse();
        // Un lien de réunion qui ne mène nulle part est pire qu'une absence
        // de lien : on ne s'en aperçoit qu'à l'heure du cours.
        assertThat(meetingProvider.createMeeting(new com.esic.connect.integration.MeetingProvider
                .MeetingRequest("Séance", java.time.Instant.now(),
                java.time.Instant.now().plusSeconds(3600), "quelconque"))).isEmpty();
        assertThat(calendarWriter.upsertEvent(new com.esic.connect.integration.ExternalCalendarWriter
                .CalendarEvent(null, "quelconque", "Séance", null, null,
                java.time.Instant.now(), java.time.Instant.now().plusSeconds(3600)))).isEmpty();
    }

    @Test
    void theStatusRouteIsClosedToTeachersStudentsAndAnonymousCallers() {
        S11TestFixture.Account teacher = fx.account(RoleCode.TEACHER);
        S11TestFixture.Account student = fx.account(RoleCode.STUDENT);
        String path = "/api/v1/integrations/microsoft/status";
        assertThat(fx.exchange(HttpMethod.GET, path, null, fx.tokenFor(teacher)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(fx.exchange(HttpMethod.GET, path, null, fx.tokenFor(student)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(fx.exchange(HttpMethod.GET, path, null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
