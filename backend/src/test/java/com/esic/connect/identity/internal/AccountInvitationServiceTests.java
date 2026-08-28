package com.esic.connect.identity.internal;

import com.esic.connect.identity.AccountInvitationIssuedEvent;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
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
 * Règles du service d'invitation : émission réservée aux comptes
 * {@code PENDING_ACTIVATION}, rôles inconnus/inactifs refusés, révocation
 * des invitations antérieures, empreinte stockée jamais égale au jeton,
 * activation encodant le mot de passe, et réponses génériques pour tout
 * jeton inutilisable.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountInvitationServiceTests {

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private AccountInvitationRepository invitationRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final InvitationTokenService tokenService = new InvitationTokenService();

    private AccountInvitationService newService(Duration ttl) {
        return new AccountInvitationService(userAccountRepository, userRoleRepository, roleRepository,
                invitationRepository, tokenService, passwordEncoder, eventPublisher, ttl);
    }

    private AccountInvitationService newService() {
        return newService(Duration.ofDays(30));
    }

    private UserAccount account(AccountStatus status) {
        UserAccount account = new UserAccount("cible@esic-connect.test", "Cible", "Test", status);
        ReflectionTestUtils.setField(account, "id", 42L);
        ReflectionTestUtils.setField(account, "publicId", UUID.randomUUID());
        return account;
    }

    private Role role(RoleCode code, boolean active) {
        return new Role(code, code.name(), null, true, active);
    }

    @Test
    void constructorRejectsNonPositiveTtl() {
        assertThatThrownBy(() -> newService(Duration.ZERO)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> newService(Duration.ofSeconds(-1))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void issueCreatesHashedInvitationAssignsRoleAndPublishesEvents() {
        UserAccount target = account(AccountStatus.PENDING_ACTIVATION);
        when(userAccountRepository.findByEmail("cible@esic-connect.test")).thenReturn(Optional.of(target));
        when(roleRepository.findByCode(RoleCode.STUDENT)).thenReturn(Optional.of(role(RoleCode.STUDENT, true)));
        when(userRoleRepository.findByUserId(42L)).thenReturn(List.of());
        when(invitationRepository.findByUserIdAndStatus(42L, AccountInvitationStatus.PENDING)).thenReturn(List.of());
        when(invitationRepository.save(any())).thenAnswer(call -> {
            AccountInvitation saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "publicId", UUID.randomUUID());
            return saved;
        });

        IssueInvitationResponse response = newService().issue("Cible@esic-connect.test", RoleCode.STUDENT, null);

        assertThat(response.invitationId()).isNotNull();
        assertThat(response.expiresAt()).isAfter(Instant.now());
        verify(passwordEncoder, never()).encode(any());
        verify(userRoleRepository).save(any(UserRole.class));

        ArgumentCaptor<AccountInvitation> savedInvitation = ArgumentCaptor.forClass(AccountInvitation.class);
        verify(invitationRepository).save(savedInvitation.capture());
        assertThat(savedInvitation.getValue().getStatus()).isEqualTo(AccountInvitationStatus.PENDING);

        ArgumentCaptor<AccountInvitationIssuedEvent> issued =
                ArgumentCaptor.forClass(AccountInvitationIssuedEvent.class);
        verify(eventPublisher).publishEvent(issued.capture());
        assertThat(issued.getValue().rawToken()).isNotBlank();
        assertThat(issued.getValue().email()).isEqualTo("cible@esic-connect.test");
        // L'empreinte persistée ne doit jamais être le jeton brut.
        assertThat(savedInvitation.getValue()).extracting("tokenHash")
                .isNotEqualTo(issued.getValue().rawToken());
        assertThat(tokenService.hash(issued.getValue().rawToken()))
                .isEqualTo(ReflectionTestUtils.getField(savedInvitation.getValue(), "tokenHash"));

        verify(eventPublisher).publishEvent(any(AccountLifecycleEvent.class));
    }

    @Test
    void issueRejectsUnknownAccount() {
        when(userAccountRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().issue("ghost@esic-connect.test", RoleCode.STUDENT, null))
                .isInstanceOf(InvitationException.class)
                .satisfies(ex -> assertThat(((InvitationException) ex).kind())
                        .isEqualTo(InvitationException.Kind.TARGET_NOT_FOUND));
    }

    @Test
    void issueRejectsAccountThatIsNotPendingActivation() {
        when(userAccountRepository.findByEmail(any())).thenReturn(Optional.of(account(AccountStatus.ACTIVE)));

        assertThatThrownBy(() -> newService().issue("cible@esic-connect.test", RoleCode.STUDENT, null))
                .isInstanceOf(InvitationException.class)
                .satisfies(ex -> assertThat(((InvitationException) ex).kind())
                        .isEqualTo(InvitationException.Kind.TARGET_NOT_PENDING));
    }

    @Test
    void issueRejectsUnknownRole() {
        when(userAccountRepository.findByEmail(any()))
                .thenReturn(Optional.of(account(AccountStatus.PENDING_ACTIVATION)));
        when(roleRepository.findByCode(RoleCode.STUDENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().issue("cible@esic-connect.test", RoleCode.STUDENT, null))
                .isInstanceOf(InvitationException.class)
                .satisfies(ex -> assertThat(((InvitationException) ex).kind())
                        .isEqualTo(InvitationException.Kind.ROLE_INVALID));
    }

    @Test
    void issueRejectsInactiveRole() {
        when(userAccountRepository.findByEmail(any()))
                .thenReturn(Optional.of(account(AccountStatus.PENDING_ACTIVATION)));
        when(roleRepository.findByCode(RoleCode.STUDENT)).thenReturn(Optional.of(role(RoleCode.STUDENT, false)));

        assertThatThrownBy(() -> newService().issue("cible@esic-connect.test", RoleCode.STUDENT, null))
                .isInstanceOf(InvitationException.class)
                .satisfies(ex -> assertThat(((InvitationException) ex).kind())
                        .isEqualTo(InvitationException.Kind.ROLE_INVALID));
    }

    @Test
    void issueRevokesPreviousPendingInvitations() {
        UserAccount target = account(AccountStatus.PENDING_ACTIVATION);
        AccountInvitation previous = new AccountInvitation(target, "old-hash",
                Instant.now().plusSeconds(3600), null);
        when(userAccountRepository.findByEmail(any())).thenReturn(Optional.of(target));
        when(roleRepository.findByCode(RoleCode.STUDENT)).thenReturn(Optional.of(role(RoleCode.STUDENT, true)));
        when(userRoleRepository.findByUserId(42L)).thenReturn(List.of());
        when(invitationRepository.findByUserIdAndStatus(42L, AccountInvitationStatus.PENDING))
                .thenReturn(List.of(previous));
        when(invitationRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        newService().issue("cible@esic-connect.test", RoleCode.STUDENT, null);

        assertThat(previous.getStatus()).isEqualTo(AccountInvitationStatus.REVOKED);
        assertThat(previous.getRevokedAt()).isNotNull();
        verify(invitationRepository).saveAll(List.of(previous));
        verify(invitationRepository).flush();
    }

    @Test
    void issueDoesNotDuplicateAnAlreadyActiveRole() {
        UserAccount target = account(AccountStatus.PENDING_ACTIVATION);
        UserRole existing = new UserRole(target, role(RoleCode.STUDENT, true), Instant.now(), true);
        when(userAccountRepository.findByEmail(any())).thenReturn(Optional.of(target));
        when(roleRepository.findByCode(RoleCode.STUDENT)).thenReturn(Optional.of(role(RoleCode.STUDENT, true)));
        when(userRoleRepository.findByUserId(42L)).thenReturn(List.of(existing));
        when(invitationRepository.findByUserIdAndStatus(42L, AccountInvitationStatus.PENDING)).thenReturn(List.of());
        when(invitationRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        newService().issue("cible@esic-connect.test", RoleCode.STUDENT, null);

        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void activateEncodesPasswordActivatesAccountAndConsumesInvitation() {
        UserAccount target = account(AccountStatus.PENDING_ACTIVATION);
        AccountInvitation invitation = new AccountInvitation(target, tokenService.hash("raw-token"),
                Instant.now().plusSeconds(3600), null);
        when(invitationRepository.findByTokenHash(tokenService.hash("raw-token")))
                .thenReturn(Optional.of(invitation));
        when(passwordEncoder.encode("Str0ngPassw0rd!")).thenReturn("ENCODED");

        newService().activate("raw-token", "Str0ngPassw0rd!");

        assertThat(target.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(target.getPasswordHash()).isEqualTo("ENCODED");
        assertThat(target.getEmailVerifiedAt()).isNotNull();
        assertThat(invitation.getStatus()).isEqualTo(AccountInvitationStatus.ACCEPTED);
        assertThat(invitation.getUsedAt()).isNotNull();

        ArgumentCaptor<AccountLifecycleEvent> event = ArgumentCaptor.forClass(AccountLifecycleEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AccountLifecycleAction.ACCOUNT_ACTIVATED);
        assertThat(event.getValue().actorUserId()).isNull();
    }

    @Test
    void activateRejectsUnknownToken() {
        when(invitationRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().activate("nope", "Str0ngPassw0rd!"))
                .isInstanceOf(InvitationException.class)
                .satisfies(ex -> assertThat(((InvitationException) ex).kind())
                        .isEqualTo(InvitationException.Kind.INVALID_TOKEN));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void activateRejectsExpiredInvitationWithoutTouchingAccount() {
        UserAccount target = account(AccountStatus.PENDING_ACTIVATION);
        AccountInvitation expired = new AccountInvitation(target, tokenService.hash("raw-token"),
                Instant.now().minusSeconds(1), null);
        when(invitationRepository.findByTokenHash(tokenService.hash("raw-token")))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> newService().activate("raw-token", "Str0ngPassw0rd!"))
                .isInstanceOf(InvitationException.class)
                .satisfies(ex -> assertThat(((InvitationException) ex).kind())
                        .isEqualTo(InvitationException.Kind.INVALID_TOKEN));
        assertThat(target.getStatus()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
    }

    @Test
    void activateRejectsAlreadyAcceptedInvitation() {
        UserAccount target = account(AccountStatus.PENDING_ACTIVATION);
        AccountInvitation used = new AccountInvitation(target, tokenService.hash("raw-token"),
                Instant.now().plusSeconds(3600), null);
        used.markAccepted(Instant.now());
        when(invitationRepository.findByTokenHash(tokenService.hash("raw-token")))
                .thenReturn(Optional.of(used));

        assertThatThrownBy(() -> newService().activate("raw-token", "Str0ngPassw0rd!"))
                .isInstanceOf(InvitationException.class)
                .satisfies(ex -> assertThat(((InvitationException) ex).kind())
                        .isEqualTo(InvitationException.Kind.INVALID_TOKEN));
    }

    @Test
    void validateReturnsGenericBooleanOnly() {
        UserAccount target = account(AccountStatus.PENDING_ACTIVATION);
        AccountInvitation usable = new AccountInvitation(target, tokenService.hash("good"),
                Instant.now().plusSeconds(3600), null);
        AccountInvitation expired = new AccountInvitation(target, tokenService.hash("old"),
                Instant.now().minusSeconds(1), null);
        when(invitationRepository.findByTokenHash(tokenService.hash("good"))).thenReturn(Optional.of(usable));
        when(invitationRepository.findByTokenHash(tokenService.hash("old"))).thenReturn(Optional.of(expired));
        when(invitationRepository.findByTokenHash(tokenService.hash("ghost"))).thenReturn(Optional.empty());

        AccountInvitationService service = newService();
        assertThat(service.validate("good").valid()).isTrue();
        assertThat(service.validate("old").valid()).isFalse();
        assertThat(service.validate("ghost").valid()).isFalse();
    }
}
