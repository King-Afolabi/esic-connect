package com.esic.connect.identity.internal;

import com.esic.connect.shared.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DefaultDemoAccountProvisioner} — profil {@code demo} :
 * l'amorçage crée un compte {@code ACTIVE} avec ses rôles, est
 * idempotent (aucun doublon, mot de passe conservé) et sait ajouter un
 * rôle manquant.
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

    @Test
    void createsAnActiveAccountWithItsRoleThenIsIdempotent() {
        String email = "demo-" + UUID.randomUUID() + "@example.test";

        UUID first = provisioner.ensureActiveAccount(email, "Awa", "Diallo", "demo-password-1234",
                Set.of("ADMIN"));

        UserAccount account = userAccountRepository.findByEmail(email).orElseThrow();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getEmailVerifiedAt()).isNotNull();
        String hashAfterFirst = account.getPasswordHash();
        assertThat(hashAfterFirst).isNotBlank().isNotEqualTo("demo-password-1234");
        assertThat(activeRoleCodes(account.getId())).containsExactly(RoleCode.ADMIN);

        // Deuxième appel : même compte, aucun doublon de rôle, mot de passe inchangé.
        UUID second = provisioner.ensureActiveAccount(email, "Awa", "Diallo", "another-password-9999",
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

    private java.util.List<RoleCode> activeRoleCodes(Long userId) {
        return userRoleRepository.findActiveWithRoleByUserId(userId).stream()
                .map(userRole -> userRole.getRole().getCode())
                .toList();
    }
}
