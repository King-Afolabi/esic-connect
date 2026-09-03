package com.esic.connect.identity.internal;

import com.esic.connect.identity.MfaChangedEvent;
import com.esic.connect.shared.ratelimit.IdentityHashing;
import com.esic.connect.shared.ratelimit.RateLimitDecision;
import com.esic.connect.shared.ratelimit.RateLimitExceededException;
import com.esic.connect.shared.ratelimit.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Second facteur TOTP et codes de récupération (EF-AUTH-008, EF-AUTH-009,
 * docs/02-cahier-des-charges.md §17.3).
 *
 * <p><strong>Deux entrées possibles.</strong> L'enrôlement se fait soit
 * depuis une session déjà ouverte (l'utilisateur ajoute volontairement un
 * facteur), soit pendant une connexion bloquée par la politique — un
 * compte {@code ADMIN} sans facteur ne reçoit pas de jeton, il reçoit un
 * défi {@code ENROLL} (AC-021). Dans les deux cas le chemin de vérité est
 * le même ; seul l'acteur est résolu différemment.
 *
 * <p><strong>Anti-rejeu.</strong> Un code TOTP reste valide pendant tout
 * son pas de 30 secondes. Le dernier pas consommé est mémorisé sur le
 * facteur : présenter deux fois le même code échoue, même à l'intérieur
 * de la fenêtre de validité.
 *
 * <p><strong>Anti-force brute.</strong> Un code à six chiffres se devine
 * en un million d'essais. Chaque vérification consomme un seau de
 * limitation indexé sur l'empreinte du compte (RG-092).
 */
@Service
public class MfaService {

    private static final String BUCKET_VERIFY = "mfa-verify";
    /** Nombre de codes de récupération remis d'un coup. */
    private static final int RECOVERY_CODE_COUNT = 10;
    /** Alphabet sans caractères ambigus (0/O, 1/I/L) : le code est recopié à la main. */
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int RECOVERY_GROUP = 5;
    private static final int RECOVERY_GROUPS = 2;

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final MfaCredentialRepository credentialRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final MfaChallengeStore challengeStore;
    private final TrustedDeviceService trustedDeviceService;
    private final MfaPolicy mfaPolicy;
    private final SecretCipher secretCipher;
    private final InvitationTokenService hashing;
    private final AccessTokenIssuer tokenIssuer;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String issuerLabel;
    private final int tolerance;
    private final int verifyLimit;
    private final Duration verifyWindow;

