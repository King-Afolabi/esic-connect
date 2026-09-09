package com.esic.connect.identity.internal;

import com.esic.connect.shared.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DefaultDemoMfaProvisioner} — active un facteur TOTP
 * <strong>déterministe</strong> pour un compte de démonstration déjà
 * provisionné (dette T-19/T-20, {@code docs/STATUS.md}) : le code
 * qu'il calcule à partir de {@code base32Secret} doit être accepté par le
 * vrai vérificateur ({@link TotpGenerator}), exactement comme le ferait une
 * application d'authentification après un enrôlement normal. L'opération
 * est idempotente et resynchronise un secret différent, comme
 * {@link DefaultDemoAccountProvisioner} le fait pour le mot de passe.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({ "test", "demo" })
// Même précaution que DefaultDemoAccountProvisionerTests : forcer la base
// de TEST, jamais celle de démonstration, quelle que soit MYSQL_DATABASE.
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/"
        + "${MYSQL_TEST_DATABASE:esic_test}"
        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
        // Clé de chiffrement dédiée à ce test : indépendante de JWT_SECRET,
        // pour ne pas dépendre d'une variable d'environnement externe.
        "app.security.mfa.encryption-key=test-only-demo-mfa-provisioner-encryption-key-32bytes",
})
@Import({ JpaAuditingConfig.class, DefaultDemoAccountProvisioner.class, DefaultDemoMfaProvisioner.class,
        SecretCipher.class, DefaultDemoMfaProvisionerTests.EncoderConfig.class })
class DefaultDemoMfaProvisionerTests {

    private static final String SECRET_A = "JBSWY3DPEHPK3PXP";
    private static final String SECRET_B = "KRSXG5CTMVRXEZLU";

    @TestConfiguration
    static class EncoderConfig {
        @Bean
        PasswordEncoder passwordEncoder() {
            return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }
    }

    @Autowired
    private DefaultDemoAccountProvisioner accountProvisioner;
    @Autowired
    private DefaultDemoMfaProvisioner mfaProvisioner;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private MfaCredentialRepository credentialRepository;
    @Autowired
    private SecretCipher secretCipher;

    @Test
    void activatesADeterministicFactorThatTheRealVerifierAccepts() {
        String email = provisionAccount();

        mfaProvisioner.ensureDeterministicTotp(email, SECRET_A);

        UserAccount account = userAccountRepository.findByEmail(email).orElseThrow();
        MfaCredential credential = credentialRepository
                .findByUserIdAndStatus(account.getId(), MfaCredentialStatus.ACTIVE)
                .orElseThrow();
        assertThat(secretCipher.decrypt(credential.getSecretCipher())).isEqualTo(SECRET_A);

        // Le code calculé maintenant avec ce secret, comme le ferait une
        // vraie application d'authentification, est bien reconnu par le
        // vérificateur réel du parcours /mfa/verify.
        String code = TotpGenerator.codeAt(SECRET_A, TotpGenerator.stepOf(Instant.now()));
        assertThat(TotpGenerator.matches(SECRET_A, code, Instant.now(), 1)).isNotNull();
    }

    @Test
    void isIdempotentWhenCalledTwiceWithTheSameSecret() {
        String email = provisionAccount();

        mfaProvisioner.ensureDeterministicTotp(email, SECRET_A);
        UserAccount account = userAccountRepository.findByEmail(email).orElseThrow();
        Long firstCredentialId = credentialRepository
                .findByUserIdAndStatus(account.getId(), MfaCredentialStatus.ACTIVE)
                .orElseThrow().getId();

        mfaProvisioner.ensureDeterministicTotp(email, SECRET_A);

        Long secondCredentialId = credentialRepository
                .findByUserIdAndStatus(account.getId(), MfaCredentialStatus.ACTIVE)
                .orElseThrow().getId();
        assertThat(secondCredentialId).isEqualTo(firstCredentialId);
    }

    @Test
    void resynchronisesToANewSecretWhenTheConfiguredValueChanges() {
        String email = provisionAccount();
        mfaProvisioner.ensureDeterministicTotp(email, SECRET_A);

        mfaProvisioner.ensureDeterministicTotp(email, SECRET_B);

        UserAccount account = userAccountRepository.findByEmail(email).orElseThrow();
        MfaCredential active = credentialRepository
                .findByUserIdAndStatus(account.getId(), MfaCredentialStatus.ACTIVE)
                .orElseThrow();
        assertThat(secretCipher.decrypt(active.getSecretCipher())).isEqualTo(SECRET_B);
        // L'ancien facteur est révoqué, pas supprimé : jamais deux facteurs actifs.
        assertThat(credentialRepository.findByUserIdAndStatus(account.getId(), MfaCredentialStatus.PENDING))
                .isEmpty();
    }

    @Test
    void rejectsAnInvalidBase32Secret() {
        String email = provisionAccount();

        assertThatThrownBy(() -> mfaProvisioner.ensureDeterministicTotp(email, "not-base32!!!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesToEnrolAnUnknownAccount() {
        assertThatThrownBy(() -> mfaProvisioner.ensureDeterministicTotp(
                "inconnu-" + UUID.randomUUID() + "@example.test", SECRET_A))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theDeterministicBehaviourCannotExistOutsideTheDemoProfile() {
        Profile profile = DefaultDemoMfaProvisioner.class.getAnnotation(Profile.class);
        assertThat(profile).as("le provisioner MFA de démo doit rester confiné au profil demo").isNotNull();
        assertThat(profile.value()).containsExactly("demo");
    }

    private String provisionAccount() {
        String email = "demo-mfa-" + UUID.randomUUID() + "@example.test";
        accountProvisioner.ensureActiveAccount(email, "Awa", "Diallo", "demo-password-1234",
                java.util.Set.of("ADMIN"));
        return email;
    }
}
