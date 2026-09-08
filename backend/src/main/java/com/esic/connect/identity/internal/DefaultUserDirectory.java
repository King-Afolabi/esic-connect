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
    public java.util.List<NamedUserRef> searchByName(String query, String roleCode, int limit) {
        String pattern = com.esic.connect.shared.SearchPattern.of(query);
        RoleCode role = parseRole(roleCode);
        if (pattern == null || role == null) {
            return java.util.List.of();
        }
        return userAccountRepository.searchByName(pattern, role, AccountStatus.ACTIVE,
                        org.springframework.data.domain.PageRequest.of(0,
                                com.esic.connect.shared.SearchPattern.bound(limit)))
                .stream()
                .map(account -> new NamedUserRef(account.getId(), account.getPublicId(),
                        account.getFirstName(), account.getLastName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<NamedUserRef> searchByNameIncludingInactive(String query, String roleCode, int limit) {
        String pattern = com.esic.connect.shared.SearchPattern.of(query);
        RoleCode role = parseRole(roleCode);
        if (pattern == null || role == null) {
            return java.util.List.of();
        }
        return userAccountRepository.searchByNameExcludingStatus(pattern, role, AccountStatus.ARCHIVED,
                        org.springframework.data.domain.PageRequest.of(0,
                                com.esic.connect.shared.SearchPattern.bound(limit)))
                .stream()
                .map(account -> new NamedUserRef(account.getId(), account.getPublicId(),
                        account.getFirstName(), account.getLastName()))
                .toList();
    }

    /** Code de rôle inconnu : aucun résultat, jamais une exception. */
    private static RoleCode parseRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        try {
            return RoleCode.valueOf(roleCode.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<Long, PersonName> findNames(java.util.Collection<Long> userInternalIds) {
        if (userInternalIds == null || userInternalIds.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<Long, PersonName> names = new java.util.HashMap<>();
        for (UserAccount account : userAccountRepository.findAllById(
                userInternalIds.stream().filter(java.util.Objects::nonNull).distinct().toList())) {
            names.put(account.getId(), new PersonName(account.getFirstName(), account.getLastName()));
        }
        return names;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<Long, NamedUserRef> findNamedRefs(java.util.Collection<Long> userInternalIds) {
        if (userInternalIds == null || userInternalIds.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<Long, NamedUserRef> refs = new java.util.HashMap<>();
        for (UserAccount account : userAccountRepository.findAllById(
                userInternalIds.stream().filter(java.util.Objects::nonNull).distinct().toList())) {
            refs.put(account.getId(), new NamedUserRef(account.getId(), account.getPublicId(),
                    account.getFirstName(), account.getLastName()));
        }
        return refs;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PersonName> findName(long userInternalId) {
        return userAccountRepository.findById(userInternalId)
                .map(account -> new PersonName(account.getFirstName(), account.getLastName()));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findActiveUserPublicIdsByRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return Set.of();
        }
        RoleCode code;
        try {
            code = RoleCode.valueOf(roleCode.trim());
        } catch (IllegalArgumentException unknown) {
            return Set.of();
        }
        // Statut ACTIVE et non « non archivé » : un compte suspendu ou en
        // attente d'activation ne peut pas se connecter, le notifier
        // n'informerait personne.
        return userRoleRepository
                .findActiveAssignmentsByRoleCodeAndUserStatus(code, AccountStatus.ACTIVE).stream()
                .map(userRole -> userRole.getUser().getPublicId())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findEmailForDelivery(long userInternalId) {
        return userAccountRepository.findById(userInternalId).map(UserAccount::getEmail);
    }

    private UserRef toRef(UserAccount account) {
        Set<String> activeRoles = userRoleRepository.findActiveWithRoleByUserId(account.getId()).stream()
                .map(userRole -> userRole.getRole().getCode().name())
                .collect(Collectors.toUnmodifiableSet());
        boolean archived = account.getStatus() == AccountStatus.ARCHIVED;
        return new UserRef(account.getId(), account.getPublicId(), archived, activeRoles);
    }
}
