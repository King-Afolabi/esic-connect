package com.esic.connect.reporting;

import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.notification.internal.InvitationMailer;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
 * Tableaux de bord complets du responsable pédagogique et de
 * l'administration (EF-REP-007, EF-REP-008 ; docs/02 §22.6), et coût SQL
 * borné (dette T-03).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
class DashboardCardsIntegrationTests {

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
    private EntityManagerFactory entityManagerFactory;

    private S11TestFixture fx;

    @BeforeEach
    void setUp() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        fx = new S11TestFixture(rest, userAccountRepository, userRoleRepository,
                roleRepository, passwordEncoder);
    }

    @Test
    void theManagerCardCarriesTheIndicatorsTheSpecificationAsksFor() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain chain = fx.academicChain(admin);
        S11TestFixture.Account manager = fx.account(RoleCode.PEDAGOGICAL_MANAGER);
        fx.assignManager(admin, manager, chain.program());
        S11TestFixture.Account teacher = fx.account(RoleCode.TEACHER);
        S11TestFixture.Student student = fx.enrolledStudent(admin, chain.classA());

        String sessionId = fx.openSession(admin, teacher, chain.classA(), Instant.now().minusSeconds(1800));
        String checkpoint = fx.openCheckpoint(admin, sessionId, "MORNING_ARRIVAL");
        fx.validateAttendance(admin, fx.tokenFor(student.account()), sessionId, checkpoint);

        Map<String, Object> card = section(dashboard(fx.tokenFor(manager)), "manager");
        assertThat(card).containsKeys("classCount", "attendanceRate", "lateCount",
                "unjustifiedAbsenceHalfDays", "classRates", "pendingJustifications",
                "openClaims", "pendingActivations", "periodFrom", "periodTo");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> classRates = (List<Map<String, Object>>) card.get("classRates");
        // EF-REP-008 : le tableau équivalent d'un graphique est cette liste.
        assertThat(classRates).isNotEmpty();
        assertThat(classRates.get(0)).containsKeys("label", "expectedHalfDays", "presentHalfDays",
                "absentHalfDays", "excusedHalfDays", "lateCount", "attendanceRate");
        assertThat(classRates).extracting(r -> r.get("label")).contains(chain.classACode());
    }

    @Test
    void theManagerCardNeverShowsAClassOutsideTheirScope() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain mine = fx.academicChain(admin);
        S11TestFixture.Chain other = fx.academicChain(admin);
        S11TestFixture.Account manager = fx.account(RoleCode.PEDAGOGICAL_MANAGER);
        fx.assignManager(admin, manager, mine.program());
        S11TestFixture.Account teacher = fx.account(RoleCode.TEACHER);

        fx.enrolledStudent(admin, other.classA());
        String outsideSession = fx.openSession(admin, teacher, other.classA(),
                Instant.now().minusSeconds(1800));
        fx.openCheckpoint(admin, outsideSession, "MORNING_ARRIVAL");

        Map<String, Object> card = section(dashboard(fx.tokenFor(manager)), "manager");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> classRates = (List<Map<String, Object>>) card.get("classRates");
        assertThat(classRates).extracting(r -> r.get("label")).doesNotContain(other.classACode());
        assertThat(card.toString()).doesNotContain(other.classA());
    }

    @Test
    void theAdministrationCardCompareProgramsAndReportsJustificationThroughput() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain chain = fx.academicChain(admin);
        S11TestFixture.Account teacher = fx.account(RoleCode.TEACHER);
        S11TestFixture.Student student = fx.enrolledStudent(admin, chain.classA());
        String sessionId = fx.openSession(admin, teacher, chain.classA(), Instant.now().minusSeconds(1800));
        String checkpoint = fx.openCheckpoint(admin, sessionId, "MORNING_ARRIVAL");
        fx.validateAttendance(admin, fx.tokenFor(student.account()), sessionId, checkpoint);

        Map<String, Object> card = section(dashboard(admin), "administration");
        assertThat(card).containsKeys("activeAccounts", "suspendedAccounts", "pendingActivation",
                "archivedAccounts", "expiredInvitations", "pendingJustifications",
                "decidedJustifications", "medianDecisionDelayHours", "globalAttendanceRate",
                "programRates", "recentExports", "recentAuditOperations");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> programRates = (List<Map<String, Object>>) card.get("programRates");
        assertThat(programRates).isNotEmpty();
        assertThat(programRates).extracting(r -> r.get("label")).contains(chain.programCode());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> auditLines = (List<Map<String, Object>>) card.get("recentAuditOperations");
        assertThat(auditLines).isNotEmpty();
        assertThat(auditLines.get(0)).containsKeys("occurredAt", "action", "result");
        // Le tableau de bord ne déverse pas le contenu de l'audit.
        assertThat(auditLines.get(0)).doesNotContainKeys("reason", "oldValuesJson", "newValuesJson");
    }

    @Test
    void medianDecisionDelayIsNullRatherThanZeroWhenNothingWasDecided() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        Map<String, Object> card = section(dashboard(admin), "administration");
        // Aucune décision sur la fenêtre : « aucun traitement » et
        // « traitement instantané » ne doivent pas s'écrire pareil.
        if (Long.parseLong(card.get("decidedJustifications").toString()) == 0L) {
            assertThat(card.get("medianDecisionDelayHours")).isNull();
        }
    }

    @Test
    void theManagerDashboardCostDoesNotGrowWithTheNumberOfSessions() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain chain = fx.academicChain(admin);
        S11TestFixture.Account manager = fx.account(RoleCode.PEDAGOGICAL_MANAGER);
        fx.assignManager(admin, manager, chain.program());
        S11TestFixture.Account teacher = fx.account(RoleCode.TEACHER);
        fx.enrolledStudent(admin, chain.classA());
        String managerToken = fx.tokenFor(manager);

        Instant base = Instant.now().plusSeconds(3600);
        for (int i = 0; i < 3; i++) {
            fx.openSession(admin, teacher, chain.classA(), base.plusSeconds(i * 7200L));
        }
        dashboard(managerToken);
        long withThree = queriesFor(() -> dashboard(managerToken));

        for (int i = 3; i < 9; i++) {
            fx.openSession(admin, teacher, chain.classA(), base.plusSeconds(i * 7200L));
        }
        long withNine = queriesFor(() -> dashboard(managerToken));

        // Dette T-03 : le coût ne doit pas suivre le nombre de séances.
        // Six séances de plus ne doivent pas coûter six requêtes de plus.
        assertThat(withNine).as("requêtes avec 9 séances (%d) vs 3 séances (%d)", withNine, withThree)
                .isLessThan(withThree + 6);
    }

    // ------------------------------------------------------------------

    private long queriesFor(Runnable action) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        action.run();
        return statistics.getPrepareStatementCount();
    }

    private Map<String, Object> dashboard(String token) {
        ResponseEntity<Map<String, Object>> r = fx.exchange(HttpMethod.GET,
                "/api/v1/me/dashboard", null, token);
        assertThat(r.getStatusCode()).as("GET /me/dashboard -> " + r.getBody()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> dashboard, String name) {
        Object section = dashboard.get(name);
        assertThat(section).as("section « " + name + " » du tableau de bord").isNotNull();
        return (Map<String, Object>) section;
    }
}
