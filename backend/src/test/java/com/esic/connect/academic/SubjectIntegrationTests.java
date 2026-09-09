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
 * Référentiel des matières (EF-ACA-006 ; docs/02 §6.4).
 *
 * <p>Ce qui est vérifié en priorité, au-delà du CRUD : le contrat exclut
 * structurellement un formateur — le cahier interdit d'attacher un
 * formateur unique à une matière — et aucun identifiant SQL interne ne
 * fuit dans les réponses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SubjectIntegrationTests {

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
    void uneMatiereEstCreeeLueModifieeArchiveePuisRestauree() {
        String admin = tokenFor(RoleCode.ADMIN);
        String code = "MAT-" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> created = created("/api/v1/subjects", body(
                "code", code, "name", "Algorithmique", "hourlyVolume", 60), admin);
        String id = (String) created.get("id");
        assertThat(created.get("status")).isEqualTo("ACTIVE");
        assertThat(created.get("hourlyVolume")).isEqualTo(60);
        assertThat(created).doesNotContainKeys("subjectId", "createdById", "updatedById");

        Map<String, Object> updated = exchange(HttpMethod.PATCH, "/api/v1/subjects/" + id,
                body("name", "Algorithmique avancée", "hourlyVolume", 80), admin).getBody();
        assertThat(updated.get("name")).isEqualTo("Algorithmique avancée");
        assertThat(updated.get("hourlyVolume")).isEqualTo(80);
        // Le code ne bouge pas : il sert de référence dans les imports.
        assertThat(updated.get("code")).isEqualTo(code);

        assertThat(exchange(HttpMethod.POST, "/api/v1/subjects/" + id + "/archive",
                Map.of("reason", "programme revu"), admin).getBody().get("status"))
                .isEqualTo("ARCHIVED");
        assertThat(exchange(HttpMethod.POST, "/api/v1/subjects/" + id + "/restore",
                Map.of(), admin).getBody().get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void uneMatiereNePorteJamaisDeFormateur() {
        String admin = tokenFor(RoleCode.ADMIN);
        Map<String, Object> created = created("/api/v1/subjects",
                body("code", "MAT-" + UUID.randomUUID().toString().substring(0, 8),
                        "name", "Droit"), admin);

        // docs/02 §6.4 : « une matière n'a pas de formateur unique global ;
        // l'affectation se fait au niveau de la séance, d'une période, ou
        // d'une association classe–matière–période ».
        assertThat(created).doesNotContainKeys("teacher", "teacherId", "teacherPublicId");
    }

    @Test
    void unCodeDejaUtiliseEstRefuse() {
        String admin = tokenFor(RoleCode.ADMIN);
        String code = "MAT-" + UUID.randomUUID().toString().substring(0, 8);
        created("/api/v1/subjects", body("code", code, "name", "Anglais"), admin);

        ResponseEntity<Map<String, Object>> duplicate = exchange(HttpMethod.POST, "/api/v1/subjects",
                body("code", code, "name", "Anglais bis"), admin);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().get("code")).isEqualTo("ACAD_DUPLICATE_CODE");
    }

    @Test
    void unVolumeHoraireNegatifEstRefuse() {
        String admin = tokenFor(RoleCode.ADMIN);

        assertThat(exchange(HttpMethod.POST, "/api/v1/subjects",
                body("code", "MAT-" + UUID.randomUUID().toString().substring(0, 8),
                        "name", "Marketing", "hourlyVolume", -5), admin).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void uneMatiereArchiveeNAcceptePlusDeModification() {
        String admin = tokenFor(RoleCode.ADMIN);
        String id = (String) created("/api/v1/subjects",
                body("code", "MAT-" + UUID.randomUUID().toString().substring(0, 8),
                        "name", "Comptabilité"), admin).get("id");
        exchange(HttpMethod.POST, "/api/v1/subjects/" + id + "/archive",
                Map.of("reason", "obsolète"), admin);

        assertThat(exchange(HttpMethod.PATCH, "/api/v1/subjects/" + id,
                body("name", "Comptabilité 2"), admin).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void uneMatiereSeRattacheAUneOuPlusieursFormations() {
        String admin = tokenFor(RoleCode.ADMIN);
        String firstProgram = createProgram(admin);
        String secondProgram = createProgram(admin);

        Map<String, Object> created = created("/api/v1/subjects", body(
                "code", "MAT-" + UUID.randomUUID().toString().substring(0, 8),
                "name", "Anglais professionnel",
                "programPublicIds", List.of(firstProgram, secondProgram)), admin);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> programs = (List<Map<String, Object>>) created.get("programs");
        assertThat(programs).hasSize(2);
        assertThat(programs).allSatisfy(program ->
                assertThat(program).containsKeys("id", "code", "name"));
    }

    @Test
    void leCatalogueSeFiltreParFormation() {
        String admin = tokenFor(RoleCode.ADMIN);
        String program = createProgram(admin);
        String attached = (String) created("/api/v1/subjects", body(
                "code", "MAT-" + UUID.randomUUID().toString().substring(0, 8),
                "name", "Systèmes", "programPublicIds", List.of(program)), admin).get("id");
        created("/api/v1/subjects", body("code", "MAT-" + UUID.randomUUID().toString().substring(0, 8),
                "name", "Sans formation"), admin);

        Map<String, Object> page = exchange(HttpMethod.GET,
                "/api/v1/subjects?programId=" + program, null, admin).getBody();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
        assertThat(content).extracting(row -> row.get("id")).containsExactly(attached);
    }

    @Test
    void unFormateurLitLeCatalogueMaisNEcritPas() {
        String admin = tokenFor(RoleCode.ADMIN);
        String teacher = tokenFor(RoleCode.TEACHER);
        created("/api/v1/subjects", body("code", "MAT-" + UUID.randomUUID().toString().substring(0, 8),
                "name", "Réseaux"), admin);

        // Lecture ouverte : un formateur doit pouvoir qualifier une séance.
        assertThat(exchange(HttpMethod.GET, "/api/v1/subjects", null, teacher).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // Écriture fermée.
        assertThat(exchange(HttpMethod.POST, "/api/v1/subjects",
                body("code", "MAT-X", "name", "Interdit"), teacher).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unApprenantNAccedePasAuCatalogue() {
        String student = tokenFor(RoleCode.STUDENT);

        assertThat(exchange(HttpMethod.GET, "/api/v1/subjects", null, student).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void laLectureAnonymeEstRefusee() {
        assertThat(exchange(HttpMethod.GET, "/api/v1/subjects", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void uneMatiereInconnueRepond404() {
        String admin = tokenFor(RoleCode.ADMIN);

        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET,
                "/api/v1/subjects/" + UUID.randomUUID(), null, admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("SUBJECT_NOT_FOUND");
    }

    // ------------------------------------------------------------------

    private String createProgram(String token) {
        return (String) created("/api/v1/programs", body(
                "code", "PRG-" + UUID.randomUUID().toString().substring(0, 8),
                "name", "Formation de test", "programType", "BTS"), token).get("publicId");
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

    private String tokenFor(RoleCode... roles) {
        UserAccount account = new UserAccount("subj-" + UUID.randomUUID() + "@esic-connect.test",
                "Sujet", "Testeur", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return AuthTestSupport.accessToken(rest, account.getEmail(), PASSWORD);
    }
}