    MfaService(UserAccountRepository userAccountRepository,
               UserRoleRepository userRoleRepository,
               MfaCredentialRepository credentialRepository,
               MfaRecoveryCodeRepository recoveryCodeRepository,
               MfaChallengeStore challengeStore,
               TrustedDeviceService trustedDeviceService,
               MfaPolicy mfaPolicy,
               SecretCipher secretCipher,
               InvitationTokenService hashing,
               AccessTokenIssuer tokenIssuer,
               RateLimiter rateLimiter,
               ApplicationEventPublisher eventPublisher,
               Clock clock,
               @Value("${app.security.mfa.issuer-label:ESIC Connect}") String issuerLabel,
               @Value("${app.security.mfa.step-tolerance:1}") int tolerance,
               @Value("${app.security.mfa.verify-limit:10}") int verifyLimit,
               @Value("${app.security.mfa.verify-window:PT15M}") Duration verifyWindow) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.credentialRepository = credentialRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.challengeStore = challengeStore;
        this.trustedDeviceService = trustedDeviceService;
        this.mfaPolicy = mfaPolicy;
        this.secretCipher = secretCipher;
        this.hashing = hashing;
        this.tokenIssuer = tokenIssuer;
        this.rateLimiter = rateLimiter;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.issuerLabel = issuerLabel;
        this.tolerance = Math.max(0, tolerance);
        this.verifyLimit = verifyLimit;
        this.verifyWindow = verifyWindow;
    }

    // ------------------------------------------------------------------
    // Décision à la connexion
    // ------------------------------------------------------------------

    /**
     * Décide si la connexion doit être suspendue par un second facteur.
     *
     * <p><strong>Authentification adaptative (EF-AUTH-010, docs/02
     * §17.4).</strong> Le facteur est redemandé systématiquement aux
     * comptes dont un rôle l'impose — un administrateur n'est jamais
     * dispensé, quel que soit son appareil (RG-007, AC-021). Pour les
     * autres, un appareil déjà reconnu évite de le redemander à chaque
     * connexion ; un appareil inconnu, lui, le déclenche.
     *
     * @param trustedDevice l'appareil présenté est déjà reconnu pour ce compte
     * @return le défi à résoudre, ou {@link Optional#empty()} si la
     *         connexion peut aboutir immédiatement
     */
    public Optional<LoginResponse.MfaChallengeResponse> challengeFor(UserAccount account,
                                                                    List<String> roleCodes,
                                                                    boolean trustedDevice) {
        boolean hasActiveFactor = credentialRepository.existsByUserIdAndStatus(
                account.getId(), MfaCredentialStatus.ACTIVE);
        boolean requiredByRole = mfaPolicy.isRequiredFor(roleCodes);
        MfaChallengePurpose purpose;
        if (hasActiveFactor && (requiredByRole || !trustedDevice)) {
            purpose = MfaChallengePurpose.VERIFY;
        } else if (hasActiveFactor) {
            // Facteur actif, appareil reconnu, rôle non privilégié : la
            // reconnexion est simplifiée (docs/02 §17.7).
            return Optional.empty();
        } else if (requiredByRole) {
            // Le compte doit enrôler avant d'obtenir un jeton : sans cela,
            // rendre le facteur obligatoire serait impossible à mettre en
            // service sur un compte existant.
            purpose = MfaChallengePurpose.ENROLL;
        } else {
            return Optional.empty();
        }
        String challengeId = challengeStore.open(account.getPublicId(), purpose);
        return Optional.of(new LoginResponse.MfaChallengeResponse(
                challengeId, purpose.name(), challengeStore.ttlSeconds()));
    }

    // ------------------------------------------------------------------
    // Enrôlement
    // ------------------------------------------------------------------

    /**
     * Ouvre un enrôlement et renvoie le secret à recopier dans
     * l'application d'authentification. Le secret n'est visible qu'ici :
     * il n'est plus jamais renvoyé ensuite.
     *
     * <p>Un enrôlement déjà ouvert et non confirmé est remplacé — un
     * utilisateur qui recommence l'écran ne doit pas être bloqué par sa
     * tentative précédente.
     */
    @Transactional
    public MfaWeb.EnrollmentResponse startEnrollment(UUID userPublicId) {
        UserAccount account = requireAccount(userPublicId);
        credentialRepository.findByUserIdAndStatus(account.getId(), MfaCredentialStatus.ACTIVE)
                .ifPresent(active -> {
                    throw new MfaException(MfaException.Code.ALREADY_ENROLLED);
                });
        credentialRepository.findByUserIdAndStatus(account.getId(), MfaCredentialStatus.PENDING)
                .ifPresent(pending -> {
                    pending.revoke(clock.instant());
                    credentialRepository.saveAndFlush(pending);
                });

        String secret = TotpGenerator.newSecret(random);
        MfaCredential credential = MfaCredential.pending(account.getId(), secretCipher.encrypt(secret));
        credentialRepository.save(credential);

        return new MfaWeb.EnrollmentResponse(
                secret,
                TotpGenerator.provisioningUri(issuerLabel, account.getEmail(), secret),
                (int) TotpGenerator.STEP_SECONDS);
    }

    /**
     * Confirme l'enrôlement par un premier code valide : le facteur passe
     * de {@code PENDING} à {@code ACTIVE} et la série de codes de
     * récupération est remise. Ceux-ci ne sont affichés qu'une fois.
     *
     * <p>Activation et remise des codes sont dans la <strong>même</strong>
     * transaction : un compte ne peut pas se retrouver protégé par un
     * facteur dont il n'aurait jamais reçu les codes de secours.
     */
    @Transactional
    public MfaWeb.ConfirmationResult confirmEnrollment(UUID userPublicId, String code) {
        UserAccount account = requireAccount(userPublicId);
        enforceRateLimit(account.getPublicId());
        MfaCredential credential = credentialRepository
                .findByUserIdAndStatus(account.getId(), MfaCredentialStatus.PENDING)
                .orElseThrow(() -> new MfaException(MfaException.Code.NO_PENDING_ENROLLMENT));
        Long step = TotpGenerator.matches(secretCipher.decrypt(credential.getSecretCipher()),
                code, clock.instant(), tolerance);
        if (step == null) {
            throw new MfaException(MfaException.Code.INVALID_CODE);
        }
        credential.confirm(clock.instant(), step);
        credentialRepository.save(credential);
        rateLimiter.reset(BUCKET_VERIFY, IdentityHashing.of(account.getPublicId().toString()));

        List<String> codes = regenerateRecoveryCodesFor(account.getId());
        publishAfterCommit(new MfaChangedEvent(account.getId(), account.getPublicId(),
                MfaChangedEvent.Action.ENROLLED));
        return new MfaWeb.ConfirmationResult(codes);
    }

    // ------------------------------------------------------------------
    // Vérification à la connexion
    // ------------------------------------------------------------------

    /**
     * Consomme un défi {@code VERIFY} avec un code TOTP ou un code de
     * récupération, et délivre le jeton d'accès.
     *
     * <p>Le défi est fermé <strong>dans tous les cas de réussite</strong>
     * et laissé ouvert en cas d'échec de code, pour que l'utilisateur
     * puisse corriger une faute de frappe sans repasser par le mot de
     * passe — la limitation de débit borne ces reprises.
     */
    @Transactional
    public LoginResponse verifyChallenge(String challengeId, String code, String deviceId) {
        MfaChallengeStore.MfaChallenge challenge = challengeStore.peek(challengeId)
                .filter(value -> value.purpose() == MfaChallengePurpose.VERIFY)
                .orElseThrow(() -> new MfaException(MfaException.Code.CHALLENGE_NOT_FOUND));
        UserAccount account = requireAccount(challenge.userPublicId());
        String method = consumeSecondFactor(account, code);
        challengeStore.close(challengeId);
        // L'appareil n'est mémorisé qu'ici : après une authentification
        // complète, jamais après le seul mot de passe.
        trustedDeviceService.remember(account.getId(), deviceId);
        return issueFor(account, List.of(AccessTokenIssuer.AMR_PASSWORD, method));
    }

    /** Mémorise l'appareil d'un compte identifié par son identifiant public. */
    @Transactional
    public void rememberDevice(UUID userPublicId, String deviceId) {
        trustedDeviceService.remember(requireAccount(userPublicId).getId(), deviceId);
    }

    /**
     * Vérifie un second facteur en dehors d'une connexion : soit un code
     * TOTP courant, soit un code de récupération à usage unique.
     *
     * @return le moyen effectivement employé, pour le claim {@code amr}
     */
    @Transactional
    public String consumeSecondFactor(UserAccount account, String code) {
        enforceRateLimit(account.getPublicId());
        Instant now = clock.instant();
        Optional<MfaCredential> maybeCredential = credentialRepository
                .findByUserIdAndStatus(account.getId(), MfaCredentialStatus.ACTIVE);
        if (maybeCredential.isPresent()) {
            MfaCredential credential = maybeCredential.get();
            Long step = TotpGenerator.matches(secretCipher.decrypt(credential.getSecretCipher()),
                    code, now, tolerance);
            if (step != null) {
                if (credential.alreadyUsed(step)) {
                    // Le code est arithmétiquement juste mais son pas a
                    // déjà servi : c'est un rejeu, pas une saisie valide.
                    throw new MfaException(MfaException.Code.CODE_ALREADY_USED);
                }
                credential.recordUse(step);
                credentialRepository.save(credential);
                rateLimiter.reset(BUCKET_VERIFY, IdentityHashing.of(account.getPublicId().toString()));
                return AccessTokenIssuer.AMR_OTP;
            }
        }
        if (consumeRecoveryCode(account, code, now)) {
            rateLimiter.reset(BUCKET_VERIFY, IdentityHashing.of(account.getPublicId().toString()));
            return AccessTokenIssuer.AMR_RECOVERY;
        }
        throw new MfaException(MfaException.Code.INVALID_CODE);
    }

    private boolean consumeRecoveryCode(UserAccount account, String rawCode, Instant now) {
        if (rawCode == null || rawCode.isBlank()) {
            return false;
        }
        String normalized = normalizeRecoveryCode(rawCode);
        Optional<MfaRecoveryCode> maybe = recoveryCodeRepository
                .findByCodeHashAndStatus(hashing.hash(normalized), MfaRecoveryCodeStatus.ACTIVE);
        if (maybe.isEmpty() || !maybe.get().getUserId().equals(account.getId())) {
            return false;
        }
        MfaRecoveryCode recoveryCode = maybe.get();
        recoveryCode.consume(now);
        recoveryCodeRepository.save(recoveryCode);
        publishAfterCommit(new MfaChangedEvent(account.getId(), account.getPublicId(),
                MfaChangedEvent.Action.RECOVERY_CODE_USED));
        return true;
    }

    // ------------------------------------------------------------------
    // Gestion depuis une session ouverte
    // ------------------------------------------------------------------

    /** État du second facteur, sans jamais exposer le secret. */
    @Transactional(readOnly = true)
    public MfaWeb.StatusResponse status(UUID userPublicId) {
        UserAccount account = requireAccount(userPublicId);
        Optional<MfaCredential> active = credentialRepository
                .findByUserIdAndStatus(account.getId(), MfaCredentialStatus.ACTIVE);
        boolean pending = credentialRepository.existsByUserIdAndStatus(
                account.getId(), MfaCredentialStatus.PENDING);
        List<String> roles = activeRoleCodes(account.getId());
        return new MfaWeb.StatusResponse(
                active.isPresent(),
                pending,
                mfaPolicy.isRequiredFor(roles),
                active.map(MfaCredential::getConfirmedAt).orElse(null),
                (int) recoveryCodeRepository.countByUserIdAndStatus(
                        account.getId(), MfaRecoveryCodeStatus.ACTIVE));
    }

    /**
     * Retire le second facteur. Refusé si la politique l'impose pour l'un
     * des rôles détenus : un administrateur ne peut pas se déprotéger
     * (RG-007).
     */
    @Transactional
    public void disable(UUID userPublicId, String code) {
        UserAccount account = requireAccount(userPublicId);
        List<String> roles = activeRoleCodes(account.getId());
        if (mfaPolicy.isRequiredFor(roles)) {
            throw new MfaException(MfaException.Code.REQUIRED_BY_ROLE);
        }
        MfaCredential credential = credentialRepository
                .findByUserIdAndStatus(account.getId(), MfaCredentialStatus.ACTIVE)
                .orElseThrow(() -> new MfaException(MfaException.Code.NOT_ENROLLED));
        consumeSecondFactor(account, code);
        credential.revoke(clock.instant());
        credentialRepository.save(credential);
        recoveryCodeRepository.findByUserIdAndStatus(account.getId(), MfaRecoveryCodeStatus.ACTIVE)
                .forEach(MfaRecoveryCode::revoke);
        publishAfterCommit(new MfaChangedEvent(account.getId(), account.getPublicId(),
                MfaChangedEvent.Action.DISABLED));
    }

    /** Régénère la série complète : les codes précédents sont invalidés. */
    @Transactional
    public List<String> regenerateRecoveryCodes(UUID userPublicId, String code) {
        UserAccount account = requireAccount(userPublicId);
        if (!credentialRepository.existsByUserIdAndStatus(account.getId(), MfaCredentialStatus.ACTIVE)) {
            throw new MfaException(MfaException.Code.NOT_ENROLLED);
        }
        consumeSecondFactor(account, code);
        List<String> codes = regenerateRecoveryCodesFor(account.getId());
        publishAfterCommit(new MfaChangedEvent(account.getId(), account.getPublicId(),
                MfaChangedEvent.Action.RECOVERY_CODES_REGENERATED));
        return codes;
    }

    private List<String> regenerateRecoveryCodesFor(Long userId) {
        recoveryCodeRepository.findByUserIdAndStatus(userId, MfaRecoveryCodeStatus.ACTIVE)
                .forEach(MfaRecoveryCode::revoke);
        List<String> raw = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = newRecoveryCode();
            raw.add(code);
            recoveryCodeRepository.save(
                    MfaRecoveryCode.active(userId, hashing.hash(normalizeRecoveryCode(code))));
        }
        return raw;
    }

    private String newRecoveryCode() {
        StringBuilder builder = new StringBuilder();
        for (int group = 0; group < RECOVERY_GROUPS; group++) {
            if (group > 0) {
                builder.append('-');
            }
            for (int i = 0; i < RECOVERY_GROUP; i++) {
                builder.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
            }
        }
        return builder.toString();
    }

    /** Tirets et casse sont cosmétiques : la comparaison porte sur les seuls caractères utiles. */
    private static String normalizeRecoveryCode(String rawCode) {
        return rawCode.trim().toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "");
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    /** Émet le jeton d'accès une fois le second facteur satisfait. */
    public LoginResponse issueFor(UserAccount account, List<String> methods) {
        return tokenIssuer.issue(account, activeRoleCodes(account.getId()), methods);
    }

    /** Codes des rôles actifs, sous forme de chaînes, comme dans le jeton. */
    private List<String> activeRoleCodes(Long userId) {
        return userRoleRepository.findActiveRoleCodesByUserId(userId).stream()
                .map(Enum::name)
                .toList();
    }

    UserAccount requireAccount(UUID userPublicId) {
        return userAccountRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new MfaException(MfaException.Code.CHALLENGE_NOT_FOUND));
    }

    MfaChallengeStore.MfaChallenge requireChallenge(String challengeId, MfaChallengePurpose purpose) {
        return challengeStore.peek(challengeId)
                .filter(value -> value.purpose() == purpose)
                .orElseThrow(() -> new MfaException(MfaException.Code.CHALLENGE_NOT_FOUND));
    }

    void closeChallenge(String challengeId) {
        challengeStore.close(challengeId);
    }

    private void enforceRateLimit(UUID userPublicId) {
        RateLimitDecision decision = rateLimiter.consume(BUCKET_VERIFY,
                IdentityHashing.of(userPublicId.toString()), verifyLimit, verifyWindow);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfter());
        }
    }

    /**
     * Publie après commit : une transaction annulée ne doit produire
     * aucune trace d'audit (RG-097, décision DEC-S2-003).
     */
    private void publishAfterCommit(Object event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishEvent(event);
                }
            });
        } else {
            eventPublisher.publishEvent(event);
        }
    }
}
