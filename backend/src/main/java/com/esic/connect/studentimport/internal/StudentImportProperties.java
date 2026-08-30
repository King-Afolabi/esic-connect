package com.esic.connect.studentimport.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration typée et validée de l'import CSV des apprenants
 * (rapport §8, §3.2). Préfixe {@code app.import.student}. Les valeurs par
 * défaut correspondent aux décisions de prototype du rapport ; elles
 * s'appliquent même en l'absence de bloc dans {@code application.yml}.
 *
 * <p>Toute valeur invalide (durée nulle ou négative, largeur de séquence
 * hors bornes, limite de lignes inférieure au minimum exigé) fait
 * échouer le démarrage — même posture que la validation du TTL
 * d'invitation dans {@code AccountInvitationService}.
 *
 * @param maxRows              nombre maximal de lignes de données d'un fichier ({@code ≥ 100} exigé,
 *                             défaut {@code 500})
 * @param maxFileBytes         taille maximale du fichier en octets (défaut {@code 2 MiB})
 * @param simulationTtl        durée de vie d'une simulation avant expiration (défaut {@code P7D})
 * @param appliedRowsTtl       durée de conservation des lignes filles d'un job {@code APPLIED}
 *                             avant purge (agrégats conservés ; défaut {@code P30D})
 * @param numberSequenceWidth  largeur du compteur zéro-padé d'un numéro
 *                             {@code ESIC-{annéeDébut}-{séquence}} (défaut {@code 5})
 * @param numberAllocMaxRetries nombre maximal de nouvelles tentatives d'allocation d'un numéro
 *                             étudiant sur collision d'unicité (défaut {@code 5})
 */
@Validated
@ConfigurationProperties(prefix = "app.import.student")
record StudentImportProperties(
        @DefaultValue("500") @Min(100) int maxRows,
        @DefaultValue("2097152") @Positive long maxFileBytes,
        @DefaultValue("P7D") @NotNull Duration simulationTtl,
        @DefaultValue("P30D") @NotNull Duration appliedRowsTtl,
        @DefaultValue("5") @Min(1) @Max(9) int numberSequenceWidth,
        @DefaultValue("5") @Min(1) int numberAllocMaxRetries) {

    StudentImportProperties {
        requirePositive("app.import.student.simulation-ttl", simulationTtl);
        requirePositive("app.import.student.applied-rows-ttl", appliedRowsTtl);
    }

    /** Borne supérieure exclusive de la séquence pour une année (10^largeur). */
    long numberSequenceUpperBound() {
        long bound = 1;
        for (int i = 0; i < numberSequenceWidth; i++) {
            bound *= 10;
        }
        return bound;
    }

    private static void requirePositive(String key, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(
                    key + " doit être une durée strictement positive (valeur reçue : " + value + ").");
        }
    }
}
