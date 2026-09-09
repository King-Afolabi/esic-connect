package com.esic.connect.integration.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory.SessionRef;
import com.esic.connect.coursesession.SessionLifecycle;
import com.esic.connect.enrollment.EnrollmentDirectory;
import com.esic.connect.identity.CurrentUserResolver;
import com.esic.connect.identity.UserDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Abonnements iCalendar : émission, révocation et rendu du flux
 * (EF-INT-001, AC-034 ; docs/02 §28.3).
 *
 * <p><strong>Périmètre.</strong> Le flux ne contient que le planning de
 * la personne : les séances de ses classes actives si elle est
 * apprenante, celles qu'elle anime si elle est formatrice. Rien n'est
 * paramétrable par l'appelant — le périmètre est décidé ici, à partir de
 * l'abonnement, jamais d'un paramètre d'URL.
 *
 * <p><strong>Authentification.</strong> Un agenda externe ne sait pas
 * s'authentifier : il rappelle une URL. Le secret est donc le jeton
 * porté par l'URL, comparé en temps constant à l'empreinte stockée. Un
 * abonnement révoqué répond {@code 410 Gone} et non {@code 404} : la
 * personne qui a révoqué doit pouvoir constater que c'est bien sa
 * révocation qui agit.
 */
@Service
class CalendarFeedService {

    /** Fenêtre publiée : assez pour l'année scolaire en cours, bornée. */
    private static final Duration PAST_WINDOW = Duration.ofDays(120);
    private static final Duration FUTURE_WINDOW = Duration.ofDays(180);
    /** Un agenda par appareil suffit largement ; au-delà, c'est une fuite. */
    static final int MAX_ACTIVE_SUBSCRIPTIONS = 10;
    /** Borne du flux : un agenda n'a pas à recevoir un historique illimité. */
    private static final int FEED_LIMIT = 500;

    private final CalendarSubscriptionRepository repository;
    private final CurrentUserResolver currentUserResolver;
    private final UserDirectory userDirectory;
    private final EnrollmentDirectory enrollmentDirectory;
    private final CourseSessionDirectory courseSessionDirectory;
    private final ClassGroupDirectory classGroupDirectory;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    CalendarFeedService(CalendarSubscriptionRepository repository,
                        CurrentUserResolver currentUserResolver,
                        UserDirectory userDirectory,
                        EnrollmentDirectory enrollmentDirectory,
                        CourseSessionDirectory courseSessionDirectory,
                        ClassGroupDirectory classGroupDirectory,
                        Clock clock) {
        this.repository = repository;
        this.currentUserResolver = currentUserResolver;
        this.userDirectory = userDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.courseSessionDirectory = courseSessionDirectory;
        this.classGroupDirectory = classGroupDirectory;
        this.clock = clock;
    }

    // --- Cycle de vie d'un abonnement ---------------------------------

    /**
     * Crée un abonnement et renvoie <strong>une seule fois</strong> son
     * jeton : il n'est pas conservé en clair, il ne pourra donc pas être
     * réaffiché. Perdre le lien impose d'en créer un autre, ce qui est le
     * comportement voulu.
     */
    @Transactional
    IntegrationResponses.CreatedSubscription create(String subject, String label) {
        Long userId = requireUser(subject);
        if (repository.countByUserIdAndRevokedAtIsNull(userId) >= MAX_ACTIVE_SUBSCRIPTIONS) {
            throw new IntegrationException(IntegrationException.Kind.TOO_MANY_SUBSCRIPTIONS);
        }
        String feedKey = randomToken(16);
        String token = randomToken(32);
        CalendarSubscription subscription = new CalendarSubscription(
                userId, feedKey, sha256(token), trimLabel(label), clock.instant());
        repository.save(subscription);
        return new IntegrationResponses.CreatedSubscription(
                subscription.getPublicId(),
                subscription.getLabel(),
                subscription.getCreatedAt(),
                feedPath(feedKey, token));
    }

    @Transactional(readOnly = true)
    List<IntegrationResponses.SubscriptionSummary> list(String subject) {
        Long userId = requireUser(subject);
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(s -> new IntegrationResponses.SubscriptionSummary(
                        s.getPublicId(), s.getLabel(), s.getCreatedAt(),
                        s.getLastUsedAt(), s.getRevokedAt()))
                .toList();
    }

    /** Révocation : idempotente, et jamais une suppression (AC-034). */
    @Transactional
    void revoke(String subject, UUID subscriptionPublicId) {
        Long userId = requireUser(subject);
        CalendarSubscription subscription = repository
                .findByPublicIdAndUserId(subscriptionPublicId, userId)
                // 404 et non 403 : l'abonnement d'autrui n'a pas à voir son
                // existence confirmée.
                .orElseThrow(() -> new IntegrationException(IntegrationException.Kind.SUBSCRIPTION_NOT_FOUND));
        subscription.revoke(clock.instant());
        repository.save(subscription);
    }

    // --- Rendu du flux -------------------------------------------------

