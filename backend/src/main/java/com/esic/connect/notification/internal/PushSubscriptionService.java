package com.esic.connect.notification.internal;

import com.esic.connect.identity.CurrentUserResolver;
import com.esic.connect.shared.ratelimit.IdentityHashing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Cycle de vie des abonnements à la poussée (EF-NOTIF-005 ; docs/02
 * §29.3 : « abonnement par appareil, révocable »).
 *
 * <p>L'abonnement appartient à son propriétaire : un identifiant
 * d'abonnement d'autrui répond {@code 404}, jamais {@code 403} — sans
 * quoi l'API permettrait de deviner l'existence d'un appareil.
 */
@Service
class PushSubscriptionService {

    private final PushSubscriptionRepository repository;
    private final CurrentUserResolver currentUserResolver;
    private final WebPushSender sender;
    private final Clock clock;

    PushSubscriptionService(PushSubscriptionRepository repository,
                            CurrentUserResolver currentUserResolver,
                            WebPushSender sender, Clock clock) {
        this.repository = repository;
        this.currentUserResolver = currentUserResolver;
        this.sender = sender;
        this.clock = clock;
    }

    /**
     * Enregistre — ou renouvelle — l'abonnement d'un appareil.
     *
     * <p>Un même appareil peut se réabonner : le navigateur régénère alors
     * ses clés en conservant, ou non, la même terminaison. La ligne est
     * réutilisée plutôt que dupliquée, ce qui évite d'envoyer deux fois la
     * même notification au même appareil.
     */
    @Transactional
    NotificationResponses.PushSubscriptionView subscribe(String callerSubject, String endpoint,
                                                         String p256dhKey, String authSecret) {
        long userId = requireCaller(callerSubject);
        if (isBlank(endpoint) || isBlank(p256dhKey) || isBlank(authSecret)) {
            throw new NotificationException(NotificationException.Kind.INVALID_SUBSCRIPTION);
        }
        if (endpoint.length() > 1024 || !endpoint.startsWith("https://")) {
            // Une terminaison de poussée est toujours en HTTPS : accepter
            // autre chose reviendrait à laisser l'application émettre des
            // requêtes vers une URL arbitraire choisie par le client.
            throw new NotificationException(NotificationException.Kind.INVALID_SUBSCRIPTION);
        }
        String endpointHash = IdentityHashing.of(endpoint);
        var now = clock.instant();
        PushSubscription subscription = repository.findByEndpointHash(endpointHash)
                .map(existing -> {
                    existing.renew(userId, p256dhKey, authSecret);
                    return existing;
                })
                .orElseGet(() -> new PushSubscription(userId, endpoint, endpointHash,
                        p256dhKey, authSecret, now));
        return toView(repository.save(subscription));
    }

    @Transactional(readOnly = true)
    NotificationResponses.PushStatus status(String callerSubject) {
        long userId = requireCaller(callerSubject);
        List<NotificationResponses.PushSubscriptionView> views =
                repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                        .map(PushSubscriptionService::toView)
                        .toList();
        return new NotificationResponses.PushStatus(sender.isActive(), views);
    }

    @Transactional
    void unsubscribe(String callerSubject, String publicId) {
        long userId = requireCaller(callerSubject);
        UUID id = parseUuid(publicId);
        PushSubscription subscription = repository.findByPublicIdAndUserId(id, userId)
                .orElseThrow(() -> new NotificationException(NotificationException.Kind.NOT_FOUND));
        subscription.revoke(clock.instant());
        repository.save(subscription);
    }

    /** Appareils actifs d'un destinataire, pour l'envoi. */
    @Transactional(readOnly = true)
    List<Target> activeTargets(long userId) {
        return repository.findByUserIdAndRevokedAtIsNull(userId).stream()
                .map(subscription -> new Target(subscription.getId(), subscription.getEndpoint(),
                        subscription.getP256dhKey(), subscription.getAuthSecret()))
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markUsed(long subscriptionId) {
        repository.findById(subscriptionId).ifPresent(subscription -> {
            subscription.markUsed(clock.instant());
            repository.save(subscription);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void revoke(long subscriptionId) {
        repository.findById(subscriptionId).ifPresent(subscription -> {
            subscription.revoke(clock.instant());
            repository.save(subscription);
        });
    }

    private long requireCaller(String callerSubject) {
        return currentUserResolver.resolveInternalId(callerSubject)
                .orElseThrow(() -> new NotificationException(NotificationException.Kind.UNAUTHENTICATED));
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException malformed) {
            throw new NotificationException(NotificationException.Kind.NOT_FOUND);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Vue de l'abonnement renvoyée par l'API. <strong>Ni la terminaison,
     * ni les clés</strong> : la terminaison est un secret d'appareil, et
     * les clés n'ont aucune utilité côté client.
     */
    private static NotificationResponses.PushSubscriptionView toView(PushSubscription subscription) {
        return new NotificationResponses.PushSubscriptionView(subscription.getPublicId(),
                subscription.isActive(), subscription.getCreatedAt(), subscription.getLastUsedAt(),
                subscription.getRevokedAt());
    }

    /** Coordonnées d'envoi d'un appareil, à usage strictement interne. */
    record Target(long id, String endpoint, String p256dhKey, String authSecret) {
    }
}
