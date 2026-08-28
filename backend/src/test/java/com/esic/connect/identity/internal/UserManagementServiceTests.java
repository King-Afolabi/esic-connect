package com.esic.connect.identity.internal;

import com.esic.connect.identity.AccountLifecycleAction;
import com.esic.connect.identity.AccountLifecycleEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Règles sensibles de l'administration des comptes, vérifiées dans la
 * couche service (indépendamment de {@code @PreAuthorize}) : transitions
 * de statut, protection {@code SUPER_ADMIN}, auto-action interdite,
 * dernier rôle actif, clôture des rôles à l'archivage, liste blanche de
 * tri et bornage de la pagination.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserManagementServiceTests {

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private UserManagementService newService() {
        return new UserManagementService(userAccountRepository, userRoleRepository, roleRepository, eventPublisher);
    }

    private UserAccount account(AccountStatus status) {
        UserAccount account = new UserAccount("cible@esic-connect.test", "Cible", "Test", status);
        ReflectionTestUtils.setField(account, "id", 42L);
        ReflectionTestUtils.setField(account, "publicId", UUID.randomUUID());
        return account;
    }

    private Role role(RoleCode code) {
        return new Role(code, code.name(), null, true, true);
    }

    private UserRole activeAssignment(UserAccount user, RoleCode code) {
        return new UserRole(user, role(code), Instant.now(), true);
    }

    private void stubFound(UserAccount account) {
        when(userAccountRepository.findByPublicId(account.getPublicId())).thenReturn(Optional.of(account));
    }

    // ---------- Suspension ----------

    @Test
    void suspendActiveAccountSetsStatusAndPublishesEvent() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);

        newService().suspend(target.getPublicId(), "Absence prolongée", null, List.of("ADMIN"));

        assertThat(target.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(target.getSuspendedAt()).isNotNull();
        assertThat(target.getSuspensionReason()).isEqualTo("Absence prolongée");
        verify(userAccountRepository).save(target);

        ArgumentCaptor<AccountLifecycleEvent> event = ArgumentCaptor.forClass(AccountLifecycleEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AccountLifecycleAction.ACCOUNT_SUSPENDED);
    }

    @Test
    void suspendRejectsAccountThatIsNotActive() {
        UserAccount target = account(AccountStatus.PENDING_ACTIVATION);
        stubFound(target);

        assertThatThrownBy(() -> newService().suspend(target.getPublicId(), "x", null, List.of("ADMIN")))
                .isInstanceOf(UserManagementException.class)
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.INVALID_STATE_TRANSITION));
    }

    @Test
    void suspendRejectsActingOnOwnAccount() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);

        assertThatThrownBy(() -> newService()
                .suspend(target.getPublicId(), "x", target.getPublicId().toString(), List.of("ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.SELF_ACTION_FORBIDDEN));
        assertThat(target.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void aSuperAdminCannotSuspendItsOwnAccount() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);

        assertThatThrownBy(() -> newService()
                .suspend(target.getPublicId(), "x", target.getPublicId().toString(), List.of("SUPER_ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.SELF_ACTION_FORBIDDEN));
    }

    @Test
    void adminCannotSuspendASuperAdminAccount() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);
        when(userRoleRepository.findActiveWithRoleByUserId(42L))
                .thenReturn(List.of(activeAssignment(target, RoleCode.SUPER_ADMIN)));

        assertThatThrownBy(() -> newService().suspend(target.getPublicId(), "x", null, List.of("ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.SUPER_ADMIN_PROTECTED));
    }

    @Test
    void superAdminCanSuspendASuperAdminAccount() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);
        when(userRoleRepository.findActiveWithRoleByUserId(42L))
                .thenReturn(List.of(activeAssignment(target, RoleCode.SUPER_ADMIN)));

        newService().suspend(target.getPublicId(), "Compromission", null, List.of("SUPER_ADMIN"));

        assertThat(target.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
    }

    // ---------- Réactivation ----------

    @Test
    void restoreReactivatesSuspendedAccount() {
        UserAccount target = account(AccountStatus.SUSPENDED);
        ReflectionTestUtils.setField(target, "suspensionReason", "ancien motif");
        stubFound(target);

        newService().restore(target.getPublicId(), "Retour", null, List.of("SCHOOL_ADMINISTRATION"));

        assertThat(target.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(target.getSuspensionReason()).isNull();
        ArgumentCaptor<AccountLifecycleEvent> event = ArgumentCaptor.forClass(AccountLifecycleEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AccountLifecycleAction.ACCOUNT_REACTIVATED);
    }

    @Test
    void restoreRejectsAccountThatIsNotSuspended() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);

        assertThatThrownBy(() -> newService().restore(target.getPublicId(), "x", null, List.of("ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.INVALID_STATE_TRANSITION));
    }

    @Test
    void restoreDoesNotBringBackAnArchivedAccount() {
        UserAccount target = account(AccountStatus.ARCHIVED);
        stubFound(target);

        assertThatThrownBy(() -> newService().restore(target.getPublicId(), "x", null, List.of("ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.INVALID_STATE_TRANSITION));
    }

    @Test
    void aSuspendedAccountCannotReactivateItself() {
        UserAccount target = account(AccountStatus.SUSPENDED);
        stubFound(target);

        assertThatThrownBy(() -> newService()
                .restore(target.getPublicId(), "je reviens", target.getPublicId().toString(), List.of("ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.SELF_ACTION_FORBIDDEN));
        assertThat(target.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    void schoolAdministrationCannotRestoreASuperAdminAccount() {
        UserAccount target = account(AccountStatus.SUSPENDED);
        stubFound(target);
        when(userRoleRepository.findActiveWithRoleByUserId(42L))
                .thenReturn(List.of(activeAssignment(target, RoleCode.SUPER_ADMIN)));

        assertThatThrownBy(() -> newService()
                .restore(target.getPublicId(), "x", null, List.of("SCHOOL_ADMINISTRATION")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.SUPER_ADMIN_PROTECTED));
    }

    // ---------- Archivage ----------

    @Test
    void archiveClosesEveryActiveRoleInTheSameTransactionAndKeepsHistory() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);
        UserRole teacher = activeAssignment(target, RoleCode.TEACHER);
        UserRole student = activeAssignment(target, RoleCode.STUDENT);
        when(userRoleRepository.findActiveWithRoleByUserId(42L)).thenReturn(List.of(teacher, student));

        newService().archive(target.getPublicId(), "Fin de scolarité", null, List.of("ADMIN"));

        assertThat(target.getStatus()).isEqualTo(AccountStatus.ARCHIVED);
        assertThat(target.getArchivedAt()).isNotNull();
        assertThat(teacher.isActive()).isFalse();
        assertThat(teacher.getValidUntil()).isNotNull();
        assertThat(student.isActive()).isFalse();
        verify(userRoleRepository).saveAll(List.of(teacher, student));
        verify(userRoleRepository, never()).delete(any());
        verify(userRoleRepository, never()).deleteAll(any());

        ArgumentCaptor<AccountLifecycleEvent> event = ArgumentCaptor.forClass(AccountLifecycleEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AccountLifecycleAction.ACCOUNT_ARCHIVED);
        assertThat(event.getValue().detail()).contains("2 rôle(s) clôturé(s)");
    }

    @Test
    void archiveRejectsAnAlreadyArchivedAccount() {
        UserAccount target = account(AccountStatus.ARCHIVED);
        stubFound(target);

        assertThatThrownBy(() -> newService().archive(target.getPublicId(), "x", null, List.of("ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.INVALID_STATE_TRANSITION));
    }

    @Test
    void archiveRejectsActingOnOwnAccount() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);

        assertThatThrownBy(() -> newService()
                .archive(target.getPublicId(), "x", target.getPublicId().toString(), List.of("ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.SELF_ACTION_FORBIDDEN));
    }

    @Test
    void aSuperAdminCannotArchiveItsOwnAccount() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);

        assertThatThrownBy(() -> newService()
                .archive(target.getPublicId(), "x", target.getPublicId().toString(), List.of("SUPER_ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.SELF_ACTION_FORBIDDEN));
    }

    @Test
    void archiveRejectsCallerWithoutAdminLevel() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);

        assertThatThrownBy(() -> newService()
                .archive(target.getPublicId(), "x", null, List.of("SCHOOL_ADMINISTRATION")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.NOT_AUTHORIZED));
    }

    // ---------- Attribution de rôle ----------

    @Test
    void assignRoleCreatesANewActiveAssignment() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);
        when(userRoleRepository.findActiveWithRoleByUserId(42L)).thenReturn(List.of());
        when(roleRepository.findByCode(RoleCode.TEACHER)).thenReturn(Optional.of(role(RoleCode.TEACHER)));

        newService().assignRole(target.getPublicId(), "teacher", "Nouvelle mission", null, List.of("ADMIN"));

        ArgumentCaptor<UserRole> saved = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(saved.capture());
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(saved.getValue().getRole().getCode()).isEqualTo(RoleCode.TEACHER);
        verify(eventPublisher).publishEvent(any(AccountLifecycleEvent.class));
    }

    @Test
    void assignRoleRejectsADuplicateActiveRole() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);
        when(userRoleRepository.findActiveWithRoleByUserId(42L))
                .thenReturn(List.of(activeAssignment(target, RoleCode.TEACHER)));
        when(roleRepository.findByCode(RoleCode.TEACHER)).thenReturn(Optional.of(role(RoleCode.TEACHER)));

        assertThatThrownBy(() -> newService()
                .assignRole(target.getPublicId(), "TEACHER", "x", null, List.of("ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.ROLE_ALREADY_ASSIGNED));
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void assignRoleRejectsAnUnknownRoleCode() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);

        assertThatThrownBy(() -> newService()
                .assignRole(target.getPublicId(), "WIZARD", "x", null, List.of("ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.ROLE_UNKNOWN));
    }

    @Test
    void adminCannotAssignTheSuperAdminRole() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);

        assertThatThrownBy(() -> newService()
                .assignRole(target.getPublicId(), "SUPER_ADMIN", "x", null, List.of("ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.SUPER_ADMIN_PROTECTED));
    }

    @Test
    void adminCannotAssignAnyRoleOnASuperAdminAccount() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);
        when(userRoleRepository.findActiveWithRoleByUserId(42L))
                .thenReturn(List.of(activeAssignment(target, RoleCode.SUPER_ADMIN)));

        assertThatThrownBy(() -> newService()
                .assignRole(target.getPublicId(), "TEACHER", "x", null, List.of("ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.SUPER_ADMIN_PROTECTED));
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void assignRoleRejectsArchivedAccount() {
        UserAccount target = account(AccountStatus.ARCHIVED);
        stubFound(target);

        assertThatThrownBy(() -> newService()
                .assignRole(target.getPublicId(), "TEACHER", "x", null, List.of("ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.INVALID_STATE_TRANSITION));
    }

    // ---------- Retrait de rôle ----------

    @Test
    void revokeRoleClosesTheAssignmentWhenItIsNotTheLastActiveOne() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);
        UserRole teacher = activeAssignment(target, RoleCode.TEACHER);
        UserRole student = activeAssignment(target, RoleCode.STUDENT);
        when(userRoleRepository.findActiveWithRoleByUserId(42L)).thenReturn(List.of(teacher, student));

        newService().revokeRole(target.getPublicId(), "teacher", "Fin de mission", null, List.of("ADMIN"));

        assertThat(teacher.isActive()).isFalse();
        assertThat(teacher.getValidUntil()).isNotNull();
        assertThat(student.isActive()).isTrue();
        verify(userRoleRepository).save(teacher);
        ArgumentCaptor<AccountLifecycleEvent> event = ArgumentCaptor.forClass(AccountLifecycleEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AccountLifecycleAction.ROLE_REVOKED);
    }

    @Test
    void revokeRoleRejectsRemovalOfTheLastActiveRole() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);
        when(userRoleRepository.findActiveWithRoleByUserId(42L))
                .thenReturn(List.of(activeAssignment(target, RoleCode.TEACHER)));

        assertThatThrownBy(() -> newService()
                .revokeRole(target.getPublicId(), "TEACHER", "x", null, List.of("ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.LAST_ACTIVE_ROLE));
    }

    @Test
    void revokeRoleRejectsARoleThatIsNotActive() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);
        when(userRoleRepository.findActiveWithRoleByUserId(42L))
                .thenReturn(List.of(activeAssignment(target, RoleCode.STUDENT),
                        activeAssignment(target, RoleCode.PEDAGOGICAL_MANAGER)));

        assertThatThrownBy(() -> newService()
                .revokeRole(target.getPublicId(), "TEACHER", "x", null, List.of("ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.ROLE_NOT_ASSIGNED));
    }

    @Test
    void revokeRoleRejectsActingOnOwnAccount() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);

        assertThatThrownBy(() -> newService()
                .revokeRole(target.getPublicId(), "TEACHER", "x", target.getPublicId().toString(), List.of("ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.SELF_ACTION_FORBIDDEN));
    }

    @Test
    void adminCannotRevokeTheSuperAdminRole() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);

        assertThatThrownBy(() -> newService()
                .revokeRole(target.getPublicId(), "SUPER_ADMIN", "x", null, List.of("ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.SUPER_ADMIN_PROTECTED));
    }

    @Test
    void adminCannotRevokeAnyRoleOnASuperAdminAccount() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);
        when(userRoleRepository.findActiveWithRoleByUserId(42L)).thenReturn(List.of(
                activeAssignment(target, RoleCode.SUPER_ADMIN),
                activeAssignment(target, RoleCode.TEACHER)));

        assertThatThrownBy(() -> newService()
                .revokeRole(target.getPublicId(), "TEACHER", "x", null, List.of("ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.SUPER_ADMIN_PROTECTED));
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void aSuperAdminCannotRevokeItsOwnRole() {
        UserAccount target = account(AccountStatus.ACTIVE);
        stubFound(target);
        when(userRoleRepository.findActiveWithRoleByUserId(42L)).thenReturn(List.of(
                activeAssignment(target, RoleCode.SUPER_ADMIN),
                activeAssignment(target, RoleCode.ADMIN)));

        assertThatThrownBy(() -> newService().revokeRole(target.getPublicId(), "SUPER_ADMIN", "x",
                target.getPublicId().toString(), List.of("SUPER_ADMIN")))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.SELF_ACTION_FORBIDDEN));
        verify(userRoleRepository, never()).save(any());
    }

    // ---------- Consultation ----------

    @Test
    void listRejectsASortFieldOutsideTheWhitelist() {
        assertThatThrownBy(() -> newService().listUsers(null, null, null, 0, 20, "passwordHash,asc"))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.INVALID_SORT));
    }

    @Test
    void listRejectsAnInvalidSortDirectionInsteadOfSilentlyDefaultingToAsc() {
        assertThatThrownBy(() -> newService().listUsers(null, null, null, 0, 20, "email,wrong"))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.INVALID_SORT));
    }

    @Test
    void listRejectsAnInvalidStatusFilter() {
        assertThatThrownBy(() -> newService().listUsers("ZOMBIE", null, null, 0, 20, null))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.INVALID_FILTER));
    }

    @Test
    void listClampsPageSizeToOneHundredAndFallsBackToDefault() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(userAccountRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        newService().listUsers(null, null, null, 0, 500, null);
        newService().listUsers(null, null, null, 0, 0, null);

        verify(userAccountRepository, org.mockito.Mockito.times(2))
                .findAll(any(Specification.class), pageable.capture());
        List<Pageable> captured = pageable.getAllValues();
        assertThat(captured.get(0).getPageSize()).isEqualTo(UserManagementService.MAX_PAGE_SIZE);
        assertThat(captured.get(1).getPageSize()).isEqualTo(UserManagementService.DEFAULT_PAGE_SIZE);
    }

    @Test
    void getUserRaisesNotFoundForAnUnknownPublicId() {
        UUID unknown = UUID.randomUUID();
        when(userAccountRepository.findByPublicId(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().getUser(unknown))
                .satisfies(ex -> assertThat(((UserManagementException) ex).kind())
                        .isEqualTo(UserManagementException.Kind.USER_NOT_FOUND));
    }

    @Test
    void listMapsPageContentToSummariesWithoutInternalIdentifiers() {
        UserAccount target = account(AccountStatus.ACTIVE);
        Page<UserAccount> page = new PageImpl<>(List.of(target));
        when(userAccountRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(userRoleRepository.findActiveWithRoleByUserIds(List.of(42L)))
                .thenReturn(List.of(activeAssignment(target, RoleCode.STUDENT)));

        PageResponse<UserSummaryResponse> result = newService().listUsers(null, null, null, 0, 20, null);

        assertThat(result.content()).hasSize(1);
        UserSummaryResponse summary = result.content().get(0);
        assertThat(summary.publicId()).isEqualTo(target.getPublicId());
        assertThat(summary.roles()).containsExactly("STUDENT");
        assertThat(summary.status()).isEqualTo(AccountStatus.ACTIVE);
    }
}
