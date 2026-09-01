package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.internal.JustificationAttachmentValidationException.Kind;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bloc G1-E — validation d'un fichier de pièce jointe <em>avant</em>
 * écriture (DEC-G1-009 étape 2 ; CDC §21.5). Composant pur : magic bytes,
 * extension, type déclaré, taille, cohérence extension ↔ contenu,
 * assainissement du nom.
 */
class JustificationFileSafetyValidatorTests {

    private static final long MAX = 5 * 1024 * 1024;

    private static byte[] pdf() {
        byte[] head = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[head.length + 32];
        System.arraycopy(head, 0, out, 0, head.length);
        return out;
    }

    private static byte[] jpeg() {
        byte[] out = new byte[64];
        out[0] = (byte) 0xFF;
        out[1] = (byte) 0xD8;
        out[2] = (byte) 0xFF;
        out[3] = (byte) 0xE0;
        return out;
    }

    private static byte[] png() {
        return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    }

    @Test
    void acceptsAPdfAndRederivesTheContentType() {
        JustificationFileSafetyValidator.Validated v = JustificationFileSafetyValidator.validate(
                "justificatif.pdf", "application/pdf", pdf(), MAX);
        assertThat(v.contentType()).isEqualTo("application/pdf");
        assertThat(v.safeFileName()).isEqualTo("justificatif.pdf");
        assertThat(v.sizeBytes()).isEqualTo(pdf().length);
    }

    @Test
    void acceptsAJpegDeclaredAsOctetStream() {
        JustificationFileSafetyValidator.Validated v = JustificationFileSafetyValidator.validate(
                "photo.JPG", "application/octet-stream", jpeg(), MAX);
        assertThat(v.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void acceptsAPngWithNoDeclaredType() {
        JustificationFileSafetyValidator.Validated v = JustificationFileSafetyValidator.validate(
                "scan.png", null, png(), MAX);
        assertThat(v.contentType()).isEqualTo("image/png");
    }

    @Test
    void rejectsAnEmptyFile() {
        assertThatThrownBy(() -> JustificationFileSafetyValidator.validate("x.pdf", "application/pdf", new byte[0], MAX))
                .isInstanceOfSatisfying(JustificationAttachmentValidationException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.EMPTY));
    }

    @Test
    void rejectsAFileOverTheSizeLimit() {
        byte[] big = pdf();
        assertThatThrownBy(() -> JustificationFileSafetyValidator.validate("x.pdf", "application/pdf", big, 4))
                .isInstanceOfSatisfying(JustificationAttachmentValidationException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.TOO_LARGE));
    }

    @Test
    void rejectsAnUnsupportedExtension() {
        assertThatThrownBy(() -> JustificationFileSafetyValidator.validate("payload.exe", null, pdf(), MAX))
                .isInstanceOfSatisfying(JustificationAttachmentValidationException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.EXTENSION_NOT_ALLOWED));
    }

    @Test
    void rejectsADangerousDoubleExtension() {
        // basename se termine par .exe -> extension refusée avant même le contenu
        assertThatThrownBy(() -> JustificationFileSafetyValidator.validate("justificatif.pdf.exe", null, pdf(), MAX))
                .isInstanceOfSatisfying(JustificationAttachmentValidationException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.EXTENSION_NOT_ALLOWED));
    }

    @Test
    void rejectsADeclaredTypeOutsideTheToleratedSet() {
        assertThatThrownBy(() -> JustificationFileSafetyValidator.validate("x.pdf", "text/html", pdf(), MAX))
                .isInstanceOfSatisfying(JustificationAttachmentValidationException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.DECLARED_TYPE_NOT_ALLOWED));
    }

    @Test
    void rejectsAZipDisguisedAsPdf() {
        byte[] zip = {0x50, 0x4B, 0x03, 0x04, 1, 2, 3, 4, 5, 6};
        assertThatThrownBy(() -> JustificationFileSafetyValidator.validate("archive.pdf", null, zip, MAX))
                .isInstanceOfSatisfying(JustificationAttachmentValidationException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.ARCHIVE_REJECTED));
    }

    @Test
    void rejectsUnrecognisedContent() {
        byte[] text = "just some text, not a document".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> JustificationFileSafetyValidator.validate("note.pdf", null, text, MAX))
                .isInstanceOfSatisfying(JustificationAttachmentValidationException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.CONTENT_NOT_RECOGNISED));
    }

    @Test
    void rejectsAPngExtensionCarryingAPdf() {
        assertThatThrownBy(() -> JustificationFileSafetyValidator.validate("fake.png", null, pdf(), MAX))
                .isInstanceOfSatisfying(JustificationAttachmentValidationException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.EXTENSION_CONTENT_MISMATCH));
    }

    @Test
    void sanitizesTheOriginalFileNameToItsBasenameWithoutControlChars() {
        JustificationFileSafetyValidator.Validated v = JustificationFileSafetyValidator.validate(
                "../../etc/passwd.pdf", "application/pdf", pdf(), MAX);
        assertThat(v.safeFileName()).isEqualTo("passwd.pdf");
        assertThat(v.safeFileName()).doesNotContain("/").doesNotContain("..");
    }

    @Test
    void fallsBackToAPlaceholderWhenTheNameIsAllPath() {
        assertThat(JustificationFileSafetyValidator.sanitizeFileName("/tmp/dir/")).isEqualTo("piece-jointe");
        assertThat(JustificationFileSafetyValidator.sanitizeFileName("..")).isEqualTo("piece-jointe");
        assertThat(JustificationFileSafetyValidator.sanitizeFileName(null)).isEqualTo("piece-jointe");
    }
}
