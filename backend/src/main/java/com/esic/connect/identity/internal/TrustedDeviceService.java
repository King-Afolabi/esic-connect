package com.esic.connect.identity.internal;

import com.esic.connect.identity.TrustedDeviceChangedEvent;
import com.esic.connect.shared.ratelimit.IdentityHashing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Appareils de confiance (EF-AUTH-013) et signal d'appareil pour
 * l'authentification adaptative (EF-AUTH-010, docs/02 §17.4 et §17.7).
 *
 * <p>Le client conserve localement un identifiant d'appareil opaque et le
 * présente à la connexion dans l'en-tête {@code X-Device-Id}. Le serveur
 * n'en stocke que l'<strong>empreinte</strong> : l'identifiant brut n'est
 * jamais écrit en base, et une empreinte extraite de la base ne permet
 * pas de reconnaître l'appareil ailleurs.
 *
 * <p><strong>Ce que la confiance donne, et ce qu'elle ne donne pas.</strong>
 * Un appareil reconnu évite de redemander le second facteur à chaque
 * connexion d'un compte pour lequel il n'est pas imposé par le rôle. Elle
 * n'ouvre jamais de session à elle seule : le mot de passe ou la passkey
 * reste exigé. Elle ne dispense jamais un compte privilégié de son second
 * facteur (RG-007).
 */
@Service
public class TrustedDeviceService {

    private final TrustedDeviceRepository repository;
    private final UserAccountRepository userAccountRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final Duration trustDuration;

    TrustedDeviceService(TrustedDeviceRepository repository,
                         UserAccountRepository userAccountRepository,
                         ApplicationEventPublisher eventPublisher,
                         Clock clock,
                         @Value("${app.security.trusted-device.duration:P30D}") Duration trustDuration) {
        if (trustDuration == null || trustDuration.isZero() || trustDuration.isNegative()) {
            throw new IllegalStateException(
                    "app.security.trusted-device.duration doit être une durée strictement positive.");
        }
        this.repository = repository;
        this.userAccountRepository = userAccountRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.trustDuration = trustDuration;
    }

    /**
     * L'appareil présenté est-il déjà reconnu pour ce compte, et sa
     * confiance encore valable ? Un identifiant absent — client qui n'en
     * envoie pas — vaut « inconnu », jamais « de confiance ».
     */
    @Transactional(readOnly = true)
    public boolean isTrusted(Long userId, String rawDeviceId) {
        if (rawDeviceId == null || rawDeviceId.isBlank()) {
            return false;
        }
        return repository.findByUserIdAndDeviceHashAndStatus(
                        userId, IdentityHashing.of(rawDeviceId), TrustedDeviceStatus.ACTIVE)
                .filter(device -> device.isUsableAt(clock.instant()))
                .isPresent();
    }

    /**
     * Mémorise l'appareil après une authentification complète, ou repousse
     * l'échéance d'un appareil déjà connu.
     *
     * <p>Appelé <em>uniquement</em> quand l'authentification a abouti :
     * mémoriser un appareil sur une tentative refusée reviendrait à laisser
     * un attaquant enregistrer son terminal.
     */
    @Transactional
    public void remember(Long userId, String rawDeviceId) {
        if (rawDeviceId == null || rawDeviceId.isBlank()) {
            return;
        }
        Instant now = clock.instant();
        Instant expiry = now.plus(trustDuration);
        String hash = IdentityHashing.of(rawDeviceId);
        Optional<TrustedDevice> existing = repository.findByUserIdAndDeviceHashAndStatus(
                userId, hash, TrustedDeviceStatus.ACTIVE);
        if (existing.isPresent()) {
            existing.get().touch(now, expiry);
            repository.save(existing.get());
            return;
        }
        repository.save(TrustedDevice.remembered(userId, hash, defaultLabel(now), now, expiry));
        userAccountRepository.findById(userId).ifPresent(account ->
                publish(new TrustedDeviceChangedEvent(userId, account.getPublicId(),
                        TrustedDeviceChangedEvent.Action.ADDED)));
    }

    /** Appareils reconnus du compte, le plus récemment vu en tête. */
    @Transactional(readOnly = true)
    public List<TrustedDeviceWeb.DeviceResponse> list(UUID userPublicId) {
        UserAccount account = requireAccount(userPublicId);
        Instant now = clock.instant();
        return repository.findByUserIdAndStatusOrderByLastSeenAtDesc(
                        account.getId(), TrustedDeviceStatus.ACTIVE).stream()
                .map(device -> new TrustedDeviceWeb.DeviceResponse(
                        device.getPublicId(),
                        device.getLabel(),
                        device.getFirstSeenAt(),
                        device.getLastSeenAt(),
                        device.getExpiresAt(),
                        device.isUsableAt(now)))
                .toList();
    }

    /**
     * Révoque un appareil. Le compte ne peut révoquer que ses propres
     * appareils : un identifiant public appartenant à autrui produit un
     * {@code 404}, jamais un {@code 403} — l'existence même de l'appareil
     * d'un tiers est une information à protéger (docs/02 §18.2).
     */
    @Transactional
    public void revoke(UUID userPublicId, UUID devicePublicId) {
        UserAccount account = requireAccount(userPublicId);
        TrustedDevice device = repository.findByPublicIdAndUserId(devicePublicId, account.getId())
                .orElseThrow(() -> new TrustedDeviceNotFoundException(devicePublicId));
        if (device.getStatus() == TrustedDeviceStatus.ACTIVE) {
            device.revoke(clock.instant());
            repository.save(device);
            publish(new TrustedDeviceChangedEvent(account.getId(), account.getPublicId(),
                    TrustedDeviceChangedEvent.Action.REVOKED));
        }
    }

    private UserAccount requireAccount(UUID userPublicId) {
        return userAccountRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new TrustedDeviceNotFoundException(userPublicId));
    }

    /**
     * Libellé par défaut : la date de première reconnaissance. Aucun
     * user-agent n'est repris, il porterait une empreinte de navigateur
     * inutile au besoin.
     */
    private String defaultLabel(Instant now) {
        return "Appareil reconnu le " + now.atZone(clock.getZone()).toLocalDate();
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
