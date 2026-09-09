package com.esic.connect.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {

    Optional<TrustedDevice> findByUserIdAndDeviceHashAndStatus(Long userId, String deviceHash,
                                                              TrustedDeviceStatus status);

    List<TrustedDevice> findByUserIdAndStatusOrderByLastSeenAtDesc(Long userId,
                                                                  TrustedDeviceStatus status);

    Optional<TrustedDevice> findByPublicIdAndUserId(UUID publicId, Long userId);

    long countByUserIdAndStatus(Long userId, TrustedDeviceStatus status);
}
