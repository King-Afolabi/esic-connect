package com.esic.connect.identity.internal;

import com.esic.connect.identity.UserDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implémentation du port {@link UserDirectory}. Reste confinée à
 * {@code identity.internal} : les autres modules ne connaissent que
 * l'interface publique et le {@link UserDirectory.UserRef}.
 */
@Component
class DefaultUserDirectory implements UserDirectory {

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;

    DefaultUserDirectory(UserAccountRepository userAccountRepository, UserRoleRepository userRoleRepository) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserRef> findByPublicId(UUID userPublicId) {
        if (userPublicId == null) {
            return Optional.empty();
        }
        return userAccountRepository.findByPublicId(userPublicId).map(this::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserRef> findByInternalId(long userInternalId) {
        return userAccountRepository.findById(userInternalId).map(this::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PersonName> findName(long userInternalId) {
        return userAccountRepository.findById(userInternalId)
                .map(account -> new PersonName(account.getFirstName(), account.getLastName()));
    }

    private UserRef toRef(UserAccount account) {
        Set<String> activeRoles = userRoleRepository.findActiveWithRoleByUserId(account.getId()).stream()
                .map(userRole -> userRole.getRole().getCode().name())
                .collect(Collectors.toUnmodifiableSet());
        boolean archived = account.getStatus() == AccountStatus.ARCHIVED;
        return new UserRef(account.getId(), account.getPublicId(), archived, activeRoles);
    }
}
