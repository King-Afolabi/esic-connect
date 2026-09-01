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

    DefaultAccountStatsDirectory(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
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
