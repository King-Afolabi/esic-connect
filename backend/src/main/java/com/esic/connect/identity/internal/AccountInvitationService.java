package com.esic.connect.identity.internal;

import com.esic.connect.identity.AccountInvitationIssuedEvent;
import com.esic.connect.identity.AccountLifecycleAction;
import com.esic.connect.identity.AccountLifecycleEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Cycle d'invitation et d'activation d'un compte (cahier §11, §8.3).
 *
 * <ul>
 *   <li>émission réservée aux comptes {@code PENDING_ACTIVATION}, avec
 *       attribution du rôle demandé via {@code user_role} ;</li>
 *   <li>jeton aléatoire à usage unique, stocké uniquement sous forme
 *       d'empreinte SHA-256 ({@link InvitationTokenService}) ;</li>
 *   <li>toute réémission révoque les invitations {@code PENDING}
 *       antérieures ;</li>
 *   <li>validation publique strictement générique (aucune donnée
 *       personnelle exposée) ;</li>
 *   <li>activation = mot de passe encodé + statut {@code ACTIVE} +
 *       invitation {@code ACCEPTED}, le tout audité.</li>
 * </ul>
 */
@Service
public class AccountInvitationService {

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final AccountInvitationRepository invitationRepository;
    private final InvitationTokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration tokenTtl;

    public AccountInvitationService(UserAccountRepository userAccountRepository,
                                   UserRoleRepository userRoleRepository,
                                   RoleRepository roleRepository,
                                   AccountInvitationRepository invitationRepository,
                                   InvitationTokenService tokenService,
                                   PasswordEncoder passwordEncoder,
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
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.tokenTtl = tokenTtl;
    }

    /**
     * Émet (ou réémet) une invitation pour un compte en attente
     * d'activation et lui attribue le rôle demandé.
     *
     * @param issuerSubject sujet du JWT de l'émetteur (identifiant public),
     *                      {@code null} si indéterminé
     */
    @Transactional
    public IssueInvitationResponse issue(String rawEmail, RoleCode roleCode, String issuerSubject) {
        String email = EmailNormalization.normalize(rawEmail);
        UserAccount account = userAccountRepository.findByEmail(email)
                .orElseThrow(() -> new InvitationException(InvitationException.Kind.TARGET_NOT_FOUND));
        if (account.getStatus() != AccountStatus.PENDING_ACTIVATION) {
            throw new InvitationException(InvitationException.Kind.TARGET_NOT_PENDING);
        }
        Role role = roleRepository.findByCode(roleCode)
                .filter(Role::isActive)
                .orElseThrow(() -> new InvitationException(InvitationException.Kind.ROLE_INVALID));

        Long issuerId = resolveIssuerId(issuerSubject);
        Instant now = Instant.now();

        assignRoleIfAbsent(account, role, issuerId, now);
        int revoked = revokePendingInvitations(account.getId(), now);

        String rawToken = tokenService.generateRawToken();
        Instant expiresAt = now.plus(tokenTtl);
        AccountInvitation invitation = invitationRepository.save(
                new AccountInvitation(account, tokenService.hash(rawToken), expiresAt, issuerId));

        eventPublisher.publishEvent(new AccountInvitationIssuedEvent(
                account.getId(), account.getPublicId(), account.getEmail(), account.getFirstName(),
                rawToken, expiresAt));
        eventPublisher.publishEvent(new AccountLifecycleEvent(
                account.getId(), account.getPublicId(), issuerId, AccountLifecycleAction.INVITATION_ISSUED,
                revoked > 0 ? revoked + " invitation(s) anterieure(s) revoquee(s)" : null));

        return new IssueInvitationResponse(invitation.getPublicId(), expiresAt);
    }

    /**
     * Réponse strictement générique : {@code true} seulement si une
     * invitation {@code PENDING} non expirée correspond au jeton. Toute
     * autre situation (inconnu, expiré, révoqué, accepté) renvoie
     * {@code false}, sans aucune donnée personnelle.
     */
    @Transactional(readOnly = true)
    public InvitationValidationResponse validate(String rawToken) {
        boolean valid = invitationRepository.findByTokenHash(tokenService.hash(rawToken))
                .map(invitation -> invitation.isUsableAt(Instant.now()))
                .orElse(false);
        return new InvitationValidationResponse(valid);
    }

    /**
     * Active le compte lié à un jeton valide. Un jeton inconnu, expiré,
     * révoqué ou déjà utilisé produit la même erreur générique
     * {@link InvitationException.Kind#INVALID_TOKEN}.
     */
    @Transactional
    public void activate(String rawToken, String rawPassword) {
        Instant now = Instant.now();
        AccountInvitation invitation = invitationRepository.findByTokenHash(tokenService.hash(rawToken))
                .orElseThrow(() -> new InvitationException(InvitationException.Kind.INVALID_TOKEN));
        if (!invitation.isUsableAt(now)) {
            throw new InvitationException(InvitationException.Kind.INVALID_TOKEN);
        }
        UserAccount account = invitation.getUser();
        if (account.getStatus() != AccountStatus.PENDING_ACTIVATION) {
            throw new InvitationException(InvitationException.Kind.INVALID_TOKEN);
        }

        account.activateWithPassword(passwordEncoder.encode(rawPassword), now);
        invitation.markAccepted(now);
        userAccountRepository.save(account);
        invitationRepository.save(invitation);

        eventPublisher.publishEvent(new AccountLifecycleEvent(
                account.getId(), account.getPublicId(), null, AccountLifecycleAction.ACCOUNT_ACTIVATED, null));
    }

