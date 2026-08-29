package com.esic.connect.alternation.internal;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Résultat de la résolution du contexte d'une <em>classe</em> à une date
 * (section 9 du lot). {@code source} vaut {@link ContextSource#PATTERN}
 * quand une affectation couvre la date, {@link ContextSource#NONE} sinon
 * (et {@code context} vaut alors {@link AlternationContext#UNKNOWN}).
 *
 * @param classGroupPublicId       classe interrogée
 * @param date                     date interrogée
 * @param context                  contexte attendu (SCHOOL / COMPANY / UNKNOWN)
 * @param source                   origine de la décision
 * @param classAssignmentPublicId  affectation utilisée, {@code null} si aucune
 * @param workStudyPatternPublicId modèle de rythme utilisé, {@code null} si aucun
 * @param workStudyPatternCode     code du modèle, {@code null} si aucun
 * @param cycleWeekIndex           position 1..N dans le cycle, {@code 0} si
 *                                 la date précède l'ancre, {@code null} si
 *                                 aucune affectation
 * @param dayOfWeek                jour de la semaine de {@code date}
 */
record AlternationContextResponse(
        UUID classGroupPublicId,
        LocalDate date,
        AlternationContext context,
        ContextSource source,
        UUID classAssignmentPublicId,
        UUID workStudyPatternPublicId,
        String workStudyPatternCode,
        Integer cycleWeekIndex,
        String dayOfWeek) {
}
