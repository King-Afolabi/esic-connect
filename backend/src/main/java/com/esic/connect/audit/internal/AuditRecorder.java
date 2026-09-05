package com.esic.connect.audit.internal;

import com.esic.connect.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Point d'entrée unique des écouteurs d'audit (EF-AUD-003 ; docs/02
 * §23.4 : « l'écriture d'audit passe par l'outbox transactionnelle »).
 *
 * <p><strong>Ce que ce bean change.</strong> Auparavant, chaque écouteur
 * insérait lui-même sa ligne {@code audit_event} dans une transaction
 * séparée ({@code REQUIRES_NEW}) ouverte <em>avant</em> le commit métier.
 * Deux défauts en découlaient, opposés et tous deux réels :
 *
 * <ul>
 *   <li>une action ensuite annulée laissait quand même sa trace de
 *       succès (RG-097 non tenu) ;</li>
 *   <li>un incident d'écriture perdait la trace sans que rien ne le
 *       signale (dette T-02).</li>
 * </ul>
 *
 * <p>Écrire l'intention <em>dans</em> la transaction métier lève les
 * deux : elle commite avec l'action, disparaît avec son annulation, et
 * le diffuseur garantit qu'elle finira écrite — ou qu'elle sera visible
 * dans la file d'échec.
 *
 * <p>Effet de bord bénéfique : la ligne d'outbox ne porte
 * <strong>aucune clé étrangère</strong> vers {@code user_account}, là où
 * {@code audit_event.actor_user_id} en porte une. Le motif
 * {@code REQUIRES_NEW} devait donc être évité chaque fois que la
 * transaction métier avait déjà verrouillé la ligne du compte concerné —
 * changement de mot de passe, révocation de sessions — sous peine
 * d'attendre un verrou que seule la transaction suspendue pouvait
 * libérer. Ce piège disparaît.
 */
@Component
class AuditRecorder {

    /** Type de message routant vers {@link AuditOutboxHandler}. */
    static final String MESSAGE_TYPE = "AUDIT";

    private final OutboxPublisher outboxPublisher;

    AuditRecorder(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    /**
     * Enregistre l'intention de tracer. Ne prend aucune décision : la
     * ligne suivra le sort de la transaction courante.
     *
     * <p>La clé d'occurrence est un UUID tiré ici. Les écouteurs sont
     * synchrones : un événement métier n'est livré qu'une fois, il n'y a
     * donc pas de redélivrance à dédupliquer. La clé sert l'unicité de la
     * ligne d'outbox et, surtout, l'idempotence du <em>gestionnaire</em> :
     * c'est elle qui est reportée dans {@code audit_event.outbox_key}
     * pour qu'un rejeu n'écrive pas la trace une seconde fois.
     */
    void record(AuditIntent intent) {
        outboxPublisher.enqueue(MESSAGE_TYPE, UUID.randomUUID().toString(), intent.toPayload());
    }
}
