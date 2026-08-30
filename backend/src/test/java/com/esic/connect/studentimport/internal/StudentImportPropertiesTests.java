package com.esic.connect.studentimport.internal;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Configuration typée de l'import (rapport §8, §3.2) : valeurs par défaut,
 * borne de séquence dérivée de la largeur, refus d'une durée non positive
 * au binding (même posture que le TTL d'invitation).
 */
class StudentImportPropertiesTests {

    private static StudentImportProperties defaults() {
        return new StudentImportProperties(500, 2_097_152L, Duration.ofDays(7), Duration.ofDays(30), 5, 5);
    }

    @Test
    void exposesTheProrotypeDefaults() {
        StudentImportProperties props = defaults();
        assertThat(props.maxRows()).isEqualTo(500);
        assertThat(props.maxFileBytes()).isEqualTo(2_097_152L);
        assertThat(props.simulationTtl()).isEqualTo(Duration.ofDays(7));
        assertThat(props.appliedRowsTtl()).isEqualTo(Duration.ofDays(30));
        assertThat(props.numberSequenceWidth()).isEqualTo(5);
        assertThat(props.numberAllocMaxRetries()).isEqualTo(5);
    }

    @Test
    void derivesTheSequenceUpperBoundFromTheWidth() {
        assertThat(defaults().numberSequenceUpperBound()).isEqualTo(100_000L);
        assertThat(new StudentImportProperties(500, 1L, Duration.ofDays(7), Duration.ofDays(30), 3, 5)
                .numberSequenceUpperBound()).isEqualTo(1_000L);
    }

    @Test
    void rejectsANonPositiveSimulationTtl() {
        assertThatThrownBy(() -> new StudentImportProperties(500, 1L, Duration.ZERO, Duration.ofDays(30), 5, 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulation-ttl");
        assertThatThrownBy(() -> new StudentImportProperties(500, 1L, Duration.ofDays(-1), Duration.ofDays(30), 5, 5))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsANonPositiveAppliedRowsTtl() {
        assertThatThrownBy(() -> new StudentImportProperties(500, 1L, Duration.ofDays(7), Duration.ZERO, 5, 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("applied-rows-ttl");
    }
}
