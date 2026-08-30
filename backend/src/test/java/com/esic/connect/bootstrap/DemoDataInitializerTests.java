package com.esic.connect.bootstrap;

import com.esic.connect.identity.DemoAccountProvisioner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DemoDataInitializer} — le mot de passe de démonstration est
 * obligatoire (≥ 12 caractères) et l'amorçage crée exactement quatre
 * comptes fictifs sur le domaine {@code example.test}.
 */
class DemoDataInitializerTests {

    private final RecordingProvisioner provisioner = new RecordingProvisioner();

    @Test
    void rejectsAMissingOrTooShortDemoPassword() {
        assertThatThrownBy(() -> new DemoDataInitializer(provisioner, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DemoDataInitializer(provisioner, "short"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void provisionsExactlyFourFictionalAccounts() {
        DemoDataInitializer initializer = new DemoDataInitializer(provisioner, "demo-password-1234");
        initializer.run(new DefaultApplicationArguments());

        assertThat(provisioner.calls.get()).isEqualTo(4);
        assertThat(provisioner.emails)
                .containsExactlyInAnyOrder("admin@example.test", "formateur@example.test",
                        "apprenant1@example.test", "apprenant2@example.test");
        assertThat(provisioner.emails).allMatch(email -> email.endsWith("@example.test"));
        assertThat(provisioner.roles).contains("ADMIN", "TEACHER", "STUDENT");
        // Le mot de passe transmis n'est jamais journalisé par l'initialiseur.
        assertThat(provisioner.passwords).containsOnly("demo-password-1234");
    }

    /** Double de {@link DemoAccountProvisioner} qui enregistre les appels. */
    private static final class RecordingProvisioner implements DemoAccountProvisioner {
        final AtomicInteger calls = new AtomicInteger();
        final java.util.Set<String> emails = new java.util.HashSet<>();
        final java.util.Set<String> roles = new java.util.HashSet<>();
        final java.util.Set<String> passwords = new java.util.HashSet<>();

        @Override
        public UUID ensureActiveAccount(String email, String firstName, String lastName,
                                        String rawPassword, java.util.Set<String> roleCodes) {
            calls.incrementAndGet();
            emails.add(email);
            roles.addAll(roleCodes);
            passwords.add(rawPassword);
            return UUID.randomUUID();
        }
    }
}
