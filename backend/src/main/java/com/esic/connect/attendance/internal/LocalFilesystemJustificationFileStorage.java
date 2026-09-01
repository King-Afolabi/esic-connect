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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
 *       aléatoire, <strong>jamais</strong> dérivée du nom client
 *       ({@link #newStorageKey()}) ;</li>
 *   <li>écriture via fichier temporaire dans {@code <base>/tmp} puis
 *       <strong>déplacement atomique</strong> ({@link StandardCopyOption#ATOMIC_MOVE},
 *       repli sur un déplacement simple si le FS ne le supporte pas) ;
 *       une cible existante n'est <strong>jamais écrasée</strong> ;</li>
 *   <li>taille appliquée <strong>pendant le flux</strong> (pas de
 *       confiance dans un en-tête) ; SHA-256 calculé pendant l'écriture ;</li>
 *   <li>nettoyage du temporaire sur toute erreur — aucun fichier partiel ;</li>
 *   <li>garde anti-traversal + refus des composants symboliques sur
 *       {@code store} / {@code open} / {@code delete} : la clé résolue
 *       doit rester sous la base <em>réelle</em> ({@code toRealPath}).</li>
 * </ul>
 *
 * <p><strong>Limite honnête (TOCTOU).</strong> Entre la résolution de la
 * clé et l'ouverture / le déplacement, un attaquant disposant d'un accès
 * en écriture au répertoire de stockage pourrait substituer un lien
 * symbolique. Le répertoire est configuré hors webroot avec des droits
 * restreints ; cet adaptateur ne prétend pas offrir l'isolation d'un
 * stockage objet (S3) — un déploiement durci substituera un adaptateur
 * objet via le port.
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
        Path configured = Path.of(storagePath).toAbsolutePath().normalize();
        Path stagingDir = configured.resolve("tmp");
        try {
            Files.createDirectories(stagingDir);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de préparer le répertoire de stockage des justificatifs.", e);
        }
        // Base et staging résolus vers leur chemin RÉEL (liens symboliques
        // suivis une seule fois, à l'initialisation) : les gardes
        // anti-traversal comparent ensuite des chemins canoniques.
        try {
            this.base = configured.toRealPath();
            this.tmp = stagingDir.toRealPath();
        } catch (IOException e) {
            throw new IllegalStateException("Répertoire de stockage des justificatifs inaccessible.", e);
        }
        if (!Files.isWritable(base)) {
            throw new IllegalStateException("Le répertoire de stockage des justificatifs n'est pas accessible en écriture.");
        }
    }

    @Override
    public String newStorageKey() {
        String raw = UUID.randomUUID().toString().replace("-", "");
        return raw.substring(0, 2) + "/" + raw.substring(2, 4) + "/" + raw.substring(4);
    }

    @Override
    public StoredRef store(String storageKey, PendingUpload upload) {
        if (upload == null || upload.content() == null) {
            throw new JustificationFileStorageException(Kind.EMPTY, "Contenu de pièce jointe absent.");
        }
        Path target = resolveKey(storageKey);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new JustificationFileStorageException(Kind.IO_ERROR, "Clé de stockage déjà utilisée.");
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

        try {
            Files.createDirectories(target.getParent());
            move(temp, target);
        } catch (FileAlreadyExistsException race) {
            deleteQuietly(temp);
            throw new JustificationFileStorageException(Kind.IO_ERROR, "Clé de stockage déjà utilisée.", race);
        } catch (IOException e) {
            deleteQuietly(temp);
            deleteQuietly(target);
            throw new JustificationFileStorageException(Kind.IO_ERROR, "Déplacement du fichier impossible.", e);
        }
        return new StoredRef(storageKey, total, HexFormat.of().formatHex(digest.digest()));
    }

    @Override
    public InputStream open(String storageKey) {
        Path path = resolveKey(storageKey);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
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

    /** Déplacement atomique si possible, sinon simple — jamais d'écrasement. */
    private void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException notAtomic) {
            Files.move(from, to); // pas de REPLACE_EXISTING : échoue si la cible existe
        }
    }

    /** Résout une clé sous la base avec garde anti-traversal + refus des liens symboliques. */
    private Path resolveKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("..")
                || storageKey.startsWith("/") || storageKey.startsWith("\\")) {
            throw new JustificationFileStorageException(Kind.NOT_FOUND, "Clé de stockage invalide.");
        }
        Path resolved = base.resolve(storageKey).normalize();
        if (!resolved.startsWith(base) || resolved.equals(tmp) || resolved.startsWith(tmp)) {
            throw new JustificationFileStorageException(Kind.NOT_FOUND, "Clé de stockage hors périmètre.");
        }
        // Refus d'un composant symbolique déjà présent sur le chemin (jusqu'à la base).
        for (Path p = resolved; p != null && !p.equals(base); p = p.getParent()) {
            if (Files.isSymbolicLink(p)) {
                throw new JustificationFileStorageException(Kind.NOT_FOUND, "Chemin de stockage non autorisé.");
            }
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
