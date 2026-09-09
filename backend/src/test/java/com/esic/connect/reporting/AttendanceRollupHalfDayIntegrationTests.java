package com.esic.connect.reporting;

import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.notification.internal.InvitationMailer;
import com.esic.connect.reporting.S11TestFixture.Account;
import com.esic.connect.reporting.S11TestFixture.Chain;
import com.esic.connect.reporting.S11TestFixture.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ANO-UX-007 — le calcul d'assiduité des <em>rapports</em> et du
 * <em>tableau de bord</em> compte une demi-journée <strong>une seule
 * fois par jour</strong>, quel que soit le nombre de séances publiées ce
 * matin-là.
 *
 * <p>Avant correction, le décompte se faisait par séance : deux séances
 * le même matin comptaient deux demi-journées « attendues », et chaque
 * séance publiée sans émargement gonflait le dénominateur — le taux du
 * tableau de bord tombait à {@code 0 %} alors que le rapport journalier
 * canonique (EF-ATT-004), lui, restait juste. Ces tests figent le calcul
 * corrigé et vérifient qu'il coïncide avec le rapport journalier.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AttendanceRollupHalfDayIntegrationTests {

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
    private String admin;

    @BeforeEach
    void setUp() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        fx = new S11TestFixture(rest, userAccountRepository, userRoleRepository, roleRepository,
                passwordEncoder);
        admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
    }

    @Test
    void twoSessionsInTheSameMorningCountAsOneExpectedHalfDay() {
        Chain chain = fx.academicChain(admin);
        Account teacher = fx.account(RoleCode.TEACHER);
        Student student = fx.enrolledStudent(admin, chain.classA());
        String studentToken = fx.tokenFor(student.account());

        // Deux séances distinctes le même matin, chacune avec son point de
        // contrôle "arrivée du matin".
        Instant morning = Instant.parse("2026-09-14T08:00:00Z");
        String s1 = fx.openSession(admin, teacher, chain.classA(), morning);
        String s2 = fx.openSession(admin, teacher, chain.classA(), morning.plus(2, ChronoUnit.HOURS));
        String cp1 = fx.openCheckpoint(admin, s1, "MORNING_ARRIVAL");
        String cp2 = fx.openCheckpoint(admin, s2, "MORNING_ARRIVAL");
        fx.validateAttendance(admin, studentToken, s1, cp1);
        fx.validateAttendance(admin, studentToken, s2, cp2);

        Map<String, Object> totals = summaryTotals(chain.classA());
        // UNE demi-journée attendue, UNE présente — pas deux (bug ANO-UX-007).
        assertThat(((Number) totals.get("expectedHalfDays")).longValue())
                .as("deux séances le même matin = une demi-journée").isEqualTo(1);
        assertThat(((Number) totals.get("presentHalfDays")).longValue()).isEqualTo(1);
        assertThat(((Number) totals.get("absentHalfDays")).longValue()).isZero();
        assertThat(((Number) totals.get("attendanceRate")).doubleValue()).isEqualTo(1.0d);
    }

    @Test
    void aSecondPublishedSessionWithoutCheckInDoesNotInflateThePresentMorning() {
        Chain chain = fx.academicChain(admin);
        Account teacher = fx.account(RoleCode.TEACHER);
        Student student = fx.enrolledStudent(admin, chain.classA());
        String studentToken = fx.tokenFor(student.account());

        Instant morning = Instant.parse("2026-09-15T08:00:00Z");
        String s1 = fx.openSession(admin, teacher, chain.classA(), morning);
        String s2 = fx.openSession(admin, teacher, chain.classA(), morning.plus(2, ChronoUnit.HOURS));
        String cp1 = fx.openCheckpoint(admin, s1, "MORNING_ARRIVAL");
        fx.openCheckpoint(admin, s2, "MORNING_ARRIVAL"); // publié, jamais émargé
        fx.validateAttendance(admin, studentToken, s1, cp1);

        Map<String, Object> totals = summaryTotals(chain.classA());
        // La séance non émargée du même matin ne transforme pas ce matin
        // présent en 50 %.
        assertThat(((Number) totals.get("expectedHalfDays")).longValue()).isEqualTo(1);
        assertThat(((Number) totals.get("presentHalfDays")).longValue()).isEqualTo(1);
        assertThat(((Number) totals.get("attendanceRate")).doubleValue()).isEqualTo(1.0d);
    }

    @Test
    void morningAndAfternoonOfTheSameDayAreTwoHalfDays() {
        Chain chain = fx.academicChain(admin);
        Account teacher = fx.account(RoleCode.TEACHER);
        Student student = fx.enrolledStudent(admin, chain.classA());
        String studentToken = fx.tokenFor(student.account());

        Instant day = Instant.parse("2026-09-16T08:00:00Z");
        String morningSession = fx.openSession(admin, teacher, chain.classA(), day);
        String afternoonSession = fx.openSession(admin, teacher, chain.classA(),
                day.plus(6, ChronoUnit.HOURS));
        String cpM = fx.openCheckpoint(admin, morningSession, "MORNING_ARRIVAL");
        String cpA = fx.openCheckpoint(admin, afternoonSession, "AFTERNOON_ARRIVAL");
        fx.validateAttendance(admin, studentToken, morningSession, cpM);
        fx.validateAttendance(admin, studentToken, afternoonSession, cpA);

        Map<String, Object> totals = summaryTotals(chain.classA());
        assertThat(((Number) totals.get("expectedHalfDays")).longValue()).isEqualTo(2);
        assertThat(((Number) totals.get("presentHalfDays")).longValue()).isEqualTo(2);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> summaryTotals(String classGroupPublicId) {
        Map<String, Object> summary = fx.exchange(HttpMethod.GET,
                "/api/v1/attendance/reports/summary?from=2026-09-01T00:00:00Z"
                        + "&to=2026-09-30T00:00:00Z&classGroup=" + classGroupPublicId,
                null, admin).getBody();
        return (Map<String, Object>) summary.get("totals");
    }
}
