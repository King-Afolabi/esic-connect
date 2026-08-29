package com.esic.connect.academic;

import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.notification.internal.InvitationMailer;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Port {@link AcademicScopeDirectory} : accès global déduit des autorités
 * Spring Security ({@code hasGlobalScope}, {@code visibleClassGroupIds}
 * vide) ; pour un {@code PEDAGOGICAL_MANAGER} sans rôle global,
 * {@code isClassInScope} vrai uniquement pour une classe d'une formation
 * de son périmètre effectif, {@code visibleClassGroupIds} restreint en
 * conséquence, classe inconnue → {@code false}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AcademicScopeDirectoryTests {

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
    private AcademicScopeDirectory academicScopeDirectory;
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

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminHasGlobalScopeAndNoClassFilter() {
        authenticate("11111111-1111-1111-1111-111111111111", "ROLE_ADMIN");
        assertThat(academicScopeDirectory.hasGlobalScope()).isTrue();
        assertThat(academicScopeDirectory.isClassInScope(UUID.randomUUID())).isTrue();
        assertThat(academicScopeDirectory.visibleClassGroupIds()).isEmpty();
    }

    @Test
    void pedagogicalManagerIsLimitedToItsAssignedProgram() {
        String admin = adminToken();
        Chain inScope = chain(admin);
        Chain outOfScope = chain(admin);
        Account manager = accountWithRoles(RoleCode.PEDAGOGICAL_MANAGER);
        post("/api/v1/pedagogical-assignments", Map.of("programPublicId", inScope.program(),
                "userPublicId", manager.publicId(), "type", "PRIMARY_MANAGER",
                "validFrom", LocalDate.now().minusDays(1).toString()), admin);

        authenticate(manager.publicId(), "ROLE_PEDAGOGICAL_MANAGER");

        assertThat(academicScopeDirectory.hasGlobalScope()).isFalse();
        assertThat(academicScopeDirectory.isClassInScope(UUID.fromString(inScope.classGroup()))).isTrue();
        assertThat(academicScopeDirectory.isClassInScope(UUID.fromString(outOfScope.classGroup()))).isFalse();
        assertThat(academicScopeDirectory.isClassInScope(UUID.randomUUID())).isFalse();
        assertThat(academicScopeDirectory.visibleClassGroupIds()).isPresent();
        assertThat(academicScopeDirectory.visibleClassGroupIds().orElseThrow()).isNotEmpty();
    }

    // ------------------------------------------------------------------

    private void authenticate(String subject, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(subject, "n/a", authorities));
    }

    private record Chain(String program, String classGroup) {
    }

    private Chain chain(String admin) {
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
        String classGroup = post("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C1", "name", "Classe 1"), admin);
        return new Chain(program, classGroup);
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

    private record Account(String publicId, String email) {
    }

    private Account accountWithRoles(RoleCode... roles) {
        UserAccount account = new UserAccount("asd-" + UUID.randomUUID() + "@esic-connect.test",
                "Asd", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return new Account(account.getPublicId().toString(), account.getEmail());
    }

    private String adminToken() {
        Account account = accountWithRoles(RoleCode.ADMIN);
        Map<String, Object> login = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", account.email(), "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) login.get("accessToken");
    }

    private static String shortCode() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
