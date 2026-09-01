package com.esic.connect.notification.internal;

import com.esic.connect.identity.CurrentUserResolver;
import com.esic.connect.notification.internal.NotificationResponses.NotificationPage;
import com.esic.connect.notification.internal.NotificationResponses.NotificationView;
import com.esic.connect.notification.internal.NotificationResponses.UnreadCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Consultation et marquage « mes notifications » (G1-D). L'appelant est
 * toujours le destinataire : aucun identifiant de destinataire ne
 * transite par le client, la résolution passe par le sujet du JWT
 * ({@link CurrentUserResolver}). Un utilisateur ne voit ni ne modifie
 * jamais les notifications d'un autre (isolation stricte — AC-017).
 */
@Service
class NotificationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    /** Tri déterministe : les plus récentes d'abord, puis par id décroissant (départage stable). */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt")
            .and(Sort.by(Sort.Direction.DESC, "id"));

    private final NotificationRepository repository;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;

    NotificationService(NotificationRepository repository, CurrentUserResolver currentUserResolver, Clock clock) {
        this.repository = repository;
        this.currentUserResolver = currentUserResolver;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    NotificationPage list(String callerSubject, String statusFilter, int page, int size) {
        long recipientId = requireCaller(callerSubject);
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size), NEWEST_FIRST);
        Optional<NotificationStatus> status = parseStatus(statusFilter);
        Page<Notification> result = status
                .map(s -> repository.findByRecipientUserIdAndStatus(recipientId, s, pageable))
                .orElseGet(() -> repository.findByRecipientUserId(recipientId, pageable));
        return NotificationPage.of(result, NotificationView::from);
    }

    @Transactional(readOnly = true)
    UnreadCount unreadCount(String callerSubject) {
        long recipientId = requireCaller(callerSubject);
        return new UnreadCount(repository.countByRecipientUserIdAndStatus(recipientId, NotificationStatus.UNREAD));
    }

    /**
     * Marque une notification de l'appelant comme lue. Idempotent : une
     * notification déjà lue renvoie {@code 204} sans effet. Une
     * notification qui n'appartient pas à l'appelant → {@code 404}
     * (jamais {@code 403} : ne pas révéler l'existence).
     */
    @Transactional
    void markRead(String callerSubject, String publicId) {
        long recipientId = requireCaller(callerSubject);
        UUID id;
        try {
            id = UUID.fromString(publicId.trim());
        } catch (IllegalArgumentException | NullPointerException notAUuid) {
            throw new NotificationException(NotificationException.Kind.NOT_FOUND);
        }
        Notification notification = repository.findByRecipientUserIdAndPublicId(recipientId, id)
                .orElseThrow(() -> new NotificationException(NotificationException.Kind.NOT_FOUND));
        notification.markRead(clock.instant());
    }

    /** Marque toutes les notifications non lues de l'appelant comme lues (borné au destinataire). */
    @Transactional
    void markAllRead(String callerSubject) {
        long recipientId = requireCaller(callerSubject);
        repository.markAllReadForRecipient(recipientId, clock.instant());
    }

    // ------------------------------------------------------------------

    private long requireCaller(String callerSubject) {
        return currentUserResolver.resolveInternalId(callerSubject)
                .orElseThrow(() -> new NotificationException(NotificationException.Kind.UNAUTHENTICATED));
    }

    private static Optional<NotificationStatus> parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(NotificationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException invalid) {
            throw new NotificationException(NotificationException.Kind.INVALID_STATUS);
        }
    }

    private static int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
