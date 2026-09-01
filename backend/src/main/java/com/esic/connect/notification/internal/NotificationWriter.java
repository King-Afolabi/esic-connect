package com.esic.connect.notification.internal;

import com.esic.connect.identity.UserDirectory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestration de l'écriture des notifications d'un événement métier
 * (G1-D). Résout les destinataires <strong>actifs</strong> et délègue
 * l'écriture d'<em>une ligne par destinataire</em> à
 * {@link NotificationRowWriter} (chacune dans sa propre transaction
 * {@code REQUIRES_NEW}) : un doublon de {@code dedup_key} n'affecte jamais
 * les autres destinataires.
 *
 * <p>Aucun {@code @Transactional} ici : cette méthode est appelée par
 * {@link NotificationListener} en {@code AFTER_COMMIT} (hors transaction).
 * Un échec de notification <strong>ne rollbacke jamais</strong> le métier
 * déjà committé.
 */
@Component
public class NotificationWriter {

    private final NotificationRowWriter rowWriter;
    private final NotificationRepository repository;
    private final UserDirectory userDirectory;
    private final Clock clock;

    NotificationWriter(NotificationRowWriter rowWriter, NotificationRepository repository,
                       UserDirectory userDirectory, Clock clock) {
        this.rowWriter = rowWriter;
        this.repository = repository;
        this.userDirectory = userDirectory;
        this.clock = clock;
    }

    /**
     * Crée une notification par destinataire <strong>actif</strong>
     * distinct, dédupliquée par {@code dedup_key}. {@code recipientPublicIds}
     * peut contenir des {@code null} et des doublons : ils sont ignorés.
     * Un compte inconnu ou archivé n'est jamais destinataire.
     */
    public void write(NotificationType type, String resourceType, UUID resourcePublicId, UUID eventKey,
                      Set<UUID> recipientPublicIds, String title, String body) {
        if (recipientPublicIds == null || recipientPublicIds.isEmpty()) {
            return;
        }
        var now = clock.instant();
        for (UUID recipientPublicId : recipientPublicIds) {
            if (recipientPublicId == null) {
                continue;
            }
            UserDirectory.UserRef recipient = userDirectory.findByPublicId(recipientPublicId).orElse(null);
            if (recipient == null || recipient.archived()) {
                continue;
            }
            String dedupKey = NotificationDedup.key(type, resourcePublicId, recipient.internalId(), eventKey);
            if (repository.existsByDedupKey(dedupKey)) {
                continue; // rejeu de l'événement : déjà notifié
            }
            rowWriter.write(recipient.internalId(), type, title, body, resourceType, resourcePublicId,
                    dedupKey, now);
        }
    }
}
