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

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DefaultDemoAccountProvisioner} — profil {@code demo} :
 * l'amorçage crée un compte {@code ACTIVE} avec ses rôles, est
 * <em>fonctionnellement</em> idempotent (aucun doublon ; hachage réécrit
 * seulement si le mot de passe courant ne correspond plus) et
 * resynchronise un compte fictif déjà présent — mot de passe et statut —
 * sans toucher à son identité ni à ses rôles.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({ "test", "demo" })
@Import({ JpaAuditingConfig.class, DefaultDemoAccountProvisioner.class })
class DefaultDemoAccountProvisionerTests {

    @TestConfiguration
    static class EncoderConfig {
        @Bean
        PasswordEncoder passwordEncoder() {
            return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }
    }

    @Autowired
    private DefaultDemoAccountProvisioner provisioner;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void createsAnActiveAccountWithItsRoleThenIsFunctionallyIdempotent() {
        String email = "demo-" + UUID.randomUUID() + "@example.test";

        UUID first = provisioner.ensureActiveAccount(email, "Awa", "Diallo", "demo-password-1234",
                Set.of("ADMIN"));

        UserAccount account = userAccountRepository.findByEmail(email).orElseThrow();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getEmailVerifiedAt()).isNotNull();
        String hashAfterFirst = account.getPasswordHash();
        assertThat(hashAfterFirst).isNotBlank().isNotEqualTo("demo-password-1234");
        assertThat(passwordEncoder.matches("demo-password-1234", hashAfterFirst)).isTrue();
        assertThat(activeRoleCodes(account.getId())).containsExactly(RoleCode.ADMIN);

        // Deuxième démarrage, MÊME mot de passe : aucun doublon de rôle,
        // hachage stocké inchangé octet pour octet (pas de réhachage).
        UUID second = provisioner.ensureActiveAccount(email, "Awa", "Diallo", "demo-password-1234",
                Set.of("ADMIN"));
        assertThat(second).isEqualTo(first);
        UserAccount reloaded = userAccountRepository.findByEmail(email).orElseThrow();
        assertThat(reloaded.getPasswordHash()).isEqualTo(hashAfterFirst);
        assertThat(activeRoleCodes(reloaded.getId())).containsExactly(RoleCode.ADMIN);
    }

    @Test
    void normalizesEmailAndAddsAMissingRoleOnASubsequentCall() {
        String email = "demo-" + UUID.randomUUID() + "@EXAMPLE.TEST";
        provisioner.ensureActiveAccount(email, "Karim", "Benali", "demo-password-1234", Set.of("TEACHER"));

        UserAccount account = userAccountRepository.findByEmail(email.toLowerCase()).orElseThrow();
        assertThat(activeRoleCodes(account.getId())).containsExactly(RoleCode.TEACHER);

        provisioner.ensureActiveAccount(email.toLowerCase(), "Karim", "Benali", "demo-password-1234",
                Set.of("TEACHER", "PEDAGOGICAL_MANAGER"));
        assertThat(activeRoleCodes(account.getId()))
                .containsExactlyInAnyOrder(RoleCode.TEACHER, RoleCode.PEDAGOGICAL_MANAGER);
    }

    @Test
    void resynchronisesTheCurrentPasswordOnAnExistingAccountWithoutTouchingIdentityOrRoles() {
        String email = "demo-" + UUID.randomUUID() + "@example.test";

        UUID publicId = provisioner.ensureActiveAccount(email, "Lina", "Sow", "old-demo-password-1",
                Set.of("STUDENT"));
        UserAccount before = userAccountRepository.findByEmail(email).orElseThrow();
        Long internalId = before.getId();
        String staleHash = before.getPasswordHash();

        // Le back-end est relancé avec une NOUVELLE valeur de ESIC_DEMO_PASSWORD.
        UUID samePublicId = provisioner.ensureActiveAccount(email, "Lina", "Sow", "new-demo-password-2",
                Set.of("STUDENT"));

        assertThat(samePublicId).isEqualTo(publicId);
        UserAccount after = userAccountRepository.findByEmail(email).orElseThrow();
        assertThat(after.getId()).isEqualTo(internalId);           // même ligne, aucun doublon
        assertThat(after.getPasswordHash()).isNotEqualTo(staleHash);
        assertThat(passwordEncoder.matches("new-demo-password-2", after.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("old-demo-password-1", after.getPasswordHash())).isFalse();
        assertThat(after.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(activeRoleCodes(internalId)).containsExactly(RoleCode.STUDENT);
    }

    @Test
    void bringsASuspendedDemoAccountBackToALoginableStateWithTheCurrentPassword() {
        String email = "demo-" + UUID.randomUUID() + "@example.test";
        provisioner.ensureActiveAccount(email, "Noah", "Mercier", "demo-password-1234", Set.of("STUDENT"));

        UserAccount account = userAccountRepository.findByEmail(email).orElseThrow();
        account.suspend("intervention manuelle pendant la validation", null, Instant.now());
        userAccountRepository.saveAndFlush(account);

        provisioner.ensureActiveAccount(email, "Noah", "Mercier", "demo-password-1234", Set.of("STUDENT"));

        UserAccount restored = userAccountRepository.findByEmail(email).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(restored.getSuspendedAt()).isNull();
        assertThat(restored.getSuspensionReason()).isNull();
        assertThat(passwordEncoder.matches("demo-password-1234", restored.getPasswordHash())).isTrue();
        assertThat(activeRoleCodes(restored.getId())).containsExactly(RoleCode.STUDENT);
    }

    @Test
    void theSynchronisationBehaviourCannotExistOutsideTheDemoProfile() {
        Profile profile = DefaultDemoAccountProvisioner.class.getAnnotation(Profile.class);
        assertThat(profile).as("le provisioner de démo doit rester confiné au profil demo").isNotNull();
        assertThat(profile.value()).containsExactly("demo");
    }

    private java.util.List<RoleCode> activeRoleCodes(Long userId) {
        return userRoleRepository.findActiveWithRoleByUserId(userId).stream()
                .map(userRole -> userRole.getRole().getCode())
                .toList();
    }
}
