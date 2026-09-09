package com.esic.connect.integration.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CalendarSubscriptionRepository extends JpaRepository<CalendarSubscription, Long> {

    Optional<CalendarSubscription> findByFeedKey(String feedKey);

    Optional<CalendarSubscription> findByPublicIdAndUserId(UUID publicId, Long userId);

    List<CalendarSubscription> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndRevokedAtIsNull(Long userId);
}
