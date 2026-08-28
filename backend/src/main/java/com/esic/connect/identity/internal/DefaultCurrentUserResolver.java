package com.esic.connect.identity.internal;

import com.esic.connect.identity.CurrentUserResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Implémentation du port {@link CurrentUserResolver}. Reste confinée à
 * {@code identity.internal} : les autres modules ne connaissent que
 * l'interface publique.
 */
@Component
class DefaultCurrentUserResolver implements CurrentUserResolver {

    private final UserAccountRepository userAccountRepository;

    DefaultCurrentUserResolver(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> resolveInternalId(String publicSubject) {
        if (publicSubject == null || publicSubject.isBlank()) {
            return Optional.empty();
        }
        UUID publicId;
        try {
            publicId = UUID.fromString(publicSubject.trim());
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
        return userAccountRepository.findByPublicId(publicId).map(UserAccount::getId);
    }
}
