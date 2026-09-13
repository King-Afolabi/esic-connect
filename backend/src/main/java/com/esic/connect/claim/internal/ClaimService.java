package com.esic.connect.claim.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.claim.ClaimAudience;
import com.esic.connect.claim.ClaimChangeAction;
import com.esic.connect.claim.ClaimCategory;
import com.esic.connect.claim.ClaimStatus;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.enrollment.EnrollmentDirectory;
import com.esic.connect.identity.TeacherDirectory;
import com.esic.connect.identity.UserDirectory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cycle de vie d'une réclamation (EF-CLAIM-001 à 004 ; docs/02 §20).
 *
 * <p><strong>Qui voit quoi.</strong> L'auteur voit ses propres
 * réclamations. Un intervenant voit la file du guichet dont il a le rôle,
 * et le {@code PEDAGOGICAL_MANAGER} n'y voit que les classes de son
 * périmètre. Personne ne voit la réclamation d'un autre apprenant —
 * `AC-017` vaut ici comme ailleurs.
 *
 * <p><strong>Ce qui n'efface jamais rien.</strong> Messages et décisions
 * sont append-only. Une réouverture ne supprime pas la clôture
 * précédente : elle ajoute un événement. RG-088 exige l'historique
 * complet « y compris après réouverture ».
 */
