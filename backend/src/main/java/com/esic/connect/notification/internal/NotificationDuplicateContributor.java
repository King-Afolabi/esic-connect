package com.esic.connect.notification.internal;

import com.esic.connect.identity.DuplicateDependencyContributor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remonte à {@code identity}, <strong>en lecture seule</strong>, le volume
 * de données que {@code notification} rattache à un compte : notifications
 * reçues, abonnements de poussée actifs (ANO-USER-001 — comparaison de
 * doublons). Deux décomptes bornés, aucune écriture.
 */
@Component
class NotificationDuplicateContributor implements DuplicateDependencyContributor {

    private final NotificationRepository notificationRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;

    NotificationDuplicateContributor(NotificationRepository notificationRepository,
                                     PushSubscriptionRepository pushSubscriptionRepository) {
        this.notificationRepository = notificationRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countsFor(long userInternalId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("notifications",
                notificationRepository.countByRecipientUserId(userInternalId));
        counts.put("pushSubscriptions",
                pushSubscriptionRepository.countByUserIdAndRevokedAtIsNull(userInternalId));
        return counts;
    }
}
