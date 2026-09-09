package com.esic.connect.outbox.internal;

import com.esic.connect.outbox.OutboxHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Exécute un gestionnaire dans une transaction <strong>neuve</strong>.
 *
 * <p>Le drain immédiat s'exécute dans le {@code afterCommit} de la
 * transaction métier : ses ressources sont encore liées au fil alors que
 * la transaction est déjà committée. Un {@code REQUIRED} y participerait
 * à une transaction morte et échouerait sur « no transaction is in
 * progress ». {@code REQUIRES_NEW} suspend le contexte périmé et en ouvre
 * un propre — c'est la seule propagation correcte à cet endroit.
 *
 * <p>Bean distinct de {@link OutboxDispatcher} pour la même raison que
 * {@link OutboxStore} : un appel interne ne passerait pas par le proxy et
 * l'annotation serait sans effet.
 */
@Component
class OutboxHandlerInvoker {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void invoke(OutboxHandler handler, String messageKey, Map<String, Object> payload) {
        handler.handle(messageKey, payload);
    }
}
