package com.esic.connect.coursesession.internal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration typée et validée de la fermeture automatique des séances
 * {@code OPEN} restées sans intervention (Lot 9). Préfixe
 * {@code app.coursesession.auto-close}. Toute valeur invalide fait
 * échouer le démarrage — même posture que {@code PlanningProperties} /
 * {@code StudentImportProperties}.
 *
 * <p><strong>Délai de grâce mono-établissement.</strong> Le modèle ne
 * porte aujourd'hui aucune notion d'établissement configurable
 * (l'entité {@code organization.internal.Site} est un campus physique,
 * sans réglages métier propres) : {@link #gracePeriod} est donc une
 * propriété globale unique, appliquée à toutes les séances. Une future
 * configuration par établissement nécessiterait une colonne ou une table
 * de réglages rattachée à l'entité qui incarnerait cet établissement (et
 * un {@code find} de ce réglage à partir de la séance, ex. via son site),
 * ce que rien n'exige ni ne justifie ici.
 *
 * @param enabled       active la fermeture automatique (défaut {@code true})
 * @param batchSize     nombre maximal de séances traitées par exécution
 *                      planifiée (défaut 200) — borne la charge d'un
 *                      passage, indépendamment de la fréquence technique
 *                      ({@code app.coursesession.auto-close.check-interval-ms},
 *                      lue directement par {@code @Scheduled})
 * @param gracePeriod   délai de grâce métier après {@code endsAt} avant
 *                      qu'une séance {@code OPEN} soit éligible à la
 *                      fermeture automatique (défaut {@code PT15M}) ;
 *                      zéro autorisé (fermeture dès {@code endsAt}),
 *                      négatif refusé
 */
@Validated
@ConfigurationProperties(prefix = "app.coursesession.auto-close")
record CourseSessionAutoCloseProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("200") @Min(1) int batchSize,
        @DefaultValue("PT15M") @NotNull Duration gracePeriod) {

    CourseSessionAutoCloseProperties {
        if (gracePeriod == null || gracePeriod.isNegative()) {
            throw new IllegalStateException(
                    "app.coursesession.auto-close.grace-period ne peut pas être négatif (valeur reçue : "
                            + gracePeriod + ").");
        }
    }
}
