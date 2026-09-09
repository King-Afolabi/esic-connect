package com.esic.connect.identity.internal;

import com.esic.connect.identity.AccountStatsDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation du port {@link AccountStatsDirectory} (bloc G1-F).
 * Confinée à {@code identity.internal} ; un seul agrégat SQL
 * ({@code GROUP BY status}).
 */
@Component
class DefaultAccountStatsDirectory implements AccountStatsDirectory {

    private final UserAccountRepository userAccountRepository;
    private final AccountInvitationRepository invitationRepository;
    private final java.time.Clock clock;

    DefaultAccountStatsDirectory(UserAccountRepository userAccountRepository,
                                 AccountInvitationRepository invitationRepository,
                                 java.time.Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.invitationRepository = invitationRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public long countPendingActivationAmong(java.util.Collection<java.util.UUID> userPublicIds) {
        if (userPublicIds == null || userPublicIds.isEmpty()) {
            return 0L;
        }
        return userAccountRepository.countByPublicIdInAndStatus(
                userPublicIds.stream().filter(java.util.Objects::nonNull).distinct().toList(),
                AccountStatus.PENDING_ACTIVATION);
    }

    @Override
    @Transactional(readOnly = true)
    public long countExpiredPendingInvitations() {
        return invitationRepository.countByStatusAndExpiresAtBefore(
                AccountInvitationStatus.PENDING, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public AccountStats counts() {
        long active = 0;
        long suspended = 0;
        long pending = 0;
        long archived = 0;
        for (Object[] row : userAccountRepository.countByStatusGrouped()) {
            AccountStatus status = (AccountStatus) row[0];
            long count = ((Number) row[1]).longValue();
            switch (status) {
                case ACTIVE -> active = count;
                case SUSPENDED, LOCKED -> suspended += count;
                case PENDING_ACTIVATION -> pending = count;
                case ARCHIVED -> archived = count;
            }
        }
        return new AccountStats(active, suspended, pending, archived);
    }
}
