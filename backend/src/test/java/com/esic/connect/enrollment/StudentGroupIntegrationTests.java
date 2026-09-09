package com.esic.connect.enrollment;

import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.notification.internal.InvitationMailer;
import com.esic.connect.support.AuthTestSupport;
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
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Groupes temporaires d'apprenants (EF-ACA-007 ; docs/02 §6.5).
 *
 * <p>Le point vérifié en priorité : un groupe rassemble des apprenants de
 * <strong>classes différentes</strong> sans jamais toucher à leur classe
 * principale (RG-022), et le retrait d'un membre est logique — la trace
 * subsiste.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StudentGroupIntegrationTests {

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
    private TestRestTemplate rest;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void unGroupeEstCreeLuModifiePuisArchiveEtRestaure() {
        String admin = tokenFor(RoleCode.ADMIN);
        Fixture fixture = fixture(admin);

        Map<String, Object> group = created("/api/v1/student-groups", body(
                "code", "GRP-" + shortId(), "name", "Anglais renforcé",
                "academicYearPublicId", fixture.yearId(),
                "programPublicId", fixture.programId()), admin);
        String id = (String) group.get("id");
        assertThat(group.get("status")).isEqualTo("ACTIVE");
        assertThat(group.get("programId")).isEqualTo(fixture.programId());
        assertThat(group.get("academicYearId")).isEqualTo(fixture.yearId());
        assertThat(group.get("memberCount")).isEqualTo(0);

        Map<String, Object> updated = exchange(HttpMethod.PATCH, "/api/v1/student-groups/" + id,
                body("name", "Anglais renforcé — S1"), admin).getBody();
        assertThat(updated.get("name")).isEqualTo("Anglais renforcé — S1");

        assertThat(exchange(HttpMethod.POST, "/api/v1/student-groups/" + id + "/archive",
                Map.of(), admin).getBody().get("status")).isEqualTo("ARCHIVED");
        assertThat(exchange(HttpMethod.POST, "/api/v1/student-groups/" + id + "/restore",
                Map.of(), admin).getBody().get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void unGroupeRassembleDesApprenantsDeClassesDifferentesSansToucherALeurClasse() {
        String admin = tokenFor(RoleCode.ADMIN);
        Fixture fixture = fixture(admin);
        String firstEnrollment = enroll(admin, fixture, fixture.firstClassId());
        String secondEnrollment = enroll(admin, fixture, fixture.secondClassId());
        String groupId = (String) created("/api/v1/student-groups", body(
                "code", "GRP-" + shortId(), "name", "Option projet",
                "academicYearPublicId", fixture.yearId(),
                "programPublicId", fixture.programId()), admin).get("id");

        ResponseEntity<List<Map<String, Object>>> added = addMembers(groupId,
                List.of(firstEnrollment, secondEnrollment), admin);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> members = members(groupId, admin);
        assertThat(members).hasSize(2);
        assertThat(members).extracting(member -> member.get("classGroupCode"))
                .doesNotHaveDuplicates();

        // La classe principale de chaque apprenant est intacte (RG-022).
        assertThat(getMap("/api/v1/enrollments/" + firstEnrollment, admin).get("classGroupPublicId"))
                .isEqualTo(fixture.firstClassId());
        assertThat(getMap("/api/v1/enrollments/" + secondEnrollment, admin).get("classGroupPublicId"))
                .isEqualTo(fixture.secondClassId());
    }

    @Test
    void ajouterDeuxFoisLeMemeApprenantNeCreePasDeDoublon() {
        String admin = tokenFor(RoleCode.ADMIN);
        Fixture fixture = fixture(admin);
        String enrollment = enroll(admin, fixture, fixture.firstClassId());
        String groupId = (String) created("/api/v1/student-groups", body(
                "code", "GRP-" + shortId(), "name", "Doublon",
                "academicYearPublicId", fixture.yearId(),
                "programPublicId", fixture.programId()), admin).get("id");

        addMembers(groupId, List.of(enrollment), admin);
        addMembers(groupId, List.of(enrollment), admin);

        // Un ajout redondant est ignoré, pas transformé en échec du lot.
        assertThat(members(groupId, admin)).hasSize(1);
    }

    @Test
    void leRetraitDUnMembreEstLogiqueEtLeGroupeRedevientAjoutable() {
        String admin = tokenFor(RoleCode.ADMIN);
        Fixture fixture = fixture(admin);
        String enrollment = enroll(admin, fixture, fixture.firstClassId());
        String groupId = (String) created("/api/v1/student-groups", body(
                "code", "GRP-" + shortId(), "name", "Retrait",
                "academicYearPublicId", fixture.yearId(),
                "programPublicId", fixture.programId()), admin).get("id");
        addMembers(groupId, List.of(enrollment), admin);
        String memberId = (String) members(groupId, admin).get(0).get("id");

        assertThat(exchange(HttpMethod.DELETE,
                "/api/v1/student-groups/" + groupId + "/members/" + memberId, null, admin)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(members(groupId, admin)).isEmpty();

        // Le retrait étant logique, un retour est possible sans buter sur
        // la contrainte d'unicité.
        addMembers(groupId, List.of(enrollment), admin);
        assertThat(members(groupId, admin)).hasSize(1);
    }

    @Test
    void unGroupeArchiveNAcceptePlusDeMembre() {
        String admin = tokenFor(RoleCode.ADMIN);
        Fixture fixture = fixture(admin);
        String enrollment = enroll(admin, fixture, fixture.firstClassId());
        String groupId = (String) created("/api/v1/student-groups", body(
                "code", "GRP-" + shortId(), "name", "Archivé",
                "academicYearPublicId", fixture.yearId(),
                "programPublicId", fixture.programId()), admin).get("id");
        exchange(HttpMethod.POST, "/api/v1/student-groups/" + groupId + "/archive", Map.of(), admin);

        ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.POST,
                "/api/v1/student-groups/" + groupId + "/members",
                body("enrollmentPublicIds", List.of(enrollment)), admin);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().get("code")).isEqualTo("ENR_GROUP_ARCHIVED");
    }

    @Test
    void unCodeDejaUtiliseDansLAnneeEstRefuse() {
        String admin = tokenFor(RoleCode.ADMIN);
        Fixture fixture = fixture(admin);
        String code = "GRP-" + shortId();
        created("/api/v1/student-groups", body("code", code, "name", "Premier",
                "academicYearPublicId", fixture.yearId(),
                "programPublicId", fixture.programId()), admin);

        ResponseEntity<Map<String, Object>> duplicate = exchange(HttpMethod.POST,
                "/api/v1/student-groups", body("code", code, "name", "Second",
                        "academicYearPublicId", fixture.yearId(),
                        "programPublicId", fixture.programId()), admin);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().get("code")).isEqualTo("ENR_DUPLICATE_GROUP_CODE");
    }

    @Test
    void unePeriodeIncoherenteEstRefusee() {
        String admin = tokenFor(RoleCode.ADMIN);
        Fixture fixture = fixture(admin);

        ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.POST, "/api/v1/student-groups",
                body("code", "GRP-" + shortId(), "name", "Période inversée",
                        "academicYearPublicId", fixture.yearId(),
                        "programPublicId", fixture.programId(),
                        "startsOn", "2027-01-10", "endsOn", "2026-12-01"), admin);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("code")).isEqualTo("ENR_INVALID_GROUP_PERIOD");
    }

    @Test
    void uneMatiereInconnueEstRefusee() {
        String admin = tokenFor(RoleCode.ADMIN);
        Fixture fixture = fixture(admin);

        ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.POST, "/api/v1/student-groups",
                body("code", "GRP-" + shortId(), "name", "Matière fantôme",
                        "academicYearPublicId", fixture.yearId(),
                        "programPublicId", fixture.programId(),
                        "subjectPublicId", UUID.randomUUID().toString()), admin);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refused.getBody().get("code")).isEqualTo("ENR_SUBJECT_NOT_FOUND");
    }

    @Test
    void unGroupeSeRattacheAUneMatiere() {
        String admin = tokenFor(RoleCode.ADMIN);
        Fixture fixture = fixture(admin);
        String subjectCode = "MAT-" + shortId();
        String subjectId = (String) created("/api/v1/subjects",
                body("code", subjectCode, "name", "Espagnol"), admin).get("id");

        Map<String, Object> group = created("/api/v1/student-groups", body(
                "code", "GRP-" + shortId(), "name", "Espagnol débutant",
                "academicYearPublicId", fixture.yearId(),
                "programPublicId", fixture.programId(),
                "subjectPublicId", subjectId), admin);

        assertThat(group.get("subjectId")).isEqualTo(subjectId);
        assertThat(group.get("subjectCode")).isEqualTo(subjectCode);
    }

    @Test
    void unApprenantNAccedePasAuxGroupes() {
        String student = tokenFor(RoleCode.STUDENT);

        assertThat(exchange(HttpMethod.GET, "/api/v1/student-groups", null, student).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void laLectureAnonymeEstRefusee() {
        assertThat(exchange(HttpMethod.GET, "/api/v1/student-groups", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unGroupeInconnuRepond404() {
        String admin = tokenFor(RoleCode.ADMIN);

        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET,
                "/api/v1/student-groups/" + UUID.randomUUID(), null, admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("ENR_STUDENT_GROUP_NOT_FOUND");
    }

    // ------------------------------------------------------------------

    /** Chaîne académique minimale : année, formation, niveau, promotion, deux classes. */
    private record Fixture(String yearId, String programId, String firstClassId, String secondClassId) {
    }

    private Fixture fixture(String admin) {
        String siteId = (String) created("/api/v1/sites", body(
                "code", "SITE-" + UUID.randomUUID(), "name", "Campus",
                "timeZoneId", "Europe/Paris"), admin).get("publicId");
        String yearId = (String) created("/api/v1/academic-years", body(
                "code", "AY-" + shortId(), "name", "2026-2027",
                "startDate", "2026-09-01", "endDate", "2027-08-31"), admin).get("publicId");
        String programId = (String) created("/api/v1/programs", body(
                "code", "PRG-" + shortId(), "name", "BTS SIO", "programType", "BTS"), admin)
                .get("publicId");
        String levelId = (String) created("/api/v1/programs/" + programId + "/levels", body(
                "code", "N1", "name", "BTS 1", "sequenceNumber", 1), admin).get("publicId");
        String promotionId = (String) created("/api/v1/promotions", body(
                "programPublicId", programId, "academicYearPublicId", yearId,
                "code", "P26", "name", "Promotion 2026"), admin).get("publicId");
        String firstClass = (String) created("/api/v1/class-groups", body(
                "promotionPublicId", promotionId, "programLevelPublicId", levelId,
                "sitePublicId", siteId, "code", "C1", "name", "Classe 1", "capacity", 24), admin)
                .get("publicId");
        String secondClass = (String) created("/api/v1/class-groups", body(
                "promotionPublicId", promotionId, "programLevelPublicId", levelId,
                "sitePublicId", siteId, "code", "C2", "name", "Classe 2", "capacity", 24), admin)
                .get("publicId");
        return new Fixture(yearId, programId, firstClass, secondClass);
    }

    /** Crée un compte apprenant, son profil et son inscription dans la classe indiquée. */
    private String enroll(String admin, Fixture fixture, String classId) {
        UserAccount student = persistUser(RoleCode.STUDENT);
        String profileId = (String) created("/api/v1/student-profiles", body(
                "userPublicId", student.getPublicId().toString(),
                "studentNumber", "ESIC-" + shortId()), admin).get("publicId");
        return (String) created("/api/v1/enrollments", body(
                "studentProfilePublicId", profileId,
                "classGroupPublicId", classId,
                "startDate", "2026-09-01"), admin).get("publicId");
    }

    /**
     * L'ajout de membres renvoie un TABLEAU JSON : il faut le type
     * correspondant, sinon la désérialisation échoue avant même
     * l'assertion.
     */
    private ResponseEntity<List<Map<String, Object>>> addMembers(String groupId,
                                                                 List<String> enrollmentIds,
                                                                 String token) {
        return rest.exchange(RequestEntity
                        .post(URI.create("/api/v1/student-groups/" + groupId + "/members"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("enrollmentPublicIds", enrollmentIds)),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                });
    }

    private List<Map<String, Object>> members(String groupId, String token) {
        return rest.exchange(RequestEntity
                        .get(URI.create("/api/v1/student-groups/" + groupId + "/members"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static Map<String, Object> body(Object... keyValues) {
        Map<String, Object> body = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            body.put((String) keyValues[i], keyValues[i + 1]);
        }
        return body;
    }

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> getMap(String path, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET, path, null, token);
        assertThat(response.getStatusCode()).as("GET %s", path).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<Map<String, Object>> exchange(HttpMethod method, String path,
                                                         Map<String, Object> body, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(method, URI.create(path));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        RequestEntity<?> entity = body == null
                ? builder.build()
                : builder.contentType(MediaType.APPLICATION_JSON).body(body);
        return rest.exchange(entity, new ParameterizedTypeReference<>() {
        });
    }

    private UserAccount persistUser(RoleCode... roles) {
        UserAccount account = new UserAccount("grp-" + UUID.randomUUID() + "@esic-connect.test",
                "Groupe", "Testeur", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return account;
    }

    private String tokenFor(RoleCode... roles) {
        UserAccount account = persistUser(roles);
        return AuthTestSupport.accessToken(rest, account.getEmail(), PASSWORD);
    }
}
