package com.esic.connect.bootstrap;

import com.esic.connect.identity.DemoAccountProvisioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Amorçage de démonstration — actif uniquement sous le profil
 * {@code demo}. Crée quatre comptes fictifs de façon idempotente ; ne
 * touche à aucune donnée métier réelle et ne s'exécute jamais sous
 * {@code local}, {@code test} ou en production.
 *
 * <p>Le référentiel académique, les profils apprenants, les inscriptions,
 * une séance {@code PLANNED} et l'affectation du responsable pédagogique
 * à la formation de démonstration sont créés séparément par
 * {@code scripts/seed-demo.sh} via les API REST réelles (avec le compte
 * {@code ADMIN} ci-dessous).
 *
 * <p>Le compte {@code responsable@example.test} porte <strong>deux
 * rôles</strong> ({@code PEDAGOGICAL_MANAGER} + {@code TEACHER}) afin de
 * rendre démontrable le <em>sélecteur de contexte de rôle</em>
 * (EF-AUTH-003). Le cumul de rôles n'élargit jamais le JWT : Spring
 * Security reste l'autorité.
 */
@Component
@Profile("demo")
class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private static final int MIN_PASSWORD_LENGTH = 12;

    /** Comptes fictifs — domaine d'email réservé {@code example.test}. */
    private static final DemoAccount ADMIN =
            new DemoAccount("admin@example.test", "Awa", "Diallo", Set.of("ADMIN"));
    private static final DemoAccount TEACHER =
            new DemoAccount("formateur@example.test", "Karim", "Benali", Set.of("TEACHER"));
    private static final DemoAccount STUDENT_ONE =
            new DemoAccount("apprenant1@example.test", "Lina", "Sow", Set.of("STUDENT"));
    private static final DemoAccount STUDENT_TWO =
            new DemoAccount("apprenant2@example.test", "Noah", "Mercier", Set.of("STUDENT"));
    /** Compte multi-rôles : démontre le sélecteur de contexte de rôle (EF-AUTH-003). */
    private static final DemoAccount RESPONSIBLE =
            new DemoAccount("responsable@example.test", "Sofia", "Traoré",
                    Set.of("PEDAGOGICAL_MANAGER", "TEACHER"));

    private final DemoAccountProvisioner provisioner;
    private final String demoPassword;

    DemoDataInitializer(DemoAccountProvisioner provisioner,
                        @Value("${app.demo.password}") String demoPassword) {
        if (demoPassword == null || demoPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "ESIC_DEMO_PASSWORD (app.demo.password) est obligatoire sous le profil demo "
                            + "et doit contenir au moins " + MIN_PASSWORD_LENGTH + " caractères.");
        }
        this.provisioner = provisioner;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (DemoAccount account : Set.of(ADMIN, TEACHER, STUDENT_ONE, STUDENT_TWO, RESPONSIBLE)) {
            provisioner.ensureActiveAccount(account.email(), account.firstName(), account.lastName(),
                    demoPassword, account.roles());
        }
        // Le mot de passe n'est jamais journalisé.
        log.info("Amorçage demo : 5 comptes fictifs synchronisés "
                + "(admin / formateur / 2 apprenants / responsable pédagogique multi-rôles) — "
                + "statut ACTIVE et mot de passe aligné sur la valeur courante de "
                + "ESIC_DEMO_PASSWORD. Complétez avec scripts/seed-demo.sh.");
    }

    private record DemoAccount(String email, String firstName, String lastName, Set<String> roles) {
    }
}
