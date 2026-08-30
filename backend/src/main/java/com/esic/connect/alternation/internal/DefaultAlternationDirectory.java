package com.esic.connect.alternation.internal;

import com.esic.connect.alternation.AlternationDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Implémentation du port {@link AlternationDirectory}. Reste confinée à
 * {@code alternation.internal} : délègue à {@link AlternationContextService}
 * <em>sans</em> contrôle d'accès de l'appelant (le module appelant a déjà
 * vérifié le périmètre concerné). Ne renvoie que le
 * {@link AlternationDirectory.EnrollmentContextView} composé de types
 * standard.
 */
@Component
class DefaultAlternationDirectory implements AlternationDirectory {

    private final AlternationContextService contextService;

    DefaultAlternationDirectory(AlternationContextService contextService) {
        this.contextService = contextService;
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollmentContextView resolveEnrollmentContext(UUID enrollmentPublicId, LocalDate date) {
        if (enrollmentPublicId == null || date == null) {
            return new EnrollmentContextView(Axis.UNKNOWN, Axis.UNKNOWN, false);
        }
        EnrollmentContextResponse resolved =
                contextService.resolveEnrollmentContextUnchecked(enrollmentPublicId, date);
        if (resolved == null) {
            return new EnrollmentContextView(Axis.UNKNOWN, Axis.UNKNOWN, false);
        }
        return new EnrollmentContextView(
                map(resolved.effectiveContext()),
                map(resolved.patternContext()),
                !resolved.coveringExceptionTypes().isEmpty());
    }

    private static Axis map(AlternationContext context) {
        return switch (context) {
            case SCHOOL -> Axis.SCHOOL;
            case COMPANY -> Axis.COMPANY;
            case UNKNOWN -> Axis.UNKNOWN;
        };
    }
}
