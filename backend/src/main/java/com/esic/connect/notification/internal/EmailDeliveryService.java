package com.esic.connect.notification.internal;

import com.esic.connect.shared.ratelimit.IdentityHashing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Suivi de délivrabilité des courriels (EF-USER-008, docs/02 §11.3).
 *
 * <p><strong>Écrit dans une transaction dédiée</strong>
 * ({@code REQUIRES_NEW}) : la trace d'envoi ne doit ni faire échouer
 * l'envoi lui-même, ni disparaître si l'appelant échoue ensuite. C'est
 * une observation, pas une étape métier.
 *
 * <p>Le statut interne et le statut fournisseur sont tenus séparément :
 * voir {@link EmailInternalStatus} et {@link EmailProviderStatus}.
 */
@Service
public class EmailDeliveryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final EmailDeliveryRepository repository;
    private final Clock clock;

    EmailDeliveryService(EmailDeliveryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Ouvre une trace avant tentative d'envoi. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmailDelivery open(String rawEmail, Long userId, String messageType) {
        return repository.save(new EmailDelivery(
                IdentityHashing.of(rawEmail),
                EmailAddressMasking.mask(rawEmail),
                userId,
                messageType));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSent(Long deliveryId) {
        repository.findById(deliveryId).ifPresent(delivery -> {
            delivery.markSentToProvider(clock.instant());
            repository.save(delivery);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long deliveryId, String reason) {
        repository.findById(deliveryId).ifPresent(delivery -> {
            delivery.markFailed(clock.instant(), reason);
            repository.save(delivery);
        });
    }

    /**
     * Journal d'envois, filtrable. Le filtre {@code internalStatus} sert à
     * isoler les échecs à traiter ; {@code providerStatus} à isoler les
     * adresses erronées quand un fournisseur remonte l'information.
     */
    @Transactional(readOnly = true)
    public EmailDeliveryResponses.Page list(String internalStatus, String providerStatus,
                                            String messageType, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page),
                Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Specification<EmailDelivery>> specs = new ArrayList<>();
        parseInternal(internalStatus).ifPresent(status ->
                specs.add((root, query, cb) -> cb.equal(root.get("internalStatus"), status)));
        parseProvider(providerStatus).ifPresent(status ->
                specs.add((root, query, cb) -> cb.equal(root.get("providerStatus"), status)));
        if (messageType != null && !messageType.isBlank()) {
            String type = messageType.trim().toUpperCase(Locale.ROOT);
            specs.add((root, query, cb) -> cb.equal(root.get("messageType"), type));
        }
        Page<EmailDelivery> result = repository.findAll(Specification.allOf(specs), pageable);
        return EmailDeliveryResponses.Page.of(result);
    }

    private java.util.Optional<EmailInternalStatus> parseInternal(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(EmailInternalStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            throw new NotificationException(NotificationException.Kind.INVALID_STATUS);
        }
    }

    private java.util.Optional<EmailProviderStatus> parseProvider(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(EmailProviderStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            throw new NotificationException(NotificationException.Kind.INVALID_STATUS);
        }
    }
}
