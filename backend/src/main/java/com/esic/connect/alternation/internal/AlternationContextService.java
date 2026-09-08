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
import java.util.Map;
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
        return computeEnrollmentContext(enrollment, date);
    }

    /**
     * Résolution du contexte effectif d'une inscription <strong>sans</strong>
     * contrôle du périmètre de l'appelant : réservé au port public
     * {@code alternation.AlternationDirectory} (le module {@code attendance}
     * a déjà vérifié le périmètre de la séance / classe). Renvoie
     * {@code null} si l'inscription est inconnue.
     */
    EnrollmentContextResponse resolveEnrollmentContextUnchecked(java.util.UUID enrollmentPublicId, LocalDate date) {
        return enrollmentDirectory.findByPublicId(enrollmentPublicId)
                .map(enrollment -> computeEnrollmentContext(enrollment, date))
                .orElse(null);
    }

    private EnrollmentContextResponse computeEnrollmentContext(EnrollmentDirectory.EnrollmentRef enrollment,
                                                              LocalDate date) {
        Long classInternalId = enrollment.classGroupPublicId() == null ? null
                : classGroupDirectory.findByPublicId(enrollment.classGroupPublicId())
                        .map(ClassGroupDirectory.ClassGroupRef::internalId).orElse(null);
        List<ClassWorkStudyPattern> classAssignments = classInternalId == null ? List.of()
                : assignmentRepository.findActiveCovering(classInternalId, date);
        List<StudentScheduleException> exceptionCandidates =
                coveringExceptions(enrollment.internalId(), date);
        return computeEnrollmentContext(enrollment.publicId(), enrollment.classGroupPublicId(), classInternalId,
                date, classAssignments, exceptionCandidates, /* candidatesArePreFiltered */ true);
    }

    /**
     * Cœur <em>pur</em> de la résolution du contexte effectif — aucune
     * I/O. Les affectations de rythme et les exceptions individuelles sont
     * fournies par l'appelant, qui les a chargées soit une par une
     * ({@link #computeEnrollmentContext(EnrollmentDirectory.EnrollmentRef, LocalDate)}),
     * soit <strong>en lot</strong> pour tout un rapport
     * ({@link #resolveEnrollmentContextsUnchecked}). Une seule
     * implémentation des règles de priorité : les deux chemins ne peuvent
     * pas diverger.
     *
     * @param classAssignments affectations {@code ACTIVE} de la classe —
     *        soit déjà restreintes au jour, soit toutes (le drapeau
     *        {@code candidatesArePreFiltered} le dit)
     * @param exceptionCandidates exceptions {@code ACTIVE} de l'inscription —
     *        déjà restreintes au jour si {@code candidatesArePreFiltered},
     *        sinon simplement pré-sélectionnées sur une fenêtre large
     */
    private EnrollmentContextResponse computeEnrollmentContext(
            UUID enrollmentPublicId, UUID classGroupPublicId, Long classInternalId, LocalDate date,
            List<ClassWorkStudyPattern> classAssignments,
            List<StudentScheduleException> exceptionCandidates,
            boolean candidatesArePreFiltered) {
        AlternationContext patternContext = AlternationContext.UNKNOWN;
        ContextSource source = ContextSource.NONE;
        if (classGroupPublicId != null && classInternalId != null) {
            AlternationContextResponse classResult = resolvePatternFrom(
                    classAssignments.stream()
                            .filter(candidatesArePreFiltered ? a -> true : a -> covers(a, date))
                            .toList(),
                    classGroupPublicId, date);
            patternContext = classResult.context();
            if (classResult.source() == ContextSource.PATTERN) {
                source = ContextSource.PATTERN;
            }
        }

        List<StudentScheduleException> covering = candidatesArePreFiltered
                ? exceptionCandidates
                : filterCoveringExceptions(exceptionCandidates, date);
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

        return new EnrollmentContextResponse(enrollmentPublicId, classGroupPublicId, date,
                patternContext, effectiveContext, source, List.copyOf(coveringTypes));
    }

    /**
     * Contexte effectif de plusieurs inscriptions sur une plage de jours,
     * résolu <strong>en lot</strong> — sans contrôle de périmètre (réservé
     * au port public {@code alternation.AlternationDirectory} ; le module
     * appelant a déjà vérifié le périmètre).
     *
     * <p>Coût : trois requêtes bornées (classes, affectations
     * {@code ACTIVE} des classes, exceptions {@code ACTIVE} des
     * inscriptions sur la fenêtre) au lieu d'une poignée par couple
     * (inscription, jour). La résolution jour par jour est ensuite
     * purement en mémoire et suit exactement les mêmes règles que
     * {@link #resolveEnrollmentContextUnchecked}.
     *
     * @param enrollments couples (inscription publique, id interne
     *                    d'inscription, classe publique) — l'appelant les
     *                    tient déjà (effectif du rapport)
     * @param fromDay     premier jour civil inclus
     * @param toDay       dernier jour civil inclus
     * @return une entrée par couple (inscription, jour) de l'intervalle
     */
    Map<EnrollmentDayKey, EnrollmentContextResponse> resolveEnrollmentContextsUnchecked(
            java.util.Collection<BatchEnrollmentRef> enrollments, LocalDate fromDay, LocalDate toDay) {
        if (enrollments == null || enrollments.isEmpty() || fromDay == null || toDay == null
                || toDay.isBefore(fromDay)) {
            return Map.of();
        }
        // 1. classe publique -> id interne, en une requête
        Set<UUID> classPublicIds = new java.util.LinkedHashSet<>();
        Set<Long> enrollmentInternalIds = new java.util.LinkedHashSet<>();
        for (BatchEnrollmentRef e : enrollments) {
            if (e.classGroupPublicId() != null) {
                classPublicIds.add(e.classGroupPublicId());
            }
            enrollmentInternalIds.add(e.enrollmentInternalId());
        }
        Map<UUID, Long> classInternalIds = new java.util.HashMap<>();
        for (ClassGroupDirectory.ClassGroupRef ref : classGroupDirectory.findByPublicIds(classPublicIds)) {
            classInternalIds.put(ref.publicId(), ref.internalId());
        }

        // 2. affectations ACTIVE de toutes ces classes, rythme chargé
        Map<Long, List<ClassWorkStudyPattern>> assignmentsByClass = new java.util.HashMap<>();
        if (!classInternalIds.isEmpty()) {
            for (ClassWorkStudyPattern a : assignmentRepository
                    .findActiveByClassGroupIdIn(new java.util.LinkedHashSet<>(classInternalIds.values()))) {
                assignmentsByClass.computeIfAbsent(a.getClassGroupId(), k -> new ArrayList<>()).add(a);
            }
        }

        // 3. exceptions ACTIVE de toutes ces inscriptions, sur une fenêtre
        //    large (± 2 jours UTC autour de l'intervalle) — le recoupement
        //    exact est calculé jour par jour ensuite.
        Instant windowStart = fromDay.minusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant windowEnd = toDay.plusDays(3).atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<Long, List<StudentScheduleException>> exceptionsByEnrollment = new java.util.HashMap<>();
        if (!enrollmentInternalIds.isEmpty()) {
            for (StudentScheduleException x : exceptionRepository
                    .findActiveOverlappingForEnrollments(enrollmentInternalIds, windowStart, windowEnd)) {
                exceptionsByEnrollment.computeIfAbsent(x.getEnrollmentId(), k -> new ArrayList<>()).add(x);
            }
        }

        // 4. résolution en mémoire, couple par couple
        Map<EnrollmentDayKey, EnrollmentContextResponse> out = new java.util.HashMap<>();
        for (BatchEnrollmentRef e : enrollments) {
            Long classInternalId = e.classGroupPublicId() == null ? null
                    : classInternalIds.get(e.classGroupPublicId());
            List<ClassWorkStudyPattern> classAssignments = classInternalId == null ? List.of()
                    : assignmentsByClass.getOrDefault(classInternalId, List.of());
            List<StudentScheduleException> candidates =
                    exceptionsByEnrollment.getOrDefault(e.enrollmentInternalId(), List.of());
            for (LocalDate day = fromDay; !day.isAfter(toDay); day = day.plusDays(1)) {
                out.put(new EnrollmentDayKey(e.enrollmentPublicId(), day),
                        computeEnrollmentContext(e.enrollmentPublicId(), e.classGroupPublicId(),
                                classInternalId, day, classAssignments, candidates,
                                /* candidatesArePreFiltered */ false));
            }
        }
        return out;
    }

    /** Descriptif minimal d'une inscription pour la résolution en lot. */
    record BatchEnrollmentRef(UUID enrollmentPublicId, long enrollmentInternalId, UUID classGroupPublicId) {
    }

    /** Clé d'un contexte résolu en lot : inscription + jour civil. */
    record EnrollmentDayKey(UUID enrollmentPublicId, LocalDate day) {
    }

    /** Une affectation {@code ACTIVE} recouvre-t-elle la date ({@code validUntil} nul = ouvert) ? */
    private static boolean covers(ClassWorkStudyPattern assignment, LocalDate date) {
        return !assignment.getValidFrom().isAfter(date)
                && (assignment.getValidUntil() == null || !assignment.getValidUntil().isBefore(date));
    }

    // ------------------------------------------------------------------

    /**
     * Résolution du rythme d'une classe <strong>sans</strong> contrôle de
     * périmètre — le module appelant l'a déjà fait. Exposée pour le port
     * public, comme {@link #resolveEnrollmentContextUnchecked}.
     */
    AlternationContextResponse resolvePatternUnchecked(UUID classGroupPublicId, long classInternalId,
                                                       LocalDate date) {
        return resolvePattern(classGroupPublicId, classInternalId, date);
    }

    private AlternationContextResponse resolvePattern(UUID classGroupPublicId, long classInternalId, LocalDate date) {
        return resolvePatternFrom(assignmentRepository.findActiveCovering(classInternalId, date),
                classGroupPublicId, date);
    }

    /**
     * Cœur <em>pur</em> de {@link #resolvePattern} : les affectations
     * {@code ACTIVE} qui recouvrent le jour sont fournies par l'appelant
     * (une requête unitaire, ou un lot filtré en mémoire).
     */
    private AlternationContextResponse resolvePatternFrom(List<ClassWorkStudyPattern> covering,
                                                          UUID classGroupPublicId, LocalDate date) {
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

    /**
     * Exceptions {@code ACTIVE} de l'inscription qui <em>couvrent</em> la
     * date civile demandée.
     *
     * <p>Sémantique retenue : une exception est l'intervalle instantané
     * demi-ouvert {@code [startAt, endAt)}. La date civile {@code date},
     * projetée dans le fuseau propre à l'exception, est elle aussi
     * l'intervalle demi-ouvert
     * {@code [date 00:00 dans le fuseau, lendemain 00:00 dans le fuseau)}.
     * La date est couverte si et seulement si les deux intervalles se
     * recoupent :
     * <pre>{@code exception.startAt < dayEnd && exception.endAt > dayStart}</pre>
     * La couverture n'est donc <strong>jamais</strong> déduite d'un simple
     * {@code startDay}/{@code endDay} arrondi. Ce calcul traite
     * correctement : une exception se terminant exactement à minuit (non
     * couverte pour le jour suivant), une exception commençant exactement
     * à la fin du jour interrogé (non couverte), les fuseaux à changement
     * d'heure (l'ancrage {@code atStartOfDay(zone)} résout le décalage),
     * les exceptions de quelques heures comme celles couvrant plusieurs
     * jours.
     */
    private List<StudentScheduleException> coveringExceptions(long enrollmentInternalId, LocalDate date) {
        // Fenêtre de présélection SQL volontairement large (± 2 jours en
        // UTC) : elle absorbe tous les décalages de fuseau et de
        // changement d'heure ; le recoupement exact est ensuite calculé
        // par intersection d'intervalles dans le fuseau de l'exception.
        Instant from = date.minusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = date.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant();
        return filterCoveringExceptions(
                exceptionRepository.findActiveOverlapping(enrollmentInternalId, from, to), date);
    }

    /**
     * Cœur <em>pur</em> de {@link #coveringExceptions} : parmi des
     * exceptions pré-sélectionnées sur une fenêtre large, retient celles
     * dont l'intervalle {@code [startAt, endAt)} recoupe réellement le
     * jour civil {@code date} projeté dans le fuseau propre à chaque
     * exception.
     */
    private List<StudentScheduleException> filterCoveringExceptions(
            List<StudentScheduleException> candidates, LocalDate date) {
        List<StudentScheduleException> covering = new ArrayList<>();
        for (StudentScheduleException exception : candidates) {
            ZoneId zone = persistedZone(exception.getTimeZoneId());
            Instant dayStart = date.atStartOfDay(zone).toInstant();
            Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
            if (exception.getStartAt().isBefore(dayEnd) && exception.getEndAt().isAfter(dayStart)) {
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

    /**
     * Résout le fuseau IANA <em>persisté</em> avec une exception. Une
     * valeur invalide est un état interne corrompu (elle a été validée à
     * l'écriture par {@code StudentScheduleExceptionService.requireZone}) :
     * elle lève une erreur interne explicite plutôt que d'être remplacée
     * silencieusement par UTC, ce qui fausserait la projection calendaire.
     */
    private static ZoneId persistedZone(String id) {
        try {
            return ZoneId.of(id);
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("Fuseau horaire persisté invalide pour une exception de calendrier");
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
