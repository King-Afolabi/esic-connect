package com.esic.connect.notification.internal;

import com.esic.connect.identity.UserDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestration de l'écriture des notifications d'un événement métier
 * (G1-D). Résout les destinataires <strong>actifs</strong> et délègue
 * l'écriture d'<em>une ligne par destinataire</em> à
 * {@link NotificationRowWriter} (chacune dans sa propre transaction
 * {@code REQUIRES_NEW}).
 *
 * <p>Aucun {@code @Transactional} ici : cette méthode est appelée par
 * {@link NotificationListener} en {@code AFTER_COMMIT} (hors transaction).
 * Un échec de notification <strong>ne rollbacke jamais</strong> le métier
 * déjà committé.
 *
 * <p><strong>G1-D.1 — isolation par destinataire.</strong> Chaque
 * destinataire est traité indépendamment. La cause d'un échec est classée
 * par {@link NotificationErrorClassifier} : une collision <em>réellement
 * attribuable</em> à {@code uq_notification_dedup} (course entre deux
 * livraisons de l'événement) est un <em>succès idempotent</em> ; toute
 * autre erreur — autre contrainte d'intégrité,
 * {@code UnexpectedRollbackException} nue, base indisponible — est une
 * <strong>vraie erreur</strong>, <em>journalisée sans donnée
 * personnelle</em> (jamais confondue avec un doublon), et le destinataire
 * suivant est quand même traité. Le modèle de livraison reste
 * <strong>« au mieux » après commit</strong> (DEC-G1-007) : sans outbox
 * transactionnelle, un arrêt de la JVM entre le commit métier et
 * l'écriture d'une notification peut perdre l'événement — l'idempotence
 * garantit l'absence de doublon si une reprise est ajoutée plus tard
 * (dette G1-D-OUTBOX).
 */
@Component
public class NotificationWriter {

    private static final Logger log = LoggerFactory.getLogger(NotificationWriter.class);

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
     * Un compte inconnu ou archivé n'est jamais destinataire. L'échec
     * d'un destinataire n'empêche jamais les autres d'être notifiés.
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
            deliverOne(recipient.internalId(), type, title, body, resourceType, resourcePublicId, dedupKey, now);
        }
    }

    private void deliverOne(long recipientInternalId, NotificationType type, String title, String body,
                            String resourceType, UUID resourcePublicId, String dedupKey,
                            java.time.Instant now) {
        try {
            rowWriter.write(recipientInternalId, type, title, body, resourceType, resourcePublicId,
                    dedupKey, now);
        } catch (RuntimeException failure) {
            if (NotificationErrorClassifier.isDedupKeyCollision(failure)) {
                // Course réellement attribuée à `uq_notification_dedup` : une
                // autre livraison du même événement a inséré la ligne entre le
                // pré-contrôle et le flush. Succès idempotent.
                log.debug("Notification deja delivree (course uq_notification_dedup) : type={}", type);
                return;
            }
            // Toute autre erreur (autre contrainte d'intégrité, rollback
            // inattendu nu, base indisponible) : VRAIE erreur — jamais
            // assimilée à un doublon. Journalisée SANS donnée personnelle ; le
            // destinataire suivant du même événement est quand même traité ;
            // aucune exception ne remonte vers le métier déjà committé.
            log.warn("Echec d'ecriture d'une notification (best effort, vraie erreur) : type={}, cause={}",
                    type, NotificationErrorClassifier.rootCauseName(failure));
        }
    }
}
