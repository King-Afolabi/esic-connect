package com.esic.connect.reporting;

import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.notification.internal.InvitationMailer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Attestation d'assiduité (EF-REP-006 ; AC-019, AC-033).
 *
 * <p>Vérifie que le document porte un identifiant et son émetteur, que
 * la mesure est exprimée en demi-journées, que l'identifiant est
 * vérifiable au registre, et qu'un apprenant hors périmètre ne donne pas
 * lieu à une attestation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AttestationIntegrationTests {

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

    @BeforeEach
    void setUp() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        fx = new S11TestFixture(rest, userAccountRepository, userRoleRepository,
                roleRepository, passwordEncoder);
    }

    @Test
    void anAttestationCarriesItsDocumentIdentityAndCountsInHalfDays() throws Exception {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain chain = fx.academicChain(admin);
        S11TestFixture.Account teacher = fx.account(RoleCode.TEACHER);
        S11TestFixture.Student student = fx.enrolledStudent(admin, chain.classA());

        Instant startsAt = Instant.now().minusSeconds(1800);
        String sessionId = fx.openSession(admin, teacher, chain.classA(), startsAt);
        String checkpoint = fx.openCheckpoint(admin, sessionId, "MORNING_ARRIVAL");
        fx.validateAttendance(admin, fx.tokenFor(student.account()), sessionId, checkpoint);

        ResponseEntity<byte[]> response = fx.bytes(HttpMethod.POST,
                "/api/v1/attendance/reports/attestation?studentProfile=" + student.profilePublicId(),
                admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String documentId = response.getHeaders().getFirst("X-Document-Id");
        assertThat(documentId).as("identifiant du document (AC-033)").startsWith("ESIC-ATT-");
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .contains("attachment").contains(documentId + ".pdf");

        try (PDDocument pdf = Loader.loadPDF(response.getBody())) {
            String text = new PDFTextStripper().getText(pdf);
            // AC-033 : identifiant + émetteur sur le document.
            assertThat(text).contains(documentId);
            assertThat(text).contains("ESIC");
            assertThat(text).contains("Document électronique");
            // AC-019 : la mesure est exprimée en demi-journées.
            assertThat(text).contains("Demi-journées attendues");
            assertThat(text).contains("Demi-journées suivies");
            assertThat(text).contains("Journées équivalentes suivies");
            assertThat(text).contains(student.studentNumber());
            // RG-028 rappelée sur le document lui-même.
            assertThat(text).contains("entreprise n'est jamais comptée comme une absence");
        }
    }

    @Test
    void anIssuedAttestationIsVerifiableByItsDocumentId() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain chain = fx.academicChain(admin);
        S11TestFixture.Account teacher = fx.account(RoleCode.TEACHER);
        S11TestFixture.Student student = fx.enrolledStudent(admin, chain.classA());
        String sessionId = fx.openSession(admin, teacher, chain.classA(), Instant.now().minusSeconds(1800));
        fx.openCheckpoint(admin, sessionId, "MORNING_ARRIVAL");

        ResponseEntity<byte[]> issued = fx.bytes(HttpMethod.POST,
                "/api/v1/attendance/reports/attestation?studentProfile=" + student.profilePublicId(),
                admin);
        assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.OK);
        String documentId = issued.getHeaders().getFirst("X-Document-Id");

        ResponseEntity<Map<String, Object>> check = fx.exchange(HttpMethod.GET,
                "/api/v1/attendance/reports/attestation/" + documentId, null, admin);
        assertThat(check.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(check.getBody().get("documentId")).isEqualTo(documentId);
        assertThat(check.getBody().get("documentType")).isEqualTo("ATTENDANCE_CERTIFICATE");
        assertThat(check.getBody().get("revoked")).isEqualTo(false);
        assertThat(check.getBody().get("issuedBy")).isNotNull();
        // La vérification n'expose aucune donnée d'assiduité.
        assertThat(check.getBody()).doesNotContainKeys("attendanceRate", "presentHalfDays", "subject");
    }

    @Test
    void anUnknownDocumentIdIsNotFound() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        ResponseEntity<Map<String, Object>> check = fx.exchange(HttpMethod.GET,
                "/api/v1/attendance/reports/attestation/ESIC-ATT-2026-ZZZZZZZZZZ", null, admin);
        assertThat(check.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(check.getBody().get("code")).isEqualTo("ATT_ATTESTATION_NOT_FOUND");
    }

    @Test
    void anUnknownStudentProducesNoAttestation() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        ResponseEntity<Map<String, Object>> response = fx.exchange(HttpMethod.POST,
                "/api/v1/attendance/reports/attestation?studentProfile=" + UUID.randomUUID(), null, admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("ATT_ATTESTATION_SUBJECT_NOT_FOUND");
    }

    @Test
    void aStudentCannotIssueAnAttestationAndAnAnonymousCallerIsRefused() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain chain = fx.academicChain(admin);
        S11TestFixture.Student student = fx.enrolledStudent(admin, chain.classA());

        assertThat(fx.exchange(HttpMethod.POST,
                "/api/v1/attendance/reports/attestation?studentProfile=" + student.profilePublicId(),
                null, fx.tokenFor(student.account())).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(fx.exchange(HttpMethod.POST,
                "/api/v1/attendance/reports/attestation?studentProfile=" + student.profilePublicId(),
                null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aManagerCannotAttestForAStudentOutsideTheirScope() {
        String admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain mine = fx.academicChain(admin);
        S11TestFixture.Chain other = fx.academicChain(admin);
        S11TestFixture.Account manager = fx.account(RoleCode.PEDAGOGICAL_MANAGER);
        fx.assignManager(admin, manager, mine.program());

        S11TestFixture.Account teacher = fx.account(RoleCode.TEACHER);
        S11TestFixture.Student outsider = fx.enrolledStudent(admin, other.classA());
        String sessionId = fx.openSession(admin, teacher, other.classA(), Instant.now().minusSeconds(1800));
        fx.openCheckpoint(admin, sessionId, "MORNING_ARRIVAL");

        // 404 et non 403 : hors périmètre et inexistant se répondent de la
        // même façon (docs/02 §18.2).
        ResponseEntity<Map<String, Object>> response = fx.exchange(HttpMethod.POST,
                "/api/v1/attendance/reports/attestation?studentProfile=" + outsider.profilePublicId(),
                null, fx.tokenFor(manager));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
