package com.esic.connect.identity.internal;

import com.esic.connect.identity.WebAuthnCredentialChangedEvent;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.credential.CredentialRecord;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.AuthenticationRequest;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.RegistrationRequest;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionsAuthenticatorOutputs;
import com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Passkeys WebAuthn : enregistrement, connexion sans mot de passe et
 * gestion (EF-AUTH-006, EF-AUTH-007 ; docs/02-cahier-des-charges.md
 * §17.2).
 *
 * <p><strong>Ce que le serveur reçoit — et ne reçoit pas.</strong> Une
 * passkey produit une signature à clé publique. L'authentificateur
 * vérifie l'utilisateur <em>localement</em> — empreinte, visage ou code
 * PIN — et n'envoie que le résultat de cette vérification sous forme d'un
 * bit dans les données d'authentification. Aucune donnée biométrique ne
 * quitte le terminal, et aucune structure de ce module ne pourrait en
 * accueillir (RG-091, AC-020).
 *
 * <p><strong>Défi.</strong> Chaque opération commence par un défi
 * aléatoire de 32 octets, conservé dans Redis avec une durée de vie
 * courte, à usage unique. Sans lui, une signature capturée serait
 * rejouable. Redis portant ici l'autorité de la décision, son
 * indisponibilité produit un refus, jamais un contournement.
 *
 * <p><strong>Compteur de signature.</strong> La valeur connue est
 * transmise au vérificateur et remise à jour après chaque assertion : un
 * authentificateur cloné se trahit par un compteur qui n'avance pas.
 */
@Service
public class WebAuthnService {

    private static final Logger log = LoggerFactory.getLogger(WebAuthnService.class);

    private static final String REGISTRATION_KEY_PREFIX = "esic:webauthn:reg:";
    private static final String AUTHENTICATION_KEY_PREFIX = "esic:webauthn:auth:";
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final int CHALLENGE_BYTES = 32;

    /** ES256 puis RS256 : couvre les authentificateurs du marché. */
    private static final List<WebAuthnWeb.CredentialParameter> SUPPORTED_ALGORITHMS = List.of(
            new WebAuthnWeb.CredentialParameter("public-key", -7),
            new WebAuthnWeb.CredentialParameter("public-key", -257));

    private final WebAuthnCredentialRepository credentialRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final AccessTokenIssuer tokenIssuer;
    private final TrustedDeviceService trustedDeviceService;
    private final WebAuthnProperties properties;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    private final WebAuthnManager webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
    private final ObjectConverter objectConverter = new ObjectConverter();
    private final AttestedCredentialDataConverter attestedCredentialDataConverter =
            new AttestedCredentialDataConverter(objectConverter);
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder urlDecoder = Base64.getUrlDecoder();

