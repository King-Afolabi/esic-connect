package com.esic.connect.attendance;

import com.esic.connect.attendance.JustificationFileStorage.PendingUpload;
import com.esic.connect.attendance.JustificationFileStorage.StoredRef;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bloc G1-E — checkpoint schéma + stockage : le contexte démarre avec la
 * migration <strong>V16</strong> appliquée (Flyway {@code V1→V16},
 * {@code ddl-auto=validate}), la table {@code justification_attachment}
 * existe avec sa colonne générée {@code active_attachment_key}, et le
 * port {@link JustificationFileStorage} est câblé sur l'adaptateur local.
 * (Le dépôt bout-en-bout via HTTP arrive au checkpoint suivant.)
 */
@SpringBootTest
@ActiveProfiles("test")
class JustificationAttachmentSchemaIntegrationTests {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JustificationFileStorage storage;

    @Test
    void v16TableExistsWithItsGeneratedActiveKeyColumn() {
        Integer table = jdbc.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema = database() and table_name = 'justification_attachment'",
                Integer.class);
        assertThat(table).isEqualTo(1);

        Integer generated = jdbc.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = database() and table_name = 'justification_attachment' "
                        + "and column_name = 'active_attachment_key' and extra like '%GENERATED%'",
                Integer.class);
        assertThat(generated).isEqualTo(1);
    }

    @Test
    void theStoragePortIsWiredToTheLocalFilesystemAdapter() throws Exception {
        assertThat(storage.getClass().getSimpleName())
                .isEqualTo("LocalFilesystemJustificationFileStorage");

        byte[] data = "%PDF-1.4 checkpoint G1-E".getBytes(StandardCharsets.UTF_8);
        String key = storage.newStorageKey();
        StoredRef ref = storage.store(key, new PendingUpload(new ByteArrayInputStream(data), 1_000_000));
        assertThat(ref.storageKey()).isEqualTo(key);
        try (InputStream in = storage.open(ref.storageKey())) {
            assertThat(in.readAllBytes()).isEqualTo(data);
        }
        storage.delete(ref.storageKey());
    }
}
