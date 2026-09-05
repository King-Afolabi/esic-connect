package com.esic.connect.notification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Adaptateur de repli : <strong>aucune poussée n'a lieu</strong>, et le
 * produit le déclare (EF-NOTIF-005).
 *
 * <p>Retenu tant qu'aucune paire de clés VAPID n'est configurée. Il ne
 * feint pas un envoi réussi : il renvoie {@link Outcome#INACTIVE}, que le
 * gestionnaire d'outbox traite comme « rien à faire » — et non comme un
 * échec à retenter indéfiniment.
 */
class InactiveWebPushSender implements WebPushSender {

    private static final Logger log = LoggerFactory.getLogger(InactiveWebPushSender.class);

    InactiveWebPushSender() {
        log.warn("Aucune cle VAPID configuree : les notifications poussees sont inactives. "
                + "L'API l'expose (GET /api/v1/me/push/status) — elle ne simule aucun envoi.");
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public Outcome send(String endpoint, String p256dhKey, String authSecret, String payload) {
        return Outcome.INACTIVE;
    }
}
