package com.esic.connect.bootstrap;

import com.esic.connect.identity.DemoAccountProvisioner;
import com.esic.connect.identity.DemoMfaProvisioner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DemoDataInitializer} — le mot de passe de démonstration est
 * obligatoire (≥ 12 caractères) et l'amorçage crée exactement cinq
 * comptes fictifs sur le domaine {@code example.test}, dont un compte
 * multi-rôles ({@code PEDAGOGICAL_MANAGER} + {@code TEACHER}). Le secret
 * TOTP déterministe (T-19/T-20) reste strictement optionnel : absent, le
 * comportement historique est inchangé.
 */
class DemoDataInitializerTests {

    private final RecordingProvisioner provisioner = new RecordingProvisioner();
    private final RecordingMfaProvisioner mfaProvisioner = new RecordingMfaProvisioner();

    @Test
    void rejectsAMissingOrTooShortDemoPassword() {
        assertThatThrownBy(() -> new DemoDataInitializer(provisioner, mfaProvisioner, null, ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DemoDataInitializer(provisioner, mfaProvisioner, "short", ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void provisionsExactlySixFictionalAccountsIncludingAMultiRoleOne() {
        DemoDataInitializer initializer =
                new DemoDataInitializer(provisioner, mfaProvisioner, "demo-password-1234", "");
        initializer.run(new DefaultApplicationArguments());

        assertThat(provisioner.calls.get()).isEqualTo(6);
        assertThat(provisioner.emails)
                .containsExactlyInAnyOrder("superadmin@example.test", "admin@example.test",
                        "formateur@example.test", "apprenant1@example.test",
                        "apprenant2@example.test", "responsable@example.test");
        assertThat(provisioner.emails).allMatch(email -> email.endsWith("@example.test"));
        assertThat(provisioner.roles)
                .contains("SUPER_ADMIN", "ADMIN", "TEACHER", "STUDENT", "PEDAGOGICAL_MANAGER");
        // Le compte responsable cumule deux rôles (sélecteur de contexte, EF-AUTH-003).
        assertThat(provisioner.rolesByEmail.get("responsable@example.test"))
                .containsExactlyInAnyOrder("PEDAGOGICAL_MANAGER", "TEACHER");
        // SUPER_ADMIN reste un compte SÉPARÉ du compte d'administration
        // quotidienne (RG-003 / cahier §6.2) : il ne cumule aucun autre rôle.
        assertThat(provisioner.rolesByEmail.get("superadmin@example.test"))
                .containsExactly("SUPER_ADMIN");
        assertThat(provisioner.rolesByEmail.get("admin@example.test"))
                .doesNotContain("SUPER_ADMIN");
        // Les six comptes partagent la même valeur locale d'ESIC_DEMO_PASSWORD,
        // jamais journalisée par l'initialiseur.
        assertThat(provisioner.passwords).containsOnly("demo-password-1234");
        // Refonte 2026-09 : seul l'apprenant COMPLET reçoit un numéro
        // étudiant ici (colonne de user_account, plus de student_profile) ;
        // l'apprenant MINIMAL et les autres rôles n'en reçoivent aucun.
        assertThat(provisioner.studentNumberByEmail.get("apprenant1@example.test")).isEqualTo("ESIC-DEMO-001");
        assertThat(provisioner.studentNumberByEmail.get("apprenant2@example.test")).isNull();
        assertThat(provisioner.studentNumberByEmail.values().stream().filter(java.util.Objects::nonNull).count())
                .isEqualTo(1);
    }

    @Test
    void doesNotTouchMfaWhenNoDeterministicSecretIsConfigured() {
        DemoDataInitializer initializer =
                new DemoDataInitializer(provisioner, mfaProvisioner, "demo-password-1234", "");
        initializer.run(new DefaultApplicationArguments());

        assertThat(mfaProvisioner.calls).isEmpty();
    }

    @Test
    void doesNotTouchMfaWhenTheSecretIsBlank() {
        DemoDataInitializer initializer =
                new DemoDataInitializer(provisioner, mfaProvisioner, "demo-password-1234", "   ");
        initializer.run(new DefaultApplicationArguments());

        assertThat(mfaProvisioner.calls).isEmpty();
    }

    @Test
    void activatesDeterministicTotpForAdminAndSuperAdminOnlyWhenConfigured() {
        DemoDataInitializer initializer = new DemoDataInitializer(provisioner, mfaProvisioner,
                "demo-password-1234", "JBSWY3DPEHPK3PXP");
        initializer.run(new DefaultApplicationArguments());

        assertThat(mfaProvisioner.calls).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                "superadmin@example.test", "JBSWY3DPEHPK3PXP",
                "admin@example.test", "JBSWY3DPEHPK3PXP"));
    }

    /** Double de {@link DemoAccountProvisioner} qui enregistre les appels. */
    private static final class RecordingProvisioner implements DemoAccountProvisioner {
        final AtomicInteger calls = new AtomicInteger();
        final java.util.Set<String> emails = new java.util.HashSet<>();
        final java.util.Set<String> roles = new java.util.HashSet<>();
        final java.util.Set<String> passwords = new java.util.HashSet<>();
        final java.util.Map<String, java.util.Set<String>> rolesByEmail = new java.util.HashMap<>();
        final java.util.Map<String, String> studentNumberByEmail = new java.util.HashMap<>();

        @Override
        public UUID ensureActiveAccount(String email, String firstName, String lastName,
                                        String rawPassword, java.util.Set<String> roleCodes,
                                        String studentNumber) {
            calls.incrementAndGet();
            emails.add(email);
            roles.addAll(roleCodes);
            rolesByEmail.put(email, new java.util.HashSet<>(roleCodes));
            passwords.add(rawPassword);
            studentNumberByEmail.put(email, studentNumber);
            return UUID.randomUUID();
        }
    }

    /** Double de {@link DemoMfaProvisioner} qui enregistre les appels. */
    private static final class RecordingMfaProvisioner implements DemoMfaProvisioner {
        final java.util.Map<String, String> calls = new java.util.HashMap<>();

        @Override
        public void ensureDeterministicTotp(String email, String base32Secret) {
            calls.put(email, base32Secret);
        }
    }
}
