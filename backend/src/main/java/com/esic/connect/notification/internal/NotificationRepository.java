package com.esic.connect.notification.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientUserId(Long recipientUserId, Pageable pageable);

    Page<Notification> findByRecipientUserIdAndStatus(Long recipientUserId, NotificationStatus status,
                                                     Pageable pageable);

    long countByRecipientUserIdAndStatus(Long recipientUserId, NotificationStatus status);

    Optional<Notification> findByRecipientUserIdAndPublicId(Long recipientUserId, UUID publicId);

    boolean existsByDedupKey(String dedupKey);

    /**
     * Marque comme lues toutes les notifications {@code UNREAD} du
     * destinataire (borné au destinataire — jamais d'effet transverse).
     * Renvoie le nombre de lignes affectées.
     */
    @Modifying
    @Query("update Notification n set n.status = com.esic.connect.notification.internal.NotificationStatus.READ, "
            + "n.readAt = :now, n.version = n.version + 1 "
            + "where n.recipientUserId = :recipientUserId "
            + "and n.status = com.esic.connect.notification.internal.NotificationStatus.UNREAD")
    int markAllReadForRecipient(@Param("recipientUserId") Long recipientUserId, @Param("now") Instant now);
}
