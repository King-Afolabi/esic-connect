package com.esic.connect.identity.internal;

import com.esic.connect.identity.TeacherDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implémentation du port {@link TeacherDirectory}. Reste confinée à
 * {@code identity.internal} : les autres modules ne connaissent que
 * l'interface publique et le {@link TeacherDirectory.TeacherRef}.
 *
 * <p>« Éligible » = compte {@link AccountStatus#ACTIVE} portant une
 * affectation active du rôle {@link RoleCode#TEACHER}.
 */
@Component
class DefaultTeacherDirectory implements TeacherDirectory {

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;

    DefaultTeacherDirectory(UserAccountRepository userAccountRepository,
                            UserRoleRepository userRoleRepository) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeacherRef> listEligibleTeachers() {
        return userRoleRepository
                .findActiveAssignmentsByRoleCodeAndUserStatus(RoleCode.TEACHER, AccountStatus.ACTIVE)
                .stream()
                .map(UserRole::getUser)
                .distinct()
                .sorted(Comparator.comparing(UserAccount::getLastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(UserAccount::getFirstName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toRef)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TeacherRef> findEligibleTeacher(UUID userPublicId) {
        if (userPublicId == null) {
            return Optional.empty();
        }
        return userAccountRepository.findByPublicId(userPublicId)
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .filter(account -> userRoleRepository.findActiveWithRoleByUserId(account.getId()).stream()
                        .anyMatch(userRole -> userRole.getRole().getCode() == RoleCode.TEACHER))
                .map(this::toRef);
    }

    private TeacherRef toRef(UserAccount account) {
        return new TeacherRef(account.getId(), account.getPublicId(),
                account.getFirstName(), account.getLastName());
    }
}
