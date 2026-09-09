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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flux iCalendar (EF-INT-001 ; AC-034 — « le flux iCalendar d'un
 * utilisateur ne contient que son propre planning et se révoque »).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CalendarFeedIntegrationTests {

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
    void aStudentFeedContainsOnlyTheirOwnPlanning() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain chain = fx.academicChain(admin);
        S11TestFixture.Account teacher = fx.account(RoleCode.TEACHER);
        S11TestFixture.Student mine = fx.enrolledStudent(admin, chain.classA());
        fx.enrolledStudent(admin, chain.classB());

        Instant startsAt = Instant.now().plusSeconds(3600);
        String mySession = fx.openTitledSession(admin, teacher, chain.classA(), startsAt, "Cours de moi");
        String otherSession = fx.openTitledSession(admin, teacher, chain.classB(),
                startsAt.plusSeconds(7200), "Cours des autres");

        String feedPath = createSubscription(fx.tokenFor(mine.account()), "Mon agenda");
        ResponseEntity<String> feed = fx.text(HttpMethod.GET, feedPath, null);
        assertThat(feed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(feed.getHeaders().getContentType().toString()).startsWith("text/calendar");

        String ics = feed.getBody();
        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n");
        assertThat(ics).endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("PRODID:-//ESIC//ESIC Connect//FR");
        // AC-034 : uniquement son propre planning.
        assertThat(ics).contains("UID:" + mySession + "@esic-connect");
        assertThat(ics).doesNotContain(otherSession);
        assertThat(ics).contains("Cours de moi");
        assertThat(ics).doesNotContain("Cours des autres");
        // Aucune donnée d'assiduité ni identité d'un tiers.
        assertThat(ics).doesNotContain("@esic-connect.test");
        assertThat(ics).doesNotContain("PRESENT");
    }

    @Test
    void aTeacherFeedContainsTheSessionsTheyTeach() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain chain = fx.academicChain(admin);
        S11TestFixture.Account mine = fx.account(RoleCode.TEACHER);
        S11TestFixture.Account other = fx.account(RoleCode.TEACHER);

        Instant startsAt = Instant.now().plusSeconds(3600);
        String mySession = fx.openTitledSession(admin, mine, chain.classA(), startsAt, "Mon cours");
        String otherSession = fx.openTitledSession(admin, other, chain.classA(),
                startsAt.plusSeconds(7200), "Le cours d'un autre");

        String feedPath = createSubscription(fx.tokenFor(mine), null);
        String ics = fx.text(HttpMethod.GET, feedPath, null).getBody();
        assertThat(ics).contains(mySession).doesNotContain(otherSession);
    }

    @Test
    void aRevokedSubscriptionAnswersGoneAndStopsServingThePlanning() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain chain = fx.academicChain(admin);
        S11TestFixture.Student student = fx.enrolledStudent(admin, chain.classA());
        String token = fx.tokenFor(student.account());

        String feedPath = createSubscription(token, "À révoquer");
        assertThat(fx.text(HttpMethod.GET, feedPath, null).getStatusCode()).isEqualTo(HttpStatus.OK);

        String subscriptionId = subscriptions(token).get(0).get("publicId").toString();
        assertThat(fx.exchange(HttpMethod.DELETE,
                "/api/v1/me/calendar-subscriptions/" + subscriptionId, null, token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        // 410 et non 404 : la révocation doit se constater.
        ResponseEntity<String> revoked = fx.text(HttpMethod.GET, feedPath, null);
        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(revoked.getBody()).contains("INT_FEED_REVOKED");

        // La révocation est une date, pas une suppression : la ligne reste.
        assertThat(subscriptions(token).get(0).get("revokedAt")).isNotNull();
    }

    @Test
    void aWrongTokenAnswersLikeAnUnknownFeed() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain chain = fx.academicChain(admin);
        S11TestFixture.Student student = fx.enrolledStudent(admin, chain.classA());
        String feedPath = createSubscription(fx.tokenFor(student.account()), null);
        String feedKey = feedPath.substring(feedPath.indexOf("/calendar/") + "/calendar/".length(),
                feedPath.indexOf(".ics"));

        ResponseEntity<String> wrongToken = fx.text(HttpMethod.GET,
                "/api/v1/calendar/" + feedKey + ".ics?token=faux-jeton", null);
        ResponseEntity<String> unknownKey = fx.text(HttpMethod.GET,
                "/api/v1/calendar/cle-inconnue.ics?token=faux-jeton", null);
        assertThat(wrongToken.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknownKey.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(wrongToken.getBody()).contains("INT_NOT_FOUND");
        assertThat(unknownKey.getBody()).contains("INT_NOT_FOUND");
    }

    @Test
    void aFeedTokenIsShownOnceAndNeverAgain() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain chain = fx.academicChain(admin);
        S11TestFixture.Student student = fx.enrolledStudent(admin, chain.classA());
        String token = fx.tokenFor(student.account());

        String feedPath = createSubscription(token, "Mon agenda");
        String secret = feedPath.substring(feedPath.indexOf("token=") + "token=".length());

        // Ni la liste ni aucun champ ne réaffiche le jeton.
        assertThat(subscriptions(token).toString()).doesNotContain(secret);
    }

    @Test
    void aSubscriptionBelongingToSomeoneElseIsNotFound() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain chain = fx.academicChain(admin);
        S11TestFixture.Student owner = fx.enrolledStudent(admin, chain.classA());
        S11TestFixture.Student stranger = fx.enrolledStudent(admin, chain.classA());

        createSubscription(fx.tokenFor(owner.account()), null);
        String subscriptionId = subscriptions(fx.tokenFor(owner.account())).get(0).get("publicId").toString();

        // 404 et non 403 : l'existence de l'abonnement d'autrui n'a pas à
        // être confirmée.
        assertThat(fx.exchange(HttpMethod.DELETE,
                "/api/v1/me/calendar-subscriptions/" + subscriptionId, null,
                fx.tokenFor(stranger.account())).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theAuthenticatedDownloadServesTheSamePlanning() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain chain = fx.academicChain(admin);
        S11TestFixture.Account teacher = fx.account(RoleCode.TEACHER);
        S11TestFixture.Student student = fx.enrolledStudent(admin, chain.classA());
        String sessionId = fx.openTitledSession(admin, teacher, chain.classA(),
                Instant.now().plusSeconds(3600), "Cours téléchargé");

        ResponseEntity<String> download = fx.text(HttpMethod.GET,
                "/api/v1/me/calendar-subscriptions/download", fx.tokenFor(student.account()));
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getBody()).contains(sessionId);
        assertThat(download.getHeaders().getFirst("Content-Disposition")).contains("attachment");
    }

    @Test
    void anAnonymousCallerCannotListOrCreateSubscriptions() {
        assertThat(fx.exchange(HttpMethod.GET, "/api/v1/me/calendar-subscriptions", null, null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(fx.exchange(HttpMethod.POST, "/api/v1/me/calendar-subscriptions",
                Map.of("label", "x"), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------

    private String createSubscription(String token, String label) {
        ResponseEntity<Map<String, Object>> created = fx.exchange(HttpMethod.POST,
                "/api/v1/me/calendar-subscriptions",
                label == null ? Map.of() : Map.of("label", label), token);
        assertThat(created.getStatusCode()).as("création d'abonnement -> " + created.getBody())
                .isEqualTo(HttpStatus.CREATED);
        String feedPath = (String) created.getBody().get("feedPath");
        assertThat(feedPath).startsWith("/api/v1/calendar/").contains(".ics?token=");
        return feedPath;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> subscriptions(String token) {
        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                org.springframework.http.RequestEntity.get(java.net.URI.create(
                                "/api/v1/me/calendar-subscriptions"))
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build(),
                new org.springframework.core.ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
