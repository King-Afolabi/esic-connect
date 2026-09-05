package com.esic.connect.notification.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    List<NotificationPreference> findByUserId(Long userId);

    Optional<NotificationPreference> findByUserIdAndCategoryAndChannel(
            Long userId, NotificationCategory category, NotificationChannel channel);
}