    /** Flux d'un abonnement public, authentifié par son jeton. */
    @Transactional
    String renderFeed(String feedKey, String token) {
        CalendarSubscription subscription = repository.findByFeedKey(feedKey)
                .orElseThrow(() -> new IntegrationException(IntegrationException.Kind.FEED_NOT_FOUND));
        if (token == null || !constantTimeEquals(sha256(token), subscription.getTokenHash())) {
            // Même réponse qu'une clé inconnue : un jeton faux ne doit pas
            // révéler qu'une clé existe.
            throw new IntegrationException(IntegrationException.Kind.FEED_NOT_FOUND);
        }
        if (subscription.isRevoked()) {
            throw new IntegrationException(IntegrationException.Kind.FEED_REVOKED);
        }
        subscription.markUsed(clock.instant());
        repository.save(subscription);
        return render(subscription.getUserId());
    }

    /** Flux de l'appelant authentifié, pour téléchargement depuis l'application. */
    @Transactional(readOnly = true)
    String renderOwnFeed(String subject) {
        return render(requireUser(subject));
    }

    private String render(Long userId) {
        UserDirectory.UserRef user = userDirectory.findByInternalId(userId)
                .orElseThrow(() -> new IntegrationException(IntegrationException.Kind.FEED_NOT_FOUND));
        Instant now = clock.instant();
        Instant from = now.minus(PAST_WINDOW);
        Instant to = now.plus(FUTURE_WINDOW);

        List<SessionRef> sessions = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();

        Set<UUID> classIds = enrollmentDirectory
                .findActiveEnrollmentsForUserOn(user.publicId(), LocalDate.ofInstant(now, clock.getZone()))
                .stream()
                .map(EnrollmentDirectory.EnrollmentRef::classGroupPublicId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!classIds.isEmpty()) {
            for (SessionRef session : courseSessionDirectory
                    .findClassSchedule(classIds, from, to, FEED_LIMIT)) {
                if (seen.add(session.publicId())) {
                    sessions.add(session);
                }
            }
        }
        if (user.activeRoles().contains("TEACHER")) {
            for (SessionRef session : courseSessionDirectory
                    .findTeacherSchedule(user.publicId(), from, to, FEED_LIMIT)) {
                if (seen.add(session.publicId())) {
                    sessions.add(session);
                }
            }
        }
        sessions.sort(java.util.Comparator.comparing(SessionRef::startsAt));

        Map<UUID, String> classCodes = resolveClassCodes(sessions);
        List<IcsWriter.IcsEvent> events = new ArrayList<>(sessions.size());
        for (SessionRef session : sessions) {
            events.add(toEvent(session, classCodes));
        }
        return IcsWriter.write("ESIC Connect — mon planning", now, events);
    }

    private Map<UUID, String> resolveClassCodes(List<SessionRef> sessions) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (SessionRef session : sessions) {
            ids.addAll(session.classGroupPublicIds());
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        // Résolution par lot : un flux de 300 séances ne doit pas produire
        // 300 requêtes de libellé (NFR-PERF-08).
        Map<UUID, String> codes = new HashMap<>();
        for (ClassGroupDirectory.ClassGroupRef ref : classGroupDirectory.findByPublicIds(ids)) {
            codes.put(ref.publicId(), ref.code());
        }
        return codes;
    }

    private static IcsWriter.IcsEvent toEvent(SessionRef session, Map<UUID, String> classCodes) {
        String classes = session.classGroupPublicIds().stream()
                .map(id -> classCodes.getOrDefault(id, "—"))
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        String summary = session.title() == null || session.title().isBlank()
                ? "Séance" : session.title();
        if (!classes.isBlank()) {
            summary = summary + " (" + classes + ")";
        }
        String location = session.roomCode();
        if (location == null || location.isBlank()) {
            location = switch (session.attendanceMode()) {
                case REMOTE -> "À distance";
                case HYBRID -> "Hybride";
                case ON_SITE -> null;
            };
        }
        String status = session.status() == SessionLifecycle.CANCELLED ? "CANCELLED" : "CONFIRMED";
        // UID stable entre deux rafraîchissements : l'agenda met l'événement
        // à jour au lieu d'en empiler un second (RG-047 côté agenda).
        String uid = session.publicId() + "@esic-connect";
        return new IcsWriter.IcsEvent(uid, summary, location, null, null, status,
                session.startsAt(), session.endsAt());
    }

    // --- Outils --------------------------------------------------------

    private Long requireUser(String subject) {
        return currentUserResolver.resolveInternalId(subject)
                .orElseThrow(() -> new IntegrationException(IntegrationException.Kind.SUBSCRIPTION_NOT_FOUND));
    }

    private static String trimLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String trimmed = label.trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
    }

    static String feedPath(String feedKey, String token) {
        return "/api/v1/calendar/" + feedKey + ".ics?token=" + token;
    }

    private String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
        // `feed_key` est un CHAR(32) : borner ici plutôt que de laisser la
        // base tronquer silencieusement.
        return bytes == 16 ? encoded.substring(0, 22) : encoded;
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponible", impossible);
        }
    }

    /** Comparaison en temps constant : la durée ne doit rien apprendre. */
    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }
}
