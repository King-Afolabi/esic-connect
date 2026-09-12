package com.esic.connect.reporting;

import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.support.AuthTestSupport;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixture partagée des tests du sprint 11 (pilotage et restitution).
 *
 * <p>Monte, par l'API réelle et non par des insertions directes, la
 * chaîne minimale nécessaire à un rapport : site → formation → niveau →
 * année → promotion → classe → séance → point de contrôle → apprenant
 * inscrit → présence. Passer par l'API garantit que ce que les tests
 * mesurent est ce que le produit produit, contrôles d'accès compris.
 */
public final class S11TestFixture {

    public static final String PASSWORD = "S3cure-Pass!word";

    private final TestRestTemplate rest;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public S11TestFixture(TestRestTemplate rest,
                          UserAccountRepository userAccountRepository,
                          UserRoleRepository userRoleRepository,
                          RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder) {
        this.rest = rest;
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Compte de test porteur des rôles donnés. */
    public record Account(String publicId, String email) {
    }

    /** Chaîne académique complète, avec deux classes pour tester le cloisonnement. */
    public record Chain(String program, String programCode, String classA, String classACode,
                        String classB, String classBCode, String site) {
    }

    public Account account(RoleCode... roles) {
        UserAccount a = new UserAccount("s11-" + UUID.randomUUID() + "@esic-connect.test",
                "Prenom", "Nom" + code(), AccountStatus.ACTIVE);
        a.setPasswordHash(passwordEncoder.encode(PASSWORD));
        a = userAccountRepository.saveAndFlush(a);
        for (RoleCode rc : roles) {
            Role role = roleRepository.findByCode(rc).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(a, role, Instant.now(), true));
        }
        return new Account(a.getPublicId().toString(), a.getEmail());
    }

