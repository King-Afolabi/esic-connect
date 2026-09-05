package com.esic.connect.notification.internal;

/**
 * Canal d'acheminement d'une notification (docs/02 §21.1).
 *
 * <p>{@link #IN_APP} n'apparaît pas dans les préférences : le centre de
 * notifications est la trace consultable de ce qui a été adressé à la
 * personne. Le rendre désactivable transformerait un choix d'affichage en
 * trou dans l'historique (§21.5).
 */
enum NotificationChannel {
    IN_APP,
    EMAIL,
    PUSH
}
