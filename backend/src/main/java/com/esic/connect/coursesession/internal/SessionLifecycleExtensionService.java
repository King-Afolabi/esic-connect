package com.esic.connect.coursesession.internal;

import com.esic.connect.coursesession.CourseSessionChangeAction;
import com.esic.connect.coursesession.CourseSessionDirectory.AccessLevel;
import com.esic.connect.identity.CurrentUserResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Report d'une séance annulée (EF-SES-007) et demandes d'annulation
 * déposées par un formateur (EF-SES-008) — docs/02 §14.4.
 *
 * <p><strong>Qui décide.</strong> Le responsable pédagogique annule et
 * reporte ; le formateur <em>demande</em>. C'est la même règle que pour
 * le remplacement, qu'il peut proposer sans jamais le valider lui-même
 * (RG-024). Un formateur qui pourrait annuler sa propre séance viderait
 * de sa substance le contrôle du planning.
 *
 * <p><strong>Ce qu'un report n'est pas.</strong> Il ne « déplace » pas la
 * séance annulée : celle-ci reste {@code CANCELLED} et consultable en
 * historique, avec un lien vers sa remplaçante. Déplacer la séance
 * d'origine effacerait la trace de l'annulation, et avec elle les
 * présences éventuellement déjà enregistrées.
 */
@Service
public class SessionLifecycleExtensionService {

    private final CourseSessionRepository sessionRepository;
    private final SessionCancellationRequestRepository requestRepository;
    private final CourseSessionService sessionService;
    private final CourseSessionChangePublisher changePublisher;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;