    WebAuthnService(WebAuthnCredentialRepository credentialRepository,
                    UserAccountRepository userAccountRepository,
                    UserRoleRepository userRoleRepository,
                    AccessTokenIssuer tokenIssuer,
                    TrustedDeviceService trustedDeviceService,
                    WebAuthnProperties properties,
                    StringRedisTemplate redis,
                    Clock clock,
                    org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.credentialRepository = credentialRepository;
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.tokenIssuer = tokenIssuer;
        this.trustedDeviceService = trustedDeviceService;
        this.properties = properties;
        this.redis = redis;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    // ------------------------------------------------------------------
    // Enregistrement
    // ------------------------------------------------------------------

    /**
     * Options d'enregistrement. Les passkeys déjà enregistrées sont
     * listées en exclusion : l'appareil refuse ainsi d'en créer une
     * seconde pour le même compte, plutôt que de produire un doublon
     * silencieux.
     */
    @Transactional(readOnly = true)
    public WebAuthnWeb.RegistrationOptions registrationOptions(UUID userPublicId) {
        UserAccount account = requireAccount(userPublicId);
        String challenge = newChallenge();
        store(REGISTRATION_KEY_PREFIX + userPublicId, challenge);

        List<WebAuthnWeb.CredentialDescriptor> existing = credentialRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(account.getId(), WebAuthnCredentialStatus.ACTIVE)
                .stream()
                .map(credential -> new WebAuthnWeb.CredentialDescriptor("public-key",
                        credential.getCredentialId()))
                .toList();

        return new WebAuthnWeb.RegistrationOptions(
                challenge,
                new WebAuthnWeb.RelyingParty(properties.relyingPartyId(), properties.relyingPartyName()),
                new WebAuthnWeb.UserEntity(
                        urlEncoder.encodeToString(userPublicId.toString().getBytes(StandardCharsets.UTF_8)),
                        account.getEmail(),
                        account.getFirstName() + " " + account.getLastName()),
                SUPPORTED_ALGORITHMS,
                existing,
                new WebAuthnWeb.AuthenticatorSelection("preferred", "preferred", false),
                properties.timeoutMillis(),
                // Aucune attestation demandée : le produit n'a pas besoin
                // de connaître le modèle d'appareil, et ne veut pas
                // collecter un identifiant matériel (minimisation, docs/02 §33).
                "none");
    }

    /** Vérifie la réponse d'enregistrement et enregistre la passkey. */
    @Transactional
    public WebAuthnWeb.CredentialResponse register(UUID userPublicId,
                                                   WebAuthnWeb.RegistrationRequestBody body) {
        UserAccount account = requireAccount(userPublicId);
        String challenge = consume(REGISTRATION_KEY_PREFIX + userPublicId);

        RegistrationData data;
        try {
            RegistrationRequest request = new RegistrationRequest(
                    urlDecoder.decode(body.response().attestationObject()),
                    urlDecoder.decode(body.response().clientDataJSON()),
                    body.transports() == null ? Set.of() : new HashSet<>(body.transports()));
            RegistrationParameters parameters = new RegistrationParameters(
                    serverProperty(challenge),
                    // La vérification de l'utilisateur reste facultative :
                    // un parcours de secours doit toujours exister (docs/02 §17.2).
                    false, false);
            data = webAuthnManager.verify(request, parameters);
        } catch (RuntimeException verificationFailure) {
            log.info("Enregistrement de passkey refusé : {}",
                    verificationFailure.getClass().getSimpleName());
            throw new WebAuthnException(WebAuthnException.Code.VERIFICATION_FAILED);
        }

        AttestedCredentialData attestedCredentialData =
                data.getAttestationObject().getAuthenticatorData().getAttestedCredentialData();
        if (attestedCredentialData == null) {
            throw new WebAuthnException(WebAuthnException.Code.VERIFICATION_FAILED);
        }
        String credentialId = urlEncoder.encodeToString(attestedCredentialData.getCredentialId());
        long signCount = data.getAttestationObject().getAuthenticatorData().getSignCount();

        WebAuthnCredentialEntity entity = credentialRepository.save(
                WebAuthnCredentialEntity.registered(
                        account.getId(),
                        credentialId,
                        attestedCredentialDataConverter.convert(attestedCredentialData),
                        signCount,
                        label(body.label())));

        publish(new WebAuthnCredentialChangedEvent(account.getId(), account.getPublicId(),
                WebAuthnCredentialChangedEvent.Action.REGISTERED));
        return toResponse(entity);
    }

    // ------------------------------------------------------------------
    // Connexion
    // ------------------------------------------------------------------

    /**
     * Options d'assertion pour une connexion sans mot de passe. Aucune
     * liste de justificatifs n'est renvoyée : elle révélerait, à qui
     * saisit une adresse, si le compte existe et combien de passkeys il
     * possède. Le navigateur propose donc les passkeys découvrables
     * qu'il détient déjà.
     */
    public WebAuthnWeb.AuthenticationOptions authenticationOptions() {
        String challenge = newChallenge();
        String challengeId = newChallenge();
        store(AUTHENTICATION_KEY_PREFIX + challengeId, challenge);
        return new WebAuthnWeb.AuthenticationOptions(
                challengeId,
                challenge,
                properties.relyingPartyId(),
                properties.timeoutMillis(),
                "preferred",
                List.of());
    }

    /** Vérifie l'assertion et délivre le jeton d'accès. */
    @Transactional
    public LoginResponse authenticate(WebAuthnWeb.AuthenticationRequestBody body, String deviceId) {
        String challenge = consume(AUTHENTICATION_KEY_PREFIX + body.challengeId());
        WebAuthnCredentialEntity entity = credentialRepository
                .findByCredentialIdAndStatus(body.id(), WebAuthnCredentialStatus.ACTIVE)
                .orElseThrow(() -> new WebAuthnException(WebAuthnException.Code.VERIFICATION_FAILED));
        UserAccount account = userAccountRepository.findById(entity.getUserId())
                .orElseThrow(() -> new WebAuthnException(WebAuthnException.Code.VERIFICATION_FAILED));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            // Un compte suspendu, verrouillé ou archivé ne se connecte
            // pas, même avec une passkey techniquement encore valide
            // (RG-004). Le refus est indistinguable d'une signature
            // fausse : il ne révèle pas l'état du compte.
            throw new WebAuthnException(WebAuthnException.Code.VERIFICATION_FAILED);
        }

        AuthenticationData data;
        try {
            AuthenticationRequest request = new AuthenticationRequest(
                    urlDecoder.decode(body.id()),
                    body.response().userHandle() == null
                            ? null : urlDecoder.decode(body.response().userHandle()),
                    urlDecoder.decode(body.response().authenticatorData()),
                    urlDecoder.decode(body.response().clientDataJSON()),
                    urlDecoder.decode(body.response().signature()));
            AuthenticationParameters parameters = new AuthenticationParameters(
                    serverProperty(challenge),
                    toCredentialRecord(entity),
                    List.of(urlDecoder.decode(entity.getCredentialId())),
                    false);
            data = webAuthnManager.verify(request, parameters);
        } catch (RuntimeException verificationFailure) {
            log.info("Assertion de passkey refusée : {}",
                    verificationFailure.getClass().getSimpleName());
            throw new WebAuthnException(WebAuthnException.Code.VERIFICATION_FAILED);
        }

        entity.recordUse(clock.instant(), data.getAuthenticatorData().getSignCount());
        credentialRepository.save(entity);
        trustedDeviceService.remember(account.getId(), deviceId);

        List<String> roles = userRoleRepository.findActiveRoleCodesByUserId(account.getId()).stream()
                .map(Enum::name)
                .collect(Collectors.toList());
        return tokenIssuer.issue(account, roles, List.of(AccessTokenIssuer.AMR_WEBAUTHN));
    }

