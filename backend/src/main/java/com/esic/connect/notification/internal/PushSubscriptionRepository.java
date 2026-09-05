package com.esic.connect.notification.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpointHash(String endpointHash);

    Optional<PushSubscription> findByPublicIdAndUserId(UUID publicId, Long userId);

    List<PushSubscription> findByUserIdAndRevokedAtIsNull(Long userId);

    List<PushSubscription> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndRevokedAtIsNull(Long userId);
}
