package com.esic.connect.academic;

import com.esic.connect.academic.ClassGroupDirectory.ClassGroupRef;
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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Port {@link ClassGroupDirectory} : résolution d'une classe par
 * identifiant public, exposition des codes formation / année scolaire et
 * du drapeau {@code openForEnrollment} (faux dès qu'un maillon de la
 * chaîne de rattachement est archivé), identifiant inconnu / {@code null}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ClassGroupDirectoryTests {

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
    private ClassGroupDirectory classGroupDirectory;
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
    void resolvesClassGroupWithProgramAndYearCodesAndOpenFlag() {
        String admin = adminToken();
        String programCode = "PRG-" + shortCode();
        String yearCode = "AY-" + shortCode();
        String classId = chain(admin, programCode, yearCode);

        ClassGroupRef ref = classGroupDirectory.findByPublicId(UUID.fromString(classId)).orElseThrow();
        assertThat(ref.publicId()).isEqualTo(UUID.fromString(classId));
        assertThat(ref.programCode()).isEqualTo(programCode);
        assertThat(ref.academicYearCode()).isEqualTo(yearCode);
        assertThat(ref.openForEnrollment()).isTrue();
        assertThat(classGroupDirectory.findByInternalId(ref.internalId()).orElseThrow().publicId())
                .isEqualTo(UUID.fromString(classId));
    }

    @Test
    void openForEnrollmentIsFalseWhenTheClassIsArchived() {
        String admin = adminToken();
        String classId = chain(admin, "PRG-" + shortCode(), "AY-" + shortCode());
        ResponseEntity<Void> archived = restTemplate.exchange(
                RequestEntity.post(java.net.URI.create("/api/v1/class-groups/" + classId + "/archive"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).body(Map.of("reason", "fermeture")),
                Void.class);
        assertThat(archived.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(classGroupDirectory.findByPublicId(UUID.fromString(classId)).orElseThrow().openForEnrollment())
                .isFalse();
    }

    @Test
    void returnsEmptyForUnknownOrNullIdentifier() {
        assertThat(classGroupDirectory.findByPublicId(UUID.randomUUID())).isEmpty();
        assertThat(classGroupDirectory.findByPublicId(null)).isEmpty();
        assertThat(classGroupDirectory.findByInternalId(-1L)).isEmpty();
    }

    // ------------------------------------------------------------------

    private String chain(String admin, String programCode, String yearCode) {
        String site = post("/api/v1/sites", Map.of("code", "SITE-" + UUID.randomUUID(),
                "name", "Campus", "timeZoneId", "Europe/Paris"), admin);
        String program = post("/api/v1/programs", Map.of("code", programCode, "name", "BTS SIO",
                "programType", "BTS"), admin);
        String level = post("/api/v1/programs/" + program + "/levels", Map.of("code", "N1", "name", "BTS 1",
                "sequenceNumber", 1), admin);
        String year = post("/api/v1/academic-years", Map.of("code", yearCode, "name", "2026-2027",
                "startDate", "2026-09-01", "endDate", "2027-08-31"), admin);
        String promo = post("/api/v1/promotions", Map.of("programPublicId", program, "academicYearPublicId", year,
                "code", "P26", "name", "Promotion 2026"), admin);
        return post("/api/v1/class-groups", Map.of("promotionPublicId", promo, "programLevelPublicId", level,
                "sitePublicId", site, "code", "C1", "name", "Classe 1"), admin);
    }

    private String post(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                RequestEntity.method(HttpMethod.POST, java.net.URI.create(path))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).body(body),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("publicId");
    }

    private String adminToken() {
        UserAccount account = new UserAccount("cgd-" + UUID.randomUUID() + "@esic-connect.test",
                "Cgd", "Tester", AccountStatus.ACTIVE);
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
