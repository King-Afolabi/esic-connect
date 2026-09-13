package com.esic.connect.bootstrap;

import com.esic.connect.identity.DemoAccountProvisioner;
import com.esic.connect.identity.DemoMfaProvisioner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code app.demo.seed-accounts} (audit 2026-09-14) : permet de couper
 * l'amorçage des 6 comptes {@code @example.test} sans quitter le profil
 * {@code demo} — la Pi tourne dessus en permanence et héberge des comptes
 * réels (voir {@link DemoDataInitializer}). Vérifie le câblage
 * conditionnel du bean, indépendamment de son comportement métier déjà
 * couvert par {@link DemoDataInitializerTests}.
 */
class DemoDataInitializerConditionalTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("demo"))
            .withUserConfiguration(TestDoubles.class, DemoDataInitializer.class)
            .withPropertyValues("app.demo.password=fixture-password-1234");

    @Test
    void isRegisteredByDefault() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(DemoDataInitializer.class));
    }

    @Test
    void isRegisteredWhenExplicitlyEnabled() {
        contextRunner.withPropertyValues("app.demo.seed-accounts=true")
                .run(context -> assertThat(context).hasSingleBean(DemoDataInitializer.class));
    }

    @Test
    void isAbsentWhenDisabled() {
        contextRunner.withPropertyValues("app.demo.seed-accounts=false")
                .run(context -> assertThat(context).doesNotHaveBean(DemoDataInitializer.class));
    }

    @Configuration
    static class TestDoubles {
        @Bean
        DemoAccountProvisioner demoAccountProvisioner() {
            return (email, firstName, lastName, rawPassword, roleCodes) -> null;
        }

        @Bean
        DemoMfaProvisioner demoMfaProvisioner() {
            return (email, base32Secret) -> { };
        }
    }
}
