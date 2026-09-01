package com.esic.connect.notification.internal;

/**
 * Type d'une notification métier persistante (G1-D). Sert de préfixe au
 * libellé et entre dans la clé d'idempotence {@code dedup_key}.
 */
enum NotificationType {
    /** Une version de planning a été publiée (RG-033). */
    PLANNING_PUBLISHED,
    /** Une séance a été annulée (G1-C). */
    SESSION_CANCELLED,
    /** Un remplaçant a été affecté à une séance (G1-C). */
    SESSION_SUBSTITUTION_ADDED,
    /** Un remplacement de formateur a pris fin (G1-C). */
    SESSION_SUBSTITUTION_ENDED,
    /** Le justificatif d'absence de l'apprenant a été accepté (G1-E). */
    JUSTIFICATION_ACCEPTED,
    /** Le justificatif d'absence de l'apprenant a été refusé (G1-E). */
    JUSTIFICATION_REJECTED
}
