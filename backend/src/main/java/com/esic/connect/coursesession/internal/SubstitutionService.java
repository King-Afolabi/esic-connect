package com.esic.connect.coursesession.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.coursesession.CourseSessionChangeAction;
import com.esic.connect.coursesession.CourseSessionDirectory.AccessLevel;
import com.esic.connect.identity.TeacherDirectory;
import com.esic.connect.identity.UserDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Remplacements de formateur sur une séance (G1-C.2 ; EF-SES-005 ;
 * CAD §24 RG-12 ; CDC §43 RG-015).
 *
 * <p>Le formateur principal de la séance n'est <strong>jamais</strong>
 * modifié : une substitution est une ligne datée, à période de validité,
 * avec motif obligatoire, jamais supprimée (fin logique). Au plus une
 * substitution {@code ACTIVE} peut chevaucher une période donnée pour une
 * séance (verrou pessimiste sur les substitutions actives + contrôle de
 * chevauchement — MySQL n'a pas d'index partiel).
 *
 * <p>Autorité serveur : la création / la fin passent par
 * {@link CourseSessionAccessGuard} (niveau {@code MANAGE}) ; l'exposition
 * du remplaçant dans {@link CourseSessionAccessGuard} lui donne le droit
 * d'ouvrir / gérer la séance <em>pendant</em> sa période de validité.
 */
@Service
class SubstitutionService {

    private final CourseSessionRepository sessionRepository;
    private final TeacherSubstitutionRepository substitutionRepository;
    private final TeacherDirectory teacherDirectory;
    private final UserDirectory userDirectory;
    private final ClassGroupDirectory classGroupDirectory;
    private final CourseSessionAccessGuard accessGuard;
    private final CourseSessionChangePublisher changePublisher;
    private final Clock clock;

    SubstitutionService(CourseSessionRepository sessionRepository,
                        TeacherSubstitutionRepository substitutionRepository,
                        TeacherDirectory teacherDirectory,
                        UserDirectory userDirectory,
                        ClassGroupDirectory classGroupDirectory,
                        CourseSessionAccessGuard accessGuard,
                        CourseSessionChangePublisher changePublisher,
                        Clock clock) {
        this.sessionRepository = sessionRepository;
        this.substitutionRepository = substitutionRepository;
        this.teacherDirectory = teacherDirectory;
        this.userDirectory = userDirectory;
        this.classGroupDirectory = classGroupDirectory;
        this.accessGuard = accessGuard;
        this.changePublisher = changePublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    List<SubstitutionResponse> list(String sessionPublicId, String callerSubject) {
        // G1-C.3 : l'historique des remplacements reste consultable pour
        // une séance CANCELLED (lecture historique). Seule une séance
        // supersédée par le planning est masquée. La création / la fin
        // d'un remplacement exigent, elles, une séance opérationnelle.
        CourseSession session = requireReadableSession(sessionPublicId);
        requireAccess(session, AccessLevel.READ, callerSubject);
        return substitutionRepository.findByCourseSessionIdOrderByValidFromAscIdAsc(session.getId()).stream()
                .map(sub -> toResponse(sub, session))
                .toList();
    }

    @Transactional
    SubstitutionResponse create(String sessionPublicId, CourseSessionRequests.CreateSubstitution request,
                                String callerSubject) {
        CourseSession session = requireOperationalSession(sessionPublicId);
        requireAccess(session, AccessLevel.MANAGE, callerSubject);
        // Verrou de ligne sur la séance : sérialise les créations
        // concurrentes de substitution (l'invariant « au plus une ACTIVE
        // applicable » ne peut pas s'appuyer sur un SELECT ... FOR UPDATE
        // d'un ensemble vide — gap locks compatibles entre eux).
        session = sessionRepository.findByIdForUpdate(session.getId()).orElse(session);
        // Séance CLOSED / CANCELLED : non substituable (CDC §16 ; une
        // séance CANCELLED est déjà écartée par requireOperationalSession).
        if (!session.isCancellable()) {
            throw new CourseSessionException(CourseSessionException.Kind.INVALID_STATE);
        }
        if (!request.validUntil().isAfter(request.validFrom())) {
            throw new CourseSessionException(CourseSessionException.Kind.SUBSTITUTION_PERIOD_INVALID);
        }
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isEmpty()) {
            throw new CourseSessionException(CourseSessionException.Kind.SUBSTITUTION_PERIOD_INVALID);
        }
        if (reason.length() > 500) {
            reason = reason.substring(0, 500);
        }
        // G1-C.3 : la période doit CHEVAUCHER réellement la séance. Une
        // substitution entièrement avant ou après le créneau n'accorde
        // jamais de droit et n'a pas de sens. Le dépôt n'ayant pas de règle
        // temporelle globale d'ouverture de séance, on impose au minimum
        // un chevauchement + une marge bornée (préparation / clôture).
        requirePeriodOverlapsSession(session, request.validFrom(), request.validUntil());

        TeacherDirectory.TeacherRef substitute = teacherDirectory
                .findEligibleTeacher(parseUuid(request.substituteTeacherPublicId()))
                .orElseThrow(() -> new CourseSessionException(
                        CourseSessionException.Kind.SUBSTITUTE_NOT_ELIGIBLE));
        if (substitute.internalId() == session.getTeacherUserId()) {
            throw new CourseSessionException(CourseSessionException.Kind.SUBSTITUTE_IS_ORIGINAL);
        }

        // Verrou pessimiste des substitutions actives de la séance :
        // sérialise la création concurrente, garantit « au plus une
        // ACTIVE applicable ».
        for (TeacherSubstitution active : substitutionRepository.lockActiveForSession(session.getId())) {
            if (active.overlaps(request.validFrom(), request.validUntil())) {
                throw new CourseSessionException(CourseSessionException.Kind.SUBSTITUTION_OVERLAP);
            }
        }

        Long actorId = changePublisher.actorId(callerSubject);
        TeacherSubstitution saved = substitutionRepository.save(new TeacherSubstitution(
                session.getId(), substitute.internalId(), session.getTeacherUserId(),
                reason, request.validFrom(), request.validUntil(), actorId));
        changePublisher.publish(session.getPublicId(), CourseSessionChangeAction.SUBSTITUTION_ADDED,
                actorId, "substitute=" + substitute.publicId(), Set.of(substitute.publicId()));
        return toResponse(saved, session);
    }

