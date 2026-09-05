package com.esic.connect.search;

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
 * Recherche globale (EF-USER-009 ; docs/02 §22.7).
 *
 * <p>Vérifie le cloisonnement de périmètre, la borne minimale de saisie,
 * l'absence de l'adresse électronique comme critère, et le refus des
 * rôles qui n'ont pas de périmètre à parcourir.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GlobalSearchIntegrationTests {

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
    void anAdministratorFindsClassesStudentsAndSessions() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain chain = fx.academicChain(admin);
        S11TestFixture.Account teacher = fx.account(RoleCode.TEACHER);
        String marker = S11TestFixture.code();
        S11TestFixture.Account studentAccount =
                fx.namedAccount("Camille", "Zerbinatti" + marker, RoleCode.STUDENT);
        fx.enrolledStudent(admin, chain.classA(), studentAccount);
        fx.openTitledSession(admin, teacher, chain.classA(), Instant.now().plusSeconds(3600),
                "Algorithmique " + marker);

        Map<String, Object> byClass = search(admin, chain.classACode());
        assertThat(types(byClass)).contains("CLASS_GROUP");

        Map<String, Object> byStudent = search(admin, "Zerbinatti" + marker);
        assertThat(types(byStudent)).contains("STUDENT");
        assertThat(labels(byStudent)).anySatisfy(l -> assertThat(l).contains("Zerbinatti" + marker));

        Map<String, Object> bySession = search(admin, "Algorithmique " + marker);
        assertThat(types(bySession)).contains("SESSION");
    }

    @Test
    void aManagerNeverFindsAClassOutsideTheirScope() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain mine = fx.academicChain(admin);
        S11TestFixture.Chain other = fx.academicChain(admin);
        S11TestFixture.Account manager = fx.account(RoleCode.PEDAGOGICAL_MANAGER);
        fx.assignManager(admin, manager, mine.program());
        String managerToken = fx.tokenFor(manager);

        assertThat(labels(search(managerToken, mine.classACode()))).contains(mine.classACode());
        assertThat(labels(search(managerToken, other.classACode()))).doesNotContain(other.classACode());
        // L'administration, elle, voit les deux.
        assertThat(labels(search(admin, other.classACode()))).contains(other.classACode());
    }

    @Test
    void aManagerNeverFindsAStudentOrSessionOutsideTheirScope() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain mine = fx.academicChain(admin);
        S11TestFixture.Chain other = fx.academicChain(admin);
        S11TestFixture.Account manager = fx.account(RoleCode.PEDAGOGICAL_MANAGER);
        fx.assignManager(admin, manager, mine.program());
        S11TestFixture.Account teacher = fx.account(RoleCode.TEACHER);

        String marker = S11TestFixture.code();
        fx.enrolledStudent(admin, other.classA(),
                fx.namedAccount("Hors", "Perimetre" + marker, RoleCode.STUDENT));
        fx.openTitledSession(admin, teacher, other.classA(), Instant.now().plusSeconds(3600),
                "Cours hors périmètre " + marker);

        Map<String, Object> result = search(fx.tokenFor(manager), "Perimetre" + marker);
        assertThat(types(result)).doesNotContain("STUDENT");
        Map<String, Object> sessions = search(fx.tokenFor(manager), "hors périmètre " + marker);
        assertThat(types(sessions)).doesNotContain("SESSION");
    }

    @Test
    void aTooShortFragmentReturnsNothingAndSaysWhy() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        Map<String, Object> result = search(admin, "a");
        assertThat(results(result)).isEmpty();
        assertThat(result.get("notes").toString()).contains("au moins");
    }

    @Test
    void aWildcardIsNotAWayToListEverything() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        fx.academicChain(admin);
        // « %% » est traité comme du texte : les jokers saisis sont échappés.
        assertThat(results(search(admin, "%%"))).isEmpty();
    }

    @Test
    void anEmailIsNeverASearchCriterion() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain chain = fx.academicChain(admin);
        S11TestFixture.Student student = fx.enrolledStudent(admin, chain.classA());

        Map<String, Object> result = search(admin, student.account().email());
        assertThat(results(result)).isEmpty();
        assertThat(result.get("notes").toString()).contains("adresse électronique");
    }

    @Test
    void aTeacherAStudentAndAnAnonymousCallerAreRefused() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain chain = fx.academicChain(admin);
        S11TestFixture.Student student = fx.enrolledStudent(admin, chain.classA());
        S11TestFixture.Account teacher = fx.account(RoleCode.TEACHER);

        assertThat(raw(fx.tokenFor(teacher), "abc").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(raw(fx.tokenFor(student.account()), "abc").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(raw(null, "abc").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------

    private ResponseEntity<Map<String, Object>> raw(String token, String query) {
        return fx.exchange(HttpMethod.GET,
                "/api/v1/search?q=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8),
                null, token);
    }

    private Map<String, Object> search(String token, String query) {
        ResponseEntity<Map<String, Object>> r = raw(token, query);
        assertThat(r.getStatusCode()).as("GET /search?q=" + query + " -> " + r.getBody())
                .isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> results(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("results");
    }

    private static List<String> types(Map<String, Object> body) {
        return results(body).stream().map(r -> (String) r.get("type")).toList();
    }

    private static List<String> labels(Map<String, Object> body) {
        return results(body).stream().map(r -> (String) r.get("label")).toList();
    }
}
