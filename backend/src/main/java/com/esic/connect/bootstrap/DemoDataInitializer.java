package com.esic.connect.bootstrap;

import com.esic.connect.identity.DemoAccountProvisioner;
import com.esic.connect.identity.DemoMfaProvisioner;
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
 * {@code demo}. Crée six comptes fictifs de façon idempotente ; ne
 * touche à aucune donnée métier réelle et ne s'exécute jamais sous
 * {@code local}, {@code test} ou en production.
 *
 * <p>Le référentiel académique, les profils apprenants, les inscriptions,
 * une séance {@code PLANNED} et l'affectation du responsable pédagogique
 * à la formation de démonstration sont créés séparément par
 * {@code scripts/seed-demo.sh} via les API REST réelles (avec le compte
 * {@code ADMIN} ci-dessous).
 *
 * <p>Le compte {@code superadmin@example.test} rend démontrables les
 * routes réservées à {@code SUPER_ADMIN} — notamment les plages réseau
 * CIDR ({@code SiteNetworkRangeController}), inaccessibles même à
 * {@code ADMIN}. Le rôle {@code SUPER_ADMIN} est déjà créé par la
 * migration {@code V2} ; aucun privilège nouveau n'est introduit ici.
 *
 * <p>Le compte {@code responsable@example.test} porte <strong>deux
 * rôles</strong> ({@code PEDAGOGICAL_MANAGER} + {@code TEACHER}) afin de
 * rendre démontrable le <em>sélecteur de contexte de rôle</em>
 * (EF-AUTH-003). Le cumul de rôles n'élargit jamais le JWT : Spring
 * Security reste l'autorité.
 *
 * <p>Si {@code ESIC_DEMO_TOTP_SECRET} (propriété {@code app.demo.totp-secret})
 * est renseignée, un facteur TOTP <strong>déterministe</strong> est en plus
 * activé pour {@code superadmin@example.test} et {@code admin@example.test}
 * (dette T-19/T-20, {@code docs/CURRENT-STATE.md}) : ces deux rôles exigent
 * un second facteur (RG-007) et, sans secret connu à l'avance, aucun script
 * ni aucune suite de bout en bout ne peut le franchir. Strictement
 * optionnel — absent, le comportement est inchangé : ces comptes restent
 * bloqués à l'écran d'enrôlement, comme avant. Cette variable ne doit
 * JAMAIS porter un secret de production ; elle n'a d'effet que sous le
 * profil {@code demo}, jamais en production.
 */
@Component
@Profile("demo")
class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private static final int MIN_PASSWORD_LENGTH = 12;

    /**
     * Comptes fictifs — domaine d'email réservé {@code example.test}.
     *
     * <p>Les noms affichés portent leur rôle : devant un jury, la colonne
     * « nom » d'une liste d'utilisateurs suffit à identifier qui est qui,
     * sans revenir à la grille des comptes.
     */
    private static final DemoAccount SUPER_ADMIN =
            new DemoAccount("superadmin@example.test", "Super Administrateur", "Démo",
                    Set.of("SUPER_ADMIN"));
    private static final DemoAccount ADMIN =
            new DemoAccount("admin@example.test", "Administrateur", "Démo", Set.of("ADMIN"));
    private static final DemoAccount TEACHER =
            new DemoAccount("formateur@example.test", "Formateur", "Démo", Set.of("TEACHER"));
    private static final DemoAccount STUDENT_ONE =
            new DemoAccount("apprenant1@example.test", "Alice", "Martin", Set.of("STUDENT"));
    private static final DemoAccount STUDENT_TWO =
            new DemoAccount("apprenant2@example.test", "Karim", "Diallo", Set.of("STUDENT"));
    /** Compte multi-rôles : démontre le sélecteur de contexte de rôle (EF-AUTH-003). */
    private static final DemoAccount RESPONSIBLE =
            new DemoAccount("responsable@example.test", "Responsable Pédagogique", "Démo",
                    Set.of("PEDAGOGICAL_MANAGER", "TEACHER"));

    private final DemoAccountProvisioner provisioner;
    private final DemoMfaProvisioner mfaProvisioner;
    private final String demoPassword;
    private final String demoTotpSecret;

    DemoDataInitializer(DemoAccountProvisioner provisioner,
                        DemoMfaProvisioner mfaProvisioner,
                        @Value("${app.demo.password}") String demoPassword,
                        @Value("${app.demo.totp-secret:}") String demoTotpSecret) {
        if (demoPassword == null || demoPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "ESIC_DEMO_PASSWORD (app.demo.password) est obligatoire sous le profil demo "
                            + "et doit contenir au moins " + MIN_PASSWORD_LENGTH + " caractères.");
        }
        this.provisioner = provisioner;
        this.mfaProvisioner = mfaProvisioner;
        this.demoPassword = demoPassword;
        this.demoTotpSecret = demoTotpSecret;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (DemoAccount account :
                Set.of(SUPER_ADMIN, ADMIN, TEACHER, STUDENT_ONE, STUDENT_TWO, RESPONSIBLE)) {
            provisioner.ensureActiveAccount(account.email(), account.firstName(), account.lastName(),
                    demoPassword, account.roles());
        }
        // Le mot de passe n'est jamais journalisé.
        log.info("Amorçage demo : 6 comptes fictifs synchronisés "
                + "(super-admin / admin / formateur / 2 apprenants / responsable pédagogique "
                + "multi-rôles) — "
                + "statut ACTIVE et mot de passe aligné sur la valeur courante de "
                + "ESIC_DEMO_PASSWORD. Complétez avec scripts/seed-demo.sh.");

        if (demoTotpSecret != null && !demoTotpSecret.isBlank()) {
            mfaProvisioner.ensureDeterministicTotp(SUPER_ADMIN.email(), demoTotpSecret);
            mfaProvisioner.ensureDeterministicTotp(ADMIN.email(), demoTotpSecret);
            log.warn("Facteur TOTP déterministe activé pour super-admin et admin de démonstration "
                    + "(ESIC_DEMO_TOTP_SECRET défini) — réservé au profil demo, jamais en production.");
        }
    }

    private record DemoAccount(String email, String firstName, String lastName, Set<String> roles) {
    }
}