    @Transactional
    void end(String sessionPublicId, String substitutionPublicId, String callerSubject) {
        CourseSession session = requireOperationalSession(sessionPublicId);
        requireAccess(session, AccessLevel.MANAGE, callerSubject);
        UUID substitutionId;
        try {
            substitutionId = UUID.fromString(substitutionPublicId.trim());
        } catch (IllegalArgumentException | NullPointerException notAUuid) {
            throw new CourseSessionException(CourseSessionException.Kind.SUBSTITUTION_NOT_FOUND);
        }
        TeacherSubstitution substitution = substitutionRepository
                .findByPublicId(substitutionId)
                .filter(s -> s.getCourseSessionId().equals(session.getId()))
                .orElseThrow(() -> new CourseSessionException(
                        CourseSessionException.Kind.SUBSTITUTION_NOT_FOUND));
        if (!substitution.isActive()) {
            throw new CourseSessionException(CourseSessionException.Kind.SUBSTITUTION_ALREADY_ENDED);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        substitution.end(clock.instant(), actorId);
        // Le remplaçant vient de passer ENDED : l'état committé ne le
        // désigne plus comme ACTIVE. On porte donc son UUID public dans
        // l'événement pour que le module notification puisse le notifier
        // de la fin de son remplacement (G1-D.1).
        UUID substitutePublicId = userDirectory
                .findByInternalId(substitution.getSubstituteTeacherUserId())
                .map(UserDirectory.UserRef::publicId)
                .orElse(null);
        Set<UUID> affected = substitutePublicId == null ? Set.of() : Set.of(substitutePublicId);
        changePublisher.publish(session.getPublicId(), CourseSessionChangeAction.SUBSTITUTION_ENDED,
                actorId, substitutePublicId == null ? null : "substitute=" + substitutePublicId, affected);
    }

    // ------------------------------------------------------------------

    /**
     * Marge tolérée de part et d'autre du créneau : une substitution peut
     * commencer jusqu'à 60&nbsp;min avant le début de la séance et finir
     * jusqu'à 60&nbsp;min après sa fin (préparation, clôture). Au-delà, ou
     * sans chevauchement réel, la période est refusée (G1-C.3).
     */
    private static final java.time.Duration PERIOD_MARGIN = java.time.Duration.ofMinutes(60);

    private static void requirePeriodOverlapsSession(CourseSession session,
                                                     java.time.Instant validFrom,
                                                     java.time.Instant validUntil) {
        java.time.Instant sessionStart = session.getStartsAt();
        java.time.Instant sessionEnd = session.getEndsAt();
        // Chevauchement réel [validFrom, validUntil) ∩ [start, end) ≠ ∅.
        boolean overlaps = validFrom.isBefore(sessionEnd) && validUntil.isAfter(sessionStart);
        // Bornes : ne pas déborder la marge avant le début ni après la fin.
        boolean withinMargins = !validFrom.isBefore(sessionStart.minus(PERIOD_MARGIN))
                && !validUntil.isAfter(sessionEnd.plus(PERIOD_MARGIN));
        if (!overlaps || !withinMargins) {
            throw new CourseSessionException(CourseSessionException.Kind.SUBSTITUTION_OUTSIDE_SESSION);
        }
    }

    /** Séance opérationnelle — pour créer / terminer un remplacement. */
    private CourseSession requireOperationalSession(String sessionPublicId) {
        CourseSession session = requireReadableSession(sessionPublicId);
        if (!session.isOperational()) {
            throw new CourseSessionException(CourseSessionException.Kind.SESSION_NOT_FOUND);
        }
        return session;
    }

    /**
     * Séance existante et historiquement lisible — pour consulter la liste
     * des remplacements (G1-C.3 ; une séance {@code CANCELLED} passe, une
     * séance supersédée par le planning est masquée).
     */
    private CourseSession requireReadableSession(String sessionPublicId) {
        CourseSession session = sessionRepository.findByPublicId(parseUuid(sessionPublicId))
                .orElseThrow(() -> new CourseSessionException(CourseSessionException.Kind.SESSION_NOT_FOUND));
        if (!session.isHistoricallyReadable()) {
            throw new CourseSessionException(CourseSessionException.Kind.SESSION_NOT_FOUND);
        }
        return session;
    }

    private void requireAccess(CourseSession session, AccessLevel level, String callerSubject) {
        Set<UUID> classPublicIds = session.getClasses().stream()
                .map(SessionClass::getClassGroupId)
                .map(classGroupDirectory::findByInternalId)
                .filter(Optional::isPresent)
                .map(ref -> ref.get().publicId())
                .collect(Collectors.toUnmodifiableSet());
        if (!accessGuard.isAllowed(session.getTeacherUserId(), session.getId(), classPublicIds, level, callerSubject)) {
            throw new CourseSessionException(accessGuard.isPedagogicalManagerScoped()
                    ? CourseSessionException.Kind.SCOPE_FORBIDDEN
                    : CourseSessionException.Kind.OPERATION_FORBIDDEN);
        }
    }

    private SubstitutionResponse toResponse(TeacherSubstitution sub, CourseSession session) {
        return new SubstitutionResponse(
                sub.getPublicId(), sub.getStatus().name(), sub.getReason(),
                sub.getValidFrom(), sub.getValidUntil(),
                teacherView(sub.getSubstituteTeacherUserId()),
                teacherView(sub.getOriginalTeacherUserId()),
                sub.getCreatedAt(), sub.getEndedAt());
    }

    private CourseSessionResponse.TeacherView teacherView(long internalId) {
        UUID publicId = userDirectory.findByInternalId(internalId)
                .map(UserDirectory.UserRef::publicId).orElse(null);
        UserDirectory.PersonName name = userDirectory.findName(internalId).orElse(null);
        return new CourseSessionResponse.TeacherView(publicId,
                name != null ? name.firstName() : null, name != null ? name.lastName() : null);
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException | NullPointerException notAUuid) {
            throw new CourseSessionException(CourseSessionException.Kind.SESSION_NOT_FOUND);
        }
    }
}
