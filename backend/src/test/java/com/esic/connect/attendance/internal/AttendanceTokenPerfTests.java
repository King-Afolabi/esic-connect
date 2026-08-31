package com.esic.connect.attendance.internal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mesure de performance (FINAL-009) — <strong>non exécutée par
 * {@code mvn test}</strong> (tag {@code perf}). Lancer via
 * {@code ./mvnw test -Pperf}.
 *
 * <p>Chronomètre l'émission + rotation d'un jeton d'émargement
 * ({@link AttendanceTokenService#issue}) contre le Redis réel du profil
 * {@code test} : lecture du pointeur courant, tirage {@link java.security.SecureRandom},
 * écriture des trois clés, suppression de l'ancien couple. Après la
 * première itération, chaque appel est une <em>rotation</em> (le couple
 * précédent existe). Nettoie ses clés à la fin. Aucune assertion de
 * latence stricte — garde-fou large uniquement ; chiffres repris dans
 * {@code docs/reports/PERF_NOTES.md}.
 */
@Tag("perf")
@SpringBootTest
@ActiveProfiles("test")
class AttendanceTokenPerfTests {

    private static final int WARMUP = 5;
    private static final int ITERATIONS = 50;

    @Autowired
    private AttendanceTokenService tokenService;

    @Test
    void measuresTokenIssueAndRotation() {
        UUID session = UUID.randomUUID();
        UUID checkpoint = UUID.randomUUID();

        long[] samplesNanos = new long[ITERATIONS];
        try {
            for (int i = 0; i < WARMUP + ITERATIONS; i++) {
                long start = System.nanoTime();
                IssuedAttendanceToken issued = tokenService.issue(session, checkpoint);
                long elapsed = System.nanoTime() - start;
                assertThat(issued.token()).isNotBlank();
                assertThat(issued.shortCode()).hasSize(8);
                if (i >= WARMUP) {
                    samplesNanos[i - WARMUP] = elapsed;
                }
            }
        } finally {
            tokenService.invalidateSession(session);
        }

        Arrays.sort(samplesNanos);
        double minMs = samplesNanos[0] / 1_000_000d;
        double p50Ms = samplesNanos[samplesNanos.length / 2] / 1_000_000d;
        double p95Ms = samplesNanos[(int) Math.ceil(samplesNanos.length * 0.95) - 1] / 1_000_000d;
        double maxMs = samplesNanos[samplesNanos.length - 1] / 1_000_000d;

        System.out.printf(
                "%n[PERF] attendance token issue()+rotation — iterations=%d warmup=%d%n"
                        + "[PERF]   min=%.2f ms  p50=%.2f ms  p95=%.2f ms  max=%.2f ms%n",
                ITERATIONS, WARMUP, minMs, p50Ms, p95Ms, maxMs);

        // Garde-fou large : détecte une régression catastrophique, pas une
        // garantie contractuelle. La cible « < 100 ms » du cadrage est
        // discutée dans docs/reports/PERF_NOTES.md.
        assertThat(p50Ms).as("génération médiane d'un jeton d'émargement").isLessThan(1_000d);
    }
}
