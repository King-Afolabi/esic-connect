package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.JustificationFileStorage;
import com.esic.connect.attendance.JustificationFileStorageException;
import com.esic.connect.attendance.JustificationFileStorageException.Kind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Adaptateur local du port {@link JustificationFileStorage} (bloc G1-E ;
 * DEC-G1-008 / DEC-G1-009). Écrit sous un répertoire configurable
 * (`app.attendance.justification-storage-path`), <strong>hors webroot</strong>,
 * jamais servi statiquement.
 *
 * <ul>
 *   <li>clé de stockage = {@code aa/bb/<uuid>} — dispersion + valeur
 *       aléatoire, <strong>jamais</strong> dérivée du nom client ;</li>
 *   <li>écriture via fichier temporaire dans {@code <base>/tmp} puis
 *       <strong>déplacement atomique</strong> ({@link StandardCopyOption#ATOMIC_MOVE},
 *       repli sur un déplacement simple si le FS ne le supporte pas) ;</li>
 *   <li>taille appliquée <strong>pendant le flux</strong> (pas de
 *       confiance dans un en-tête) ; SHA-256 calculé pendant l'écriture ;</li>
 *   <li>nettoyage du temporaire sur toute erreur — aucun fichier partiel ;</li>
 *   <li>garde anti-traversal sur {@code open} / {@code delete} : la clé
 *       résolue doit rester sous la base.</li>
 * </ul>
 */
@Component
class LocalFilesystemJustificationFileStorage implements JustificationFileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFilesystemJustificationFileStorage.class);
    private static final int BUFFER = 8192;

    private final Path base;
    private final Path tmp;

    LocalFilesystemJustificationFileStorage(
            @Value("${app.attendance.justification-storage-path}") String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalStateException(
                    "app.attendance.justification-storage-path doit être défini (répertoire hors webroot).");
        }
        this.base = Path.of(storagePath).toAbsolutePath().normalize();
        this.tmp = base.resolve("tmp");
        try {
            Files.createDirectories(tmp);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de préparer le répertoire de stockage des justificatifs.", e);
        }
        if (!Files.isWritable(base)) {
            throw new IllegalStateException("Le répertoire de stockage des justificatifs n'est pas accessible en écriture.");
        }
    }

    @Override
    public StoredRef store(PendingUpload upload) {
        if (upload == null || upload.content() == null) {
            throw new JustificationFileStorageException(Kind.EMPTY, "Contenu de pièce jointe absent.");
        }
        Path temp;
        try {
            temp = Files.createTempFile(tmp, "att-", ".part");
        } catch (IOException e) {
            throw new JustificationFileStorageException(Kind.IO_ERROR, "Écriture temporaire impossible.", e);
        }

        MessageDigest digest = sha256();
        long total = 0;
        try (InputStream in = upload.content();
             OutputStream out = Files.newOutputStream(temp)) {
            byte[] buf = new byte[BUFFER];
            int read;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > upload.maxSizeBytes()) {
                    throw new JustificationFileStorageException(Kind.TOO_LARGE,
                            "La pièce jointe dépasse la taille maximale autorisée.");
                }
                digest.update(buf, 0, read);
                out.write(buf, 0, read);
            }
        } catch (JustificationFileStorageException tooLarge) {
            deleteQuietly(temp);
            throw tooLarge;
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new JustificationFileStorageException(Kind.IO_ERROR, "Écriture du fichier impossible.", e);
        }

        if (total == 0) {
            deleteQuietly(temp);
            throw new JustificationFileStorageException(Kind.EMPTY, "La pièce jointe est vide.");
        }

        String key = newStorageKey();
        Path target = resolveKey(key);
        try {
            Files.createDirectories(target.getParent());
            move(temp, target);
        } catch (IOException e) {
            deleteQuietly(temp);
            deleteQuietly(target);
            throw new JustificationFileStorageException(Kind.IO_ERROR, "Déplacement du fichier impossible.", e);
        }
        return new StoredRef(key, total, HexFormat.of().formatHex(digest.digest()));
    }

    @Override
    public InputStream open(String storageKey) {
        Path path = resolveKey(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new JustificationFileStorageException(Kind.NOT_FOUND, "Pièce jointe introuvable.");
        }
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new JustificationFileStorageException(Kind.IO_ERROR, "Lecture de la pièce jointe impossible.", e);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path path = resolveKey(storageKey);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new JustificationFileStorageException(Kind.IO_ERROR, "Suppression de la pièce jointe impossible.", e);
        }
    }

    // ------------------------------------------------------------------

    private void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException notAtomic) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** {@code aa/bb/<uuid>} — dispersion sur deux niveaux, valeur aléatoire. */
    private static String newStorageKey() {
        String raw = UUID.randomUUID().toString().replace("-", "");
        return raw.substring(0, 2) + "/" + raw.substring(2, 4) + "/" + raw.substring(4);
    }

    /** Résout une clé sous la base avec garde anti-traversal stricte. */
    private Path resolveKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("..")
                || storageKey.startsWith("/") || storageKey.startsWith("\\")) {
            throw new JustificationFileStorageException(Kind.NOT_FOUND, "Clé de stockage invalide.");
        }
        Path resolved = base.resolve(storageKey).normalize();
        if (!resolved.startsWith(base) || resolved.equals(tmp) || resolved.startsWith(tmp)) {
            throw new JustificationFileStorageException(Kind.NOT_FOUND, "Clé de stockage hors périmètre.");
        }
        return resolved;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Nettoyage d'un fichier temporaire de pièce jointe impossible (best effort).");
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponible", impossible);
        }
    }
}
