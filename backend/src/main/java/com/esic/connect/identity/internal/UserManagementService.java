package com.esic.connect.identity.internal;

import com.esic.connect.identity.AccountLifecycleAction;
import com.esic.connect.identity.AccountLifecycleEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Administration minimale des comptes et des rôles (cahier §6, §9, §29,
 * §30 ; modèle §2.5, §10.3).
 *
 * <p>Les contrôles sensibles sont appliqués <strong>ici</strong>, en
 * complément des {@code @PreAuthorize} du contrôleur :
 * <ul>
 *   <li>un compte ou un rôle {@code SUPER_ADMIN} n'est administrable que
 *       par un appelant {@code SUPER_ADMIN} ;</li>
 *   <li>auto-suspension, auto-archivage et retrait de son propre rôle
 *       interdits ;</li>
 *   <li>impossible de retirer le dernier rôle actif d'un utilisateur ;</li>
 *   <li>l'archivage clôture, dans la même transaction, tous les rôles
 *       actifs en conservant leur historique ;</li>
 *   <li>{@code ARCHIVED} est un état terminal dans ce lot.</li>
 * </ul>
 * Aucune suppression physique n'est réalisée.
 */
@Service
@Transactional
public class UserManagementService {

    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_QUERY_LENGTH = 100;
    /** Champs de tri publics autorisés (liste blanche). */
    static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "lastLoginAt", "email", "lastName");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UserManagementService(UserAccountRepository userAccountRepository,
                                 UserRoleRepository userRoleRepository,
                                 RoleRepository roleRepository,
                                 ApplicationEventPublisher eventPublisher) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.eventPublisher = eventPublisher;
    }

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> listUsers(String statusFilter, String roleFilter,
                                                       String textFilter, int page, int size, String sort) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size), parseSort(sort));

        List<Specification<UserAccount>> specs = new ArrayList<>();
        parseStatus(statusFilter).ifPresent(status -> specs.add(UserAdminSpecifications.hasStatus(status)));
        parseRoleFilter(roleFilter).ifPresent(role -> specs.add(UserAdminSpecifications.hasActiveRole(role)));
        normalizeText(textFilter).ifPresent(text -> specs.add(UserAdminSpecifications.matchesText(text)));

        Page<UserAccount> pageResult = userAccountRepository.findAll(Specification.allOf(specs), pageable);

        Map<Long, List<String>> activeRolesByUser = activeRoleCodesByUser(
                pageResult.getContent().stream().map(UserAccount::getId).toList());

        return PageResponse.of(pageResult, account -> new UserSummaryResponse(
                account.getPublicId(),
                account.getEmail(),
                account.getFirstName(),
                account.getLastName(),
                account.getStatus(),
                activeRolesByUser.getOrDefault(account.getId(), List.of()),
                account.getCreatedAt(),
                account.getLastLoginAt()));
    }

    @Transactional(readOnly = true)
    public UserDetailResponse getUser(UUID publicId) {
        UserAccount account = requireAccount(publicId);
        List<RoleAssignmentResponse> assignments = userRoleRepository.findWithRoleByUserId(account.getId()).stream()
                .sorted(Comparator.comparing(UserRole::getValidFrom).reversed())
                .map(userRole -> new RoleAssignmentResponse(
                        userRole.getRole().getCode(),
                        userRole.isActive(),
                        userRole.getValidFrom(),
                        userRole.getValidUntil()))
                .toList();
        return new UserDetailResponse(
                account.getPublicId(),
                account.getEmail(),
                account.getFirstName(),
                account.getLastName(),
                account.getPhone(),
                account.getStatus(),
                account.getEmailVerifiedAt(),
                account.getLastLoginAt(),
                account.getSuspendedAt(),
                account.getSuspensionReason(),
                account.getArchivedAt(),
                account.getCreatedAt(),
                account.getUpdatedAt(),
                assignments);
    }

    // ------------------------------------------------------------------
    // Cycle de vie du compte
    // ------------------------------------------------------------------

    public void suspend(UUID publicId, String reason, String callerSubject, Collection<String> callerRoles) {
        CallerContext caller = resolveCaller(callerSubject, callerRoles);
        UserAccount target = requireAccount(publicId);
        guardNotSelf(caller, target);
        guardSuperAdminTarget(caller, target);
        if (target.getStatus() != AccountStatus.ACTIVE) {
            throw new UserManagementException(UserManagementException.Kind.INVALID_STATE_TRANSITION);
        }
        target.suspend(reason, caller.internalId(), Instant.now());
        userAccountRepository.save(target);
        publish(target, caller, AccountLifecycleAction.ACCOUNT_SUSPENDED, reason);
    }

    public void restore(UUID publicId, String reason, String callerSubject, Collection<String> callerRoles) {
        CallerContext caller = resolveCaller(callerSubject, callerRoles);
        UserAccount target = requireAccount(publicId);
        // Un compte suspendu ne peut plus se connecter : l'auto-réactivation
        // est déjà impossible en pratique. La garde reste explicite pour ne
        // dépendre d'aucun autre mécanisme.
        guardNotSelf(caller, target);
        guardSuperAdminTarget(caller, target);
        if (target.getStatus() != AccountStatus.SUSPENDED) {
            throw new UserManagementException(UserManagementException.Kind.INVALID_STATE_TRANSITION);
        }
        target.reactivate(caller.internalId());
        userAccountRepository.save(target);
        publish(target, caller, AccountLifecycleAction.ACCOUNT_REACTIVATED, reason);
    }

    public void archive(UUID publicId, String reason, String callerSubject, Collection<String> callerRoles) {
        CallerContext caller = resolveCaller(callerSubject, callerRoles);
        requireAdminLevel(caller);
        UserAccount target = requireAccount(publicId);
        guardNotSelf(caller, target);
        guardSuperAdminTarget(caller, target);
        if (target.getStatus() == AccountStatus.ARCHIVED) {
            throw new UserManagementException(UserManagementException.Kind.INVALID_STATE_TRANSITION);
        }

        Instant now = Instant.now();
        List<UserRole> activeRoles = userRoleRepository.findActiveWithRoleByUserId(target.getId());
        activeRoles.forEach(userRole -> userRole.close(now));
        userRoleRepository.saveAll(activeRoles);

        target.archive(caller.internalId(), now);
        userAccountRepository.save(target);

        String detail = reason + " (" + activeRoles.size() + " rôle(s) clôturé(s))";
        publish(target, caller, AccountLifecycleAction.ACCOUNT_ARCHIVED, detail);
    }

    // ------------------------------------------------------------------
    // Rôles
    // ------------------------------------------------------------------

    public void assignRole(UUID publicId, String roleName, String reason,
                           String callerSubject, Collection<String> callerRoles) {
        CallerContext caller = resolveCaller(callerSubject, callerRoles);
        requireAdminLevel(caller);
        RoleCode roleCode = parseRole(roleName);
        UserAccount target = requireAccount(publicId);
        guardSuperAdminRole(caller, roleCode);
        guardSuperAdminTarget(caller, target);
        if (target.getStatus() == AccountStatus.ARCHIVED) {
            throw new UserManagementException(UserManagementException.Kind.INVALID_STATE_TRANSITION);
        }
        Role role = roleRepository.findByCode(roleCode)
                .filter(Role::isActive)
                .orElseThrow(() -> new UserManagementException(UserManagementException.Kind.ROLE_UNKNOWN));

        boolean alreadyActive = userRoleRepository.findActiveWithRoleByUserId(target.getId()).stream()
                .anyMatch(userRole -> userRole.getRole().getCode() == roleCode);
        if (alreadyActive) {
            throw new UserManagementException(UserManagementException.Kind.ROLE_ALREADY_ASSIGNED);
        }

        UserRole assignment = new UserRole(target, role, Instant.now(), true);
        assignment.recordAssignment(caller.internalId(), reason);
        userRoleRepository.save(assignment);
        publish(target, caller, AccountLifecycleAction.ROLE_ASSIGNED, roleCode.name() + " — " + reason);
    }

    public void revokeRole(UUID publicId, String roleName, String reason,
                           String callerSubject, Collection<String> callerRoles) {
        CallerContext caller = resolveCaller(callerSubject, callerRoles);
        requireAdminLevel(caller);
        RoleCode roleCode = parseRole(roleName);
        UserAccount target = requireAccount(publicId);
        guardNotSelf(caller, target);
        guardSuperAdminRole(caller, roleCode);
        guardSuperAdminTarget(caller, target);
        if (target.getStatus() == AccountStatus.ARCHIVED) {
            throw new UserManagementException(UserManagementException.Kind.INVALID_STATE_TRANSITION);
        }

        List<UserRole> activeRoles = userRoleRepository.findActiveWithRoleByUserId(target.getId());
        UserRole toRevoke = activeRoles.stream()
                .filter(userRole -> userRole.getRole().getCode() == roleCode)
                .findFirst()
                .orElseThrow(() -> new UserManagementException(UserManagementException.Kind.ROLE_NOT_ASSIGNED));
        if (activeRoles.size() == 1) {
            throw new UserManagementException(UserManagementException.Kind.LAST_ACTIVE_ROLE);
        }

        toRevoke.close(Instant.now());
        userRoleRepository.save(toRevoke);
        publish(target, caller, AccountLifecycleAction.ROLE_REVOKED, roleCode.name() + " — " + reason);
    }

    // ------------------------------------------------------------------
    // Gardes et utilitaires
    // ------------------------------------------------------------------

    private UserAccount requireAccount(UUID publicId) {
        return userAccountRepository.findByPublicId(publicId)
                .orElseThrow(() -> new UserManagementException(UserManagementException.Kind.USER_NOT_FOUND));
    }

    private void requireAdminLevel(CallerContext caller) {
        if (!caller.isAdminLevel()) {
            throw new UserManagementException(UserManagementException.Kind.NOT_AUTHORIZED);
        }
    }

    private void guardNotSelf(CallerContext caller, UserAccount target) {
        boolean sameById = caller.internalId() != null && caller.internalId().equals(target.getId());
        boolean sameByPublicId = caller.publicId() != null && caller.publicId().equals(target.getPublicId());
        if (sameById || sameByPublicId) {
            throw new UserManagementException(UserManagementException.Kind.SELF_ACTION_FORBIDDEN);
        }
    }

    /** Un {@code SUPER_ADMIN} ne peut être administré que par un {@code SUPER_ADMIN}. */
    private void guardSuperAdminTarget(CallerContext caller, UserAccount target) {
        if (caller.isSuperAdmin()) {
            return;
        }
        boolean targetIsSuperAdmin = userRoleRepository.findActiveWithRoleByUserId(target.getId()).stream()
                .anyMatch(userRole -> userRole.getRole().getCode() == RoleCode.SUPER_ADMIN);
        if (targetIsSuperAdmin) {
            throw new UserManagementException(UserManagementException.Kind.SUPER_ADMIN_PROTECTED);
        }
    }

    /** Le rôle {@code SUPER_ADMIN} ne peut être attribué/retiré que par un {@code SUPER_ADMIN}. */
    private void guardSuperAdminRole(CallerContext caller, RoleCode roleCode) {
        if (roleCode == RoleCode.SUPER_ADMIN && !caller.isSuperAdmin()) {
            throw new UserManagementException(UserManagementException.Kind.SUPER_ADMIN_PROTECTED);
        }
    }

    private void publish(UserAccount target, CallerContext caller, AccountLifecycleAction action, String detail) {
        eventPublisher.publishEvent(new AccountLifecycleEvent(
                target.getId(), target.getPublicId(), caller.internalId(), action, detail));
    }

    private Map<Long, List<String>> activeRoleCodesByUser(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRoleRepository.findActiveWithRoleByUserIds(userIds).stream()
                .collect(Collectors.groupingBy(
                        userRole -> userRole.getUser().getId(),
                        Collectors.mapping(userRole -> userRole.getRole().getCode().name(), Collectors.toList())));
    }

    private static int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_SORT;
        }
        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        if (!SORTABLE_FIELDS.contains(field)) {
            throw new UserManagementException(UserManagementException.Kind.INVALID_SORT);
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            // Une direction fournie doit être exactement "asc" ou "desc" :
            // toute autre valeur (ex. "email,wrong") est refusée, jamais
            // réinterprétée silencieusement.
            direction = Sort.Direction.fromOptionalString(parts[1].trim())
                    .orElseThrow(() -> new UserManagementException(UserManagementException.Kind.INVALID_SORT));
        }
        return Sort.by(direction, field);
    }

    private static java.util.Optional<AccountStatus> parseStatus(String value) {
        return parseEnum(value, AccountStatus::valueOf);
    }

    private static java.util.Optional<RoleCode> parseRoleFilter(String value) {
        return parseEnum(value, RoleCode::valueOf);
    }

    private static <E> java.util.Optional<E> parseEnum(String value, Function<String, E> parser) {
        if (value == null || value.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(parser.apply(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException invalid) {
            throw new UserManagementException(UserManagementException.Kind.INVALID_FILTER);
        }
    }

    private static java.util.Optional<String> normalizeText(String value) {
        if (value == null) {
            return java.util.Optional.empty();
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return java.util.Optional.empty();
        }
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_QUERY_LENGTH);
        }
        return java.util.Optional.of(trimmed.toLowerCase(Locale.ROOT));
    }

    private static RoleCode parseRole(String value) {
        if (value == null || value.isBlank()) {
            throw new UserManagementException(UserManagementException.Kind.ROLE_UNKNOWN);
        }
        try {
            return RoleCode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new UserManagementException(UserManagementException.Kind.ROLE_UNKNOWN);
        }
    }

    private CallerContext resolveCaller(String subject, Collection<String> roleNames) {
        UUID publicId = null;
        Long internalId = null;
        if (subject != null && !subject.isBlank()) {
            try {
                publicId = UUID.fromString(subject);
                internalId = userAccountRepository.findByPublicId(publicId)
                        .map(UserAccount::getId)
                        .orElse(null);
            } catch (IllegalArgumentException notAUuid) {
                publicId = null;
            }
        }
        Set<RoleCode> roles = EnumSet.noneOf(RoleCode.class);
        if (roleNames != null) {
            for (String name : roleNames) {
                try {
                    roles.add(RoleCode.valueOf(name));
                } catch (IllegalArgumentException ignored) {
                    // Autorité inconnue du modèle : ignorée.
                }
            }
        }
        return new CallerContext(publicId, internalId, roles);
    }

    /** Contexte de l'appelant, reconstruit depuis le JWT (identifiant public + rôles). */
    private record CallerContext(UUID publicId, Long internalId, Set<RoleCode> roles) {
        boolean isSuperAdmin() {
            return roles.contains(RoleCode.SUPER_ADMIN);
        }

        boolean isAdminLevel() {
            return roles.contains(RoleCode.ADMIN) || roles.contains(RoleCode.SUPER_ADMIN);
        }
    }
}
