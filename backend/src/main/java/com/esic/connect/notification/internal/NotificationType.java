package com.esic.connect.notification.internal;

/**
 * Type d'une notification métier persistante (G1-D). Sert de préfixe au
 * libellé et entre dans la clé d'idempotence {@code dedup_key}.
 *
 * <p>Chaque type porte sa {@link NotificationCategory} : c'est elle qui
 * décide des canaux, l'utilisateur réglant ses préférences par catégorie
 * (EF-NOTIF-006).
 */
enum NotificationType {
    /** Une version de planning a été publiée (RG-033). */
    PLANNING_PUBLISHED(NotificationCategory.PLANNING),
    /** Une séance a été annulée (G1-C). */
    SESSION_CANCELLED(NotificationCategory.SESSION),
    /** Un remplaçant a été affecté à une séance (G1-C). */
    SESSION_SUBSTITUTION_ADDED(NotificationCategory.SESSION),
    /** Un remplacement de formateur a pris fin (G1-C). */
    SESSION_SUBSTITUTION_ENDED(NotificationCategory.SESSION),
    /** Le justificatif d'absence de l'apprenant a été accepté (G1-E). */
    JUSTIFICATION_ACCEPTED(NotificationCategory.JUSTIFICATION),
    /** Le justificatif d'absence de l'apprenant a été refusé (G1-E). */
    JUSTIFICATION_REJECTED(NotificationCategory.JUSTIFICATION),
    /** Une réclamation a été déposée et attend le guichet destinataire (T-12). */
    CLAIM_OPENED(NotificationCategory.CLAIM),
    /** Un message a été ajouté au fil d'une réclamation (T-12). */
    CLAIM_MESSAGE_ADDED(NotificationCategory.CLAIM),
    /** Une réclamation a changé d'état — transfert, résolution, réouverture (T-12). */
    CLAIM_STATUS_CHANGED(NotificationCategory.CLAIM);

    private final NotificationCategory category;

    NotificationType(NotificationCategory category) {
        this.category = category;
    }

    NotificationCategory category() {
        return category;
    }
}
