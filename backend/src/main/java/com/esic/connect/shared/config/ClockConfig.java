package com.esic.connect.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Fournit une {@link Clock} injectable pour tout code qui a besoin de
 * « maintenant » (dates de validité, décision de périmètre...). Passer par
 * l'horloge plutôt que par {@code LocalDate.now()} / {@code Instant.now()}
 * rend ces comportements testables avec une horloge figée.
 *
 * <p>{@link ConditionalOnMissingBean} permet à un test de fournir sa
 * propre {@code Clock} (ex. {@code Clock.fixed(...)}).
 */
@Configuration
public class ClockConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}
