package com.esic.connect.enrollment;

import com.esic.connect.enrollment.EnrollmentDirectory.EnrollmentRef;
import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
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
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Port {@link EnrollmentDirectory} : résolution d'une inscription par
 * identifiant public / interne, exposition des identifiants publics
 * profil / classe / année, drapeau {@code usable} (vrai tant que
 * l'inscription est {@code ACTIVE}), identifiant inconnu / {@code null}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EnrollmentDirectoryTests {

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
    private TestRestTemplate restTemplate;
    @Autowired
    private EnrollmentDirectory enrollmentDirectory;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void useJdkClient() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void resolvesEnrollmentWithPublicIdsAndUsableFlag() {
        String admin = adminToken();
        String classId = chain(admin);
        String profileId = studentProfile(admin);
        String enrollmentId = post("/api/v1/enrollments", Map.of("studentProfilePublicId", profileId,
                "classGroupPublicId", classId), admin);

        EnrollmentRef ref = enrollmentDirectory.findByPublicId(UUID.fromString(enrollmentId)).orElseThrow();
        assertThat(ref.publicId()).isEqualTo(UUID.fromString(enrollmentId));
        assertThat(ref.studentProfilePublicId()).isEqualTo(UUID.fromString(profileId));
        assertThat(ref.classGroupPublicId()).isEqualTo(UUID.fromString(classId));
        assertThat(ref.classGroupCode()).isEqualTo("C1");
        assertThat(ref.academicYearCode()).isNotBlank();
        assertThat(ref.usable()).isTrue();
        assertThat(enrollmentDirectory.findByInternalId(ref.internalId()).orElseThrow().publicId())
                .isEqualTo(UUID.fromString(enrollmentId));
    }

    @Test
    void usableIsFalseOnceEnrollmentIsClosed() {
        String admin = adminToken();
        String classId = chain(admin);
        String profileId = studentProfile(admin);
        String enrollmentId = post("/api/v1/enrollments", Map.of("studentProfilePublicId", profileId,
                "classGroupPublicId", classId), admin);
        ResponseEntity<Map<String, Object>> closed = restTemplate.exchange(
                RequestEntity.method(HttpMethod.POST, URI.create("/api/v1/enrollments/" + enrollmentId + "/close"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("status", "COMPLETED", "reason", "diplômé")),
                new ParameterizedTypeReference<>() {
                });
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(enrollmentDirectory.findByPublicId(UUID.fromString(enrollmentId)).orElseThrow().usable())
                .isFalse();
    }

    @Test
    void returnsEmptyForUnknownOrNullIdentifier() {
        assertThat(enrollmentDirectory.findByPublicId(UUID.randomUUID())).isEmpty();
        assertThat(enrollmentDirectory.findByPublicId(null)).isEmpty();
        assertThat(enrollmentDirectory.findByInternalId(-1L)).isEmpty();
    }

    // ------------------------------------------------------------------

    private String chain(String admin) {
        String site = post("/api/v1/sites", Map.of("code", "SITE-" + UUID.randomUUID(),
                "name", "Campus", "timeZoneId", "Europe/Paris"), admin);
        String program = post("/api/v1/programs", Map.of("code", "PRG-" + shortCode(), "name", "BTS SIO",
                "programType", "BTS"), admin);
        String level = post("/api/v1/programs/" + program + "/levels", Map.of("code", "N1", "name", "BTS 1",
                "sequenceNumber", 1), admin);
        String year = post("/api/v1/academic-years", Map.of("code", "AY-" + shortCode(), "name", "2026-2027",
                "startDate", "2026-09-01", "endDate", "2027-08-31"), admin);
        String promo = post("/api/v1/promotions", Map.of("programPublicId", program, "academicYearPublicId", year,
                "code", "P26", "name", "Promotion 2026"), admin);
        return post("/api/v1/class-groups", Map.of("promotionPublicId", promo, "programLevelPublicId", level,
                "sitePublicId", site, "code", "C1", "name", "Classe 1"), admin);
    }

    private String studentProfile(String admin) {
        String studentUser = studentAccountPublicId();
        return post("/api/v1/student-profiles", Map.of("userPublicId", studentUser,
                "studentNumber", "ESIC-2026-" + shortCode()), admin);
    }

    private String studentAccountPublicId() {
        UserAccount account = new UserAccount("edir-stu-" + UUID.randomUUID() + "@esic-connect.test",
                "Edir", "Student", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        Role role = roleRepository.findByCode(RoleCode.STUDENT).orElseThrow();
        userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        return account.getPublicId().toString();
    }

    private String post(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                RequestEntity.method(HttpMethod.POST, URI.create(path))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).body(body),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("publicId");
    }

    private String adminToken() {
        UserAccount account = new UserAccount("edir-" + UUID.randomUUID() + "@esic-connect.test",
                "Edir", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        Role role = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
        userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        Map<String, Object> login = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", account.getEmail(), "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) login.get("accessToken");
    }

    private static String shortCode() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
