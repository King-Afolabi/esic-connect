package com.esic.connect.identity;

import com.esic.connect.audit.internal.AuditEvent;
import com.esic.connect.audit.internal.AuditEventRepository;
import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parcours « mot de passe oublié » de bout en bout (EF-AUTH-005,
 * docs/02 §17.8).
 *
 * <p>Le jeton brut n'existe qu'en mémoire, le temps d'être envoyé : ces
 * tests l'interceptent en écoutant {@link PasswordResetRequestedEvent},
 * exactement comme le fait le module {@code notification}. Rien n'est lu
 * en base pour le reconstituer — ce serait impossible, seule l'empreinte
 * y est stockée.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PasswordResetIntegrationTests {

    private static final String OLD_PASSWORD = "AncienMotDePasse!2026";
    private static final String NEW_PASSWORD = "cheval batterie agrafe correct";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CapturedResetTokens capturedTokens;

    @TestConfiguration
    static class CaptureConfig {
        @Bean
        CapturedResetTokens capturedResetTokens() {
            return new CapturedResetTokens();
        }
    }

    /**
     * Collecte les jetons émis. Écoute en {@code AFTER_COMMIT} : un
     * jeton capturé prouve donc aussi que la transaction d'émission a bien
     * été validée.
     */
    static class CapturedResetTokens {
        private final List<PasswordResetRequestedEvent> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void onRequested(PasswordResetRequestedEvent event) {
            events.add(event);
        }

        Optional<PasswordResetRequestedEvent> latestFor(String email) {
            return events.stream().filter(e -> e.email().equals(email)).reduce((first, second) -> second);
        }

        long countFor(String email) {
            return events.stream().filter(e -> e.email().equals(email)).count();
        }
    }

    @Test
    void anUnknownAddressGetsTheSameNeutralAnswerAsAKnownOne() {
        UserAccount account = persistUser(uniqueEmail(), AccountStatus.ACTIVE);
        String unknownEmail = uniqueEmail();

        ResponseEntity<Map<String, Object>> known = requestReset(account.getEmail());
        ResponseEntity<Map<String, Object>> unknown = requestReset(unknownEmail);

        assertThat(known.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(unknown.getStatusCode()).isEqualTo(known.getStatusCode());
        assertThat(unknown.getBody()).isEqualTo(known.getBody());

        // Et surtout : aucun jeton n'a été émis pour l'adresse inconnue.
        assertThat(capturedTokens.countFor(unknownEmail)).isZero();
        assertThat(capturedTokens.countFor(account.getEmail())).isEqualTo(1);
    }

    @Test
    void aValidTokenSetsTheNewPasswordAndLetsTheUserLogIn() {
        UserAccount account = persistUser(uniqueEmail(), AccountStatus.ACTIVE);
        requestReset(account.getEmail());
        String rawToken = latestTokenFor(account.getEmail());

        ResponseEntity<Void> reset = resetPassword(rawToken, NEW_PASSWORD);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(login(account.getEmail(), NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login(account.getEmail(), OLD_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aTokenCannotBeUsedTwice() {
        UserAccount account = persistUser(uniqueEmail(), AccountStatus.ACTIVE);
        requestReset(account.getEmail());
        String rawToken = latestTokenFor(account.getEmail());

        assertThat(resetPassword(rawToken, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map<String, Object>> replay = resetPasswordExpectingError(rawToken, "une autre passphrase ok");
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(replay.getBody()).containsEntry("code", "AUTH_RESET_TOKEN_INVALID");
    }

    @Test
    void requestingAgainRevokesThePreviousToken() {
        UserAccount account = persistUser(uniqueEmail(), AccountStatus.ACTIVE);
        requestReset(account.getEmail());
        String firstToken = latestTokenFor(account.getEmail());

        requestReset(account.getEmail());
        String secondToken = latestTokenFor(account.getEmail());
        assertThat(secondToken).isNotEqualTo(firstToken);

        // Le premier lien ne doit plus fonctionner : sinon un lien
        // intercepté resterait utilisable indéfiniment.
        assertThat(resetPasswordExpectingError(firstToken, NEW_PASSWORD).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resetPassword(secondToken, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void anUnknownTokenIsRejectedExactlyLikeAConsumedOne() {
        ResponseEntity<Map<String, Object>> response =
                resetPasswordExpectingError("jeton-qui-nexiste-pas", NEW_PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "AUTH_RESET_TOKEN_INVALID");
    }

    @Test
    void aWeakPasswordIsRejectedWithoutBurningTheToken() {
        UserAccount account = persistUser(uniqueEmail(), AccountStatus.ACTIVE);
        requestReset(account.getEmail());
        String rawToken = latestTokenFor(account.getEmail());

        ResponseEntity<Map<String, Object>> weak = resetPasswordExpectingError(rawToken, "motdepasse123");
        assertThat(weak.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(weak.getBody()).containsEntry("code", "AUTH_PASSWORD_TOO_WEAK");

        // Le jeton reste utilisable : la personne corrige son mot de passe
        // sans avoir à redemander un lien.
        assertThat(resetPassword(rawToken, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void aSuspendedAccountCannotResetItsPassword() {
        UserAccount account = persistUser(uniqueEmail(), AccountStatus.SUSPENDED);

        assertThat(requestReset(account.getEmail()).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Réponse neutre côté API, mais aucun jeton émis : la
        // réinitialisation ne doit pas servir à contourner une décision
        // administrative.
        assertThat(capturedTokens.countFor(account.getEmail())).isZero();
    }

    @Test
    void aPendingAccountBecomesActiveAfterResettingItsPassword() {
        UserAccount account = persistUser(uniqueEmail(), AccountStatus.PENDING_ACTIVATION);
        requestReset(account.getEmail());

        assertThat(resetPassword(latestTokenFor(account.getEmail()), NEW_PASSWORD).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        UserAccount reloaded = userAccountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(login(account.getEmail(), NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aSuccessfulResetIsAudited() {
        UserAccount account = persistUser(uniqueEmail(), AccountStatus.ACTIVE);
        requestReset(account.getEmail());
        resetPassword(latestTokenFor(account.getEmail()), NEW_PASSWORD);

        List<AuditEvent> events = auditEventRepository.findAll().stream()
                .filter(e -> account.getId().equals(e.getActorUserId()))
                .toList();

        assertThat(events).extracting(AuditEvent::getAction)
                .contains("PASSWORD_CHANGED", "SESSIONS_REVOKED");
        assertThat(events).allSatisfy(event ->
                assertThat(event.toString()).doesNotContain(NEW_PASSWORD, OLD_PASSWORD));
    }

    private ResponseEntity<Map<String, Object>> requestReset(String email) {
        return restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email)),
                new ParameterizedTypeReference<>() {
                });
    }

    private ResponseEntity<Void> resetPassword(String token, String newPassword) {
        return restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("token", token, "newPassword", newPassword)),
                Void.class);
    }

    private ResponseEntity<Map<String, Object>> resetPasswordExpectingError(String token, String newPassword) {
        return restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("token", token, "newPassword", newPassword)),
                new ParameterizedTypeReference<>() {
                });
    }

    private ResponseEntity<Map<String, Object>> login(String email, String password) {
        return restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email, "password", password)),
                new ParameterizedTypeReference<>() {
                });
    }

    private String latestTokenFor(String email) {
        return capturedTokens.latestFor(email)
                .map(PasswordResetRequestedEvent::rawToken)
                .orElseThrow(() -> new AssertionError("Aucun jeton émis pour " + email));
    }

    private UserAccount persistUser(String email, AccountStatus status) {
        UserAccount account = new UserAccount(email, "Prénom", "Nom", status);
        account.setPasswordHash(passwordEncoder.encode(OLD_PASSWORD));
        return userAccountRepository.saveAndFlush(account);
    }

    private static String uniqueEmail() {
        return "reset-" + UUID.randomUUID() + "@esic-connect.test";
    }
}
