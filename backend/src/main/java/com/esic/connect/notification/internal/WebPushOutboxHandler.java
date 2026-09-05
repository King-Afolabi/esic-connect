package com.esic.connect.notification.internal;

import com.esic.connect.identity.UserDirectory;
import com.esic.connect.outbox.OutboxHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pousse une notification vers les appareils abonnés du destinataire
 * (EF-NOTIF-005 ; docs/02 §29.3).
 *
 * <p><strong>Contenu volontairement pauvre.</strong> Le message poussé ne
 * porte que le titre et le corps déjà neutres du centre de notifications,
 * plus la catégorie : « le contenu poussé ne comporte aucune donnée
 * sensible : il renvoie vers l'application » (§29.3).
 *
 * <p><strong>Quand l'adaptateur est inactif, rien n'est feint.</strong>
 * Le gestionnaire rend la main sans erreur — le message ne part pas et
 * n'encombre pas la file d'échec — parce qu'un déploiement sans clés
 * VAPID est une configuration valide, pas un incident. L'état réel est
 * exposé par l'API et affiché à l'écran.
 */
@Component
class WebPushOutboxHandler implements OutboxHandler {

    /** Type de message d'outbox routant vers ce gestionnaire. */
    static final String MESSAGE_TYPE = "NOTIFICATION_PUSH";

    private static final Logger log = LoggerFactory.getLogger(WebPushOutboxHandler.class);

    private final WebPushSender sender;
    private final PushSubscriptionService subscriptions;
    private final UserDirectory userDirectory;
    private final ObjectMapper objectMapper;

    WebPushOutboxHandler(WebPushSender sender, PushSubscriptionService subscriptions,
                         UserDirectory userDirectory, ObjectMapper objectMapper) {
        this.sender = sender;
        this.subscriptions = subscriptions;
        this.userDirectory = userDirectory;
        this.objectMapper = objectMapper;
    }

    @Override
    public String messageType() {
        return MESSAGE_TYPE;
    }

    @Override
    public boolean transactional() {
        return false;
    }

    @Override
    public void handle(String messageKey, Map<String, Object> payload) {
        if (!sender.isActive()) {
            return;
        }
        UUID recipientPublicId = UUID.fromString(String.valueOf(payload.get("recipientPublicId")));
        UserDirectory.UserRef recipient = userDirectory.findByPublicId(recipientPublicId).orElse(null);
        if (recipient == null || recipient.archived()) {
            return;
        }
        List<PushSubscriptionService.Target> targets = subscriptions.activeTargets(recipient.internalId());
        if (targets.isEmpty()) {
            return;
        }
        String body = serialize(payload);
        int failures = 0;
        for (PushSubscriptionService.Target target : targets) {
            WebPushSender.Outcome outcome =
                    sender.send(target.endpoint(), target.p256dhKey(), target.authSecret(), body);
            switch (outcome) {
                case SENT -> subscriptions.markUsed(target.id());
                // Un abonnement disparu est un fait, pas une panne : le
                // révoquer évite qu'il ne soit retenté à chaque
                // notification, indéfiniment.
                case GONE -> subscriptions.revoke(target.id());
                case FAILED -> failures++;
                case INACTIVE -> { /* déjà écarté plus haut */ }
            }
        }
        if (failures > 0) {
            log.warn("Poussee incomplete : {} appareil(s) en echec.", failures);
            throw new IllegalStateException("poussee incomplete : " + failures + " appareil(s)");
        }
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "title", String.valueOf(payload.get("title")),
                    "body", String.valueOf(payload.get("body")),
                    "category", String.valueOf(payload.get("category"))));
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Contenu de poussee non serialisable", impossible);
        }
    }
}
