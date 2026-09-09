package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttendanceChangeAction;
import com.esic.connect.attendance.EarlyDepartureOpinion;
import com.esic.connect.attendance.EarlyDepartureStatus;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory.AccessLevel;
import com.esic.connect.coursesession.CourseSessionDirectory.SessionRef;
import com.esic.connect.enrollment.EnrollmentDirectory;
import com.esic.connect.identity.UserDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Départ anticipé (EF-ATT-013 ; docs/02 §16.13).
 *
 * <p>« L'apprenant signale son départ au formateur, qui accepte, refuse,
 * recommande favorablement ou transmet au responsable pédagogique. »
 *
 * <p>Trois principes tiennent tout le service :
 *
 * <ul>
 *   <li><strong>signaler n'est pas partir en règle</strong> : tant que
 *       personne n'a tranché, l'effet est {@code TO_CONFIRM} — un dossier
 *       ouvert n'excuse rien ;</li>
 *   <li><strong>transmettre n'est pas décider</strong> : le formateur qui
 *       transmet perd la main. Le laisser trancher ensuite viderait la
 *       transmission de son sens ;</li>
 *   <li><strong>le dossier ne fabrique aucune présence</strong> : il
 *       qualifie une journée incomplète, il ne crée ni ne supprime de
 *       ligne d'émargement.</li>
 * </ul>
 */
@Service
class EarlyDepartureService {

    /** Statuts pour lesquels un second dossier sur la même séance n'a pas de sens. */
    private static final Set<EarlyDepartureStatus> OPEN_STATUSES =
            Set.of(EarlyDepartureStatus.REQUESTED, EarlyDepartureStatus.FORWARDED);

    private final EarlyDepartureRepository repository;
    private final CourseSessionDirectory courseSessionDirectory;
    private final EnrollmentDirectory enrollmentDirectory;
    private final UserDirectory userDirectory;
    private final AttendanceChangePublisher changePublisher;
    private final Clock clock;

    EarlyDepartureService(EarlyDepartureRepository repository,
                          CourseSessionDirectory courseSessionDirectory,
                          EnrollmentDirectory enrollmentDirectory,
                          UserDirectory userDirectory,
                          AttendanceChangePublisher changePublisher,
                          Clock clock) {
        this.repository = repository;
        this.courseSessionDirectory = courseSessionDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.userDirectory = userDirectory;
        this.changePublisher = changePublisher;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Apprenant
    // ------------------------------------------------------------------

    @Transactional
    EarlyDepartureResponse declare(String studentSubject, EarlyDepartureRequests.Declare request) {
        UUID sessionPublicId = parseUuid(request.sessionPublicId(),
                AttendanceException.Kind.SESSION_NOT_FOUND);
        // Résolution sans contrôle de rôle : l'apprenant n'a pas d'accès
        // « séance », sa légitimité vient de son inscription, vérifiée
        // juste après.
        SessionRef session = courseSessionDirectory.findForAttendance(sessionPublicId)
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.SESSION_NOT_FOUND));

        UUID userPublicId = parseUuid(studentSubject, AttendanceException.Kind.OPERATION_FORBIDDEN);
        EnrollmentDirectory.EnrollmentRef enrollment =
                enrollmentDirectory.findEnrollmentsForUser(userPublicId).stream()
                        .filter(e -> e.classGroupPublicId() != null
                                && session.classGroupPublicIds().contains(e.classGroupPublicId()))
                        .findFirst()
                        .orElseThrow(() -> new AttendanceException(
                                AttendanceException.Kind.NOT_ENROLLED));

        requireDepartureWithinSession(session, request.departureAt());

        if (repository.existsByCourseSessionIdAndEnrollmentIdAndStatusIn(
                session.internalId(), enrollment.internalId(), OPEN_STATUSES)) {
            throw new AttendanceException(AttendanceException.Kind.EARLY_DEPARTURE_ALREADY_OPEN);
        }

