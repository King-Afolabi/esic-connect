package com.esic.connect.organization;

import com.esic.connect.audit.internal.AuditEvent;
import com.esic.connect.audit.internal.AuditEventRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parcours de bout en bout du référentiel organisationnel : hiérarchie
 * site → bâtiment → salle, archivage / restauration, plages réseau
 * (SUPER_ADMIN), validations (fuseau, pays, CIDR, cohérence), pagination
 * et écriture de l'audit. Les DTO ne doivent jamais exposer d'identifiant
 * SQL interne.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrganizationIntegrationTests {

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
    private UserAccountRepository userAccountRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void useJdkClient() {
        // SimpleClientHttpRequestFactory ne supporte pas PATCH.
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    // ------------------------------------------------------------------
    // Hiérarchie complète + audit
    // ------------------------------------------------------------------

    @Test
    void siteBuildingRoomLifecycleIsAuditedAndHidesInternalIds() {
        String admin = adminToken();
        String code = siteCode();

        Map<String, Object> site = created("/api/v1/sites",
                Map.of("code", code, "name", "Campus Paris", "timeZoneId", "Europe/Paris", "countryCode", "fr"),
                admin);
        String sitePublicId = (String) site.get("publicId");
        assertThat(site).containsKey("publicId").doesNotContainKeys("id", "createdById", "siteId");
        assertThat(site.get("status")).isEqualTo("ACTIVE");
        assertThat(site.get("countryCode")).isEqualTo("FR");

        Map<String, Object> building = created("/api/v1/sites/" + sitePublicId + "/buildings",
                Map.of("code", "B1", "name", "Bâtiment 1"), admin);
        String buildingPublicId = (String) building.get("publicId");
        assertThat(building.get("sitePublicId")).isEqualTo(sitePublicId);
        assertThat(building).doesNotContainKeys("id", "siteId");

        Map<String, Object> room = created("/api/v1/sites/" + sitePublicId + "/rooms",
                Map.of("code", "R1", "name", "Salle 1", "capacity", 24, "buildingPublicId", buildingPublicId),
                admin);
        String roomPublicId = (String) room.get("publicId");
        assertThat(room.get("sitePublicId")).isEqualTo(sitePublicId);
        assertThat(room.get("buildingPublicId")).isEqualTo(buildingPublicId);
        assertThat(room.get("capacity")).isEqualTo(24);
        assertThat(room).doesNotContainKeys("id", "siteId", "buildingId");

        // Consultations
        assertThat(getMap("/api/v1/rooms/" + roomPublicId, admin).get("code")).isEqualTo("R1");
        Map<String, Object> listed = getMap("/api/v1/sites?q=" + code, admin);
        assertThat(listed.get("totalElements")).isEqualTo(1);

        // Modification
        ResponseEntity<Map<String, Object>> renamed = exchange(HttpMethod.PATCH, "/api/v1/rooms/" + roomPublicId,
                Map.of("name", "Salle 1 rénovée", "capacity", 30), admin);
        assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(renamed.getBody().get("name")).isEqualTo("Salle 1 rénovée");

        // Archivage en cascade contrôlée (enfant -> parent)
        assertThat(action("/api/v1/rooms/" + roomPublicId + "/archive", "désaffectation", admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(action("/api/v1/buildings/" + buildingPublicId + "/archive", "travaux", admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(action("/api/v1/sites/" + sitePublicId + "/archive", "fermeture site", admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getMap("/api/v1/sites/" + sitePublicId, admin).get("status")).isEqualTo("ARCHIVED");

        // Restauration
        assertThat(restore("/api/v1/sites/" + sitePublicId + "/restore", admin)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getMap("/api/v1/sites/" + sitePublicId, admin).get("status")).isEqualTo("ACTIVE");

        assertThat(auditActions(sitePublicId)).contains("SITE_CREATED", "SITE_ARCHIVED", "SITE_RESTORED");
        assertThat(auditActions(buildingPublicId)).contains("BUILDING_CREATED", "BUILDING_ARCHIVED");
        assertThat(auditActions(roomPublicId)).contains("ROOM_CREATED", "ROOM_UPDATED", "ROOM_ARCHIVED");
    }

    // ------------------------------------------------------------------
    // Règles de hiérarchie
    // ------------------------------------------------------------------

    @Test
    void archiveSiteRefusedWhileActiveChildrenRemain() {
        String admin = adminToken();
        String sitePublicId = (String) created("/api/v1/sites",
                Map.of("code", siteCode(), "name", "S", "timeZoneId", "Europe/Paris"), admin).get("publicId");
        created("/api/v1/sites/" + sitePublicId + "/buildings", Map.of("code", "B1", "name", "B"), admin);

        ResponseEntity<Map<String, Object>> archive = exchange(HttpMethod.POST,
                "/api/v1/sites/" + sitePublicId + "/archive", Map.of("reason", "x"), admin);
        assertThat(archive.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(archive.getBody().get("code")).isEqualTo("ORG_HAS_ACTIVE_CHILDREN");
    }

    @Test
    void buildingCannotBeCreatedUnderArchivedSite() {
        String admin = adminToken();
        String sitePublicId = (String) created("/api/v1/sites",
                Map.of("code", siteCode(), "name", "S", "timeZoneId", "Europe/Paris"), admin).get("publicId");
        assertThat(action("/api/v1/sites/" + sitePublicId + "/archive", "fermeture", admin))
                .isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map<String, Object>> create = exchange(HttpMethod.POST,
                "/api/v1/sites/" + sitePublicId + "/buildings", Map.of("code", "B1", "name", "B"), admin);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(create.getBody().get("code")).isEqualTo("ORG_ARCHIVED_PARENT");
    }

    @Test
    void roomRejectsBuildingBelongingToAnotherSite() {
        String admin = adminToken();
        String siteA = (String) created("/api/v1/sites",
                Map.of("code", siteCode(), "name", "A", "timeZoneId", "Europe/Paris"), admin).get("publicId");
        String siteB = (String) created("/api/v1/sites",
                Map.of("code", siteCode(), "name", "B", "timeZoneId", "Europe/Paris"), admin).get("publicId");
        String buildingB = (String) created("/api/v1/sites/" + siteB + "/buildings",
                Map.of("code", "B1", "name", "B1"), admin).get("publicId");

        ResponseEntity<Map<String, Object>> create = exchange(HttpMethod.POST,
                "/api/v1/sites/" + siteA + "/rooms",
                Map.of("code", "R1", "name", "R1", "buildingPublicId", buildingB), admin);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(create.getBody().get("code")).isEqualTo("ORG_BUILDING_SITE_MISMATCH");
    }

    // ------------------------------------------------------------------
    // Validations de champs
    // ------------------------------------------------------------------

    @Test
    void siteCreationValidatesUniquenessTimeZoneAndCountry() {
        String admin = adminToken();
        String code = siteCode();
        created("/api/v1/sites", Map.of("code", code, "name", "S", "timeZoneId", "Europe/Paris"), admin);

        ResponseEntity<Map<String, Object>> duplicate = exchange(HttpMethod.POST, "/api/v1/sites",
                Map.of("code", code, "name", "S2", "timeZoneId", "Europe/Paris"), admin);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().get("code")).isEqualTo("ORG_DUPLICATE_CODE");

        ResponseEntity<Map<String, Object>> badZone = exchange(HttpMethod.POST, "/api/v1/sites",
                Map.of("code", siteCode(), "name", "S", "timeZoneId", "Nowhere/Void"), admin);
        assertThat(badZone.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badZone.getBody().get("code")).isEqualTo("ORG_INVALID_TIME_ZONE");

        ResponseEntity<Map<String, Object>> badCountry = exchange(HttpMethod.POST, "/api/v1/sites",
                Map.of("code", siteCode(), "name", "S", "timeZoneId", "Europe/Paris", "countryCode", "ZZ"), admin);
        assertThat(badCountry.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badCountry.getBody().get("code")).isEqualTo("ORG_INVALID_COUNTRY_CODE");
    }

    @Test
    void siteListClampsPageSizeAndRejectsUnknownSort() {
        String admin = adminToken();
        Map<String, Object> clamped = getMap("/api/v1/sites?size=9999", admin);
        assertThat(clamped.get("size")).isEqualTo(100);

        ResponseEntity<Map<String, Object>> badSort = exchange(HttpMethod.GET,
                "/api/v1/sites?sort=createdById,asc", null, admin);
        assertThat(badSort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badSort.getBody().get("code")).isEqualTo("ORG_INVALID_SORT");
    }

    // ------------------------------------------------------------------
    // Plages réseau (SUPER_ADMIN)
    // ------------------------------------------------------------------

    @Test
    void networkRangeCrudValidatesCidrAndIsAudited() {
        String superAdmin = superAdminToken();
        String sitePublicId = (String) created("/api/v1/sites",
                Map.of("code", siteCode(), "name", "S", "timeZoneId", "Europe/Paris"), superAdmin).get("publicId");
        String base = "/api/v1/sites/" + sitePublicId + "/network-ranges";

        ResponseEntity<Map<String, Object>> badCidr = exchange(HttpMethod.POST, base,
                Map.of("cidr", "10.0.0.0/40", "label", "LAN"), superAdmin);
        assertThat(badCidr.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badCidr.getBody().get("code")).isEqualTo("ORG_INVALID_CIDR");

        Map<String, Object> range = created(base, Map.of("cidr", "10.0.0.0/8", "label", "LAN ESIC"), superAdmin);
        String rangePublicId = (String) range.get("publicId");
        assertThat(range.get("active")).isEqualTo(true);
        assertThat(range).doesNotContainKeys("id", "siteId", "createdById");

        ResponseEntity<Map<String, Object>> duplicate = exchange(HttpMethod.POST, base,
                Map.of("cidr", "10.0.0.0/8", "label", "doublon"), superAdmin);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().get("code")).isEqualTo("ORG_DUPLICATE_ACTIVE_RANGE");

        assertThat(action("/api/v1/network-ranges/" + rangePublicId + "/deactivate", null, superAdmin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        // Créneau actif libéré : même CIDR de nouveau accepté.
        created(base, Map.of("cidr", "10.0.0.0/8", "label", "LAN v2"), superAdmin);

        Map<String, Object> activeOnly = getMap(base + "?active=true", superAdmin);
        assertThat(activeOnly.get("totalElements")).isEqualTo(1);

        assertThat(auditActions(rangePublicId))
                .contains("SITE_NETWORK_RANGE_CREATED", "SITE_NETWORK_RANGE_DEACTIVATED");
    }

    @Test
    void ipv6RangeIsAccepted() {
        String superAdmin = superAdminToken();
        String sitePublicId = (String) created("/api/v1/sites",
                Map.of("code", siteCode(), "name", "S", "timeZoneId", "Europe/Paris"), superAdmin).get("publicId");
        Map<String, Object> range = created("/api/v1/sites/" + sitePublicId + "/network-ranges",
                Map.of("cidr", "2001:db8::/32", "label", "IPv6"), superAdmin);
        assertThat(range.get("cidr")).isEqualTo("2001:db8::/32");
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST " + path).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> getMap(String path, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET, path, null, token);
        assertThat(response.getStatusCode()).as("GET " + path).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private HttpStatus action(String path, String reason, String token) {
        Map<String, Object> body = reason == null ? Map.of() : Map.of("reason", reason);
        return (HttpStatus) exchange(HttpMethod.POST, path, body, token).getStatusCode();
    }

    private HttpStatus restore(String path, String token) {
        return (HttpStatus) exchange(HttpMethod.POST, path, Map.of(), token).getStatusCode();
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
        return restTemplate.exchange(entity, new ParameterizedTypeReference<>() {
        });
    }

    private List<String> auditActions(String resourcePublicId) {
        UUID target = UUID.fromString(resourcePublicId);
        return auditEventRepository.findAll().stream()
                .filter(e -> target.equals(e.getResourcePublicId()))
                .map(AuditEvent::getAction)
                .toList();
    }

    private String adminToken() {
        return tokenFor(RoleCode.ADMIN);
    }

    private String superAdminToken() {
        return tokenFor(RoleCode.SUPER_ADMIN);
    }

    private String tokenFor(RoleCode... roles) {
        UserAccount account = new UserAccount("org-" + UUID.randomUUID() + "@esic-connect.test",
                "Org", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        Map<String, Object> login = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", account.getEmail(), "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) login.get("accessToken");
    }

    private static String siteCode() {
        return "IT-" + UUID.randomUUID();
    }
}
