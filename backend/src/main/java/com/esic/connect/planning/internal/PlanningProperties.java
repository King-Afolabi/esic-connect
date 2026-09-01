package com.esic.connect.planning.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalTime;

/**
 * Configuration typée et validée du module {@code planning}. Préfixe
 * {@code app.planning}. Valeurs par défaut = décisions de prototype
 * (DEC-G1-005 : bornes de durée et fenêtre horaire configurables). Toute
 * valeur invalide fait échouer le démarrage — même posture que
 * {@code StudentImportProperties}.
 *
 * @param maxRows           lignes de données maximales d'un fichier (défaut 500)
 * @param maxFileBytes      taille maximale du fichier (défaut 2 MiB)
 * @param simulationTtl     durée de vie d'une simulation avant expiration (défaut P7D)
 * @param minDuration       durée minimale d'un créneau avant {@code WARNING} (défaut PT15M)
 * @param maxDuration       durée maximale d'un créneau avant {@code WARNING} (défaut PT8H)
 * @param workingDayStart   début de la plage horaire habituelle (défaut 08:00)
 * @param workingDayEnd     fin de la plage horaire habituelle (défaut 19:00)
 */
@Validated
@ConfigurationProperties(prefix = "app.planning")
record PlanningProperties(
        @DefaultValue("500") @Min(1) int maxRows,
        @DefaultValue("2097152") @Positive long maxFileBytes,
        @DefaultValue("P7D") @NotNull Duration simulationTtl,
        @DefaultValue("PT15M") @NotNull Duration minDuration,
        @DefaultValue("PT8H") @NotNull Duration maxDuration,
        @DefaultValue("08:00") @NotNull LocalTime workingDayStart,
        @DefaultValue("19:00") @NotNull LocalTime workingDayEnd) {

    PlanningProperties {
        requirePositive("app.planning.simulation-ttl", simulationTtl);
        requirePositive("app.planning.min-duration", minDuration);
        requirePositive("app.planning.max-duration", maxDuration);
        if (maxDuration.compareTo(minDuration) <= 0) {
            throw new IllegalStateException(
                    "app.planning.max-duration doit être strictement supérieure à app.planning.min-duration.");
        }
        if (!workingDayEnd.isAfter(workingDayStart)) {
            throw new IllegalStateException(
                    "app.planning.working-day-end doit être postérieure à app.planning.working-day-start.");
        }
    }

    private static void requirePositive(String key, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(
                    key + " doit être une durée strictement positive (valeur reçue : " + value + ").");
        }
    }
}
