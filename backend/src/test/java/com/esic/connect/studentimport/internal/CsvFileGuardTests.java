package com.esic.connect.studentimport.internal;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrôles de sécurité du fichier téléversé (rapport §5.1, §5.4, §10,
 * §14.1) : extension, type MIME, magie binaire, taille, encodage UTF-8
 * strict, retrait du BOM. Aucun accès disque, aucun accès métier.
 */
class CsvFileGuardTests {

    private static final long MAX = 2_097_152L;

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static StudentImportException.Kind kindOf(Runnable call) {
        try {
            call.run();
        } catch (StudentImportException ex) {
            return ex.kind();
        }
        throw new AssertionError("StudentImportException attendue");
    }

    @Test
    void acceptsAPlainCsvAndReturnsTheDecodedContent() {
        String decoded = CsvFileGuard.decodeAndValidate("apprenants.csv", "text/csv",
                utf8("last_name,first_name\nDoe,Jane\n"), MAX);
        assertThat(decoded).startsWith("last_name,first_name");
    }

    @Test
    void stripsAUtf8Bom() {
        byte[] withBom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = utf8("email\nx@y.z\n");
        byte[] content = new byte[withBom.length + body.length];
        System.arraycopy(withBom, 0, content, 0, withBom.length);
        System.arraycopy(body, 0, content, withBom.length, body.length);
        assertThat(CsvFileGuard.decodeAndValidate("f.csv", "text/csv", content, MAX)).startsWith("email");
    }

    @Test
    void rejectsANonCsvExtension() {
        assertThat(kindOf(() -> CsvFileGuard.decodeAndValidate("apprenants.xlsx", "text/csv", utf8("a,b\n"), MAX)))
                .isEqualTo(StudentImportException.Kind.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void rejectsAnUntoleratedContentType() {
        assertThat(kindOf(() -> CsvFileGuard.decodeAndValidate("f.csv", "application/zip", utf8("a,b\n"), MAX)))
                .isEqualTo(StudentImportException.Kind.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void toleratesAMissingContentType() {
        assertThat(CsvFileGuard.decodeAndValidate("f.csv", null, utf8("email\nx@y.z\n"), MAX)).contains("email");
        assertThat(CsvFileGuard.decodeAndValidate("f.csv", "  ", utf8("email\nx@y.z\n"), MAX)).contains("email");
    }

    @Test
    void rejectsAZipXlsxByMagicNumber() {
        byte[] zip = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};
        assertThat(kindOf(() -> CsvFileGuard.decodeAndValidate("f.csv", "text/csv", zip, MAX)))
                .isEqualTo(StudentImportException.Kind.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void rejectsAnOle2OrPdfContainer() {
        byte[] ole = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0x00};
        byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        assertThat(kindOf(() -> CsvFileGuard.decodeAndValidate("f.csv", "text/csv", ole, MAX)))
                .isEqualTo(StudentImportException.Kind.UNSUPPORTED_MEDIA_TYPE);
        assertThat(kindOf(() -> CsvFileGuard.decodeAndValidate("f.csv", "text/csv", pdf, MAX)))
                .isEqualTo(StudentImportException.Kind.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void rejectsContentWithANulByte() {
        byte[] binary = {'a', ',', 'b', 0x00, '\n'};
        assertThat(kindOf(() -> CsvFileGuard.decodeAndValidate("f.csv", "text/csv", binary, MAX)))
                .isEqualTo(StudentImportException.Kind.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void rejectsInvalidUtf8() {
        byte[] invalid = {'e', 'm', 'a', 'i', 'l', '\n', (byte) 0xFF, (byte) 0xFE, '\n'};
        assertThat(kindOf(() -> CsvFileGuard.decodeAndValidate("f.csv", "text/csv", invalid, MAX)))
                .isEqualTo(StudentImportException.Kind.ENCODING_INVALID);
    }

    @Test
    void rejectsAFileOverTheSizeLimit() {
        byte[] big = new byte[11];
        assertThat(kindOf(() -> CsvFileGuard.decodeAndValidate("f.csv", "text/csv", big, 10)))
                .isEqualTo(StudentImportException.Kind.FILE_TOO_LARGE);
    }

    @Test
    void rejectsAnEmptyFile() {
        assertThatThrownBy(() -> CsvFileGuard.decodeAndValidate("f.csv", "text/csv", new byte[0], MAX))
                .isInstanceOf(StudentImportException.class);
    }
}