        Long actorId = changePublisher.actorId(studentSubject);
        if (actorId == null) {
            throw new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN);
        }

        EarlyDeparture saved = repository.saveAndFlush(new EarlyDeparture(
                session.internalId(), enrollment.internalId(),
                request.departureAt(), request.reason().trim(), actorId, clock.instant()));

        // Aucun motif libre dans l'audit : il peut porter un élément de
        // santé ou de vie privée (docs/02 §23.3).
        changePublisher.publishRecord(saved.getPublicId(), actorId,
                AttendanceChangeAction.EARLY_DEPARTURE_DECLARED,
                "session=" + session.publicId());
        return toResponse(saved, session, enrollment);
    }

    @Transactional(readOnly = true)
    List<EarlyDepartureResponse> listOwn(String studentSubject) {
        UUID userPublicId = parseUuid(studentSubject, AttendanceException.Kind.OPERATION_FORBIDDEN);
        List<EnrollmentDirectory.EnrollmentRef> enrollments =
                enrollmentDirectory.findEnrollmentsForUser(userPublicId);
        if (enrollments.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = enrollments.stream()
                .map(EnrollmentDirectory.EnrollmentRef::internalId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return repository.findByEnrollmentIdInOrderByRequestedAtDesc(ids).stream()
                .map(this::toResponse)
                .toList();
    }

    // ------------------------------------------------------------------
    // Formateur et responsable
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<EarlyDepartureResponse> listForSession(String sessionPublicId) {
        SessionRef session = requireSession(sessionPublicId, AccessLevel.READ);
        return repository.findByCourseSessionIdOrderByRequestedAtAsc(session.internalId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    EarlyDepartureResponse forward(String publicId, EarlyDepartureRequests.Forward request,
                                   String callerSubject) {
        EarlyDeparture dossier = requireOpenDossier(publicId);
        if (dossier.getStatus() == EarlyDepartureStatus.FORWARDED) {
            // Retransmettre effacerait l'avis déjà porté au dossier.
            throw new AttendanceException(AttendanceException.Kind.EARLY_DEPARTURE_INVALID_STATE);
        }
        SessionRef session = sessionOf(dossier);
        requireSession(session.publicId().toString(), AccessLevel.MANAGE);

        Long actorId = changePublisher.actorId(callerSubject);
        EarlyDepartureOpinion opinion = request.opinion() == null || request.opinion().isBlank()
                ? null : EarlyDepartureOpinion.valueOf(request.opinion());
        dossier.forward(opinion, trimToNull(request.comment()), actorId, clock.instant());
        EarlyDeparture saved = repository.saveAndFlush(dossier);

        changePublisher.publishRecord(saved.getPublicId(), actorId,
                AttendanceChangeAction.EARLY_DEPARTURE_FORWARDED,
                "session=" + session.publicId()
                        + ";opinion=" + (opinion == null ? "NONE" : opinion.name()));
        return toResponse(saved, session, null);
    }

    @Transactional
    EarlyDepartureResponse decide(String publicId, EarlyDepartureRequests.Decide request,
                                  String callerSubject) {
        EarlyDeparture dossier = requireOpenDossier(publicId);
        SessionRef session = sessionOf(dossier);
        requireSession(session.publicId().toString(), AccessLevel.MANAGE);

        Long actorId = changePublisher.actorId(callerSubject);
        UserDirectory.UserRef caller = actorId == null ? null
                : userDirectory.findByInternalId(actorId).orElse(null);
        if (caller == null) {
            throw new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN);
        }
        if (dossier.getStatus() == EarlyDepartureStatus.FORWARDED && !canDecideForwarded(caller)) {
            // Le formateur a transmis : reprendre la main annulerait la
            // transmission sans que personne ne l'ait décidé.
            throw new AttendanceException(AttendanceException.Kind.EARLY_DEPARTURE_DECISION_RESERVED);
        }

        dossier.decide(Boolean.TRUE.equals(request.accepted()), request.comment().trim(),
                actorId, clock.instant());
        EarlyDeparture saved = repository.saveAndFlush(dossier);

        changePublisher.publishRecord(saved.getPublicId(), actorId,
                AttendanceChangeAction.EARLY_DEPARTURE_DECIDED,
                "session=" + session.publicId() + ";status=" + saved.getStatus().name());
        return toResponse(saved, session, null);
    }

    // ------------------------------------------------------------------

    /**
     * Départs <strong>acceptés</strong> ou <strong>en attente</strong> d'un
     * ensemble d'inscriptions sur une fenêtre — support du résultat
     * journalier (docs/02 §16.13).
     */
    @Transactional(readOnly = true)
    List<EarlyDeparture> findBetween(Set<Long> enrollmentIds, Instant from, Instant to) {
        if (enrollmentIds == null || enrollmentIds.isEmpty()) {
            return List.of();
        }
        return repository.findByEnrollmentIdInAndDepartureAtBetween(enrollmentIds, from, to);
    }

    private void requireDepartureWithinSession(SessionRef session, Instant departureAt) {
        if (departureAt == null || session.startsAt() == null || session.endsAt() == null) {
            throw new AttendanceException(AttendanceException.Kind.INVALID_SUBMISSION);
        }
        // Un départ « anticipé » hors des bornes de la séance n'est pas un
        // départ anticipé : c'est une saisie erronée, refusée plutôt que
        // stockée telle quelle.
        if (departureAt.isBefore(session.startsAt()) || departureAt.isAfter(session.endsAt())) {
            throw new AttendanceException(
                    AttendanceException.Kind.EARLY_DEPARTURE_TIME_OUTSIDE_SESSION);
        }
    }

    private static boolean canDecideForwarded(UserDirectory.UserRef caller) {
        return caller.activeRoles().stream().anyMatch(role -> switch (stripPrefix(role)) {
            case "PEDAGOGICAL_MANAGER", "SCHOOL_ADMINISTRATION", "ADMIN", "SUPER_ADMIN" -> true;
            default -> false;
        });
    }

    private EarlyDeparture requireOpenDossier(String publicId) {
        EarlyDeparture dossier = repository.findByPublicId(
                        parseUuid(publicId, AttendanceException.Kind.EARLY_DEPARTURE_NOT_FOUND))
                .orElseThrow(() -> new AttendanceException(
                        AttendanceException.Kind.EARLY_DEPARTURE_NOT_FOUND));
        if (dossier.getStatus().isDecided()) {
            throw new AttendanceException(AttendanceException.Kind.EARLY_DEPARTURE_INVALID_STATE);
        }
        return dossier;
    }

    private SessionRef sessionOf(EarlyDeparture dossier) {
        return courseSessionDirectory.findSessionByInternalId(dossier.getCourseSessionId())
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.SESSION_NOT_FOUND));
    }

    private SessionRef requireSession(String sessionPublicId, AccessLevel level) {
        CourseSessionDirectory.SessionAccess access = courseSessionDirectory.resolve(
                parseUuid(sessionPublicId, AttendanceException.Kind.SESSION_NOT_FOUND), level);
        return switch (access.access()) {
            case NOT_FOUND -> throw new AttendanceException(AttendanceException.Kind.SESSION_NOT_FOUND);
            case FORBIDDEN -> throw new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN);
            case GRANTED -> access.session();
        };
    }

    private EarlyDepartureResponse toResponse(EarlyDeparture dossier) {
        SessionRef session = courseSessionDirectory
                .findSessionByInternalId(dossier.getCourseSessionId()).orElse(null);
        EnrollmentDirectory.EnrollmentRef enrollment = enrollmentDirectory
                .findByInternalId(dossier.getEnrollmentId()).orElse(null);
        return toResponse(dossier, session, enrollment);
    }

    private EarlyDepartureResponse toResponse(EarlyDeparture dossier, SessionRef session,
                                              EnrollmentDirectory.EnrollmentRef enrollment) {
        EnrollmentDirectory.EnrollmentRef resolved = enrollment != null ? enrollment
                : enrollmentDirectory.findByInternalId(dossier.getEnrollmentId()).orElse(null);
        return new EarlyDepartureResponse(
                dossier.getPublicId(),
                session != null ? session.publicId() : null,
                session != null ? session.title() : null,
                session != null ? session.startsAt() : null,
                resolved != null ? resolved.publicId() : null,
                resolved != null ? resolved.classGroupCode() : null,
                dossier.getDepartureAt(),
                dossier.getReason(),
                dossier.getStatus().name(),
                dossier.getStatus().effect(),
                dossier.getRequestedAt(),
                dossier.getTeacherOpinion() == null ? null : dossier.getTeacherOpinion().name(),
                dossier.getTeacherOpinionComment(),
                dossier.getTeacherOpinionAt(),
                roleOf(dossier.getDecidedById()),
                dossier.getDecidedAt(),
                dossier.getDecisionComment());
    }

    /**
     * Rôle le plus significatif d'un acteur, jamais son identité civile :
     * l'apprenant a besoin de savoir quelle fonction a tranché son
     * dossier, pas qui, nommément.
     */
    private String roleOf(Long userInternalId) {
        if (userInternalId == null) {
            return null;
        }
        return userDirectory.findByInternalId(userInternalId)
                .map(user -> {
                    Set<String> roles = user.activeRoles().stream()
                            .map(EarlyDepartureService::stripPrefix)
                            .collect(java.util.stream.Collectors.toUnmodifiableSet());
                    for (String candidate : List.of("SUPER_ADMIN", "ADMIN", "SCHOOL_ADMINISTRATION",
                            "PEDAGOGICAL_MANAGER", "TEACHER", "STUDENT")) {
                        if (roles.contains(candidate)) {
                            return candidate;
                        }
                    }
                    return null;
                })
                .orElse(null);
    }

    private static String stripPrefix(String role) {
        return role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static UUID parseUuid(String value, AttendanceException.Kind kind) {
        try {
            return UUID.fromString(String.valueOf(value).trim());
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new AttendanceException(kind);
        }
    }
}
