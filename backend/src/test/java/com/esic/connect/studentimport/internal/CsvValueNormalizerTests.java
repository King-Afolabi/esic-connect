package com.esic.connect.studentimport.internal;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Normalisation technique des valeurs de cellule (rapport §5.2, §14.1) :
 * {@code trim}, casse, réduction d'espaces, téléphone, date
 * ({@code yyyy-MM-dd} et {@code dd/MM/yyyy}), booléen tolérant,
 * assainissement du nom de fichier, empreinte, troncature.
 */
class CsvValueNormalizerTests {

    @Test
    void trimsCollapsesAndChangesCase() {
        assertThat(CsvValueNormalizer.trimToNull("  ")).isNull();
        assertThat(CsvValueNormalizer.collapseSpaces("  Van   der  Berg ")).isEqualTo("Van der Berg");
        assertThat(CsvValueNormalizer.lowerCase("  JANE@X.TEST ")).isEqualTo("jane@x.test");
        assertThat(CsvValueNormalizer.upperCase(" bts-sio ")).isEqualTo("BTS-SIO");
    }

    @Test
    void normalizesPhoneByRemovingSeparators() {
        assertThat(CsvValueNormalizer.normalizePhone(" 01 02.03-04(05) ")).isEqualTo("0102030405");
        assertThat(CsvValueNormalizer.normalizePhone("   ")).isNull();
    }

    @Test
    void parsesBothDateFormatsAndFlagsMalformed() {
        assertThat(CsvValueNormalizer.parseBirthDate("2004-05-06").value()).isEqualTo(LocalDate.of(2004, 5, 6));
        assertThat(CsvValueNormalizer.parseBirthDate("06/05/2004").value()).isEqualTo(LocalDate.of(2004, 5, 6));

        CsvValueNormalizer.BirthDateResult absent = CsvValueNormalizer.parseBirthDate("  ");
        assertThat(absent.present()).isFalse();
        assertThat(absent.malformed()).isFalse();

        CsvValueNormalizer.BirthDateResult bad = CsvValueNormalizer.parseBirthDate("le 6 mai");
        assertThat(bad.present()).isTrue();
        assertThat(bad.malformed()).isTrue();
        assertThat(bad.value()).isNull();
    }

    @Test
    void parsesTolerantBooleans() {
        assertThat(CsvValueNormalizer.parseWorkStudy("Oui").value()).isTrue();
        assertThat(CsvValueNormalizer.parseWorkStudy("0").value()).isFalse();
        assertThat(CsvValueNormalizer.parseWorkStudy("").present()).isFalse();

        CsvValueNormalizer.WorkStudyResult bad = CsvValueNormalizer.parseWorkStudy("peut-être");
        assertThat(bad.present()).isTrue();
        assertThat(bad.malformed()).isTrue();
        assertThat(bad.value()).isNull();
    }

    @Test
    void sanitizesFileNameToASafeBasename() {
        assertThat(CsvValueNormalizer.sanitizeFileName("../../etc/passwd.csv")).isEqualTo("passwd.csv");
        assertThat(CsvValueNormalizer.sanitizeFileName("C:\\Temp\\liste des apprenants.csv"))
                .isEqualTo("liste des apprenants.csv");
        assertThat(CsvValueNormalizer.sanitizeFileName("rapport;rm -rf.csv")).isEqualTo("rapport_rm -rf.csv");
        assertThat(CsvValueNormalizer.sanitizeFileName(".hidden")).isEqualTo("hidden");
        assertThat(CsvValueNormalizer.sanitizeFileName(null)).isEqualTo("import.csv");
    }

    @Test
    void hashesContentWithSha256Hex() {
        String hash = CsvValueNormalizer.sha256Hex("abc".getBytes(StandardCharsets.UTF_8));
        assertThat(hash).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void truncatesReceivedValueAndFlattensNewlines() {
        assertThat(CsvValueNormalizer.truncateReceivedValue("a\nb\rc")).isEqualTo("a b c");
        assertThat(CsvValueNormalizer.truncateReceivedValue("x".repeat(250))).hasSize(200);
    }
}
