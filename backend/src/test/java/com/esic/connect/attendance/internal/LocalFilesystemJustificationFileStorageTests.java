package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.JustificationFileStorage;
import com.esic.connect.attendance.JustificationFileStorage.PendingUpload;
import com.esic.connect.attendance.JustificationFileStorage.StoredRef;
import com.esic.connect.attendance.JustificationFileStorageException;
import com.esic.connect.attendance.JustificationFileStorageException.Kind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bloc G1-E — adaptateur local du port {@link JustificationFileStorage}
 * (DEC-G1-008). Clé opaque, écriture atomique via temporaire, taille
 * appliquée pendant le flux, SHA-256 calculé, anti-traversal, aucun
 * fichier partiel sur erreur.
 */
class LocalFilesystemJustificationFileStorageTests {

    @TempDir
    Path root;

    private LocalFilesystemJustificationFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFilesystemJustificationFileStorage(root.toString());
    }

    private static PendingUpload upload(byte[] bytes, long max) {
        return new PendingUpload(new ByteArrayInputStream(bytes), max);
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    void storesThenReadsBackTheExactContentWithHashAndSize() throws Exception {
        byte[] data = "%PDF-1.4 contenu fictif de justificatif".getBytes(StandardCharsets.UTF_8);

        StoredRef ref = storage.store(upload(data, 1_000_000));

        assertThat(ref.sizeBytes()).isEqualTo(data.length);
        assertThat(ref.sha256()).isEqualTo(sha256Hex(data));
        assertThat(ref.storageKey()).matches("[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]+");
        try (InputStream in = storage.open(ref.storageKey())) {
            assertThat(in.readAllBytes()).isEqualTo(data);
        }
    }

    @Test
    void storesTheContentOutsideTheTmpStagingDirectory() {
        StoredRef ref = storage.store(upload("%PDF-1.4".getBytes(StandardCharsets.UTF_8), 1000));
        Path stored = root.resolve(ref.storageKey());
        assertThat(Files.exists(stored)).isTrue();
        assertThat(stored.startsWith(root.resolve("tmp"))).isFalse();
        // aucun résidu dans le répertoire de staging
        assertThat(Stream.of(root.resolve("tmp").toFile().listFiles()).count()).isZero();
    }

    @Test
    void rejectsAnEmptyStreamWithoutLeavingAFile() {
        assertThatThrownBy(() -> storage.store(upload(new byte[0], 1000)))
                .isInstanceOfSatisfying(JustificationFileStorageException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.EMPTY));
        assertThat(Stream.of(root.resolve("tmp").toFile().listFiles()).count()).isZero();
    }

    @Test
    void enforcesTheSizeLimitDuringStreamingAndLeavesNoPartialFile() {
        byte[] data = new byte[4096];
        assertThatThrownBy(() -> storage.store(upload(data, 1024)))
                .isInstanceOfSatisfying(JustificationFileStorageException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.TOO_LARGE));
        assertThat(Stream.of(root.resolve("tmp").toFile().listFiles()).count()).isZero();
    }

    @Test
    void openIsNotFoundForAnUnknownKey() {
        assertThatThrownBy(() -> storage.open("ab/cd/does-not-exist"))
                .isInstanceOfSatisfying(JustificationFileStorageException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.NOT_FOUND));
    }

    @Test
    void rejectsPathTraversalKeys() {
        for (String evil : new String[] {"../secret", "../../etc/passwd", "/etc/passwd", "a/../../b"}) {
            assertThatThrownBy(() -> storage.open(evil))
                    .as("open(%s)", evil)
                    .isInstanceOfSatisfying(JustificationFileStorageException.class,
                            e -> assertThat(e.kind()).isEqualTo(Kind.NOT_FOUND));
        }
    }

    @Test
    void deleteIsIdempotent() {
        StoredRef ref = storage.store(upload("%PDF-1.4".getBytes(StandardCharsets.UTF_8), 1000));
        storage.delete(ref.storageKey());
        storage.delete(ref.storageKey()); // pas d'erreur sur une clé déjà absente
        assertThat(Files.exists(root.resolve(ref.storageKey()))).isFalse();
    }

    @Test
    void refusesToBeConstructedWithoutAPath() {
        assertThatThrownBy(() -> new LocalFilesystemJustificationFileStorage("  "))
                .isInstanceOf(IllegalStateException.class);
    }
}