    /**
     * Journal des invitations, filtrable par statut (EF-USER-007).
     *
     * <p>Une invitation {@code PENDING} dont la date d'expiration est
     * passée est présentée comme <strong>expirée</strong>, sans que son
     * statut stocké change : l'expiration se déduit de {@code expires_at}
     * (docs/04 §10.4), et une tâche de balayage n'apporterait rien.
     */
    @Transactional(readOnly = true)
    public PageResponse<InvitationSummaryResponse> list(String statusFilter, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Instant now = Instant.now();
        Page<AccountInvitation> result = parseStatus(statusFilter)
                .map(status -> invitationRepository.findByStatus(status, pageable))
                .orElseGet(() -> invitationRepository.findAll(pageable));
        return PageResponse.of(result, invitation -> InvitationSummaryResponse.from(invitation, now));
    }

    /**
     * Réémet l'invitation d'un compte : la précédente est révoquée, un
     * nouveau jeton est produit, et un nouveau courriel part
     * (EF-USER-007, docs/02 §11.3 : « révoquer l'ancien jeton, en générer
     * un nouveau, relancer l'envoi »).
     *
     * <p>Réémettre est la seule réponse correcte à une adresse corrigée :
     * renvoyer l'ancien jeton à la nouvelle adresse laisserait le premier
     * lien valide dans une boîte qui n'est pas la bonne.
     */
    @Transactional
    public IssueInvitationResponse resend(UUID invitationPublicId, String issuerSubject) {
        AccountInvitation invitation = invitationRepository.findByPublicId(invitationPublicId)
                .orElseThrow(() -> new InvitationException(InvitationException.Kind.TARGET_NOT_FOUND));
        UserAccount account = invitation.getUser();
        if (account.getStatus() != AccountStatus.PENDING_ACTIVATION) {
            // Compte déjà activé, suspendu ou archivé : réémettre n'aurait
            // aucun sens et rouvrirait un chemin d'activation.
            throw new InvitationException(InvitationException.Kind.TARGET_NOT_PENDING);
        }

        Long issuerId = resolveIssuerId(issuerSubject);
        Instant now = Instant.now();
        int revoked = revokePendingInvitations(account.getId(), now);

        String rawToken = tokenService.generateRawToken();
        Instant expiresAt = now.plus(tokenTtl);
        AccountInvitation reissued = invitationRepository.save(
                new AccountInvitation(account, tokenService.hash(rawToken), expiresAt, issuerId));

        eventPublisher.publishEvent(new AccountInvitationIssuedEvent(
                account.getId(), account.getPublicId(), account.getEmail(), account.getFirstName(),
                rawToken, expiresAt));
        eventPublisher.publishEvent(new AccountLifecycleEvent(
                account.getId(), account.getPublicId(), issuerId,
                AccountLifecycleAction.INVITATION_ISSUED,
                "Reemission" + (revoked > 0 ? " (" + revoked + " revoquee(s))" : "")));

        return new IssueInvitationResponse(reissued.getPublicId(), expiresAt);
    }

    private Optional<AccountInvitationStatus> parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(AccountInvitationStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            throw new InvitationException(InvitationException.Kind.ROLE_INVALID);
        }
    }

    private void assignRoleIfAbsent(UserAccount account, Role role, Long issuerId, Instant now) {
        boolean alreadyAssigned = userRoleRepository.findByUserId(account.getId()).stream()
                .anyMatch(userRole -> userRole.isActive() && userRole.getRole().getCode() == role.getCode());
        if (alreadyAssigned) {
            return;
        }
        UserRole assignment = new UserRole(account, role, now, true);
        assignment.recordAssignment(issuerId, "Attribue lors de l'emission de l'invitation");
        userRoleRepository.save(assignment);
    }

    private int revokePendingInvitations(Long userId, Instant now) {
        List<AccountInvitation> pending =
                invitationRepository.findByUserIdAndStatus(userId, AccountInvitationStatus.PENDING);
        if (pending.isEmpty()) {
            return 0;
        }
        pending.forEach(invitation -> invitation.revoke(now));
        invitationRepository.saveAll(pending);
        // Force l'UPDATE des révocations avant l'INSERT de la nouvelle
        // invitation : sinon l'unicité (user_id, active_invitation_key)
        // serait violée le temps d'un flush.
        invitationRepository.flush();
        return pending.size();
    }

    private Long resolveIssuerId(String issuerSubject) {
        if (issuerSubject == null || issuerSubject.isBlank()) {
            return null;
        }
        try {
            return userAccountRepository.findByPublicId(UUID.fromString(issuerSubject))
                    .map(UserAccount::getId)
                    .orElse(null);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }
}
