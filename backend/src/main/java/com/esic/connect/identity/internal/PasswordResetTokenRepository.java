package com.esic.connect.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    List<PasswordResetToken> findByUserIdAndStatus(Long userId, PasswordResetStatus status);

    @Query("""
            select t from PasswordResetToken t
            where t.status = com.esic.connect.identity.internal.PasswordResetStatus.PENDING
              and t.expiresAt < :now
            """)
    List<PasswordResetToken> findExpiredPending(@Param("now") Instant now);
}
