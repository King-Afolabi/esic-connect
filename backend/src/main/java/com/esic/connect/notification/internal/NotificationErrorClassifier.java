package com.esic.connect.notification.internal;

import org.hibernate.exception.ConstraintViolationException;

import java.util.Locale;

/**
 * Classe la cause d'un échec d'écriture d'une ligne {@code notification}
 * (correctif résiduel G1-D.1).
 *
 * <p><strong>Problème corrigé.</strong> Une
 * {@link org.springframework.dao.DataIntegrityViolationException} générique
 * ne prouve pas que c'est la contrainte de déduplication qui a été violée
 * (ce pourrait être {@code uq_notification_public_id},
 * {@code fk_notification_recipient}, un {@code CHECK}…). Une
 * {@link org.springframework.transaction.UnexpectedRollbackException} nue
 * ne prouve <em>aucune</em> collision de {@code dedup_key}.
 *
 * <p><strong>Politique.</strong> Seule une violation <strong>réellement
 * attribuable</strong> à {@code uq_notification_dedup} (course entre deux
 * livraisons du même événement) est un <em>succès idempotent</em>. Toute
 * autre violation d'intégrité, et toute {@code UnexpectedRollbackException}
 * dont la chaîne de causes ne nomme pas cette contrainte, est une
 * <strong>vraie erreur</strong> : journalisée sans donnée personnelle, le
 * destinataire suivant est traité, mais elle n'est jamais assimilée à un
 * doublon.
 *
 * <p>Même approche que
 * {@code attendance.internal.AttendanceRecordPersister#isDuplicateAttendanceViolation} :
 * parcours borné de la chaîne de causes +
 * {@link ConstraintViolationException#getConstraintName()} + message MySQL
 * « Duplicate entry … for key '…uq_notification_dedup' ». Ne se fie
 * jamais à un {@code SQLState} / code MySQL générique seul, qui ne
 * distingue pas les contraintes entre elles.
 */
final class NotificationErrorClassifier {

    /** Nom réel de la contrainte d'unicité de déduplication (migration {@code V15}). */
    static final String DEDUP_CONSTRAINT = "uq_notification_dedup";

    private static final int MAX_DEPTH = 15;

    private NotificationErrorClassifier() {
    }

    /**
     * {@code true} uniquement si la chaîne de causes de {@code error}
     * nomme explicitement {@link #DEDUP_CONSTRAINT} — soit via
     * {@link ConstraintViolationException#getConstraintName()}, soit via un
     * message « Duplicate entry … for key … ». Un rollback inattendu nu,
     * une autre contrainte d'unicité, un code SQL générique ne suffisent
     * pas.
     */
    static boolean isDedupKeyCollision(Throwable error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < MAX_DEPTH; depth++) {
            if (cause instanceof ConstraintViolationException constraint) {
                String name = constraint.getConstraintName();
                if (name != null && name.toLowerCase(Locale.ROOT).contains(DEDUP_CONSTRAINT)) {
                    return true;
                }
            }
            String message = cause.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains(DEDUP_CONSTRAINT)
                        && (lower.contains("duplicate entry") || lower.contains("unique"))) {
                    return true;
                }
            }
            Throwable next = cause.getCause();
            if (next == cause) {
                break;
            }
            cause = next;
        }
        return false;
    }

    /** Nom de classe simple de la cause racine — journalisation sans donnée personnelle. */
    static String rootCauseName(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }
}
