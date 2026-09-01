package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.JustificationFileStorage;
import com.esic.connect.attendance.JustificationFileStorage.PendingUpload;
import com.esic.connect.attendance.JustificationFileStorage.StoredRef;
import com.esic.connect.attendance.JustificationFileStorageException;
import com.esic.connect.attendance.internal.JustificationAttachmentResponses.Download;
import com.esic.connect.attendance.internal.JustificationAttachmentResponses.Meta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Choréographie base ↔ fichier d'une pièce jointe de justificatif
 * (bloc G1-E ; DEC-G1-009). <strong>Aucune atomicité distribuée n'est
 * revendiquée</strong> : séquence avec compensation.
 *
 * <ol>
 *   <li>validation stricte du contenu ({@link JustificationFileSafetyValidator}) ;</li>
 *   <li>génération d'une clé opaque ({@link JustificationFileStorage#newStorageKey()}) ;</li>
 *   <li>insertion {@code PENDING_STORAGE} — transaction courte
 *       {@link JustificationAttachmentPreparer} ({@code REQUIRES_NEW}) ;</li>
 *   <li>déplacement du contenu sous la clé <em>persistée</em>
 *       ({@code store}) — hors transaction SQL ;</li>
 *   <li>vérification SHA-256 / taille réellement écrits ;</li>
 *   <li>bascule {@code STORED} — transaction courte
 *       {@link JustificationAttachmentFinalizer} ({@code REQUIRES_NEW}).</li>
 * </ol>
 *
 * <p><strong>Compensation.</strong> Échec du {@code store} après le
 * commit {@code PENDING_STORAGE} ⇒ la ligne est immédiatement marquée
 * {@code DELETED} (le créneau d'unicité est libéré, l'apprenant peut
 * recommencer) et aucun fichier ne subsiste. Échec de la bascule
 * {@code STORED} ⇒ la ligne reste {@code PENDING_STORAGE} et le fichier
 * en place : la <em>réconciliation</em> ({@link #reconcileOne(long)})
 * finalisera (fichier présent + empreinte cohérente → {@code STORED} ;
 * fichier absent → {@code DELETED} ; incohérence → suppression +
 * {@code DELETED}).
 */
@Component
class JustificationAttachmentStore {

    private static final Logger log = LoggerFactory.getLogger(JustificationAttachmentStore.class);

    private final JustificationAttachmentRepository repository;
    private final JustificationAttachmentPreparer preparer;
    private final JustificationAttachmentFinalizer finalizer;
    private final JustificationFileStorage storage;
    private final Clock clock;
    private final long maxFileBytes;
    private final Duration reconciliationAfter;

    JustificationAttachmentStore(JustificationAttachmentRepository repository,
                                 JustificationAttachmentPreparer preparer,
                                 JustificationAttachmentFinalizer finalizer,
                                 JustificationFileStorage storage,
                                 Clock clock,
                                 @Value("${app.attendance.justification-max-file-bytes:5242880}") long maxFileBytes,
                                 @Value("${app.attendance.justification-reconciliation-after:PT15M}")
                                 Duration reconciliationAfter) {
        if (maxFileBytes <= 0) {
            throw new IllegalStateException("app.attendance.justification-max-file-bytes doit être strictement positif.");
        }
        if (reconciliationAfter.isNegative() || reconciliationAfter.isZero()) {
            throw new IllegalStateException(
                    "app.attendance.justification-reconciliation-after doit être une durée strictement positive.");
        }
        this.repository = repository;
        this.preparer = preparer;
        this.finalizer = finalizer;
        this.storage = storage;
        this.clock = clock;
        this.maxFileBytes = maxFileBytes;
        this.reconciliationAfter = reconciliationAfter;
    }

    long maxFileBytes() {
        return maxFileBytes;
    }

    Duration reconciliationAfter() {
        return reconciliationAfter;
    }

    // ------------------------------------------------------------------
    // Dépôt
    // ------------------------------------------------------------------

    Meta store(long justificationId, long createdById, String fileName, String declaredType, byte[] content) {
        JustificationFileSafetyValidator.Validated validated =
                JustificationFileSafetyValidator.validate(fileName, declaredType, content, maxFileBytes);
        String expectedSha = sha256Hex(content);
        String key = storage.newStorageKey();

        JustificationAttachment pending;
        try {
            pending = preparer.insertPending(justificationId, validated.safeFileName(), key,
                    validated.contentType(), validated.sizeBytes(), expectedSha, createdById, clock.instant());
        } catch (DataIntegrityViolationException duplicate) {
            if (mentionsActiveConstraint(duplicate)) {
                throw new AttendanceException(AttendanceException.Kind.ATTACHMENT_ALREADY_EXISTS);
            }
            throw duplicate;
        }

        StoredRef stored;
        try {
            stored = storage.store(key, new PendingUpload(new ByteArrayInputStream(content), maxFileBytes));
        } catch (RuntimeException storageFailure) {
            // Compensation : la ligne PENDING_STORAGE committée est marquée
            // DELETED (créneau d'unicité libéré) ; aucun fichier ne subsiste
            // (l'adaptateur nettoie son temporaire).
            safeMarkDeleted(pending.getId());
            throw storageFailure;
        }

        if (!expectedSha.equals(stored.sha256()) || stored.sizeBytes() != validated.sizeBytes()) {
            storage.delete(key);
            safeMarkDeleted(pending.getId());
            throw new AttendanceException(AttendanceException.Kind.ATTACHMENT_STORAGE_FAILED);
        }

        try {
            finalizer.markStored(pending.getId(), clock.instant());
        } catch (RuntimeException finalizeFailure) {
            // Le fichier est en place et la ligne PENDING_STORAGE persiste :
            // la réconciliation la fera passer STORED. On signale un échec
            // transitoire plutôt que de prétendre au succès.
            log.warn("Bascule STORED d'une pièce jointe échouée (réconciliation prendra le relais) : cause={}",
                    finalizeFailure.getClass().getSimpleName());
            throw new AttendanceException(AttendanceException.Kind.ATTACHMENT_STORAGE_FAILED);
        }

        JustificationAttachment fresh = repository.findById(pending.getId()).orElseThrow();
        return toMeta(fresh);
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    Meta describeStored(long justificationId) {
        return toMeta(requireStored(justificationId));
    }

    Download open(long justificationId) {
        JustificationAttachment attachment = requireStored(justificationId);
        InputStream content = storage.open(attachment.getStorageKey());
        return new Download(attachment.getOriginalFileName(), attachment.getContentType(),
                attachment.getSizeBytes(), content);
    }

    /**
     * Retrait de la pièce active d'un justificatif (statut {@code DELETED}
     * + suppression du fichier en <em>best effort</em>). Sans effet s'il
     * n'y a pas de pièce active. Libère le créneau d'unicité.
     */
    void deleteActive(long justificationId) {
        JustificationAttachment attachment = repository
                .findByJustificationIdAndStatusNot(justificationId, JustificationAttachmentStatus.DELETED)
                .orElse(null);
        if (attachment == null) {
            return;
        }
        finalizer.markDeleted(attachment.getId(), clock.instant());
        try {
            storage.delete(attachment.getStorageKey());
        } catch (RuntimeException ignored) {
            log.warn("Suppression best-effort du fichier d'une pièce jointe échouée : la réconciliation ignorera "
                    + "les DELETED, le fichier peut subsister.");
        }
    }

    private JustificationAttachment requireStored(long justificationId) {
        JustificationAttachment attachment = repository
                .findByJustificationIdAndStatusNot(justificationId, JustificationAttachmentStatus.DELETED)
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.ATTACHMENT_NOT_FOUND));
        if (!attachment.isStored()) {
            // PENDING_STORAGE : jamais exposée comme pièce disponible.
            throw new AttendanceException(AttendanceException.Kind.ATTACHMENT_NOT_FOUND);
        }
        return attachment;
    }

    // ------------------------------------------------------------------
    // Réconciliation (appelée par JustificationAttachmentReconciliationService)
    // ------------------------------------------------------------------

    /** Résultat d'une réconciliation d'une ligne {@code PENDING_STORAGE}. */
    enum ReconcileOutcome {
        /** Fichier présent + empreinte cohérente → {@code STORED}. */
        FINALISED,
        /** Fichier absent → {@code DELETED} (l'apprenant peut redéposer). */
        DROPPED_MISSING,
        /** Fichier présent mais incohérent → supprimé + {@code DELETED}. */
        DROPPED_CORRUPT,
        /** La ligne n'était plus {@code PENDING_STORAGE} (autre worker). */
        SKIPPED
    }

    /**
     * Réconcilie <strong>une</strong> ligne {@code PENDING_STORAGE}.
     * Non transactionnel : la lecture du fichier est hors transaction ;
     * la bascule d'état passe par {@link JustificationAttachmentFinalizer}
     * ({@code REQUIRES_NEW}). Une {@code OptimisticLockingFailureException}
     * (course avec un autre réconciliateur) remonte et est ignorée par
     * l'appelant.
     */
    ReconcileOutcome reconcileOne(long attachmentId) {
        JustificationAttachment attachment = repository.findById(attachmentId).orElse(null);
        if (attachment == null || !attachment.isPendingStorage()) {
            return ReconcileOutcome.SKIPPED;
        }
        String key = attachment.getStorageKey();
        Instant now = clock.instant();

        String actualSha;
        long actualSize;
        try (InputStream in = storage.open(key)) {
            long[] size = {0};
            actualSha = sha256Hex(in, size);
            actualSize = size[0];
        } catch (JustificationFileStorageException notFound) {
            if (notFound.kind() == JustificationFileStorageException.Kind.NOT_FOUND) {
                finalizer.markDeleted(attachmentId, now);
                return ReconcileOutcome.DROPPED_MISSING;
            }
            throw notFound;
        } catch (IOException io) {
            throw new JustificationFileStorageException(JustificationFileStorageException.Kind.IO_ERROR,
                    "Lecture de la pièce jointe impossible pendant la réconciliation.", io);
        }

        if (actualSha.equals(attachment.getSha256()) && actualSize == attachment.getSizeBytes()) {
            finalizer.markStored(attachmentId, now);
            return ReconcileOutcome.FINALISED;
        }
        storage.delete(key);
        finalizer.markDeleted(attachmentId, now);
        return ReconcileOutcome.DROPPED_CORRUPT;
    }

    // ------------------------------------------------------------------

    private void safeMarkDeleted(long attachmentId) {
        try {
            finalizer.markDeleted(attachmentId, clock.instant());
        } catch (RuntimeException ignored) {
            log.warn("Compensation (markDeleted) d'une pièce jointe échouée : la réconciliation prendra le relais.");
        }
    }

    private static Meta toMeta(JustificationAttachment attachment) {
        return new Meta(attachment.getPublicId(), attachment.getOriginalFileName(), attachment.getContentType(),
                attachment.getSizeBytes(), attachment.getSha256(),
                attachment.getStoredAt() != null ? attachment.getStoredAt() : attachment.getCreatedAt());
    }

    private static boolean mentionsActiveConstraint(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint) {
                String name = constraint.getConstraintName();
                if (name != null && name.toLowerCase(Locale.ROOT)
                        .contains(JustificationAttachmentPreparer.ACTIVE_CONSTRAINT)) {
                    return true;
                }
            }
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT)
                    .contains(JustificationAttachmentPreparer.ACTIVE_CONSTRAINT)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private static String sha256Hex(byte[] content) {
        return HexFormat.of().formatHex(sha256().digest(content));
    }

    private static String sha256Hex(InputStream in, long[] sizeOut) throws IOException {
        MessageDigest digest = sha256();
        byte[] buf = new byte[8192];
        int read;
        long total = 0;
        while ((read = in.read(buf)) != -1) {
            total += read;
            digest.update(buf, 0, read);
        }
        sizeOut[0] = total;
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponible", impossible);
        }
    }
}