    SessionLifecycleExtensionService(CourseSessionRepository sessionRepository,
                                     SessionCancellationRequestRepository requestRepository,
                                     CourseSessionService sessionService,
                                     CourseSessionChangePublisher changePublisher,
                                     CurrentUserResolver currentUserResolver,
                                     Clock clock) {
        this.sessionRepository = sessionRepository;
        this.requestRepository = requestRepository;
        this.sessionService = sessionService;
        this.changePublisher = changePublisher;
        this.currentUserResolver = currentUserResolver;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // EF-SES-007 — report
    // ------------------------------------------------------------------

    /**
     * Reporte une séance annulée en créant une séance de remplacement.
     *
     * @throws CourseSessionException si la séance n'est pas annulée, ou si
     *                                elle a déjà été reportée — un double
     *                                report produirait un historique
     *                                ambigu
     */
    @Transactional
    public CourseSessionResponse postpone(String publicId, CourseSessionRequests.Postpone request,
                                          String callerSubject) {
        CourseSession original = sessionRepository.findByPublicId(parse(publicId))
                .orElseThrow(() -> new CourseSessionException(
                        CourseSessionException.Kind.SESSION_NOT_FOUND));
        sessionService.requireAccess(original, AccessLevel.MANAGE, callerSubject);

        if (!original.isCancelled()) {
            // Reporter une séance encore vivante n'a pas de sens : il faut
            // d'abord l'annuler, avec un motif, ce qui prévient les
            // apprenants (docs/02 §14.4).
            throw new CourseSessionException(CourseSessionException.Kind.INVALID_STATE);
        }
        if (original.getPostponedToSessionId() != null) {
            throw new CourseSessionException(CourseSessionException.Kind.ALREADY_POSTPONED);
        }

        String teacher = request.teacherPublicId() != null && !request.teacherPublicId().isBlank()
                ? request.teacherPublicId()
                : originalTeacherPublicId(original);
        List<String> classes = request.classPublicIds() == null || request.classPublicIds().isEmpty()
                ? originalClassPublicIds(original)
                : request.classPublicIds();

        CourseSessionResponse replacement = sessionService.create(new CourseSessionRequests.Create(
                teacher, classes, request.startsAt(), request.endsAt(), request.timeZoneId(),
                request.reason(),
                request.title() != null && !request.title().isBlank()
                        ? request.title()
                        : original.getTitle(),
                // La séance reportée conserve la modalité de l'originale :
                // un cours distanciel reporté reste distanciel, sauf
                // décision explicite prise ensuite.
                original.getAttendanceMode(), original.getRemoteLink()), callerSubject);

        Long actorId = changePublisher.actorId(callerSubject);
        CourseSession created = sessionRepository.findByPublicId(replacement.publicId()).orElseThrow();
        original.markPostponedTo(created.getId(), actorId);
        sessionRepository.save(original);

        changePublisher.publish(original.getPublicId(), CourseSessionChangeAction.POSTPONED, actorId,
                "replacement=" + created.getPublicId());
        return replacement;
    }

    // ------------------------------------------------------------------
    // EF-SES-008 — demande d'annulation
    // ------------------------------------------------------------------

    /** Le formateur de la séance dépose une demande motivée. */
    @Transactional
    public CancellationRequestResponse requestCancellation(
            String publicId, CourseSessionRequests.RequestCancellation request, String callerSubject) {
        CourseSession session = sessionRepository.findByPublicId(parse(publicId))
                .orElseThrow(() -> new CourseSessionException(
                        CourseSessionException.Kind.SESSION_NOT_FOUND));
        // Niveau READ : le formateur voit sa séance, c'est suffisant pour
        // demander. Exiger MANAGE reviendrait à interdire la demande à
        // celui qui en a précisément besoin.
        sessionService.requireAccess(session, AccessLevel.READ, callerSubject);

        if (!session.isCancellable()) {
            throw new CourseSessionException(CourseSessionException.Kind.INVALID_STATE);
        }
        requestRepository.findBySession_IdAndStatus(session.getId(), CancellationRequestStatus.REQUESTED)
                .ifPresent(pending -> {
                    throw new CourseSessionException(
                            CourseSessionException.Kind.CANCELLATION_ALREADY_REQUESTED);
                });

        Long actorId = requireActor(callerSubject);
        SessionCancellationRequest saved = requestRepository.save(
                new SessionCancellationRequest(session, actorId, request.reason().trim()));
        changePublisher.publish(session.getPublicId(),
                CourseSessionChangeAction.CANCELLATION_REQUESTED, actorId, null);
        return CancellationRequestResponse.from(saved, session.getPublicId());
    }

    /**
     * Décide d'une demande. Une acceptation annule la séance dans la
     * foulée : accepter sans annuler laisserait la séance vivante et la
     * demande close, ce qui serait le pire des deux.
     */
    @Transactional
    public CancellationRequestResponse decide(String requestPublicId,
                                              CourseSessionRequests.DecideCancellation decision,
                                              String callerSubject) {
        SessionCancellationRequest request = requestRepository.findByPublicId(parse(requestPublicId))
                .orElseThrow(() -> new CourseSessionException(
                        CourseSessionException.Kind.CANCELLATION_REQUEST_NOT_FOUND));
        CourseSession session = request.getSession();
        sessionService.requireAccess(session, AccessLevel.MANAGE, callerSubject);

        if (!request.isPending()) {
            throw new CourseSessionException(CourseSessionException.Kind.INVALID_STATE);
        }
        Long actorId = requireActor(callerSubject);
        Instant now = clock.instant();

        if (Boolean.TRUE.equals(decision.approved())) {
            sessionService.cancel(session.getPublicId().toString(), request.getReason(), callerSubject);
            request.decide(CancellationRequestStatus.APPROVED, actorId, decision.comment(), now);
        } else {
            request.decide(CancellationRequestStatus.REJECTED, actorId, decision.comment(), now);
        }
        requestRepository.save(request);
        changePublisher.publish(session.getPublicId(),
                CourseSessionChangeAction.CANCELLATION_DECIDED, actorId,
                Boolean.TRUE.equals(decision.approved()) ? "APPROVED" : "REJECTED");
        return CancellationRequestResponse.from(request, session.getPublicId());
    }

    /** Le demandeur retire sa demande tant qu'elle n'est pas décidée. */
    @Transactional
    public void withdraw(String requestPublicId, String callerSubject) {
        SessionCancellationRequest request = requestRepository.findByPublicId(parse(requestPublicId))
                .orElseThrow(() -> new CourseSessionException(
                        CourseSessionException.Kind.CANCELLATION_REQUEST_NOT_FOUND));
        Long actorId = requireActor(callerSubject);
        if (!request.getRequestedById().equals(actorId)) {
            // Retirer la demande d'un autre reviendrait à décider à sa
            // place, sans trace de décision.
            throw new CourseSessionException(CourseSessionException.Kind.SCOPE_FORBIDDEN);
        }
        if (!request.isPending()) {
            throw new CourseSessionException(CourseSessionException.Kind.INVALID_STATE);
        }
        request.withdraw(clock.instant());
        requestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<CancellationRequestResponse> listForSession(String publicId, String callerSubject) {
        CourseSession session = sessionRepository.findByPublicId(parse(publicId))
                .orElseThrow(() -> new CourseSessionException(
                        CourseSessionException.Kind.SESSION_NOT_FOUND));
        sessionService.requireAccess(session, AccessLevel.READ, callerSubject);
        return requestRepository.findBySession_IdOrderByCreatedAtDesc(session.getId()).stream()
                .map(request -> CancellationRequestResponse.from(request, session.getPublicId()))
                .toList();
    }

    // ------------------------------------------------------------------

    private Long requireActor(String callerSubject) {
        return currentUserResolver.resolveInternalId(callerSubject)
                .orElseThrow(() -> new CourseSessionException(
                        CourseSessionException.Kind.SCOPE_FORBIDDEN));
    }

    private String originalTeacherPublicId(CourseSession session) {
        return sessionService.teacherPublicIdOf(session)
                .orElseThrow(() -> new CourseSessionException(
                        CourseSessionException.Kind.TEACHER_NOT_ELIGIBLE))
                .toString();
    }

    private List<String> originalClassPublicIds(CourseSession session) {
        return sessionService.classPublicIdsOf(session).stream().map(UUID::toString).toList();
    }

    private static UUID parse(String publicId) {
        try {
            return UUID.fromString(publicId);
        } catch (IllegalArgumentException notAUuid) {
            throw new CourseSessionException(CourseSessionException.Kind.SESSION_NOT_FOUND);
        }
    }
}
