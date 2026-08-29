package com.esic.connect.alternation.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Résultat de la résolution du contexte <em>effectif</em> d'une
 * inscription à une date (section 10 du lot).
 *
 * <p>{@code patternContext} est le contexte issu du rythme de la classe.
 * {@code effectiveContext} applique la priorité <em>structurelle</em>
 * d'une exception individuelle active : une exception
 * {@code ON_SITE_REQUIRED} impose {@code SCHOOL}, une exception
 * {@code COMPANY_PERIOD} impose {@code COMPANY} ({@code source} =
 * {@link ContextSource#INDIVIDUAL_EXCEPTION}). Les autres types
 * ({@code REMOTE_ALLOWED}, {@code VALIDATED_UNAVAILABILITY}) sont
 * signalés dans {@code coveringExceptionTypes} pour information mais ne
 * modifient pas l'axe SCHOOL / COMPANY dans ce lot (aucun calcul
 * d'assiduité). En l'absence de rythme <em>et</em> d'exception
 * déterminante, {@code effectiveContext} vaut {@code UNKNOWN} et
 * {@code source} vaut {@link ContextSource#NONE}.
 *
 * @param enrollmentPublicId      inscription interrogée
 * @param classGroupPublicId      classe de l'inscription
 * @param date                    date interrogée
 * @param patternContext          contexte issu du rythme de classe
 * @param effectiveContext        contexte effectif après exceptions
 * @param source                  origine de la décision effective
 * @param coveringExceptionTypes  types des exceptions ACTIVE qui recouvrent
 *                                la date (peut être vide)
 */
record EnrollmentContextResponse(
        UUID enrollmentPublicId,
        UUID classGroupPublicId,
        LocalDate date,
        AlternationContext patternContext,
        AlternationContext effectiveContext,
        ContextSource source,
        List<ScheduleExceptionType> coveringExceptionTypes) {
}
