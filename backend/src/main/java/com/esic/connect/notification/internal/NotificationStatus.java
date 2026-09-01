package com.esic.connect.notification.internal;

/** Cycle de vie d'une notification (G1-D). {@code ARCHIVED} réservé pour une évolution. */
enum NotificationStatus {
    UNREAD,
    READ,
    ARCHIVED
}
