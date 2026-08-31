package com.esic.connect.identity.internal;

import com.esic.connect.identity.AccountInvitationIssuedEvent;
import com.esic.connect.identity.StudentAccountProvisioner;
import com.esic.connect.identity.StudentAccountProvisioningException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implémentation du port {@link StudentAccountProvisioner}. Confinée à
 * {@code identity.internal}. Reprend la logique d'émission d'invitation de
 * {@link AccountInvitationService#issue} <strong>moins</strong> la
 * publication de {@code AccountLifecycleEvent} : l'audit d'un import passe
 * par un unique {@code StudentImportChangeEvent} côté module
 * {@code studentimport} (invariant T5). {@code AccountInvitationService}
 * reste inchangé pour le parcours HTTP mono-compte.
 *
 * <p>Les méthodes d'écriture portent {@code @Transactional} en propagation
 * <strong>{@code REQUIRED}</strong> (jamais {@code REQUIRES_NEW}) : elles
 * rejoignent la transaction unique de la confirmation d'import et sont
 * annulées avec elle en cas d'échec.
 */
@Component
class DefaultStudentAccountProvisioner implements StudentAccountProvisioner {

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final AccountInvitationRepository invitationRepository;
    private final InvitationTokenService tokenService;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration tokenTtl;

    DefaultStudentAccountProvisioner(UserAccountRepository userAccountRepository,
                                     UserRoleRepository userRoleRepository,
                                     RoleRepository roleRepository,
                                     AccountInvitationRepository invitationRepository,
                                     InvitationTokenService tokenService,
                                     ApplicationEventPublisher eventPublisher,
                                     @Value("${app.security.invitation.token-ttl}") Duration tokenTtl) {
        if (tokenTtl == null || tokenTtl.isZero() || tokenTtl.isNegative()) {
            throw new IllegalStateException(
                    "INVITATION_TOKEN_TTL doit être une durée strictement positive (valeur reçue : " + tokenTtl + ").");
        }
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.invitationRepository = invitationRepository;
        this.tokenService = tokenService;
        this.eventPublisher = eventPublisher;
        this.tokenTtl = tokenTtl;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExistingAccountView> findByEmail(String rawEmail) {
        String email = EmailNormalization.normalize(rawEmail);
        if (email == null || email.isEmpty()) {
            return Optional.empty();
        }
        return userAccountRepository.findByEmail(email).map(account -> {
            boolean hasStudentRole = userRoleRepository.findByUserId(account.getId()).stream()
                    .anyMatch(ur -> ur.isActive() && ur.getRole().getCode() == RoleCode.STUDENT);
            return new ExistingAccountView(
                    account.getPublicId(),
                    account.getId(),
                    toStatusView(account.getStatus()),
                    account.getFirstName(),
                    account.getLastName(),
                    account.getPhone(),
                    hasStudentRole);
        });
    }

    @Override
    @Transactional
    public PreparedAccount prepareStudentAccountAndInvitation(NewStudentAccount command, Long issuerUserInternalId) {
        String email = EmailNormalization.normalize(command.rawEmail());
        Instant now = Instant.now();
        Optional<UserAccount> existing = userAccountRepository.findByEmail(email);

        boolean created = false;
        UserAccount account;
        if (existing.isPresent()) {
            account = existing.get();
            if (account.getStatus() != AccountStatus.PENDING_ACTIVATION) {
                throw new StudentAccountProvisioningException(
                        StudentAccountProvisioningException.Reason.ACCOUNT_NOT_USABLE);
            }
        } else {
            account = new UserAccount(email, command.firstName(), command.lastName(),
                    AccountStatus.PENDING_ACTIVATION);
            if (command.phone() != null) {
                account.updatePhone(command.phone(), issuerUserInternalId);
            }
            account.markCreatedBy(issuerUserInternalId);
            account = userAccountRepository.save(account);
            created = true;
        }

        assignStudentRoleIfAbsent(account, issuerUserInternalId, now);
        revokePendingInvitations(account.getId(), now);

        String rawToken = tokenService.generateRawToken();
        Instant expiresAt = now.plus(tokenTtl);
        invitationRepository.save(new AccountInvitation(account, tokenService.hash(rawToken), expiresAt,
                issuerUserInternalId));

        eventPublisher.publishEvent(new AccountInvitationIssuedEvent(
                account.getId(), account.getPublicId(), account.getEmail(), account.getFirstName(),
                rawToken, expiresAt));

        return new PreparedAccount(account.getPublicId(), account.getId(), created, true);
    }

    @Override
    @Transactional
    public void updateStudentPhone(UUID userPublicId, String phone, Long actorUserInternalId) {
        if (userPublicId == null || phone == null) {
            return;
        }
        userAccountRepository.findByPublicId(userPublicId)
                .ifPresent(account -> account.updatePhone(phone, actorUserInternalId));
    }

    private void assignStudentRoleIfAbsent(UserAccount account, Long issuerId, Instant now) {
        boolean alreadyAssigned = userRoleRepository.findByUserId(account.getId()).stream()
                .anyMatch(ur -> ur.isActive() && ur.getRole().getCode() == RoleCode.STUDENT);
        if (alreadyAssigned) {
            return;
        }
        Role role = roleRepository.findByCode(RoleCode.STUDENT)
                .orElseThrow(() -> new IllegalStateException("Rôle STUDENT introuvable (seed V2)."));
        UserRole assignment = new UserRole(account, role, now, true);
        assignment.recordAssignment(issuerId, "Attribue lors d'un import CSV d'apprenants");
        userRoleRepository.save(assignment);
    }

    private void revokePendingInvitations(Long userId, Instant now) {
        List<AccountInvitation> pending =
                invitationRepository.findByUserIdAndStatus(userId, AccountInvitationStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }
        pending.forEach(invitation -> invitation.revoke(now));
        invitationRepository.saveAll(pending);
        // Force l'UPDATE des révocations avant l'INSERT de la nouvelle invitation :
        // sinon l'unicité (user_id, active_invitation_key) serait violée le temps d'un flush.
        invitationRepository.flush();
    }

    private static StatusView toStatusView(AccountStatus status) {
        return switch (status) {
            case PENDING_ACTIVATION -> StatusView.PENDING_ACTIVATION;
            case ACTIVE -> StatusView.ACTIVE;
            case SUSPENDED -> StatusView.SUSPENDED;
            case LOCKED -> StatusView.LOCKED;
            case ARCHIVED -> StatusView.ARCHIVED;
        };
    }
}
