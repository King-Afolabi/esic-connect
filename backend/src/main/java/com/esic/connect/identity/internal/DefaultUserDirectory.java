package com.esic.connect.identity.internal;

import com.esic.connect.identity.UserDirectory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    public AccountPage listAccountsByActiveRole(AccountRoleQuery query) {
        RoleCode role = parseRole(query.roleCode());
        if (role == null) {
            return new AccountPage(List.of(), 0);
        }
        List<Specification<UserAccount>> specs = new ArrayList<>();
        specs.add(UserAdminSpecifications.hasActiveRole(role));
        parseStatus(query.status()).ifPresent(status -> specs.add(UserAdminSpecifications.hasStatus(status)));
        normalizeText(query.text()).ifPresent(text -> specs.add(UserAdminSpecifications.matchesText(text)));
        if (query.restrictToInternalIds() != null) {
            specs.add(UserAdminSpecifications.idIn(query.restrictToInternalIds()));
        }

        Pageable pageable = PageRequest.of(query.page(), normalizePageSize(query.size()), parseSort(query.sort()));
        Page<UserAccount> page = userAccountRepository.findAll(Specification.allOf(specs), pageable);
        List<AccountSummary> content = page.getContent().stream().map(this::toAccountSummary).toList();
        return new AccountPage(content, page.getTotalElements());
    }

    private AccountSummary toAccountSummary(UserAccount account) {
        return new AccountSummary(account.getId(), account.getPublicId(), account.getEmail(),
                account.getFirstName(), account.getLastName(), account.getStatus().name(),
                account.getCreatedAt(), account.getLastLoginAt());
    }

    private static int normalizePageSize(int size) {
        if (size <= 0) {
            return UserManagementService.DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, UserManagementService.MAX_PAGE_SIZE);
    }

    /**
     * Liste blanche interne, jamais une erreur SQL : un champ hors liste
     * (ou absent) retombe silencieusement sur le tri par défaut. Le
     * contrôle strict (rejet {@code 400}) d'un tri invalide en provenance
     * d'une requête HTTP est de la responsabilité du module appelant, sur
     * sa propre liste blanche, avant d'invoquer ce port (docs/03 §6.4).
     */
    private static Sort parseSort(String sort) {
        Sort fallback = Sort.by(Sort.Direction.DESC, "createdAt");
        if (sort == null || sort.isBlank()) {
            return fallback;
        }
        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        if (!UserManagementService.SORTABLE_FIELDS.contains(field)) {
            return fallback;
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            direction = Sort.Direction.fromOptionalString(parts[1].trim()).orElse(Sort.Direction.ASC);
        }
        return Sort.by(direction, field);
    }

    private static Optional<AccountStatus> parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(AccountStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private static Optional<String> normalizeText(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed.toLowerCase(Locale.ROOT));
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
