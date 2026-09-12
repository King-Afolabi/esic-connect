package com.esic.connect.enrollment;

import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.notification.internal.InvitationMailer;
import com.esic.connect.reporting.S11TestFixture;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Périmètre de <strong>consultation</strong> des apprenants (bug
 * « apprenants invisibles pour le responsable pédagogique / le
 * formateur »).
 *
 * <p>Vérifie, par l'API réelle, que :
 * <ul>
 *   <li>un {@code PEDAGOGICAL_MANAGER} ne voit que les apprenants des
 *       classes de <em>ses</em> formations — jamais ceux d'une autre
 *       formation (liste filtrée, fiche hors périmètre → 404) ;</li>
 *   <li>un {@code TEACHER} ne voit que les apprenants des classes de
 *       <em>ses</em> séances ;</li>
 *   <li>un {@code TEACHER} sans séance ne voit aucun apprenant (page
 *       vide), jamais tous ;</li>
 *   <li>un {@code ADMIN} garde l'accès global ;</li>
 *   <li>l'écriture reste refusée au {@code PEDAGOGICAL_MANAGER} et au
 *       {@code TEACHER}.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RosterScopeIntegrationTests {

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
    private String adminToken;

    @BeforeEach
    void setUp() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        fx = new S11TestFixture(rest, userAccountRepository, userRoleRepository, roleRepository,
                passwordEncoder);
        adminToken = fx.tokenFor(fx.account(RoleCode.ADMIN));
    }

    @Test
    void pedagogicalManagerSeesOnlyStudentsOfItsOwnPrograms() {
        Chain mine = fx.academicChain(adminToken);
        Chain other = fx.academicChain(adminToken);

        Account manager = fx.account(RoleCode.PEDAGOGICAL_MANAGER);
        fx.assignManager(adminToken, manager, mine.program());
        String managerToken = fx.tokenFor(manager);

        Student inScope = fx.enrolledStudent(adminToken, mine.classA());
        Student otherClassSameProgram = fx.enrolledStudent(adminToken, mine.classB());
        Student outOfScope = fx.enrolledStudent(adminToken, other.classA());

        // Écran « Apprenants » (rôle STUDENT) : fiche dans le périmètre →
        // 200 ; hors périmètre → 404 (l'existence même est une information
        // à protéger — cahier §18.2), jamais 403.
        List<String> visibleStudents = studentUserIds(managerToken);
        assertThat(visibleStudents).contains(inScope.account().publicId(), otherClassSameProgram.account().publicId());
        assertThat(visibleStudents).doesNotContain(outOfScope.account().publicId());
        assertThat(status(managerToken, "/api/v1/students/" + inScope.account().publicId()))
                .isEqualTo(HttpStatus.OK);
        assertThat(status(managerToken, "/api/v1/students/" + outOfScope.account().publicId()))
                .isEqualTo(HttpStatus.NOT_FOUND);

        // Inscriptions : la liste ne remonte que le périmètre ; cibler
        // explicitement un apprenant hors périmètre ne fuite rien.
        ResponseEntity<Map<String, Object>> scopedEnrollments = get(managerToken,
                "/api/v1/enrollments?student=" + outOfScope.account().publicId());
        assertThat(scopedEnrollments.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) scopedEnrollments.getBody().get("totalElements")).intValue()).isZero();
    }

    @Test
    void teacherSeesOnlyStudentsOfTheClassesTheyTeach() {
        Chain chain = fx.academicChain(adminToken);
        Account teacher = fx.account(RoleCode.TEACHER);
        String teacherToken = fx.tokenFor(teacher);

        Student taught = fx.enrolledStudent(adminToken, chain.classA());
        Student notTaught = fx.enrolledStudent(adminToken, chain.classB());

        // Le formateur enseigne une séance de la classe A uniquement.
        fx.openSession(adminToken, teacher, chain.classA(),
                Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS));

        List<String> visibleStudents = studentUserIds(teacherToken);
        assertThat(visibleStudents).contains(taught.account().publicId());
        assertThat(visibleStudents).doesNotContain(notTaught.account().publicId());
        assertThat(status(teacherToken, "/api/v1/students/" + notTaught.account().publicId()))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void teacherWithoutSessionsSeesNoStudents() {
        Chain chain = fx.academicChain(adminToken);
        fx.enrolledStudent(adminToken, chain.classA());
        String teacherToken = fx.tokenFor(fx.account(RoleCode.TEACHER));

        // Périmètre vide ⇒ page vide sur l'écran « Apprenants »,
        // jamais tous les comptes STUDENT.
        ResponseEntity<Map<String, Object>> studentsPage = get(teacherToken, "/api/v1/students");
        assertThat(studentsPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) studentsPage.getBody().get("totalElements")).intValue()).isZero();
    }

    @Test
    void adminKeepsGlobalAccess() {
        Chain a = fx.academicChain(adminToken);
        Chain b = fx.academicChain(adminToken);
        Student s1 = fx.enrolledStudent(adminToken, a.classA());
        Student s2 = fx.enrolledStudent(adminToken, b.classA());

        List<String> visibleStudents = studentUserIds(adminToken);
        assertThat(visibleStudents).contains(s1.account().publicId(), s2.account().publicId());
    }

    // ------------------------------------------------------------------

    /** Écran « Apprenants » (rôle STUDENT) — pagination limitée au périmètre de l'appelant. */
    private List<String> studentUserIds(String token) {
        ResponseEntity<Map<String, Object>> page = get(token, "/api/v1/students?size=100");
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content =
                (List<Map<String, Object>>) page.getBody().get("content");
        return content.stream().map(row -> String.valueOf(row.get("userPublicId"))).toList();
    }

    private ResponseEntity<Map<String, Object>> get(String token, String path) {
        return rest.exchange(RequestEntity.get(URI.create(path))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<>() {
                });
    }

    private HttpStatus status(String token, String path) {
        return (HttpStatus) rest.exchange(RequestEntity.get(URI.create(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(), String.class)
                .getStatusCode();
    }
}
