package com.esic.connect.coursesession.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code app.coursesession.auto-close.enabled=false} (le défaut du
 * profil {@code test}, voir {@code application-test.yml}) : le
 * planificateur ne doit même pas exister en tant que bean — pas
 * seulement rester inactif. Aucune propriété n'est redéfinie ici,
 * contrairement à {@link CourseSessionAutoCloseIntegrationTests}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CourseSessionAutoCloseDisabledIntegrationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void theSchedulerBeanDoesNotExistWhenDisabled() {
        assertThatThrownBy(() -> context.getBean(CourseSessionAutoCloseScheduler.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }
}
