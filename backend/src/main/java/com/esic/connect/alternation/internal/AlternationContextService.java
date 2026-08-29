package com.esic.connect.alternation.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.enrollment.EnrollmentDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Résolution du contexte d'alternance attendu (sections 9 et 10 du lot) —
 * lecture seule, aucun calcul d'assiduité.
 *
 * <ul>
 *   <li>{@link #resolveClassContext} : contexte d'une <em>classe</em> à
 *       une date, issu du rythme affecté ({@code source = PATTERN}) ou
 *       {@code UNKNOWN} / {@code NONE} si aucune affectation ne couvre la
 *       date.</li>
 *   <li>{@link #resolveEnrollmentContext} : contexte <em>effectif</em>
 *       d'une inscription. Priorité <em>structurelle</em> : une exception
 *       individuelle active {@code ON_SITE_REQUIRED} impose {@code SCHOOL},
 *       {@code COMPANY_PERIOD} impose {@code COMPANY}
 *       ({@code source = INDIVIDUAL_EXCEPTION}). Si les deux types
 *       recouvrent la date (configuration contradictoire), le résultat
 *       est {@code UNKNOWN}. Les types {@code REMOTE_ALLOWED} et
 *       {@code VALIDATED_UNAVAILABILITY} sont signalés mais n'agissent
 *       pas sur l'axe SCHOOL / COMPANY dans ce lot.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
class AlternationContextService {

    private final ClassWorkStudyPatternRepository assignmentRepository;
    private final StudentScheduleExceptionRepository exceptionRepository;
    private final AlternationConfigParser configParser;
    private final AlternationResolver resolver;
    private final ClassGroupDirectory classGroupDirectory;
    private final EnrollmentDirectory enrollmentDirectory;
    private final AcademicScopeDirectory academicScope;

    AlternationContextService(ClassWorkStudyPatternRepository assignmentRepository,
                              StudentScheduleExceptionRepository exceptionRepository,
                              AlternationConfigParser configParser,
                              AlternationResolver resolver,
                              ClassGroupDirectory classGroupDirectory,
                              EnrollmentDirectory enrollmentDirectory,
                              AcademicScopeDirectory academicScope) {
        this.assignmentRepository = assignmentRepository;
        this.exceptionRepository = exceptionRepository;
        this.configParser = configParser;
        this.resolver = resolver;
        this.classGroupDirectory = classGroupDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.academicScope = academicScope;
    }

    AlternationContextResponse resolveClassContext(String classGroupPublicId, LocalDate date) {
        ClassGroupDirectory.ClassGroupRef classRef = classGroupDirectory.findByPublicId(parseUuid(classGroupPublicId,
                        AlternationException.Kind.CLASS_GROUP_NOT_FOUND))
                .orElseThrow(() -> new AlternationException(AlternationException.Kind.CLASS_GROUP_NOT_FOUND));
        requireInScope(classRef.publicId());
        return resolvePattern(classRef.publicId(), classRef.internalId(), date);
    }

    EnrollmentContextResponse resolveEnrollmentContext(String enrollmentPublicId, LocalDate date) {
        EnrollmentDirectory.EnrollmentRef enrollment = enrollmentDirectory.findByPublicId(parseUuid(enrollmentPublicId,
                        AlternationException.Kind.ENROLLMENT_NOT_FOUND))
                .orElseThrow(() -> new AlternationException(AlternationException.Kind.ENROLLMENT_NOT_FOUND));
        requireInScope(enrollment.classGroupPublicId());

        AlternationContext patternContext = AlternationContext.UNKNOWN;
        ContextSource source = ContextSource.NONE;
        if (enrollment.classGroupPublicId() != null) {
            Long classInternalId = classGroupDirectory.findByPublicId(enrollment.classGroupPublicId())
                    .map(ClassGroupDirectory.ClassGroupRef::internalId).orElse(null);
            if (classInternalId != null) {
                AlternationContextResponse classResult = resolvePattern(enrollment.classGroupPublicId(),
                        classInternalId, date);
                patternContext = classResult.context();
                if (classResult.source() == ContextSource.PATTERN) {
                    source = ContextSource.PATTERN;
                }
            }
        }

        List<StudentScheduleException> covering = coveringExceptions(enrollment.internalId(), date);
        List<ScheduleExceptionType> coveringTypes = new ArrayList<>();
        Set<AlternationContext> individualContexts = EnumSet.noneOf(AlternationContext.class);
        for (StudentScheduleException exception : covering) {
            coveringTypes.add(exception.getExceptionType());
            if (exception.getExceptionType() == ScheduleExceptionType.ON_SITE_REQUIRED) {
                individualContexts.add(AlternationContext.SCHOOL);
            } else if (exception.getExceptionType() == ScheduleExceptionType.COMPANY_PERIOD) {
                individualContexts.add(AlternationContext.COMPANY);
            }
        }

        AlternationContext effectiveContext = patternContext;
        if (individualContexts.size() == 1) {
            effectiveContext = individualContexts.iterator().next();
            source = ContextSource.INDIVIDUAL_EXCEPTION;
        } else if (individualContexts.size() == 2) {
            // Exceptions contradictoires (école ET entreprise) : on ne
            // tranche pas — aucune règle métier inventée (section 10).
            effectiveContext = AlternationContext.UNKNOWN;
            source = ContextSource.INDIVIDUAL_EXCEPTION;
        }

        return new EnrollmentContextResponse(enrollment.publicId(), enrollment.classGroupPublicId(), date,
                patternContext, effectiveContext, source, List.copyOf(coveringTypes));
    }

    // ------------------------------------------------------------------

    private AlternationContextResponse resolvePattern(UUID classGroupPublicId, long classInternalId, LocalDate date) {
        List<ClassWorkStudyPattern> covering = assignmentRepository.findActiveCovering(classInternalId, date);
        String dayOfWeek = date.getDayOfWeek().name();
        if (covering.isEmpty()) {
            return new AlternationContextResponse(classGroupPublicId, date, AlternationContext.UNKNOWN,
                    ContextSource.NONE, null, null, null, null, dayOfWeek);
        }
        ClassWorkStudyPattern assignment = covering.get(0);
        WorkStudyPattern pattern = assignment.getPattern();
        PatternConfiguration config = configParser.parseCanonical(pattern.getConfigurationJson());
        int weekIndex = resolver.cycleWeekIndex(assignment.getCycleStartDate(), config.cycleLengthWeeks(), date);
        AlternationContext context = resolver.resolve(assignment.getCycleStartDate(), config, date);
        return new AlternationContextResponse(classGroupPublicId, date, context, ContextSource.PATTERN,
                assignment.getPublicId(), pattern.getPublicId(), pattern.getCode(), weekIndex, dayOfWeek);
    }

    private List<StudentScheduleException> coveringExceptions(long enrollmentInternalId, LocalDate date) {
        // Fenêtre UTC large ; le recoupement exact est ensuite vérifié
        // dans le fuseau propre à chaque exception (jour civil).
        Instant from = date.minusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = date.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<StudentScheduleException> candidates = exceptionRepository
                .findActiveOverlapping(enrollmentInternalId, from, to);
        List<StudentScheduleException> covering = new ArrayList<>();
        for (StudentScheduleException exception : candidates) {
            ZoneId zone = safeZone(exception.getTimeZoneId());
            LocalDate startDay = exception.getStartAt().atZone(zone).toLocalDate();
            LocalDate endDay = exception.getEndAt().atZone(zone).toLocalDate();
            if (!date.isBefore(startDay) && !date.isAfter(endDay)) {
                covering.add(exception);
            }
        }
        return covering;
    }

    private void requireInScope(UUID classGroupPublicId) {
        if (!academicScope.hasGlobalScope() && !academicScope.isClassInScope(classGroupPublicId)) {
            throw new AlternationException(AlternationException.Kind.OUT_OF_SCOPE);
        }
    }

    private static ZoneId safeZone(String id) {
        try {
            return ZoneId.of(id);
        } catch (RuntimeException invalid) {
            return ZoneOffset.UTC;
        }
    }

    private static UUID parseUuid(String value, AlternationException.Kind kind) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException | NullPointerException notAUuid) {
            throw new AlternationException(kind);
        }
    }
}