    /** Compte avec un nom civil imposé (recherche globale). */
    public Account namedAccount(String firstName, String lastName, RoleCode... roles) {
        UserAccount a = new UserAccount("s11-" + UUID.randomUUID() + "@esic-connect.test",
                firstName, lastName, AccountStatus.ACTIVE);
        a.setPasswordHash(passwordEncoder.encode(PASSWORD));
        a = userAccountRepository.saveAndFlush(a);
        for (RoleCode rc : roles) {
            Role role = roleRepository.findByCode(rc).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(a, role, Instant.now(), true));
        }
        return new Account(a.getPublicId().toString(), a.getEmail());
    }

    public String tokenFor(Account account) {
        return AuthTestSupport.accessToken(rest, account.email(), PASSWORD);
    }

    public Chain academicChain(String admin) {
        String siteCode = "SITE-" + code();
        String site = created(admin, "/api/v1/sites", Map.of("code", siteCode,
                "name", "Campus " + code(), "timeZoneId", "Europe/Paris")).get("publicId").toString();
        String programCode = "PRG-" + code();
        String program = created(admin, "/api/v1/programs", Map.of("code", programCode,
                "name", "BTS SIO", "programType", "BTS")).get("publicId").toString();
        String level = created(admin, "/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1", "name", "BTS 1", "sequenceNumber", 1)).get("publicId").toString();
        String year = created(admin, "/api/v1/academic-years", Map.of("code", "AY-" + code(),
                "name", "2026-2027", "startDate", "2026-09-01", "endDate", "2027-08-31"))
                .get("publicId").toString();
        String promo = created(admin, "/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P" + code(), "name", "Promotion"))
                .get("publicId").toString();
        String classACode = "CA-" + code();
        String classA = created(admin, "/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", classACode, "name", "Classe A"))
                .get("publicId").toString();
        String classBCode = "CB-" + code();
        String classB = created(admin, "/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", classBCode, "name", "Classe B"))
                .get("publicId").toString();
        return new Chain(program, programCode, classA, classACode, classB, classBCode, site);
    }

    /** Crée une séance ouverte et renvoie son identifiant public. */
    public String openSession(String admin, Account teacher, String classPublicId, Instant startsAt) {
        Map<String, Object> body = new HashMap<>();
        body.put("teacherPublicId", teacher.publicId());
        body.put("classPublicIds", List.of(classPublicId));
        body.put("startsAt", startsAt.toString());
        body.put("endsAt", startsAt.plusSeconds(2 * 3600).toString());
        body.put("timeZoneId", "Europe/Paris");
        body.put("title", "Séance " + code());
        body.put("reason", "séance de test");
        String id = created(admin, "/api/v1/sessions", body).get("publicId").toString();
        exchange(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, admin);
        return id;
    }

    /** Crée une séance ouverte portant un titre imposé (recherche globale). */
    public String openTitledSession(String admin, Account teacher, String classPublicId,
                                    Instant startsAt, String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("teacherPublicId", teacher.publicId());
        body.put("classPublicIds", List.of(classPublicId));
        body.put("startsAt", startsAt.toString());
        body.put("endsAt", startsAt.plusSeconds(2 * 3600).toString());
        body.put("timeZoneId", "Europe/Paris");
        body.put("title", title);
        body.put("reason", "séance de test");
        String id = created(admin, "/api/v1/sessions", body).get("publicId").toString();
        exchange(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, admin);
        return id;
    }

    /** Point de contrôle nommé, ouvert, prêt à recevoir un émargement. */
    public String openCheckpoint(String admin, String sessionId, String type) {
        String checkpoint = created(admin, "/api/v1/sessions/" + sessionId + "/checkpoints",
                Map.of("label", type, "type", type)).get("publicId").toString();
        exchange(HttpMethod.POST,
                "/api/v1/sessions/" + sessionId + "/checkpoints/" + checkpoint + "/open", null, admin);
        return checkpoint;
    }

    /** Fait émarger l'apprenant sur un point de contrôle ouvert. */
    public void validateAttendance(String admin, String studentToken, String sessionId, String checkpointId) {
        ResponseEntity<Map<String, Object>> issued = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + sessionId + "/checkpoints/" + checkpointId + "/attendance-token",
                null, admin);
        assertThat(issued.getStatusCode().is2xxSuccessful()).as("émission du jeton").isTrue();
        ResponseEntity<Map<String, Object>> validated = exchange(HttpMethod.POST,
                "/api/v1/attendance/validate",
                Map.of("shortCode", issued.getBody().get("shortCode")), studentToken);
        assertThat(validated.getStatusCode().is2xxSuccessful())
                .as("émargement -> " + validated.getStatusCode() + " " + validated.getBody()).isTrue();
    }

    /** Apprenant inscrit ; renvoie le compte et son numéro étudiant. */
    public record Student(Account account, String studentNumber) {
    }

    public Student enrolledStudent(String admin, String classPublicId) {
        return enrolledStudent(admin, classPublicId, account(RoleCode.STUDENT));
    }

    /**
     * Refonte 2026-09 : le numéro étudiant est une colonne de
     * {@code user_account} — il n'existe plus de {@code student_profile}
     * ni de route dédiée pour le poser après coup. Affecté ici directement
     * en base (comme {@link #account}), avant l'inscription.
     */
    public Student enrolledStudent(String admin, String classPublicId, Account student) {
        String number = "ESIC-2026-" + code();
        UserAccount account = userAccountRepository.findByPublicId(UUID.fromString(student.publicId()))
                .orElseThrow();
        account.assignStudentNumber(number, null, null);
        userAccountRepository.saveAndFlush(account);
        created(admin, "/api/v1/enrollments", Map.of("studentUserPublicId", student.publicId(),
                "classGroupPublicId", classPublicId, "startDate", "2026-08-01"));
        return new Student(student, number);
    }

    /** Affecte un responsable pédagogique à une formation. */
    public void assignManager(String admin, Account manager, String programPublicId) {
        created(admin, "/api/v1/pedagogical-assignments", Map.of(
                "programPublicId", programPublicId,
                "userPublicId", manager.publicId(),
                "type", "PRIMARY_MANAGER",
                "validFrom", java.time.LocalDate.now().minusDays(1).toString()));
    }

    public Map<String, Object> created(String token, String path, Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> r = exchange(HttpMethod.POST, path, body, token);
        assertThat(r.getStatusCode()).as("POST " + path + " -> " + r.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return r.getBody();
    }

    public ResponseEntity<Map<String, Object>> exchange(HttpMethod method, String path,
                                                        Object body, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(method, URI.create(path));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        RequestEntity<?> entity = body == null ? builder.build()
                : builder.contentType(MediaType.APPLICATION_JSON).body(body);
        return rest.exchange(entity, new ParameterizedTypeReference<>() {
        });
    }

    /** Requête renvoyant des octets bruts (exports, PDF, ICS). */
    public ResponseEntity<byte[]> bytes(HttpMethod method, String path, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(method, URI.create(path));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return rest.exchange(builder.build(), byte[].class);
    }

    public ResponseEntity<String> text(HttpMethod method, String path, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(method, URI.create(path));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return rest.exchange(builder.build(), String.class);
    }

    public static String code() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