    // ------------------------------------------------------------------
    // Gestion
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<WebAuthnWeb.CredentialResponse> list(UUID userPublicId) {
        UserAccount account = requireAccount(userPublicId);
        return credentialRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(account.getId(), WebAuthnCredentialStatus.ACTIVE)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Révoque une passkey individuellement (RG-008). */
    @Transactional
    public void revoke(UUID userPublicId, UUID credentialPublicId) {
        UserAccount account = requireAccount(userPublicId);
        WebAuthnCredentialEntity entity = credentialRepository
                .findByPublicIdAndUserId(credentialPublicId, account.getId())
                .orElseThrow(() -> new WebAuthnException(WebAuthnException.Code.CREDENTIAL_NOT_FOUND));
        if (entity.getStatus() == WebAuthnCredentialStatus.ACTIVE) {
            entity.revoke(clock.instant());
            credentialRepository.save(entity);
            publish(new WebAuthnCredentialChangedEvent(account.getId(),
                    account.getPublicId(), WebAuthnCredentialChangedEvent.Action.REVOKED));
        }
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private CredentialRecord toCredentialRecord(WebAuthnCredentialEntity entity) {
        AttestedCredentialData attestedCredentialData =
                attestedCredentialDataConverter.convert(entity.getCredentialRecord());
        return new CredentialRecordImpl(
                null,
                null,
                null,
                null,
                entity.getSignatureCount(),
                attestedCredentialData,
                new AuthenticationExtensionsAuthenticatorOutputs<RegistrationExtensionAuthenticatorOutput>(),
                null,
                null,
                null);
    }

    private com.webauthn4j.server.ServerProperty serverProperty(String challenge) {
        Set<Origin> origins = properties.allowedOrigins().stream()
                .map(Origin::new)
                .collect(Collectors.toSet());
        return new com.webauthn4j.server.ServerProperty(origins, properties.relyingPartyId(),
                new DefaultChallenge(urlDecoder.decode(challenge)));
    }

    private String newChallenge() {
        byte[] raw = new byte[CHALLENGE_BYTES];
        random.nextBytes(raw);
        return urlEncoder.encodeToString(raw);
    }

    private void store(String key, String challenge) {
        try {
            redis.opsForValue().set(key, challenge, CHALLENGE_TTL);
        } catch (DataAccessException e) {
            throw new MfaBackendUnavailableException(e);
        }
    }

    /** Lit et supprime le défi : à usage unique, sous peine de rejeu. */
    private String consume(String key) {
        String challenge;
        try {
            challenge = redis.opsForValue().getAndDelete(key);
        } catch (DataAccessException e) {
            throw new MfaBackendUnavailableException(e);
        }
        if (challenge == null) {
            throw new WebAuthnException(WebAuthnException.Code.CHALLENGE_NOT_FOUND);
        }
        return challenge;
    }

    private UserAccount requireAccount(UUID userPublicId) {
        return userAccountRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new WebAuthnException(WebAuthnException.Code.ACCOUNT_NOT_ELIGIBLE));
    }

    private String label(String requested) {
        return Optional.ofNullable(requested)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse("Clé d'accès");
    }

    private WebAuthnWeb.CredentialResponse toResponse(WebAuthnCredentialEntity entity) {
        return new WebAuthnWeb.CredentialResponse(entity.getPublicId(), entity.getLabel(),
                entity.getCreatedAt(), entity.getLastUsedAt());
    }

    /**
     * Publication <strong>synchrone</strong>, dans la transaction
     * courante.
     *
     * <p>Ce point publiait auparavant après commit (DEC-S2-003) parce que
     * l'écouteur d'audit écrivait immédiatement, dans une transaction
     * séparée : publier avant le commit aurait laissé une trace de succès
     * derrière une opération ensuite annulée.
     *
     * <p>Ce détour n'a plus lieu d'être depuis l'outbox transactionnelle
     * (EF-AUD-003) : l'écouteur n'écrit plus rien, il enregistre une
     * intention qui commite — ou disparaît — avec cette transaction. La
     * garantie RG-097 est donc tenue par construction.
     *
     * <p>Le conserver serait de surcroît <em>incorrect</em> : appelé
     * depuis un {@code afterCommit}, l'enregistrement de l'intention
     * participerait à une transaction déjà committée et échouerait —
     * l'audit disparaîtrait en silence.
     */
    private void publish(Object event) {
        eventPublisher.publishEvent(event);
    }
}