@Service
class ClaimService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "status");

    private final ClaimRepository claimRepository;
    private final ClaimMessageRepository messageRepository;
    private final ClaimEventRepository eventRepository;
    private final UserDirectory userDirectory;
    private final TeacherDirectory teacherDirectory;
    private final EnrollmentDirectory enrollmentDirectory;
    private final CourseSessionDirectory courseSessionDirectory;
    private final ClassGroupDirectory classGroupDirectory;
    private final AcademicScopeDirectory academicScope;
    private final ClaimChangePublisher changePublisher;
    private final ClaimAudienceResolver audienceResolver;
    private final Clock clock;

    ClaimService(ClaimRepository claimRepository,
                 ClaimMessageRepository messageRepository,
                 ClaimEventRepository eventRepository,
                 UserDirectory userDirectory,
                 TeacherDirectory teacherDirectory,
                 EnrollmentDirectory enrollmentDirectory,
                 CourseSessionDirectory courseSessionDirectory,
                 ClassGroupDirectory classGroupDirectory,
                 AcademicScopeDirectory academicScope,
                 ClaimChangePublisher changePublisher,
                 ClaimAudienceResolver audienceResolver,
                 Clock clock) {
        this.claimRepository = claimRepository;
        this.messageRepository = messageRepository;
        this.eventRepository = eventRepository;
        this.userDirectory = userDirectory;
        this.teacherDirectory = teacherDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.courseSessionDirectory = courseSessionDirectory;
        this.classGroupDirectory = classGroupDirectory;
        this.academicScope = academicScope;
        this.changePublisher = changePublisher;
        this.audienceResolver = audienceResolver;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // EF-CLAIM-001 — création
    // ------------------------------------------------------------------

    @Transactional
    ClaimResponse create(ClaimRequests.Create request, String callerSubject) {
        UserDirectory.UserRef author = requireCaller(callerSubject);
        ClaimCategory category = parse(ClaimCategory.class, request.category());
        ClaimAudience audience = parse(ClaimAudience.class, request.audience());

        if (request.periodStart() != null && request.periodEnd() != null
                && request.periodEnd().isBefore(request.periodStart())) {
            throw new ClaimException(ClaimException.Kind.INVALID_SUBMISSION);
        }

        Long sessionInternalId = null;
        if (request.sessionPublicId() != null && !request.sessionPublicId().isBlank()) {
            CourseSessionDirectory.SessionRef session = courseSessionDirectory
                    .findForAttendance(parseUuid(request.sessionPublicId(),
                            ClaimException.Kind.SESSION_NOT_FOUND))
                    .orElseThrow(() -> new ClaimException(ClaimException.Kind.SESSION_NOT_FOUND));
            sessionInternalId = session.internalId();
        }

        // Ciblage facultatif d'un formateur (Lot 19) : jamais un UUID
        // accepté tel quel — le compte doit exister, être actif et porter
        // un rôle TEACHER actif, exactement comme un formateur de séance
        // (TeacherDirectory.findEligibleTeacher).
        Long targetTeacherInternalId = null;
        if (request.targetTeacherPublicId() != null && !request.targetTeacherPublicId().isBlank()) {
            targetTeacherInternalId = teacherDirectory
                    .findEligibleTeacher(parseUuid(request.targetTeacherPublicId(),
                            ClaimException.Kind.TARGET_TEACHER_NOT_ELIGIBLE))
                    .orElseThrow(() -> new ClaimException(ClaimException.Kind.TARGET_TEACHER_NOT_ELIGIBLE))
                    .internalId();
        }

        // Classe de l'auteur au jour de la création : elle borne la lecture
        // du responsable pédagogique. Sans elle, il verrait la file entière
        // de son guichet, périmètres confondus.
        Long classInternalId = enrollmentDirectory
                .findActiveEnrollmentsForUserOn(author.publicId(), LocalDate.now(clock)).stream()
                .findFirst()
                .flatMap(enrollment -> classGroupDirectory.findByPublicId(enrollment.classGroupPublicId()))
                .map(ClassGroupDirectory.ClassGroupRef::internalId)
                .orElse(null);

        // Un guichet PÉRIMÉTRÉ ne peut pas traiter une réclamation sans
        // classe : le formateur et le responsable pédagogique raisonnent
        // par périmètre, et la réclamation deviendrait invisible pour tout
        // le monde sauf son auteur. Mieux vaut la refuser en le disant que
        // de l'accepter dans le vide.
        if (classInternalId == null && audience != ClaimAudience.SCHOOL_ADMINISTRATION
                && !isStaff(author)) {
            throw new ClaimException(ClaimException.Kind.NO_SCOPE_FOR_AUDIENCE);
        }

        Claim claim = new Claim(author.internalId(), category, request.subject().trim(), audience,
                sessionInternalId, request.periodStart(), request.periodEnd(), classInternalId,
                targetTeacherInternalId);
        Claim saved = claimRepository.saveAndFlush(claim);

        // Le premier message porte la description : le cahier veut un fil,
        // pas un formulaire suivi d'un fil vide.
        messageRepository.saveAndFlush(new ClaimMessage(saved.getId(), author.internalId(),
                primaryRole(author), request.description().trim()));
        eventRepository.saveAndFlush(new ClaimEvent(saved.getId(), ClaimEvent.Type.CREATED,
                author.internalId(), null, ClaimStatus.OPEN.name(), null, audience.name(),
                "Dépôt de la réclamation"));

        changePublisher.publish(saved.getPublicId(), ClaimChangeAction.CREATED,
                author.internalId(), "audience=" + audience.name() + ";category=" + category.name(),
                audienceResolver.recipientsOf(saved, author.internalId()));
        return toResponse(saved);
    }

    // ------------------------------------------------------------------
    // EF-CLAIM-002 — conversation
    // ------------------------------------------------------------------

    @Transactional
    ClaimThreadResponse addMessage(String claimPublicId, ClaimRequests.PostMessage request,
                                   String callerSubject) {
        UserDirectory.UserRef caller = requireCaller(callerSubject);
        Claim claim = requireVisible(claimPublicId, caller);
        if (!claim.getStatus().acceptsMessages()) {
            // Écrire dans un fil clos donnerait l'illusion d'une reprise :
            // il faut rouvrir explicitement (EF-CLAIM-004).
            throw new ClaimException(ClaimException.Kind.INVALID_STATE);
        }

        messageRepository.saveAndFlush(new ClaimMessage(claim.getId(), caller.internalId(),
                primaryRole(caller), request.body().trim()));

        // Une réponse d'un intervenant fait passer la réclamation « en
        // cours » ; une réponse de l'auteur ne décide de rien.
        if (!java.util.Objects.equals(caller.internalId(), claim.getAuthorUserId())
                && claim.getStatus() == ClaimStatus.OPEN) {
            applyStatus(claim, ClaimStatus.IN_PROGRESS, caller, "Première réponse d'un intervenant");
        }

        changePublisher.publish(claim.getPublicId(), ClaimChangeAction.MESSAGE_POSTED,
                caller.internalId(), null, audienceResolver.recipientsOf(claim, caller.internalId()));
        return thread(claim);
    }

    // ------------------------------------------------------------------
    // EF-CLAIM-003 — transfert
    // ------------------------------------------------------------------

    @Transactional
    ClaimResponse transfer(String claimPublicId, ClaimRequests.Transfer request,
                           String callerSubject) {
        UserDirectory.UserRef caller = requireCaller(callerSubject);
        Claim claim = requireVisible(claimPublicId, caller);
        if (java.util.Objects.equals(caller.internalId(), claim.getAuthorUserId()) && !isStaff(caller)) {
            // L'auteur choisit son guichet au dépôt ; il ne se transfère
            // pas ensuite d'un service à l'autre.
            throw new ClaimException(ClaimException.Kind.FORBIDDEN);
        }
        ClaimAudience target = parse(ClaimAudience.class, request.audience());
        if (target == claim.getAudience()) {
            throw new ClaimException(ClaimException.Kind.INVALID_SUBMISSION);
        }
        if (claim.getStatus().isClosed()) {
            throw new ClaimException(ClaimException.Kind.INVALID_STATE);
        }

        String from = claim.getAudience().name();
        String fromStatus = claim.getStatus().name();
        claim.transferTo(target);
        claimRepository.saveAndFlush(claim);
        eventRepository.saveAndFlush(new ClaimEvent(claim.getId(), ClaimEvent.Type.TRANSFERRED,
                caller.internalId(), fromStatus, claim.getStatus().name(), from, target.name(),
                request.motive().trim()));

        // Audience résolue APRÈS le transfert : le nouveau guichet doit
        // apprendre qu'un dossier l'attend, l'ancien qu'il ne l'a plus.
        changePublisher.publish(claim.getPublicId(), ClaimChangeAction.TRANSFERRED,
                caller.internalId(), "from=" + from + ";to=" + target.name(),
                audienceResolver.recipientsOf(claim, caller.internalId()));
        return toResponse(claim);
    }

    // ------------------------------------------------------------------
    // Décision et réouverture (EF-CLAIM-004)
    // ------------------------------------------------------------------

    @Transactional
    ClaimResponse decide(String claimPublicId, ClaimRequests.Decide request, String callerSubject) {
        UserDirectory.UserRef caller = requireCaller(callerSubject);
        Claim claim = requireVisible(claimPublicId, caller);
        if (!isStaff(caller)) {
            // L'apprenant expose, il ne tranche pas.
            throw new ClaimException(ClaimException.Kind.FORBIDDEN);
        }
        ClaimStatus target = parse(ClaimStatus.class, request.status());
        if (target == ClaimStatus.REOPENED || target == ClaimStatus.TRANSFERRED) {
            // Ces deux statuts sont des RÉSULTATS d'opérations dédiées :
            // les poser directement contournerait leur traçabilité.
            throw new ClaimException(ClaimException.Kind.INVALID_SUBMISSION);
        }
        if (claim.getStatus().isClosed()) {
            throw new ClaimException(ClaimException.Kind.INVALID_STATE);
        }

        applyStatus(claim, target, caller, request.motive().trim());
        changePublisher.publish(claim.getPublicId(), ClaimChangeAction.STATUS_CHANGED,
                caller.internalId(), "status=" + target.name(),
                audienceResolver.recipientsOf(claim, caller.internalId()));
        return toResponse(claim);
    }

    @Transactional
    ClaimResponse reopen(String claimPublicId, ClaimRequests.Reopen request, String callerSubject) {
        UserDirectory.UserRef caller = requireCaller(callerSubject);
        Claim claim = requireVisible(claimPublicId, caller);
        if (!claim.getStatus().isClosed()) {
            throw new ClaimException(ClaimException.Kind.INVALID_STATE);
        }

        String fromStatus = claim.getStatus().name();
        claim.changeStatus(ClaimStatus.REOPENED, caller.internalId(), clock.instant());
        claimRepository.saveAndFlush(claim);
        eventRepository.saveAndFlush(new ClaimEvent(claim.getId(), ClaimEvent.Type.REOPENED,
                caller.internalId(), fromStatus, ClaimStatus.REOPENED.name(), null, null,
                request.motive().trim()));
        // Le motif de réouverture appartient au fil : c'est ce que lira
        // l'intervenant qui reprend le dossier.
        messageRepository.saveAndFlush(new ClaimMessage(claim.getId(), caller.internalId(),
                primaryRole(caller), request.motive().trim()));

        changePublisher.publish(claim.getPublicId(), ClaimChangeAction.REOPENED,
                caller.internalId(), "from=" + fromStatus,
                audienceResolver.recipientsOf(claim, caller.internalId()));
        return toResponse(claim);
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    ClaimThreadResponse get(String claimPublicId, String callerSubject) {
        UserDirectory.UserRef caller = requireCaller(callerSubject);
        return thread(requireVisible(claimPublicId, caller));
    }

    /**
     * Réclamations visibles par l'appelant.
     *
     * <p>Un apprenant reçoit les siennes. Un intervenant reçoit la file du
     * guichet correspondant à son rôle, restreinte à son périmètre
     * pédagogique s'il en a un.
     */
    @Transactional(readOnly = true)
    ClaimPageResponse list(String audienceFilter, int page, int size, String sort,
                           String callerSubject) {
        UserDirectory.UserRef caller = requireCaller(callerSubject);
        Pageable pageable = pageable(page, size, sort);

        if (!isStaff(caller)) {
            return ClaimPageResponse.ofBatch(
                    claimRepository.findByAuthorUserId(caller.internalId(), pageable), this::toResponses);
        }

        ClaimAudience audience = audienceFilter == null || audienceFilter.isBlank()
                ? defaultAudienceFor(caller)
                : parse(ClaimAudience.class, audienceFilter);
        if (!hasAudienceRole(caller, audience)) {
            throw new ClaimException(ClaimException.Kind.FORBIDDEN);
        }

        Optional<Set<Long>> visibleClasses = academicScope.visibleClassGroupIds();
        Page<Claim> claims = visibleClasses
                .map(ids -> ids.isEmpty()
                        ? Page.<Claim>empty(pageable)
                        : claimRepository.findByAudienceAndClassGroupIdIn(audience, ids, pageable))
                .orElseGet(() -> claimRepository.findByAudience(audience, pageable));
        return ClaimPageResponse.ofBatch(claims, this::toResponses);
    }

    /**
     * Recherche assistée d'une séance pour le dépôt (Lot 18) — jamais un
     * identifiant saisi à la main. Périmètre : les classes actives de
     * l'appelant s'il est apprenant, son périmètre pédagogique s'il est
     * intervenant, sans filtre s'il a l'accès global. Requête vide ⇒
     * aucun résultat, jamais un « top N » sans rapport avec la saisie.
     */
    @Transactional(readOnly = true)
    List<ClaimResponses.SessionOption> searchSessionsForFiling(String query, String callerSubject) {
        UserDirectory.UserRef caller = requireCaller(callerSubject);
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        Optional<Set<UUID>> scope = callerClassScope(caller);
        if (scope.isPresent() && scope.get().isEmpty()) {
            return List.of();
        }
        return courseSessionDirectory.searchSessions(trimmed, scope.orElse(null), 20).stream()
                .map(ref -> new ClaimResponses.SessionOption(ref.publicId(), sessionLabel(ref)))
                .toList();
    }

    /**
     * Recherche assistée d'un formateur pour le ciblage facultatif du
     * guichet TEACHER (Lot 19) — jamais la liste complète des comptes.
     */
    @Transactional(readOnly = true)
    List<ClaimResponses.TeacherOption> searchTeachersForFiling(String query, String callerSubject) {
        requireCaller(callerSubject);
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return teacherDirectory.searchEligibleTeachers(trimmed, 20).stream()
                .map(ref -> new ClaimResponses.TeacherOption(ref.publicId(), ref.firstName(), ref.lastName()))
                .toList();
    }

    /**
     * Périmètre de classes (identifiants publics) applicable à une
     * recherche de séance : {@link Optional#empty()} pour un accès
     * global (aucun filtre), sinon l'ensemble — éventuellement vide — des
     * classes visibles. Un apprenant est scopé à ses seules classes
     * actives (mêmes données que la classe mémorisée à la création,
     * §20.1) ; un intervenant l'est à son périmètre pédagogique.
     */
    private Optional<Set<UUID>> callerClassScope(UserDirectory.UserRef caller) {
        if (academicScope.hasGlobalScope()) {
            return Optional.empty();
        }
        if (!isStaff(caller)) {
            Set<UUID> ownClasses = enrollmentDirectory
                    .findActiveEnrollmentsForUserOn(caller.publicId(), LocalDate.now(clock)).stream()
                    .map(EnrollmentDirectory.EnrollmentRef::classGroupPublicId)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            return Optional.of(ownClasses);
        }
        return academicScope.visibleClassGroupIds()
                .map(internalIds -> classGroupDirectory.findByInternalIds(internalIds).stream()
                        .map(ClassGroupDirectory.ClassGroupRef::publicId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    // ------------------------------------------------------------------

    private void applyStatus(Claim claim, ClaimStatus target, UserDirectory.UserRef actor,
                             String motive) {
        String fromStatus = claim.getStatus().name();
        claim.changeStatus(target, actor.internalId(), clock.instant());
        claimRepository.saveAndFlush(claim);
        eventRepository.saveAndFlush(new ClaimEvent(claim.getId(), ClaimEvent.Type.STATUS_CHANGED,
                actor.internalId(), fromStatus, target.name(), null, null, motive));
    }

    /**
     * Réclamation visible par l'appelant, ou {@code NOT_FOUND}.
     *
     * <p>Le refus est un {@code 404}, jamais un {@code 403} : l'existence
     * même d'une réclamation d'autrui est une information à protéger
     * (docs/02 §18.2).
     */
    private Claim requireVisible(String claimPublicId, UserDirectory.UserRef caller) {
        Claim claim = claimRepository.findByPublicId(
                        parseUuid(claimPublicId, ClaimException.Kind.NOT_FOUND))
                .orElseThrow(() -> new ClaimException(ClaimException.Kind.NOT_FOUND));

        if (java.util.Objects.equals(caller.internalId(), claim.getAuthorUserId())) {
            return claim;
        }
        if (!isStaff(caller) || !hasAudienceRole(caller, claim.getAudience())) {
            throw new ClaimException(ClaimException.Kind.NOT_FOUND);
        }
        Optional<Set<Long>> visibleClasses = academicScope.visibleClassGroupIds();
        if (visibleClasses.isPresent()
                && (claim.getClassGroupId() == null
                    || !visibleClasses.get().contains(claim.getClassGroupId()))) {
            throw new ClaimException(ClaimException.Kind.NOT_FOUND);
        }
        return claim;
    }

    private ClaimThreadResponse thread(Claim claim) {
        List<ClaimResponses.MessageView> messages = messageRepository
                .findByClaimIdOrderByCreatedAtAscIdAsc(claim.getId()).stream()
                .map(message -> new ClaimResponses.MessageView(message.getPublicId(),
                        authorPublicId(message.getAuthorUserId()), message.getAuthorRole(),
                        message.getBody(), message.getCreatedAt()))
                .toList();
        List<ClaimResponses.EventView> events = eventRepository
                .findByClaimIdOrderByCreatedAtAscIdAsc(claim.getId()).stream()
                .map(event -> new ClaimResponses.EventView(event.getPublicId(),
                        event.getEventType().name(), event.getFromStatus(), event.getToStatus(),
                        event.getFromAudience(), event.getToAudience(), event.getMotive(),
                        event.getCreatedAt()))
                .toList();
        return new ClaimThreadResponse(toResponse(claim), messages, events);
    }

    private ClaimResponse toResponse(Claim claim) {
        return toResponses(List.of(claim)).get(0);
    }

    /**
     * Assemble un lot de réclamations en réponses enrichies (Lot 18) en un
     * nombre de requêtes <strong>borné</strong>, pas proportionnel au
     * nombre de réclamations (NFR-PERF-08) : une résolution de comptes en
     * bloc ({@link UserDirectory#findNamedRefs}), une résolution de
     * séances en bloc ({@link CourseSessionDirectory#findSessionsByInternalIds}),
     * une résolution de classes en bloc ({@link ClassGroupDirectory#findByInternalIds}).
     */
    private List<ClaimResponse> toResponses(List<Claim> claims) {
        if (claims.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new LinkedHashSet<>();
        Set<Long> sessionIds = new LinkedHashSet<>();
        Set<Long> classIds = new LinkedHashSet<>();
        for (Claim claim : claims) {
            userIds.add(claim.getAuthorUserId());
            if (claim.getTargetTeacherUserId() != null) {
                userIds.add(claim.getTargetTeacherUserId());
            }
            if (claim.getCourseSessionId() != null) {
                sessionIds.add(claim.getCourseSessionId());
            }
            if (claim.getClassGroupId() != null) {
                classIds.add(claim.getClassGroupId());
            }
        }
        Map<Long, UserDirectory.NamedUserRef> people = userDirectory.findNamedRefs(userIds);
        Map<Long, CourseSessionDirectory.SessionRef> sessions = new HashMap<>();
        for (CourseSessionDirectory.SessionRef ref : courseSessionDirectory.findSessionsByInternalIds(sessionIds)) {
            sessions.put(ref.internalId(), ref);
        }
        Map<Long, ClassGroupDirectory.ClassGroupRef> classGroups = new HashMap<>();
        for (ClassGroupDirectory.ClassGroupRef ref : classGroupDirectory.findByInternalIds(classIds)) {
            classGroups.put(ref.internalId(), ref);
        }

        List<ClaimResponse> responses = new ArrayList<>(claims.size());
        for (Claim claim : claims) {
            UserDirectory.NamedUserRef authorRef = people.get(claim.getAuthorUserId());
            CourseSessionDirectory.SessionRef session = claim.getCourseSessionId() == null ? null
                    : sessions.get(claim.getCourseSessionId());
            ClassGroupDirectory.ClassGroupRef classGroup = claim.getClassGroupId() == null ? null
                    : classGroups.get(claim.getClassGroupId());
            UserDirectory.NamedUserRef targetTeacherRef = claim.getTargetTeacherUserId() == null ? null
                    : people.get(claim.getTargetTeacherUserId());
            responses.add(new ClaimResponse(claim.getPublicId(),
                    authorRef == null ? null : authorRef.publicId(),
                    claim.getCategory().name(), claim.getSubject(), claim.getStatus().name(),
                    claim.getAudience().name(),
                    session == null ? null : session.publicId(),
                    classGroup == null ? null : classGroup.publicId(),
                    claim.getPeriodStart(), claim.getPeriodEnd(), claim.getClosedAt(),
                    claim.getCreatedAt(), claim.getUpdatedAt(),
                    fullName(authorRef),
                    session == null ? null : sessionLabel(session),
                    classGroup == null ? null : classLabel(classGroup),
                    targetTeacherRef == null ? null : targetTeacherRef.publicId(),
                    fullName(targetTeacherRef)));
        }
        return responses;
    }

    private static String fullName(UserDirectory.NamedUserRef ref) {
        if (ref == null) {
            return null;
        }
        String full = (nullToEmpty(ref.firstName()) + " " + nullToEmpty(ref.lastName())).trim();
        return full.isEmpty() ? null : full;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Libellé lisible d'une séance : libellé libre s'il existe, sinon un
     * repli générique, suivi du début converti dans le fuseau
     * <strong>déclaré de la séance</strong> — jamais celui du navigateur
     * (docs/02 §8 ; cohérent avec le Lot 8) — et de ce fuseau, affiché à
     * côté pour éviter toute ambiguïté.
     */
    private static String sessionLabel(CourseSessionDirectory.SessionRef session) {
        String title = session.title() != null && !session.title().isBlank()
                ? session.title()
                : "Séance";
        ZoneId zone = safeZone(session.timeZoneId());
        String when = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(zone).format(session.startsAt());
        return title + " — " + when + " (" + zone.getId() + ")";
    }

    private static ZoneId safeZone(String timeZoneId) {
        try {
            return ZoneId.of(timeZoneId);
        } catch (RuntimeException invalid) {
            return ZoneOffset.UTC;
        }
    }

    /** Format « Nom — Code — Année », identique à celui visé pour les Lots 14/15. */
    private static String classLabel(ClassGroupDirectory.ClassGroupRef classGroup) {
        return classGroup.name() + " — " + classGroup.code() + " — " + classGroup.academicYearCode();
    }

    private UUID authorPublicId(Long internalId) {
        return internalId == null ? null
                : userDirectory.findByInternalId(internalId)
                        .map(UserDirectory.UserRef::publicId).orElse(null);
    }

    private UserDirectory.UserRef requireCaller(String callerSubject) {
        UserDirectory.UserRef caller = userDirectory
                .findByPublicId(parseUuid(callerSubject, ClaimException.Kind.FORBIDDEN))
                .orElseThrow(() -> new ClaimException(ClaimException.Kind.FORBIDDEN));
        if (caller.archived()) {
            throw new ClaimException(ClaimException.Kind.FORBIDDEN);
        }
        return caller;
    }

    /** Intervenant : quelqu'un qui traite, par opposition à l'auteur. */
    private static boolean isStaff(UserDirectory.UserRef caller) {
        return caller.activeRoles().stream().anyMatch(role -> switch (stripPrefix(role)) {
            case "TEACHER", "PEDAGOGICAL_MANAGER", "SCHOOL_ADMINISTRATION", "ADMIN", "SUPER_ADMIN" ->
                    true;
            default -> false;
        });
    }

    private static boolean hasAudienceRole(UserDirectory.UserRef caller, ClaimAudience audience) {
        return caller.activeRoles().stream().map(ClaimService::stripPrefix)
                .anyMatch(role -> role.equals(audience.roleCode())
                        // ADMIN et SUPER_ADMIN voient tous les guichets :
                        // ils administrent, ils ne sont pas un guichet.
                        || role.equals("ADMIN") || role.equals("SUPER_ADMIN"));
    }

    private static ClaimAudience defaultAudienceFor(UserDirectory.UserRef caller) {
        Set<String> roles = caller.activeRoles().stream().map(ClaimService::stripPrefix)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (roles.contains("TEACHER")) {
            return ClaimAudience.TEACHER;
        }
        if (roles.contains("PEDAGOGICAL_MANAGER")) {
            return ClaimAudience.PEDAGOGICAL_MANAGER;
        }
        return ClaimAudience.SCHOOL_ADMINISTRATION;
    }

    /** Rôle affiché dans le fil — le plus significatif de l'auteur. */
    private static String primaryRole(UserDirectory.UserRef caller) {
        Set<String> roles = caller.activeRoles().stream().map(ClaimService::stripPrefix)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (String candidate : List.of("SUPER_ADMIN", "ADMIN", "SCHOOL_ADMINISTRATION",
                "PEDAGOGICAL_MANAGER", "TEACHER", "STUDENT")) {
            if (roles.contains(candidate)) {
                return candidate;
            }
        }
        return "STUDENT";
    }

    private static String stripPrefix(String role) {
        return role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role;
    }

    private static Pageable pageable(int page, int size, String sort) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        Sort resolved = Sort.by(Sort.Direction.DESC, "createdAt");
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            if (!SORTABLE.contains(parts[0])) {
                throw new ClaimException(ClaimException.Kind.INVALID_SORT);
            }
            Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;
            resolved = Sort.by(direction, parts[0]);
        }
        return PageRequest.of(Math.max(page, 0), boundedSize, resolved);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new ClaimException(ClaimException.Kind.INVALID_SUBMISSION);
        }
    }

    private static UUID parseUuid(String value, ClaimException.Kind kind) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new ClaimException(kind);
        }
    }
}
