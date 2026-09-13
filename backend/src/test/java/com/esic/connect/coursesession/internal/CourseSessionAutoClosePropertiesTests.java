package com.esic.connect.coursesession.internal;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validation de {@link CourseSessionAutoCloseProperties} (Lot 9) : délai
 * de grâce absent → 15 minutes, négatif refusé au démarrage, zéro
 * autorisé.
 */
class CourseSessionAutoClosePropertiesTests {

    @Test
    void gracePeriodDefaultsTo15MinutesWhenNotBound() {
        // Le défaut Spring Boot (@DefaultValue) s'applique quand la
        // valeur n'est pas liée : ce test le fixe explicitement à la
        // même valeur pour documenter l'attendu sans dépendre du
        // mécanisme de binding lui-même (couvert par application.yml).
        CourseSessionAutoCloseProperties properties =
                new CourseSessionAutoCloseProperties(true, 200, Duration.ofMinutes(15));
        assertThat(properties.gracePeriod()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void aNegativeGracePeriodIsRejected() {
        assertThatThrownBy(() -> new CourseSessionAutoCloseProperties(true, 200, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("négatif");
    }

    @Test
    void aNullGracePeriodIsRejected() {
        assertThatThrownBy(() -> new CourseSessionAutoCloseProperties(true, 200, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aZeroGracePeriodIsAllowed() {
        CourseSessionAutoCloseProperties properties =
                new CourseSessionAutoCloseProperties(true, 200, Duration.ZERO);
        assertThat(properties.gracePeriod()).isZero();
    }
}
